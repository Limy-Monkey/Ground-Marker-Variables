package com.groundmarkervariables;

import com.groundmarkervariables.variables.support.RegionNames;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Named;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.FontTypeFace;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.game.chatbox.ChatboxInput;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.util.Text;

// ::manageGroundMarkerVariables -- every saved marker, grouped by area name (current area first),
// with same-label markers in an area combined into one row. Rows and region headers have
// checkboxes; the top bar filters to markers with variables, selects all shown, and exports or deletes the selection.
class MarkerManager extends ChatboxInput implements KeyListener
{
	private static final int LINE_HEIGHT = 20;
	private static final int ROW_HEIGHT = 16;
	private static final int ENTRY_CHECKBOX_SIZE = 12;
	private static final int BUTTON_FONT = FontID.PLAIN_11;
	private static final int RESULTS_Y = 30;
	// Fixed, like AdvancedLabelEditor -- fills the chatbox down to where its own list ends.
	private static final int VISIBLE_ROWS = 6;
	private static final int SCROLLBAR_WIDTH = 14;
	private static final int SCROLLBAR_ARROW_HEIGHT = 12;
	private static final int SCROLLBAR_TRACK_WIDTH = 4;
	private static final int SCROLLBAR_THUMB_MIN_HEIGHT = 6;
	private static final int TOP_BAR_GAP = 8;
	private static final int HOVER_COLOR = 0x444444;
	private static final int DISABLED_COLOR = 0x888888;
	private static final int ACTIVE_COLOR = 0x800000;
	private static final Pattern HAS_VARIABLE = Pattern.compile("\\{.*\\}", Pattern.DOTALL);

	private final ChatboxPanelManager chatboxPanelManager;
	private final MarkerStorage markerStorage;
	private final ChatMessageManager chatMessageManager;
	private final Client client;
	private final DeletedMarkers deletedMarkers;
	private final GroundMarkerVariablesSharingManager sharingManager;

	private final List<Entry> entries = new ArrayList<>();
	private final Set<Entry> selected = new HashSet<>();
	private int scrollOffset;
	// { } toggle: only rows whose label has a variable (and the areas containing them). The
	// toggle is developer mode only.
	private boolean variablesOnly = true;
	private final boolean developerMode;

	// Every region sharing one RegionNames name, or a single unnamed region.
	private static final class Group
	{
		private final String name;
		private final Set<Integer> regionIds = new TreeSet<>();
		private final Map<String, Entry> byLabel = new LinkedHashMap<>();

		private Group(String name)
		{
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
		Client client, DeletedMarkers deletedMarkers, GroundMarkerVariablesSharingManager sharingManager,
		@Named("developerMode") boolean developerMode)
	{
		this.chatboxPanelManager = chatboxPanelManager;
		this.markerStorage = markerStorage;
		this.chatMessageManager = chatMessageManager;
		this.client = client;
		this.deletedMarkers = deletedMarkers;
		this.sharingManager = sharingManager;
		this.developerMode = developerMode;
	}

