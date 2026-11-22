package com.groundmarkervariables.chatbox;

import com.groundmarkervariables.markers.DeletedMarkers;
import com.groundmarkervariables.markers.GroundMarkerPointData;
import com.groundmarkervariables.markers.MarkerStorage;
import com.groundmarkervariables.variables.support.RegionNames;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.FontTypeFace;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.game.chatbox.ChatboxInput;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.util.Text;

// ::manageGroundMarkerVariables -- every saved marker, grouped by area name (current area first),
// with same-label markers in an area combined into one row. Rows and region headers have
// checkboxes; the top bar filters to markers with variables, selects all shown, and exports or deletes the selection.
public class MarkerManager extends ChatboxInput implements KeyListener
{
	private static final int LINE_HEIGHT = 20;
	private static final int ROW_HEIGHT = 16;
	private static final int ENTRY_CHECKBOX_SIZE = 12;
	private static final int BUTTON_FONT = FontID.PLAIN_11;
	private static final int RESULTS_Y = 30;
	// Fixed, like AdvancedLabelEditor -- fills the chatbox down to where its own list ends.
	private static final int VISIBLE_ROWS = 6;
	private static final int TOP_BAR_GAP = 8;
	private static final int ACTIVE_COLOR = 0x800000;
	private static final Pattern HAS_VARIABLE = Pattern.compile("\\{.*\\}", Pattern.DOTALL);

	private final ChatboxPanelManager chatboxPanelManager;
	private final MarkerStorage markerStorage;
	private final ChatMessageManager chatMessageManager;
	private final Client client;
	private final DeletedMarkers deletedMarkers;
	private final GroundMarkerVariablesSharingManager sharingManager;
	private final ClientThread clientThread;

	private final List<Entry> entries = new ArrayList<>();
	private final Set<Entry> selected = new HashSet<>();
	private int scrollOffset;
	// { } toggle: only rows whose label has a variable (and the areas containing them).
	private boolean variablesOnly = true;
	// Typed while open: only rows whose area name or label contains it, any case.
	private volatile String query = "";

	// Every marker sharing one RegionNames name, or the unnamed ones of a single region.
	private static final class Group
	{
		private final String key;
		private final String name;
		private final Set<Integer> regionIds = new TreeSet<>();
		private final Map<String, Entry> byLabel = new LinkedHashMap<>();

		private Group(String key, String name)
		{
			this.key = key;
			this.name = name;
		}
	}

	// Every marker in one group sharing the same label ("" for none).
	private static final class Entry
	{
		private final Group group;
		private final String label;
		private final List<GroundMarkerPointData> points = new ArrayList<>();

		private Entry(Group group, String label)
		{
			this.group = group;
			this.label = label;
		}
	}

	// A group header (entry == null) or one Entry.
	private static final class Row
	{
		private final Group group;
		private final Entry entry;

		private Row(Group group, Entry entry)
		{
			this.group = group;
			this.entry = entry;
		}
	}

	@Inject
	MarkerManager(ChatboxPanelManager chatboxPanelManager, MarkerStorage markerStorage, ChatMessageManager chatMessageManager,
		Client client, DeletedMarkers deletedMarkers, GroundMarkerVariablesSharingManager sharingManager, ClientThread clientThread)
	{
		this.clientThread = clientThread;
		this.chatboxPanelManager = chatboxPanelManager;
		this.markerStorage = markerStorage;
		this.chatMessageManager = chatMessageManager;
		this.client = client;
		this.deletedMarkers = deletedMarkers;
		this.sharingManager = sharingManager;
	}

	// Rebuilds the list from storage, clearing the selection.
	public void load()
	{
		entries.clear();
		selected.clear();
		scrollOffset = 0;

		Map<String, Group> groups = new LinkedHashMap<>();
		for (int regionId : markerStorage.storedRegionIds())
		{
			// Each marker's own area: one region can hold several.
			for (GroundMarkerPointData point : markerStorage.getStoredPoints(regionId))
			{
				String name = RegionNames.name(WorldPoint.fromRegion(regionId, point.getRegionX(), point.getRegionY(), point.getZ()));
				Group group = groups.computeIfAbsent(groupKey(name, regionId), k -> new Group(k, name));
				group.regionIds.add(regionId);
				String label = point.getLabel() == null ? "" : point.getLabel();
				group.byLabel.computeIfAbsent(label, l -> new Entry(group, l)).points.add(point);
			}
		}

		// Current area first, then named areas alphabetically, then unnamed regions by id.
		String currentGroup = currentGroupKey();
		List<Group> sorted = new ArrayList<>(groups.values());
		sorted.sort(Comparator.comparing((Group g) -> !g.key.equals(currentGroup))
			.thenComparing(g -> g.name == null)
			.thenComparing(g -> g.name == null ? "" : g.name, String.CASE_INSENSITIVE_ORDER)
			.thenComparing(g -> g.regionIds.iterator().next()));

		for (Group group : sorted)
		{
			List<Entry> groupEntries = new ArrayList<>(group.byLabel.values());
			groupEntries.sort(Comparator.comparing((Entry e) -> e.label.isEmpty()).thenComparing(e -> e.label, String.CASE_INSENSITIVE_ORDER));
			entries.addAll(groupEntries);
		}
	}

