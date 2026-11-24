package com.groundmarkervariables.chatbox;

import com.groundmarkervariables.chatbox.completion.Completion;
import com.groundmarkervariables.markers.DeletedMarkers;
import com.groundmarkervariables.markers.GroundMarkerPointData;
import com.groundmarkervariables.markers.MarkerStorage;
import com.groundmarkervariables.markers.MarkerSet;
import com.groundmarkervariables.markers.MarkerSets;
import com.groundmarkervariables.variables.support.RegionNames;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import lombok.RequiredArgsConstructor;
import net.runelite.api.FontTypeFace;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.util.Text;

// ::manageGroundMarkerVariables -- every saved marker in a table: one row per area (a RegionNames name, else its region),
// label and color, with how many markers share them; current area first, or sorted by a heading. Rows have checkboxes;
// the top bar filters to markers with variables, selects all shown, and exports or deletes the selection.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class MarkerManager extends SearchableChatbox
{
	private static final int ACTIVE_COLOR = 0x800000;
	private static final Pattern HAS_VARIABLE = Pattern.compile("\\{.*\\}", Pattern.DOTALL);
	// Region, count and color diamond (both unheaded), then label.
	private static final String[] HEADINGS = {"Region", "", "", "Label"};
	private static final int LABEL = 3;
	// The widest value each column but Label (which takes the rest) is sized for: Region the longest area name, and the
	// diamond's just its room.
	private static final List<List<String>> SIZED_FOR = List.of(List.of("Sergeant Damien's Training Camp"), List.of("999"),
		List.of("#"), List.of());
	private static final int DIAMOND = 10;

	private final MarkerStorage markerStorage;
	private final DeletedMarkers deletedMarkers;
	private final GroundMarkerVariablesSharingManager sharingManager;
	private final MarkerSets markerSets;
	private final Provider<MarkerSetManager> markerSetManagerProvider;
	// Pinned while this is open: the selected rows' markers and the last row Show on map moved to.
	private final WorldMapPins mapPins;
	@Nullable
	private Entry shown;

	private final List<Entry> entries = new ArrayList<>();
	private final Set<Entry> selected = new HashSet<>();
	// { } toggle: only rows whose label has a variable. Off at first.
	private boolean variablesOnly;
	// The player's area when loaded, listed first.
	@Nullable
	private String currentArea;

	// Every marker in one area sharing a label ("" for none) and color.
	@RequiredArgsConstructor
	private static final class Entry
	{
		// Its RegionNames name, else "Region <id>".
		private final String area;
		private final boolean named;
		private final int regionId;
		private final String label;
		@Nullable
		private final Color color;
		private final List<GroundMarkerPointData> points = new ArrayList<>();
	}

	// Rebuilds the list from storage, clearing the selection but keeping the scroll (a new manager starts at the top).
	public void load()
	{
		entries.clear();
		selected.clear();
		shown = null;

		Map<String, Entry> byKey = new LinkedHashMap<>();
		leaveEmptyBin();
		if (recycleBin)
		{
			deletedMarkers.values().forEach(point -> addPoint(byKey, point, String.valueOf(byKey.size())));
		}
		else
		{
			for (int regionId : markerStorage.storedRegionIds())
			{
				markerStorage.getStoredPoints(regionId).forEach(point -> addPoint(byKey, point, ""));
			}
		}
		entries.addAll(byKey.values());

		Player player = client.getLocalPlayer();
		WorldPoint location = player == null ? null : WorldPoint.fromLocalInstance(client, player.getLocalLocation());
		String name = location == null ? null : RegionNames.name(location);
		currentArea = location == null ? null : name != null ? name : "Region " + location.getRegionID();
	}

	// point into the row for its own area (one region can hold several), label and color; row ("" to share) keeps it apart.
	private static void addPoint(Map<String, Entry> byKey, GroundMarkerPointData point, String row)
	{
		String name = RegionNames.name(point.worldPoint());
		String area = name != null ? name : "Region " + point.getRegionId();
		String label = point.getLabel() == null ? "" : point.getLabel();
		byKey.computeIfAbsent(row + '\0' + area + '\0' + label + '\0' + point.getColor(),
			key -> new Entry(area, name != null, point.getRegionId(), label, point.getColor())).points.add(point);
	}

	// By sortColumn, reversed if descending; then the current area first, named areas alphabetically, unnamed regions by id,
	// labels alphabetically (none last), then color.
	private Comparator<Entry> order()
	{
		Comparator<Entry> byArea = Comparator.comparing((Entry e) -> !e.area.equals(currentArea)).thenComparing(e -> !e.named)
			.thenComparing(e -> e.area, String.CASE_INSENSITIVE_ORDER).thenComparingInt(e -> e.regionId);
		Comparator<Entry> byLabel = Comparator.comparing((Entry e) -> e.label.isEmpty()).thenComparing(e -> e.label, String.CASE_INSENSITIVE_ORDER);
		Comparator<Entry> byColor = Comparator.comparing(e -> e.color == null ? null : e.color.getRGB(),
			Comparator.nullsFirst(Comparator.naturalOrder()));
		Comparator<Entry> byColumn = sortColumn == LABEL ? byLabel : byArea;
		return (descending ? byColumn.reversed() : byColumn).thenComparing(byArea).thenComparing(byLabel).thenComparing(byColor);
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

	@Override
	protected int recycled()
	{
		return deletedMarkers.size();
	}

	// After x and _, right to left: the recycling bin's :recycle: (while it has markers, or is open; alone there), Del, Copy,
	// Set, { }, All (while filtered), (selected count). The full name shows as the hover/menu text.
	@Override
	protected void buildTopBar(Widget container, FontTypeFace font, int rightX)
	{
		rightX = recycleBinButton(container, rightX, this::reopen);
		if (recycleBin)
		{
			return;
		}

		List<Entry> visible = visibleEntries();
		boolean anySelected = visible.stream().anyMatch(selected::contains);
		boolean allSelected = !visible.isEmpty() && selected.containsAll(visible);
		int count = selectedPoints().size();
		String selectedTarget = "<col=ff9040>" + count + (count == 1 ? " marker" : " markers") + "</col>";

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

	@Override
	protected void buildRows(Widget container)
	{
		updatePins();
		List<Entry> rows = visibleEntries();
		if (rows.isEmpty())
		{
			String none = !query.isEmpty() ? "No ground markers match \"" + Text.escapeJagex(query) + "\"."
				: variablesOnly ? "No ground markers have variables." : "You have no saved ground markers.";
			addText(container, none, widgets.fontId(), 0x000000, 6, RESULTS_Y);
			return;
		}
		rows.sort(order());
		columns(container, HEADINGS, SIZED_FOR, LABEL);
		table(container, true, rows.size(), (i, y) -> addEntry(container, rows.get(i), y), null);
	}

	// Its checkbox, area, count (right-aligned), color diamond (none for the default color) and label (escaped, so a label's
	// own <col=> etc. shows as written). The whole row is clicked to select it, or a single marker's to edit it, and grays
	// while hovered. In the recycling bin, its crossed check box (or the row) restores it, and right-click deletes it for good.
	private void addEntry(Widget container, Entry entry, int y)
	{
		boolean checked = selected.contains(entry);
		checkbox(container, checked, 6, y);
		Widget area = cell(container, entry.area, 0x000000, 0, y);
		cell(container, String.valueOf(entry.points.size()), DETAIL_COLOR, 1, y).setXTextAlignment(WidgetTextAlignment.RIGHT);
		if (entry.color != null)
		{
			iconPlacements.add(new SuggestionIconOverlay.Placement(container,
				new Rectangle(columnX(2), y + (ROW_HEIGHT - DIAMOND) / 2, DIAMOND, DIAMOND), Completion.Icon.diamond(entry.color)));
		}
		Widget label = cell(container, entry.label.isEmpty() ? "(no label)" : entry.label, 0x000000, LABEL, y);

		// Over the whole row, on top of its cells.
		Widget row = ChatboxWidgets.child(container, WidgetType.TEXT, 6, y, columnX(LABEL) + columnWidth(LABEL) - 6, ROW_HEIGHT);
		List<Runnable> actions = new ArrayList<>();
		addAction(row, actions, recycleBin ? "Restore" : checked ? "Deselect" : "Select",
			recycleBin ? () -> restore(entry) : () -> toggle(entry, !checked));
		if (entry.points.size() == 1 && !recycleBin)
		{
			addAction(row, actions, "Edit", () -> edit(entry.points.get(0)));
		}
		Rectangle labelArea = new Rectangle(columnX(LABEL), y, columnWidth(LABEL), ROW_HEIGHT);
		addSubmenu(row, actions, "Map", mapPins.mapChoices(entry.points, entry.points, () ->
		{
			shown = entry;
			updatePins();
		}, text -> notice(labelArea, text), this::updateIfOpen));
		if (recycleBin)
		{
			addAction(row, actions, "Delete", () -> confirmPurge(deletedMarkers.minutesLeft(entry.points.get(0)), labelArea,
				"this ground marker", () ->
				{
					deletedMarkers.removeAll(entry.points);
					sendChatMessage("The ground marker was deleted permanently.");
				}));
		}
		ChatboxWidgets.hover(row, 0x000000, area, label);
	}

	private void updatePins()
	{
		Set<Entry> rows = new HashSet<>(selected);
		if (shown != null)
		{
			rows.add(shown);
		}
		Map<String, List<GroundMarkerPointData>> tiles = new HashMap<>();
		rows.forEach(entry -> tiles.computeIfAbsent(entry.label.isEmpty() ? entry.area : entry.label, label -> new ArrayList<>())
			.addAll(entry.points));
		mapPins.pin(rows, tiles);
	}

	@Override
	protected void close()
	{
		mapPins.clear();
		super.close();
	}

	private void edit(GroundMarkerPointData point)
	{
		editLabel(point, label -> plugin.saveExpandedLabel(point.worldPoint(), label));
	}

	// Reloaded, e.g. to show an edited label.
	@Override
	protected void reopen()
	{
		load();
		super.reopen();
	}

	private void toggle(Entry entry, boolean select)
	{
		if (select)
		{
			selected.add(entry);
		}
		else
		{
			selected.remove(entry);
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
		String search = query.toLowerCase(Locale.ROOT);
		return entry.area.toLowerCase(Locale.ROOT).contains(search) || entry.label.toLowerCase(Locale.ROOT).contains(search);
	}

	// plain: labels left readable rather than compressed (see GroundMarkerVariablesSharingManager#exportText).
	private void exportSelected(boolean plain)
	{
		List<GroundMarkerPointData> points = selectedPoints();
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sharingManager.exportText(points, plain)), null);
		sendChatMessage(points.size() + " ground markers were copied to your clipboard.");
	}

	// Asks for a title, starting as (and blank is) MarkerSets#defaultTitle's, then whether to keep the markers or move them
	// into the set. The author is the logged-in name, else anon. onDone returns false: the menu it opens replaces this
	// prompt, which closing would close instead.
	private void makeSet()
	{
		List<Entry> chosen = chosenEntries();
		String defaultTitle = MarkerSets.defaultTitle(selectedPoints());
		Player player = client.getLocalPlayer();
		String author = player != null && player.getName() != null ? player.getName() : MarkerSet.ANON;
		chatboxPanelManager.openTextInput("Marker set title")
			.value(defaultTitle)
			.onDone((Predicate<String>) title ->
			{
				String setTitle = title.isBlank() ? defaultTitle : title.trim();
				keepOrMove(chosen, move -> addToSet(chosen, move, entries -> markerSets.create(setTitle, author, entries)));
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
		new ConfirmChatbox(chatboxPanelManager, "Keep these markers as your own too, or move them into the set?"
				+ "<br>Moving deletes them from core Ground Markers if this plugin is off.")
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
				entries.add(new MarkerSet.Entry(null, null, entry.points, entry.points.size() > 1, true, entries.size()));
				points.addAll(entry.points);
			}
			MarkerSet set = save.apply(entries);
			// Too large: nothing saved, so nothing moved.
			if (set == null)
			{
				sendChatMessage(MarkerSets.TOO_LARGE);
				chatboxPanelManager.openInput(this);
				return;
			}
			// Moved, not deleted: they're in the set, so not recycled.
			if (move)
			{
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
				+ (points.size() == 1 ? " ground marker?" : " ground markers?"))
			.yes(() ->
			{
				deletedMarkers.addAll(points);
				markerStorage.deletePoints(points);
				sendChatMessage(points.size() + " ground markers were moved to the recycling bin, where they can be restored until you close the client.");
				reopen();
			})
			.no(() -> chatboxPanelManager.openInput(this))
			.build();
	}

	// Back into saved markers, through the same confirmation as a clipboard import; back to them once the bin's empty.
	private void restore(Entry entry)
	{
		sharingManager.confirmImport(entry.points, " ground markers were restored.", () ->
		{
			deletedMarkers.removeAll(entry.points);
			reopen();
		}, () -> chatboxPanelManager.openInput(this));
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

}