	// Rebuilds the list from storage, clearing the selection.
	void load()
	{
		entries.clear();
		selected.clear();
		scrollOffset = 0;

		Map<String, Group> groups = new LinkedHashMap<>();
		for (int regionId : markerStorage.storedRegionIds())
		{
			String name = RegionNames.name(regionId);
			Group group = groups.computeIfAbsent(name != null ? name : "#" + regionId, k -> new Group(name));
			group.regionIds.add(regionId);
			for (GroundMarkerPointData point : markerStorage.getStoredPoints(regionId))
			{
				String label = point.getLabel() == null ? "" : point.getLabel();
				group.byLabel.computeIfAbsent(label, l -> new Entry(group, l)).points.add(point);
			}
		}

		// Current area first, then named areas alphabetically, then unnamed regions by id.
		int currentRegion = currentRegionId();
		List<Group> sorted = new ArrayList<>(groups.values());
		sorted.sort(Comparator.comparing((Group g) -> !g.regionIds.contains(currentRegion))
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

	@Override
	public void keyPressed(KeyEvent ev)
	{
		if (ev.getKeyCode() == KeyEvent.VK_ESCAPE && chatboxPanelManager.shouldTakeInput())
		{
			ev.consume();
			chatboxPanelManager.close();
		}
	}

	@Override
	public void keyTyped(KeyEvent ev)
	{
	}

	@Override
	public void keyReleased(KeyEvent ev)
	{
	}

	private void update()
	{
		Widget container = chatboxPanelManager.getContainerWidget();
		container.deleteAllChildren();

		Widget title = container.createChild(-1, WidgetType.TEXT);
		title.setText("Ground Markers (" + markerCount(visibleEntries()) + ")");
		title.setTextColor(0x800000);
		title.setFontId(FontID.QUILL_8);
		title.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
		title.setOriginalX(6);
		title.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		title.setOriginalY(5);
		title.setOriginalWidth(200);
		title.setOriginalHeight(LINE_HEIGHT);
		title.setYTextAlignment(WidgetTextAlignment.CENTER);
		title.revalidate();

		buildTopBar(container);

		Widget separator = container.createChild(-1, WidgetType.LINE);
		separator.setXPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
		separator.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		separator.setOriginalY(RESULTS_Y - 4);
		separator.setOriginalHeight(0);
		separator.setOriginalWidth(16);
		separator.setWidthMode(WidgetSizeMode.MINUS);
		separator.revalidate();

		buildRows(container);
	}

	// Right to left: x, Del, Copy, { }, All, (selected count). The full name shows as the hover/menu text.
	private void buildTopBar(Widget container)
	{
		Widget probe = container.createChild(-1, WidgetType.TEXT);
		probe.setFontId(BUTTON_FONT);
		FontTypeFace font = probe.getFont();

		List<Entry> visible = visibleEntries();
		boolean anySelected = !selected.isEmpty();
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
		rightX = nextX(addButton(container, font, "Copy", "Export selected", selectedTarget, anySelected, rightX, this::exportSelected));
		if (developerMode)
		{
			Widget filter = addButton(container, font, "{ }", variablesOnly ? "Show all markers" : "Show only markers with variables", "",
				!entries.isEmpty(), rightX, this::toggleVariablesFilter);
			if (variablesOnly)
			{
				filter.setTextColor(ACTIVE_COLOR);
				filter.setOnMouseLeaveListener((JavaScriptCallback) ev -> filter.setTextColor(ACTIVE_COLOR));
			}
			rightX = nextX(filter);
		}

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
		int width = font.getTextWidth(text) + 4;
		Widget button = container.createChild(-1, WidgetType.TEXT);
		button.setText(text);
		button.setFontId(BUTTON_FONT);
		button.setTextColor(enabled ? 0x000000 : DISABLED_COLOR);
		button.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		button.setOriginalX(rightX);
		button.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		button.setOriginalY(5);
		button.setOriginalWidth(width);
		button.setOriginalHeight(LINE_HEIGHT);
		button.setXTextAlignment(WidgetTextAlignment.CENTER);
		button.setYTextAlignment(WidgetTextAlignment.CENTER);
		if (enabled)
		{
			button.setHasListener(true);
			button.setAction(0, action);
			button.setName(target);
			button.setOnOpListener((JavaScriptCallback) ev -> onClick.run());
			button.setOnMouseRepeatListener((JavaScriptCallback) ev -> button.setTextColor(HOVER_COLOR));
			button.setOnMouseLeaveListener((JavaScriptCallback) ev -> button.setTextColor(0x000000));
		}
		button.revalidate();
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
			addText(container, variablesOnly ? "No ground markers have variables." : "You have no saved ground markers.", FontID.PLAIN_12,
				0x000000, 6, RESULTS_Y);
			return;
		}

		int visibleRows = VISIBLE_ROWS;
		int maxOffset = Math.max(0, rows.size() - visibleRows);
		scrollOffset = Math.max(0, Math.min(scrollOffset, maxOffset));

		// Invisible -- captures mouse wheel input over the list.
		Widget scrollCapture = container.createChild(-1, WidgetType.TEXT);
		scrollCapture.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
		scrollCapture.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		scrollCapture.setOriginalY(RESULTS_Y);
		scrollCapture.setOriginalWidth(container.getWidth());
		scrollCapture.setOriginalHeight(visibleRows * ROW_HEIGHT);
		scrollCapture.setHasListener(true);
		scrollCapture.setNoScrollThrough(true);
		scrollCapture.setOnScrollWheelListener((JavaScriptCallback) ev -> scroll(ev.getMouseY()));
		scrollCapture.revalidate();

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

			Widget checkbox = container.createChild(-1, WidgetType.GRAPHIC);
			checkbox.setSpriteId(checked ? SpriteID.Checkbox._2 : SpriteID.Checkbox._0);
			checkbox.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
			checkbox.setOriginalX(checkboxX);
			checkbox.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
			checkbox.setOriginalY(y + (ROW_HEIGHT - checkboxSize) / 2);
			checkbox.setOriginalWidth(checkboxSize);
			checkbox.setOriginalHeight(checkboxSize);
			checkbox.revalidate();

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
			label.setOnMouseRepeatListener((JavaScriptCallback) ev -> label.setTextColor(HOVER_COLOR));
			label.setOnMouseLeaveListener((JavaScriptCallback) ev -> label.setTextColor(color));
		}

		buildScrollbar(container, rows.size(), maxOffset);
	}

