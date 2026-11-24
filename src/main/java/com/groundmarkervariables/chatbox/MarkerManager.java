package com.groundmarkervariables.chatbox;

import com.groundmarkervariables.GroundMarkerVariablesConfig;
import com.groundmarkervariables.GroundMarkerVariablesPlugin;
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
import java.awt.image.BufferedImage;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Comparator;
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
import net.runelite.api.Client;
import net.runelite.api.FontTypeFace;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.Text;

// ::manageGroundMarkerVariables -- every saved marker in a table: one row per area (a RegionNames name, else its region),
// label and color, with how many markers share them; current area first, or sorted by a heading. Rows have checkboxes;
// the top bar filters to markers with variables, selects all shown, and exports or deletes the selection.
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
	// The world map's list of maps, and its proc returning a coord's place in one map (-1 if none); unnamed in RuneLite.
	private static final int WORLDMAP_LIST_ENUM = 1697;
	private static final int FIND_COORD_IN_MAP_SCRIPT = 1715;

	private final MarkerStorage markerStorage;
	private final DeletedMarkers deletedMarkers;
	private final GroundMarkerVariablesSharingManager sharingManager;
	private final MarkerSets markerSets;
	private final Provider<MarkerSetManager> markerSetManagerProvider;
	private final WorldMapPointManager worldMapPointManager;
	private final GroundMarkerVariablesConfig config;
	// Show on map's pins: the last row shown's markers, until the next or closing.
	private final List<WorldMapPoint> pins = new ArrayList<>();

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
		// On some world map (not e.g. a raid's rooms), so it can be shown on it; found just after loading.
		private boolean mapped;
	}

	@Inject
	MarkerManager(ChatboxPanelManager chatboxPanelManager, MarkerStorage markerStorage, ChatMessageManager chatMessageManager,
		Client client, DeletedMarkers deletedMarkers, GroundMarkerVariablesSharingManager sharingManager, ClientThread clientThread,
		MarkerSets markerSets, Provider<MarkerSetManager> markerSetManagerProvider, ChatboxWidgets widgets, SuggestionIconOverlay iconOverlay,
		GroundMarkerVariablesPlugin plugin, WorldMapPointManager worldMapPointManager, GroundMarkerVariablesConfig config)
	{
		super(chatboxPanelManager, clientThread, chatMessageManager, iconOverlay, widgets, client, plugin);
		this.worldMapPointManager = worldMapPointManager;
		this.config = config;
		this.markerSets = markerSets;
		this.markerSetManagerProvider = markerSetManagerProvider;
		this.markerStorage = markerStorage;
		this.deletedMarkers = deletedMarkers;
		this.sharingManager = sharingManager;
	}

	// Rebuilds the list from storage, clearing the selection but keeping the scroll (a new manager starts at the top).
	public void load()
	{
		entries.clear();
		selected.clear();

		Map<String, Entry> byKey = new LinkedHashMap<>();
		for (int regionId : markerStorage.storedRegionIds())
		{
			// Each marker's own area: one region can hold several.
			for (GroundMarkerPointData point : markerStorage.getStoredPoints(regionId))
			{
				String name = RegionNames.name(WorldPoint.fromRegion(regionId, point.getRegionX(), point.getRegionY(), point.getZ()));
				String area = name != null ? name : "Region " + regionId;
				String label = point.getLabel() == null ? "" : point.getLabel();
				byKey.computeIfAbsent(area + '\0' + label + '\0' + point.getColor(),
					key -> new Entry(area, name != null, regionId, label, point.getColor())).points.add(point);
			}
		}
		entries.addAll(byKey.values());
		// Later: Check if tile is on a map to navigate to
		clientThread.invokeLater(() ->
		{
			for (Entry entry : entries)
			{
				GroundMarkerPointData first = entry.points.get(0);
				entry.mapped = onAnyMap(WorldPoint.fromRegion(first.getRegionId(), first.getRegionX(), first.getRegionY(), first.getZ()));
			}
			if (chatboxPanelManager.getCurrentInput() == this)
			{
				update();
			}
		});

		Player player = client.getLocalPlayer();
		WorldPoint location = player == null ? null : WorldPoint.fromLocalInstance(client, player.getLocalLocation());
		String name = location == null ? null : RegionNames.name(location);
		currentArea = location == null ? null : name != null ? name : "Region " + location.getRegionID();
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

	@Override
	protected void buildRows(Widget container)
	{
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
		table(container, true, rows.size(), (i, y) -> addEntry(container, rows.get(i), y));
	}

	// Its checkbox, area, count (right-aligned), color diamond (none for the default color) and label (escaped, so a label's
	// own <col=> etc. shows as written). The whole row is clicked to select it, or a single marker's to edit it, and grays
	// while hovered.
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
		addAction(row, actions, checked ? "Deselect" : "Select", () -> toggle(entry, !checked));
		if (entry.points.size() == 1)
		{
			addAction(row, actions, "Edit", () -> edit(entry.points.get(0)));
		}
		if (entry.mapped)
		{
			Rectangle labelArea = new Rectangle(columnX(LABEL), y, columnWidth(LABEL), ROW_HEIGHT);
			addAction(row, actions, "Show on map", () -> showOnMap(entry, labelArea));
		}
		row.setOnOpListener((JavaScriptCallback) ev -> actions.get(ev.getOp() - 1).run());
		ChatboxWidgets.hover(row, 0x000000, area, label);
	}

	// Pins entry's markers on the open world map and moves it to them, switching maps (e.g. to Prifddinas) through the map's
	// own link handling. Else a notice under labelArea that the map is closed.
	private void showOnMap(Entry entry, Rectangle labelArea)
	{
		Widget map = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
		if (map == null || map.isHidden())
		{
			notice(labelArea, "Open the world map first, then choose Show on map again.");
			return;
		}
		clearPins();
		BufferedImage image = SuggestionIconOverlay.diamond(new Color((entry.color != null ? entry.color : config.markerColor()).getRGB()));
		List<WorldPoint> points = new ArrayList<>();
		for (GroundMarkerPointData point : entry.points)
		{
			WorldPoint worldPoint = WorldPoint.fromRegion(point.getRegionId(), point.getRegionX(), point.getRegionY(), point.getZ());
			points.add(worldPoint);
			WorldMapPoint pin = new WorldMapPoint(worldPoint, image);
			pin.setTooltip(Text.escapeJagex(entry.label.isEmpty() ? entry.area : entry.label));
			pins.add(pin);
			worldMapPointManager.add(pin);
		}
		int x = (int) points.stream().mapToInt(WorldPoint::getX).average().orElse(0);
		int y = (int) points.stream().mapToInt(WorldPoint::getY).average().orElse(0);
		client.setVarcIntValue(VarClientID.WORLDMAP_INTERMAPLINK, coord(new WorldPoint(x, y, points.get(0).getPlane())));
	}

	// Whether any world map has point, as the map's own links look: worldmap_findcoordinmap over each map in its list.
	private boolean onAnyMap(WorldPoint point)
	{
		for (int map : client.getEnum(WORLDMAP_LIST_ENUM).getIntVals())
		{
			client.runScript(FIND_COORD_IN_MAP_SCRIPT, map, coord(point));
			if (client.getIntStack()[0] != -1)
			{
				return true;
			}
		}
		return false;
	}

	// A clientscript coord: plane, x and y packed.
	private static int coord(WorldPoint point)
	{
		return point.getPlane() << 28 | point.getX() << 14 | point.getY();
	}

	private void clearPins()
	{
		pins.forEach(worldMapPointManager::remove);
		pins.clear();
	}

	@Override
	protected void close()
	{
		clearPins();
		super.close();
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
