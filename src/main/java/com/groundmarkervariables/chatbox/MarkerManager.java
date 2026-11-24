package com.groundmarkervariables.chatbox;

import com.groundmarkervariables.GroundMarkerVariablesPlugin;
import com.groundmarkervariables.markers.DeletedMarkers;
import com.groundmarkervariables.markers.GroundMarkerPointData;
import com.groundmarkervariables.markers.MarkerStorage;
import com.groundmarkervariables.markers.MarkerSet;
import com.groundmarkervariables.markers.MarkerSets;
import com.groundmarkervariables.variables.support.RegionNames;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Provider;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.FontTypeFace;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.util.Text;

// ::manageGroundMarkerVariables -- every saved marker, grouped by area name (current area first),
// with same-label markers in an area combined into one row. Rows and region headers have
// checkboxes; the top bar filters to markers with variables, selects all shown, and exports or deletes the selection.
public class MarkerManager extends SearchableChatbox
{
	private static final int ACTIVE_COLOR = 0x800000;
	private static final Pattern HAS_VARIABLE = Pattern.compile("\\{.*\\}", Pattern.DOTALL);

	private final MarkerStorage markerStorage;
	private final DeletedMarkers deletedMarkers;
	private final GroundMarkerVariablesSharingManager sharingManager;
	private final MarkerSets markerSets;
	private final Provider<MarkerSetManager> markerSetManagerProvider;

	private final List<Entry> entries = new ArrayList<>();
	private final Set<Entry> selected = new HashSet<>();
	// { } toggle: only rows whose label has a variable (and the areas containing them).
	private boolean variablesOnly = true;

	// Every marker sharing one RegionNames name, or the unnamed ones of a single region.
	@RequiredArgsConstructor
	private static final class Group
	{
		private final String key;
		private final String name;
		private final Set<Integer> regionIds = new TreeSet<>();
		private final Map<String, Entry> byLabel = new LinkedHashMap<>();
	}

	// Every marker in one group sharing the same label ("" for none).
	@RequiredArgsConstructor
	private static final class Entry
	{
		private final Group group;
		private final String label;
		private final List<GroundMarkerPointData> points = new ArrayList<>();
	}

	// A group header (entry == null) or one Entry.
	@RequiredArgsConstructor
	private static final class Row
	{
		private final Group group;
		private final Entry entry;
	}