	private Widget addText(Widget container, String text, int fontId, int color, int x, int y)
	{
		Widget widget = container.createChild(-1, WidgetType.TEXT);
		widget.setText(text);
		widget.setFontId(fontId);
		widget.setTextColor(color);
		widget.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
		widget.setOriginalX(x);
		widget.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		widget.setOriginalY(y);
		widget.setOriginalHeight(ROW_HEIGHT);
		widget.setOriginalWidth(x + SCROLLBAR_WIDTH + 6);
		widget.setWidthMode(WidgetSizeMode.MINUS);
		widget.setXTextAlignment(WidgetTextAlignment.LEFT);
		widget.setYTextAlignment(WidgetTextAlignment.CENTER);
		widget.revalidate();
		return widget;
	}

	// Same arrows/track/thumb as AdvancedLabelEditor's own scrollbar.
	private void buildScrollbar(Widget container, int rowCount, int maxOffset)
	{
		if (maxOffset <= 0)
		{
			return;
		}

		int listHeight = VISIBLE_ROWS * ROW_HEIGHT;
		addScrollArrow(container, "^", "Scroll up", RESULTS_Y, scrollOffset > 0, -1);
		addScrollArrow(container, "v", "Scroll down", RESULTS_Y + listHeight - SCROLLBAR_ARROW_HEIGHT, scrollOffset < maxOffset, 1);

		int trackY = RESULTS_Y + SCROLLBAR_ARROW_HEIGHT;
		int trackHeight = listHeight - SCROLLBAR_ARROW_HEIGHT * 2;
		int trackX = (SCROLLBAR_WIDTH - SCROLLBAR_TRACK_WIDTH) / 2;

		Widget track = container.createChild(-1, WidgetType.RECTANGLE);
		track.setFilled(true);
		track.setOpacity(120);
		track.setTextColor(0x000000);
		track.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		track.setOriginalX(trackX);
		track.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		track.setOriginalY(trackY);
		track.setOriginalWidth(SCROLLBAR_TRACK_WIDTH);
		track.setOriginalHeight(trackHeight);
		track.revalidate();

		int thumbHeight = Math.max(SCROLLBAR_THUMB_MIN_HEIGHT, trackHeight * VISIBLE_ROWS / rowCount);
		int thumbY = trackY + (trackHeight - thumbHeight) * scrollOffset / maxOffset;

		Widget thumb = container.createChild(-1, WidgetType.RECTANGLE);
		thumb.setFilled(true);
		thumb.setTextColor(0x800000);
		thumb.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		thumb.setOriginalX(trackX);
		thumb.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		thumb.setOriginalY(thumbY);
		thumb.setOriginalWidth(SCROLLBAR_TRACK_WIDTH);
		thumb.setOriginalHeight(thumbHeight);
		thumb.revalidate();
	}

	private void addScrollArrow(Widget container, String text, String action, int y, boolean enabled, int direction)
	{
		Widget arrow = container.createChild(-1, WidgetType.TEXT);
		arrow.setText(text);
		arrow.setFontId(FontID.BOLD_12);
		arrow.setTextColor(enabled ? 0x000000 : DISABLED_COLOR);
		arrow.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		arrow.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		arrow.setOriginalY(y);
		arrow.setOriginalWidth(SCROLLBAR_WIDTH);
		arrow.setOriginalHeight(SCROLLBAR_ARROW_HEIGHT);
		arrow.setXTextAlignment(WidgetTextAlignment.CENTER);
		arrow.setYTextAlignment(WidgetTextAlignment.CENTER);
		arrow.setHasListener(true);
		arrow.setAction(0, action);
		arrow.setOnOpListener((JavaScriptCallback) ev -> scroll(direction));
		arrow.revalidate();
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
		if (!variablesOnly)
		{
			return entries;
		}

		List<Entry> visible = new ArrayList<>();
		for (Entry entry : entries)
		{
			if (hasVariable(entry))
			{
				visible.add(entry);
			}
		}
		return visible;
	}

	private void exportSelected()
	{
		List<GroundMarkerPointData> points = selectedPoints();
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sharingManager.exportText(points)), null);
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
			if (!variablesOnly || hasVariable(entry))
			{
				result.add(entry);
			}
		}
		return result;
	}

	private List<GroundMarkerPointData> selectedPoints()
	{
		List<GroundMarkerPointData> points = new ArrayList<>();
		for (Entry entry : entries)
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

	private int currentRegionId()
	{
		Player player = client.getLocalPlayer();
		return player == null ? -1 : WorldPoint.fromLocalInstance(client, player.getLocalLocation()).getRegionID();
	}

	private void sendChatMessage(String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(message)
			.build());
	}
}