	@Override
	protected void open()
	{
		update();
	}

	// Escape clears the search, then closes; Backspace removes a character from it.
	@Override
	public void keyPressed(KeyEvent ev)
	{
		if (!chatboxPanelManager.shouldTakeInput())
		{
			return;
		}
		if (ev.getKeyCode() == KeyEvent.VK_ESCAPE)
		{
			ev.consume();
			if (query.isEmpty())
			{
				chatboxPanelManager.close();
			}
			else
			{
				search(query -> "");
			}
		}
		else if (ev.getKeyCode() == KeyEvent.VK_BACK_SPACE && !query.isEmpty())
		{
			ev.consume();
			search(query -> query.isEmpty() ? query : query.substring(0, query.length() - 1));
		}
	}

	@Override
	public void keyTyped(KeyEvent ev)
	{
		char c = ev.getKeyChar();
		if (chatboxPanelManager.shouldTakeInput() && !Character.isISOControl(c) && !ev.isControlDown() && !ev.isAltDown())
		{
			ev.consume();
			search(query -> query + c);
		}
	}

	// Keys arrive on the AWT thread; the search changes, and widgets are built, on the client thread.
	private void search(UnaryOperator<String> change)
	{
		clientThread.invoke(() ->
		{
			query = change.apply(query);
			scrollOffset = 0;
			update();
		});
	}

	@Override
	public void keyReleased(KeyEvent ev)
	{
	}

	private void update()
	{
		Widget container = chatboxPanelManager.getContainerWidget();
		container.deleteAllChildren();

		String search = query.isEmpty() ? " <col=808080>type to search</col>" : ": <col=000000>" + Text.escapeJagex(query) + "</col>";
		ChatboxWidgets.text(container, "Ground Markers (" + markerCount(visibleEntries()) + ")" + search, FontID.QUILL_8, 0x800000, 6, 5,
			260, LINE_HEIGHT);
		buildTopBar(container);
		ChatboxWidgets.separator(container, RESULTS_Y - 4);
		buildRows(container);
	}

	// Right to left: x, Del, Copy, { }, All, (selected count). The full name shows as the hover/menu text.
	private void buildTopBar(Widget container)
	{
		FontTypeFace font = ChatboxWidgets.font(container, BUTTON_FONT);

		List<Entry> visible = visibleEntries();
		boolean anySelected = visible.stream().anyMatch(selected::contains);
		boolean allSelected = !visible.isEmpty() && selected.containsAll(visible);
		int count = selectedPoints().size();
		String selectedTarget = "<col=ff9040>" + count + (count == 1 ? " marker" : " markers") + "</col>";

		int rightX = TOP_BAR_GAP;
		rightX = nextX(addButton(container, font, "x", "Close", "", true, rightX, chatboxPanelManager::close));
		int deleted = deletedMarkers.last().size();
		if (deleted > 0)
		{
			String deletedTarget = "<col=ff9040>" + deleted + (deleted == 1 ? " marker" : " markers") + "</col>";
			rightX = nextX(addButton(container, font, "Undo", "Restore deleted", deletedTarget, true, rightX, this::undoDelete));
		}
		rightX = nextX(addButton(container, font, "Del", "Delete selected", selectedTarget, anySelected, rightX, this::confirmDelete));
		Widget copy = addButton(container, font, "Copy", "Export selected", selectedTarget, anySelected, rightX, () -> exportSelected(false));
		if (anySelected)
		{
			copy.setAction(1, "Copy as plaintext");
			copy.setOnOpListener((JavaScriptCallback) ev -> exportSelected(ev.getOp() == 2));
		}
		rightX = nextX(copy);
		Widget filter = addButton(container, font, "{ }", variablesOnly ? "Show all markers" : "Show only markers with variables", "",
			!entries.isEmpty(), rightX, this::toggleVariablesFilter);
		if (variablesOnly)
		{
			filter.setTextColor(ACTIVE_COLOR);
			filter.setOnMouseLeaveListener((JavaScriptCallback) ev -> filter.setTextColor(ACTIVE_COLOR));
		}
		rightX = nextX(filter);

		Widget all = addButton(container, font, "All", allSelected ? "Deselect all" : "Select all", "", !visible.isEmpty(), rightX,
			allSelected ? this::deselectAll : this::selectAll);
		if (anySelected && !allSelected)
		{
			all.setAction(1, "Deselect all");
			all.setOnOpListener((JavaScriptCallback) ev ->
			{
				if (ev.getOp() == 2)
				{
					deselectAll();
				}
				else
				{
					selectAll();
				}
			});
		}

		if (count > 0)
		{
			addButton(container, font, "(" + count + ")", "Deselect all", selectedTarget, true, nextX(all), this::deselectAll);
		}
	}