	@Inject
	MarkerManager(ChatboxPanelManager chatboxPanelManager, MarkerStorage markerStorage, ChatMessageManager chatMessageManager,
		Client client, DeletedMarkers deletedMarkers, GroundMarkerVariablesSharingManager sharingManager, ClientThread clientThread,
		MarkerSets markerSets, Provider<MarkerSetManager> markerSetManagerProvider, ChatboxWidgets widgets, SuggestionIconOverlay iconOverlay,
		GroundMarkerVariablesPlugin plugin)
	{
		super(chatboxPanelManager, clientThread, chatMessageManager, iconOverlay, widgets, client, plugin);
		this.markerSets = markerSets;
		this.markerSetManagerProvider = markerSetManagerProvider;
		this.markerStorage = markerStorage;
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

	@Override
	protected String title()
	{
		return "Ground Markers (" + markerCount(visibleEntries()) + ")";
	}

	// After x and _, right to left: Undo, Del, Copy, Set, { }, All (while filtered), (selected count). The full name shows as the hover/menu
	// text.
	@Override
	protected void buildTopBar(Widget container, FontTypeFace font, int rightX)
	{
		List<Entry> visible = visibleEntries();
		boolean anySelected = visible.stream().anyMatch(selected::contains);
		boolean allSelected = !visible.isEmpty() && selected.containsAll(visible);
		int count = selectedPoints().size();
		String selectedTarget = "<col=ff9040>" + count + (count == 1 ? " marker" : " markers") + "</col>";

		int deleted = deletedMarkers.last().size();
		if (deleted > 0)
		{
			String deletedTarget = "<col=ff9040>" + deleted + (deleted == 1 ? " marker" : " markers") + "</col>";
			rightX = nextX(button(container, font, "Undo", "Restore deleted", deletedTarget, true, rightX, this::undoDelete));
		}
		rightX = nextX(button(container, font, "Del", "Delete selected", selectedTarget, anySelected, rightX, this::confirmDelete));
		Widget copy = button(container, font, "Copy", "Export selected", selectedTarget, anySelected, rightX, () -> exportSelected(false));
		if (anySelected)
		{
			copy.setAction(1, "Copy as plaintext");
			copy.setOnOpListener((JavaScriptCallback) ev -> exportSelected(ev.getOp() == 2));
		}
		rightX = nextX(copy);
		Widget set = button(container, font, "Set", "Make a marker set from selected", selectedTarget, anySelected, rightX, this::makeSet);
		if (anySelected && !ownSets().isEmpty())
		{
			set.setAction(1, "Add to existing set");
			set.setOnOpListener((JavaScriptCallback) ev ->
			{
				if (ev.getOp() == 2)
				{
					addToExistingSet();
				}
				else
				{
					makeSet();
				}
			});
		}
		rightX = nextX(set);
		Widget filter = button(container, font, "{ }", variablesOnly ? "Show all markers" : "Show only markers with variables", "",
			!entries.isEmpty(), rightX, this::toggleVariablesFilter);
		if (variablesOnly)
		{
			filter.setTextColor(ACTIVE_COLOR);
			filter.setOnMouseLeaveListener((JavaScriptCallback) ev -> filter.setTextColor(ACTIVE_COLOR));
		}
		rightX = nextX(filter);

		// All only while filtered to markers with variables.
		if (variablesOnly)
		{
			Widget all = button(container, font, "All", allSelected ? "Deselect all" : "Select all", "", !visible.isEmpty(), rightX,
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
			rightX = nextX(all);
		}

		if (count > 0)
		{
			button(container, font, "(" + count + ")", "Deselect all", selectedTarget, true, rightX, this::deselectAll);
		}
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

	@Override
	protected void buildRows(Widget container)
	{
		List<Row> rows = rows();
		if (rows.isEmpty())
		{
			String none = !query.isEmpty() ? "No ground markers match \"" + Text.escapeJagex(query) + "\"."
				: variablesOnly ? "No ground markers have variables." : "You have no saved ground markers.";
			addText(container, none, widgets.fontId(), 0x000000, 6, RESULTS_Y);
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
			Widget checkbox = checkbox(container, checked, checkboxX, y);

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

			Widget label = addText(container, text, header ? FontID.QUILL_8 : widgets.fontId(), color, checkboxX + checkbox.getOriginalWidth() + 4, y);
			// A single marker can also be edited.
			GroundMarkerPointData single = !header && row.entry.points.size() == 1 ? row.entry.points.get(0) : null;
			for (Widget clickable : List.of(checkbox, label))
			{
				clickable.setHasListener(true);
				clickable.setAction(0, checked ? "Deselect" : "Select");
				if (single != null)
				{
					clickable.setAction(1, "Edit");
				}
				clickable.setOnOpListener((JavaScriptCallback) ev ->
				{
					if (ev.getOp() == 2 && single != null)
					{
						edit(single);
					}
					else
					{
						toggle(rowEntries, !checked);
					}
				});
			}
			ChatboxWidgets.hover(label, color);
		}

		ChatboxWidgets.scrollbar(container, RESULTS_Y, visibleRows * ROW_HEIGHT, scrollOffset, maxOffset, visibleRows, rows.size(),
			this::scroll);
	}

	private void edit(GroundMarkerPointData point)
	{
		editLabel(point, label -> plugin.saveExpandedLabel(
			WorldPoint.fromRegion(point.getRegionId(), point.getRegionX(), point.getRegionY(), point.getZ()), label));
	}

	// Reloaded, e.g. to show an edited label.
	@Override
	protected void reopen()
	{
		load();
		super.reopen();
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

	// Asks for a title, then whether to keep the markers or move them into the set. The author is the logged-in name, else
	// anon. onDone returns false: the menu it opens replaces this prompt, which closing would close instead.
	private void makeSet()
	{
		List<Entry> chosen = chosenEntries();
		Player player = client.getLocalPlayer();
		String author = player != null && player.getName() != null ? player.getName() : MarkerSet.ANON;
		chatboxPanelManager.openTextInput("Marker set title")
			.onDone((Predicate<String>) title ->
			{
				if (title.isBlank())
				{
					chatboxPanelManager.openInput(this);
					return false;
				}
				keepOrMove(chosen, move -> addToSet(chosen, move, entries -> markerSets.create(title.trim(), author, entries)));
				return false;
			})
			.build();
	}

	// Picks one of the user's own sets in Marker Sets (searchable, however many there are), then asks whether to keep the
	// markers or move them into it.
	private void addToExistingSet()
	{
		List<Entry> chosen = chosenEntries();
		chatboxPanelManager.openInput(markerSetManagerProvider.get().pick(set ->
			keepOrMove(chosen, move -> addToSet(chosen, move, entries -> markerSets.add(set, entries)))));
	}

	private void keepOrMove(List<Entry> chosen, Consumer<Boolean> then)
	{
		new ConfirmChatbox(chatboxPanelManager, "Keep these markers as your own too, or move them into the set?")
			.option("Keep them", () -> then.accept(false))
			.option("Move them", () -> then.accept(true))
			.option("Cancel", () -> chatboxPanelManager.openInput(this))
			.build();
	}

	// One entry per row (a row of same-label markers becomes a group), given to save, which returns the set; then shows
	// Marker Sets.
	private void addToSet(List<Entry> chosen, boolean move, Function<List<MarkerSet.Entry>, MarkerSet> save)
	{
		clientThread.invoke(() ->
		{
			List<MarkerSet.Entry> entries = new ArrayList<>();
			List<GroundMarkerPointData> points = new ArrayList<>();
			for (Entry entry : chosen)
			{
				entries.add(new MarkerSet.Entry(null, entry.points, entry.points.size() > 1, true, entries.size()));
				points.addAll(entry.points);
			}
			MarkerSet set = save.apply(entries);
			if (move)
			{
				deletedMarkers.remember(points);
				markerStorage.deletePoints(points);
			}
			sendChatMessage(points.size() + " ground markers were added to the marker set \"" + set.title + "\".");
			chatboxPanelManager.openInput(markerSetManagerProvider.get());
		});
	}

	private List<Entry> chosenEntries()
	{
		List<Entry> chosen = new ArrayList<>();
		for (Entry entry : visibleEntries())
		{
			if (selected.contains(entry))
			{
				chosen.add(entry);
			}
		}
		return chosen;
	}

	private List<MarkerSet> ownSets()
	{
		List<MarkerSet> own = new ArrayList<>();
		for (MarkerSet set : markerSets.sets())
		{
			if (set.kind == MarkerSet.Kind.OWN)
			{
				own.add(set);
			}
		}
		return own;
	}

	private void confirmDelete()
	{
		List<GroundMarkerPointData> points = selectedPoints();
		new ConfirmChatbox(chatboxPanelManager, "Are you sure you want to delete " + points.size()
				+ (points.size() == 1 ? " ground marker?" : " ground markers?") + "<br>This cannot be undone.")
			.yes(() ->
			{
				deletedMarkers.remember(points);
				markerStorage.deletePoints(points);
				sendChatMessage(points.size() + " ground markers were deleted.");
				load();
				chatboxPanelManager.openInput(this);
			})
			.no(() -> chatboxPanelManager.openInput(this))
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
}