	private static int nextX(Widget button)
	{
		return button.getOriginalX() + button.getOriginalWidth() + TOP_BAR_GAP;
	}

	private Widget addButton(Widget container, FontTypeFace font, String text, String action, String target, boolean enabled, int rightX,
		Runnable onClick)
	{
		Widget button = ChatboxWidgets.button(container, text, BUTTON_FONT, rightX, 5, font.getTextWidth(text) + 4, LINE_HEIGHT, action,
			enabled ? onClick : null);
		if (enabled)
		{
			button.setName(target);
		}
		return button;
	}

	private List<Row> rows()
	{
		List<Row> rows = new ArrayList<>();
		Group group = null;
		for (Entry entry : visibleEntries())
		{
			if (entry.group != group)
			{
				group = entry.group;
				rows.add(new Row(group, null));
			}
			rows.add(new Row(group, entry));
		}
		return rows;
	}

	private void buildRows(Widget container)
	{
		List<Row> rows = rows();
		if (rows.isEmpty())
		{
			String none = !query.isEmpty() ? "No ground markers match \"" + Text.escapeJagex(query) + "\"."
				: variablesOnly ? "No ground markers have variables." : "You have no saved ground markers.";
			addText(container, none, FontID.PLAIN_12, 0x000000, 6, RESULTS_Y);
			return;
		}

		int visibleRows = VISIBLE_ROWS;
		int maxOffset = Math.max(0, rows.size() - visibleRows);
		scrollOffset = Math.max(0, Math.min(scrollOffset, maxOffset));

		ChatboxWidgets.scrollCapture(container, RESULTS_Y, visibleRows * ROW_HEIGHT, this::scroll);

		int visibleCount = Math.min(visibleRows, rows.size() - scrollOffset);
		for (int i = 0; i < visibleCount; i++)
		{
			Row row = rows.get(scrollOffset + i);
			int y = RESULTS_Y + ROW_HEIGHT * i;
			boolean header = row.entry == null;
			List<Entry> rowEntries = header ? groupEntries(row.group) : List.of(row.entry);
			boolean checked = selected.containsAll(rowEntries);
			int checkboxX = header ? 6 : 22;
			int checkboxSize = header ? ROW_HEIGHT : ENTRY_CHECKBOX_SIZE;

			Widget checkbox = ChatboxWidgets.child(container, WidgetType.GRAPHIC, checkboxX, y + (ROW_HEIGHT - checkboxSize) / 2, checkboxSize,
				checkboxSize);
			checkbox.setSpriteId(checked ? SpriteID.Checkbox._2 : SpriteID.Checkbox._0);

			String text;
			int color;
			if (header)
			{
				int count = markerCount(rowEntries);
				String markers = count + (count == 1 ? " marker" : " markers");
				text = row.group.name == null
					? "Region " + row.group.regionIds.iterator().next() + " (" + markers + ")"
					: row.group.name + ": " + markers;
				color = 0x800000;
			}
			else if (row.entry.label.isEmpty())
			{
				text = "(no label)" + countSuffix(row.entry);
				color = 0x000000;
			}
			else
			{
				// Escaped so a label's own <col=> etc. shows as literal text.
				text = Text.escapeJagex(row.entry.label) + countSuffix(row.entry);
				color = 0x000000;
			}

			Widget label = addText(container, text, header ? FontID.QUILL_8 : FontID.PLAIN_12, color, checkboxX + checkboxSize + 4, y);
			for (Widget clickable : List.of(checkbox, label))
			{
				clickable.setHasListener(true);
				clickable.setAction(0, checked ? "Deselect" : "Select");
				clickable.setOnOpListener((JavaScriptCallback) ev -> toggle(rowEntries, !checked));
			}
			ChatboxWidgets.hover(label, color);
		}

		ChatboxWidgets.scrollbar(container, RESULTS_Y, visibleRows * ROW_HEIGHT, scrollOffset, maxOffset, visibleRows, rows.size(),
			this::scroll);
	}

	private Widget addText(Widget container, String text, int fontId, int color, int x, int y)
	{
		return ChatboxWidgets.rowText(container, text, fontId, color, x, y, ROW_HEIGHT, x + ChatboxWidgets.SCROLLBAR_WIDTH + 6);
	}

	private void scroll(int direction)
	{
		scrollOffset += direction;
		update();
	}

	private void toggle(List<Entry> rowEntries, boolean select)
	{
		if (select)
		{
			selected.addAll(rowEntries);
		}
		else
		{
			rowEntries.forEach(selected::remove);
		}
		update();
	}

	private void selectAll()
	{
		selected.addAll(visibleEntries());
		update();
	}

	private void deselectAll()
	{
		selected.clear();
		update();
	}

	// Hidden rows are deselected, so Del/Copy never act on something not shown.
	private void toggleVariablesFilter()
	{
		variablesOnly = !variablesOnly;
		if (variablesOnly)
		{
			selected.removeIf(entry -> !hasVariable(entry));
		}
		scrollOffset = 0;
		update();
	}

	private static boolean hasVariable(Entry entry)
	{
		return HAS_VARIABLE.matcher(entry.label).find();
	}

	private List<Entry> visibleEntries()
	{
		List<Entry> visible = new ArrayList<>();
		for (Entry entry : entries)
		{
			if (shown(entry))
			{
				visible.add(entry);
			}
		}
		return visible;
	}

	// Passes the { } filter and the search: its area name (or "Region <id>") or label contains it.
	private boolean shown(Entry entry)
	{
		if (variablesOnly && !hasVariable(entry))
		{
			return false;
		}
		if (query.isEmpty())
		{
			return true;
		}
		String search = query.toLowerCase(Locale.ROOT);
		String area = entry.group.name != null ? entry.group.name : "Region " + entry.group.regionIds.iterator().next();
		return area.toLowerCase(Locale.ROOT).contains(search) || entry.label.toLowerCase(Locale.ROOT).contains(search);
	}

	// plain: labels left readable rather than compressed (see GroundMarkerVariablesSharingManager#exportText).
	private void exportSelected(boolean plain)
	{
		List<GroundMarkerPointData> points = selectedPoints();
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sharingManager.exportText(points, plain)), null);
		sendChatMessage(points.size() + " ground markers were copied to your clipboard.");
	}

	private void confirmDelete()
	{
		List<GroundMarkerPointData> points = selectedPoints();
		chatboxPanelManager.openTextMenuInput("Are you sure you want to delete " + points.size()
				+ (points.size() == 1 ? " ground marker?" : " ground markers?") + "<br>This cannot be undone.")
			.option("Yes", () ->
			{
				deletedMarkers.remember(points);
				markerStorage.deletePoints(points);
				sendChatMessage(points.size() + " ground markers were deleted.");
				load();
				chatboxPanelManager.openInput(this);
			})
			.option("No", () -> chatboxPanelManager.openInput(this))
			.build();
	}

	// Restores the last deleted batch through the same confirmation as a clipboard import.
	private void undoDelete()
	{
		sharingManager.confirmImport(deletedMarkers.last(), " ground markers were restored.", () ->
		{
			deletedMarkers.clear();
			load();
			chatboxPanelManager.openInput(this);
		}, () -> chatboxPanelManager.openInput(this));
	}

	private List<Entry> groupEntries(Group group)
	{
		List<Entry> result = new ArrayList<>();
		for (Entry entry : group.byLabel.values())
		{
			if (shown(entry))
			{
				result.add(entry);
			}
		}
		return result;
	}

	// The selected markers shown: hidden ones stay selected, but aren't copied or deleted.
	private List<GroundMarkerPointData> selectedPoints()
	{
		List<GroundMarkerPointData> points = new ArrayList<>();
		for (Entry entry : visibleEntries())
		{
			if (selected.contains(entry))
			{
				points.addAll(entry.points);
			}
		}
		return points;
	}

	private static int markerCount(List<Entry> entries)
	{
		int count = 0;
		for (Entry entry : entries)
		{
			count += entry.points.size();
		}
		return count;
	}

	private static String countSuffix(Entry entry)
	{
		return entry.points.size() > 1 ? " (x" + entry.points.size() + ")" : "";
	}

	private static String groupKey(String name, int regionId)
	{
		return name != null ? name : "#" + regionId;
	}

	// The group of the player's tile, or null when not logged in.
	private String currentGroupKey()
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return null;
		}
		WorldPoint location = WorldPoint.fromLocalInstance(client, player.getLocalLocation());
		return groupKey(RegionNames.name(location), location.getRegionID());
	}

	private void sendChatMessage(String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(message)
			.build());
	}
}
