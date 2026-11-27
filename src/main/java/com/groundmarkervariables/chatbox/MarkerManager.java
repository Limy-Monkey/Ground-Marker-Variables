package com.groundmarkervariables.chatbox;

import com.groundmarkervariables.GroundMarkerVariablesConfig;
import com.groundmarkervariables.chatbox.completion.Completion;
import com.groundmarkervariables.chatbox.completion.LabelCompleter;
import com.groundmarkervariables.markers.DeletedMarkers;
import com.groundmarkervariables.markers.GroundMarkerPointData;
import com.groundmarkervariables.markers.MarkerStorage;
import com.groundmarkervariables.markers.MarkerSet;
import com.groundmarkervariables.markers.MarkerSets;
import com.groundmarkervariables.markers.TileFacts;
import com.groundmarkervariables.markers.minify.MarkerMinifier;
import com.groundmarkervariables.variables.support.RegionNames;
import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.swing.SwingUtilities;
import lombok.RequiredArgsConstructor;
import net.runelite.api.FontTypeFace;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.components.colorpicker.RuneliteColorPicker;
import net.runelite.client.util.Text;

// ::manageGroundMarkerVariables -- every saved marker in a table: one row per area (a RegionNames name, else its region),
// label and color, with how many markers share them; current area first, or sorted by a heading. Rows have checkboxes;
// the top bar filters to markers with variables, selects all shown, and exports or deletes the selection. show(set) lists
// one marker set's tiles instead (disabled entries grayed), never mixed with saved markers; only your own sets' are edited.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class MarkerManager extends SearchableChatbox {
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
    // Tile info (TileFacts) columns after Label, shown by +/-, sortable and headed by icons: Map, yes/no ones by their
    // TileFacts bit (with their tooltips), then the Wilderness level. Region is sized a little shorter; Label gives up the most.
    private static final String[] INFO_HEADINGS = {"Region", "", "", "Label", "Instanced", "On water", "Walkable", "Map", "Wilderness"};
    private static final List<List<String>> INFO_SIZED_FOR = List.of(List.of("Sergeant Damien's Training"), List.of("999"),
        List.of("#"), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    private static final String[] INFO_ICONS = {"<sprite=House Portal (Map Icon)>", "<sprite=Sails (Sailing)>",
        "<sprite=Orb Walk Icon (Minimap)>", "<sprite=Orb World Map Planet (Minimap)>", "<sprite=Killer Skull (Player)>"};
    private static final int[] INFO_BITS = {TileFacts.SEEN_INSTANCED, TileFacts.WATER, TileFacts.WALKABLE, 0, 0};
    private static final String[] INFO_YES = {"Instanced", "On water", "Walkable", null, null};
    private static final String[] INFO_NO = {"Not instanced", "Not on water", "Not walkable", null, "Not in the Wilderness"};
    private static final int MAP = 3;
    private static final int WILDERNESS = 4;
    // Each column's name in the search, *<name> for yes and !<name> for no (see shown).
    private static final String[] INFO_SEARCH = {"instanced", "water", "walkable", "map", "wilderness"};
    // An unlabeled row's label, shown and searched.
    private static final String NO_LABEL = "(no label)";
    // infoValue() until known (Map's is the map id, -1 for none).
    private static final int UNKNOWN = -2;
    static final String YES = ":check_mark@lightgreen:";
    private static final String NO = ":ballot_x@lightpink:";
    // A value's icon size (headings' are larger).
    private static final int INFO_ICON = 10;
    // Shown or not, until the client closes.
    private static boolean showInfo;

    private final MarkerStorage markerStorage;
    private final DeletedMarkers deletedMarkers;
    private final GroundMarkerVariablesSharingManager sharingManager;
    private final MarkerMinifier markerMinifier;
    private final MarkerSets markerSets;
    private final Provider<MarkerSetManager> markerSetManagerProvider;
    // Pinned while this is open: the selected rows' markers and the last row Show on map moved to.
    private final WorldMapPins mapPins;
    private final ColorPickerManager colorPickerManager;
    private final GroundMarkerVariablesConfig config;
    private final TileFacts tileFacts;
    // Resolves the tile info icons by name.
    private final LabelCompleter completer;
    @Nullable
    private Entry shown;
    // The search's @x,y,plane tiles as loaded, each a row of its own (to edit on its own).
    private Set<WorldPoint> ownRowTiles = Set.of();

    private final List<Entry> entries = new ArrayList<>();
    private final Set<Entry> selected = new HashSet<>();
    // { } toggle: only rows whose label has a variable. Off at first.
    private boolean variablesOnly;
    // The player's area when loaded, listed first; then areas with a marker drawn nearby (see forEachNearby).
    @Nullable
    private String currentArea;
    private Set<String> nearbyAreas = Set.of();
    // A world map lookup finished since load() (see areaName).
    private boolean namesStale;
    // The set whose tiles are listed (see show), as of load(), else saved markers (and, while searching, every set's).
    @Nullable
    private MarkerSet set;
    // The sets load() listed tiles from, in order.
    private List<MarkerSet> loadedSets = List.of();
    // What load() listed (the recycling bin, a set, or saved markers); the selection isn't kept into or out of it.
    @Nullable
    private String loadedView;

    // Every marker in one area sharing a label ("" for none) and color; a set's tiles, its set and entry.
    @RequiredArgsConstructor
    private static final class Entry {
        @Nullable
        private final MarkerSet inSet;
        @Nullable
        private final MarkerSet.Entry setEntry;
        // Its RegionNames name, else "Dungeon <id>".
        private final String area;
        private final boolean named;
        private final int regionId;
        private final String label;
        @Nullable
        private final Color color;
        // The world maps drawing it (see mapNames), as of load().
        private final List<String> maps;
        private final List<GroundMarkerPointData> points = new ArrayList<>();

        // Its label as shown and searched.
        private String shownLabel() {
            return label.isEmpty() ? NO_LABEL : label;
        }

        // Its preferred map's name (the Map column's), else "".
        private String mapName() {
            return maps.isEmpty() ? "" : maps.get(0);
        }
    }

    // Rebuilds the list from storage, keeping the scroll (a new manager starts at the top) and the selection: each row whose
    // tiles were all selected, by position (so not one a new marker joined).
    public void load() {
        // As now stored; one that's gone (e.g. deleted): back to saved markers.
        String key = set == null ? null : set.key();
        set = markerSets.listed().stream().filter(listed -> listed.key().equals(key)).findFirst().orElse(null);
        String view = (recycleBin ? "\0bin" : "") + (set == null ? "" : key);
        Set<WorldPoint> wasSelected = new HashSet<>();
        if (Objects.equals(view, loadedView)) {
            selected.forEach(entry -> entry.points.forEach(point -> wasSelected.add(point.worldPoint())));
        }
        loadedView = view;
        entries.clear();
        selected.clear();
        shown = null;

        Map<String, Entry> byKey = new LinkedHashMap<>();
        ownRowTiles = searchedTiles();
        leaveEmptyBin();
        if (recycleBin) {
            // Deleted saved markers, or the set's tiles (each in its entry as it was).
            deletedMarkers.from(setKey()).forEach(deleted -> addPoint(byKey, deleted.tile, String.valueOf(byKey.size()), set, deleted.entry));
        } else {
            if (set == null) {
                for (int regionId : markerStorage.storedRegionIds()) {
                    markerStorage.getStoredPoints(regionId).forEach(point -> addPoint(byKey, point, "", null, null));
                }
            }
            // The set's tiles, or every set's (shown while searching, see shown). A row never spans entries, which are
            // switched on and off whole.
            loadedSets = set != null ? List.of(set) : markerSets.listed();
            for (MarkerSet listed : loadedSets) {
                listed.entries.forEach(entry -> entry.tiles.forEach(tile -> addPoint(byKey, tile, listed.key() + '\0' + entry.key, listed, entry)));
            }
        }
        entries.addAll(byKey.values());
        entries.stream().filter(entry -> entry.points.stream().allMatch(point -> wasSelected.contains(point.worldPoint())))
            .forEach(selected::add);

        Player player = client.getLocalPlayer();
        currentArea = player == null ? null : areaOf(WorldPoint.fromLocalInstance(client, player.getLocalLocation()));
        Set<WorldPoint> nearby = new HashSet<>();
        plugin.forEachNearby((wv, rendered) -> nearby.add(rendered.stored));
        nearbyAreas = entries.stream().filter(entry -> entry.points.stream().anyMatch(point -> nearby.contains(point.worldPoint())))
            .map(entry -> entry.area).collect(Collectors.toSet());
    }

    // RegionNames' name for point, by the world map drawing it once that's known (then this reloads, if still open).
    @Nullable
    private String areaName(WorldPoint point) {
        return RegionNames.name(point, mapPins.mapAt(point, this::mapChecked));
    }

    // areaName(point), else "Dungeon <region id>".
    private String areaOf(WorldPoint point) {
        return Objects.requireNonNullElse(areaName(point), "Dungeon " + point.getRegionID());
    }

    // point into the row for its own area (one region can hold several), label and color; row ("" to share) keeps it apart,
    // as does searching for its tile.
    private void addPoint(Map<String, Entry> byKey, GroundMarkerPointData point, String row, @Nullable MarkerSet inSet,
        @Nullable MarkerSet.Entry setEntry) {
        String area = areaOf(point.worldPoint());
        String label = point.getLabel() == null ? "" : point.getLabel();
        String own = ownRowTiles.contains(point.worldPoint()) ? "\0" + point.worldPoint() : "";
        byKey.computeIfAbsent(row + '\0' + area + '\0' + label + '\0' + point.getColor() + infoKey(point) + own,
            key -> new Entry(inSet, setEntry, area, areaName(point.worldPoint()) != null, point.getRegionId(), label, point.getColor(),
                mapNames(point.worldPoint()))).points.add(point);
    }

    // Opens on set's tiles (null: saved markers), searching for search. Client thread.
    public void show(@Nullable MarkerSet set, String search) {
        this.set = set;
        recycleBin = false;
        query = search;
        load();
        chatboxPanelManager.openInput(this);
    }

    // Whether a set's tiles are listed, not saved markers (so the logo offers them).
    public boolean showingSet() {
        return set != null;
    }

    // Whether set and its entry are both switched on.
    private boolean entryOn(MarkerSet set, MarkerSet.Entry entry) {
        return markerSets.enabled(set) && markerSets.enabled(set, entry);
    }

    // Whether set is the user's own, so editable.
    private static boolean own(@Nullable MarkerSet set) {
        return set != null && set.kind == MarkerSet.Kind.OWN;
    }

    // By sortColumn, reversed if descending; then the current area first, nearby ones next, named areas alphabetically,
    // unnamed regions by id, labels alphabetically (none last), then color.
    private Comparator<Entry> order() {
        // The player's area, areas with markers nearby, then by world map: the overworld, each other map (by name), none, not yet
        // known.
        Comparator<Entry> byArea = Comparator.comparingInt((Entry e) -> e.area.equals(currentArea) ? 0 : nearbyAreas.contains(e.area) ? 1 : 2)
            .thenComparingInt(e -> mapRank(infoValue(e.points.get(0), MAP)))
            .thenComparing(Entry::mapName)
            .thenComparing(e -> !e.named).thenComparing(e -> e.area, String.CASE_INSENSITIVE_ORDER).thenComparingInt(e -> e.regionId);
        Comparator<Entry> byLabel = Comparator.comparing((Entry e) -> e.label.isEmpty()).thenComparing(e -> e.label, String.CASE_INSENSITIVE_ORDER);
        Comparator<Entry> byColor = Comparator.comparing(e -> e.color == null ? null : e.color.getRGB(),
            Comparator.nullsFirst(Comparator.naturalOrder()));
        Comparator<Entry> byColumn = sortColumn == LABEL ? byLabel : byArea;
        if (showInfo && sortColumn > LABEL) {
            // A tile info column: yes (higher levels first), then no; Map: overworld, dungeon, then none. Reversed if descending;
            // unknown always last.
            int info = sortColumn - LABEL - 1;
            ToIntFunction<Entry> value = e -> infoValue(e.points.get(0), info);
            Comparator<Entry> byInfo = Comparator.comparingInt(e -> info != MAP ? -value.applyAsInt(e) : mapRank(value.applyAsInt(e)));
            return Comparator.comparing((Entry e) -> value.applyAsInt(e) == UNKNOWN).thenComparing(descending ? byInfo.reversed() : byInfo)
                .thenComparing(byArea).thenComparing(byLabel).thenComparing(byColor);
        }
        return (descending ? byColumn.reversed() : byColumn).thenComparing(byArea).thenComparing(byLabel).thenComparing(byColor);
    }

    @Override
    protected void open() {
        update();
    }

    @Override
    protected String title() {
        String name = set != null ? Text.escapeJagex(set.title) : recycleBin ? "Markers" : "Ground Markers";
        return recycleBin ? name : name + " (" + viewRows().stream().mapToInt(entry -> entry.points.size()).sum() + ")";
    }

    // A set's tiles: Sets (opening Marker Sets), then the set.
    @Override
    protected List<Crumb> crumbs() {
        return set == null ? super.crumbs()
            : List.of(new Crumb("Sets", "Open Marker Sets", () -> chatboxPanelManager.openInput(markerSetManagerProvider.get())),
                new Crumb(title(), null, null));
    }

    @Override
    protected int recycled() {
        return deletedMarkers.from(setKey()).size();
    }

    @Nullable
    private String setKey() {
        return set == null ? null : set.key();
    }

    // Right to left after x and _: :recycle:, Del, Copy, Set, { }, All (while filtered), (selected count). The bin shows
    // none; a set just :recycle: (its deleted tiles) and { } (its rows copy and delete).
    @Override
    protected void buildTopBar(Widget container, FontTypeFace font, int rightX) {
        rightX = recycleBinButton(container, rightX, this::reopen);
        if (recycleBin) {
            return;
        }

        List<Entry> visible = viewRows();
        boolean anySelected = visible.stream().anyMatch(selected::contains);
        boolean allSelected = !visible.isEmpty() && selected.containsAll(visible);
        int count = selectedPoints().size();
        String selectedTarget = "<col=ff9040>" + count + (count == 1 ? " marker" : " markers") + "</col>";

        if (set == null) {
            rightX = nextX(button(container, font, "Del", "Delete selected", selectedTarget, anySelected, rightX, () -> confirmDelete(null, selectedPoints())));
            rightX = nextX(button(container, font, "Copy", "Export selected", selectedTarget, anySelected, rightX, () -> export(selectedPoints())));
            Widget makeSet = button(container, font, "Set", "Make a marker set from selected", selectedTarget, anySelected, rightX, this::makeSet);
            if (anySelected && markerSets.sets().stream().anyMatch(MarkerManager::own)) {
                makeSet.setAction(1, "Add to existing set");
                makeSet.setOnOpListener((JavaScriptCallback) ev -> {
                    if (ev.getOp() == 2) {
                        addToExistingSet();
                    } else {
                        makeSet();
                    }
                });
            }
            rightX = nextX(makeSet);
        }
        Widget filter = button(container, font, "{ }", variablesOnly ? "Show all markers" : "Show only markers with variables", "",
            !entries.isEmpty(), rightX, this::toggleVariablesFilter);
        if (variablesOnly) {
            filter.setTextColor(ACTIVE_COLOR);
            filter.setOnMouseLeaveListener((JavaScriptCallback) ev -> filter.setTextColor(ACTIVE_COLOR));
        }
        rightX = nextX(filter);

        // All only while filtered to markers with variables.
        if (variablesOnly && set == null) {
            Widget all = button(container, font, "All", allSelected ? "Deselect all" : "Select all", "", !visible.isEmpty(), rightX,
                allSelected ? this::deselectAll : this::selectAll);
            if (anySelected && !allSelected) {
                all.setAction(1, "Deselect all");
                all.setOnOpListener((JavaScriptCallback) ev -> {
                    if (ev.getOp() == 2) {
                        deselectAll();
                    } else {
                        selectAll();
                    }
                });
            }
            rightX = nextX(all);
        }

        if (count > 0) {
            button(container, font, "(" + count + ")", "Deselect all", selectedTarget, true, rightX, this::deselectAll);
        }
    }

    @Override
    protected void buildRows(Widget container) {
        if (namesStale || !searchedTiles().equals(ownRowTiles)) {
            namesStale = false;
            load();
        }
        updatePins();
        List<Entry> rows = visibleEntries();
        if (rows.isEmpty()) {
            String none = !query.isEmpty() ? "No ground markers match \"" + Text.escapeJagex(query) + "\"."
                : variablesOnly ? "No ground markers have variables." : set != null ? "This marker set has no tiles." : "You have no saved ground markers.";
            addText(container, none, widgets.fontId(), 0x000000, 6, RESULTS_Y);
            return;
        }
        // By section (saved markers, then each set) while a search also finds other sets' rows, and a set's by entry (in its
        // order); each under a row naming it, which stays on the fixed line while scrolled into its rows.
        boolean sections = rows.stream().anyMatch(row -> row.inSet != set);
        rows.sort(Comparator.comparingInt((Entry e) -> loadedSets.indexOf(e.inSet))
            .thenComparingInt(e -> e.inSet == null ? 0 : e.inSet.entries.indexOf(e.setEntry)).thenComparing(order()));
        List<Object> lines = new ArrayList<>();
        // The last section's loadedSets index (-1 for saved markers').
        int section = -2;
        MarkerSet.Entry titled = null;
        for (Entry row : rows) {
            if (sections && loadedSets.indexOf(row.inSet) != section) {
                lines.add(new Group(row.inSet, null));
                section = loadedSets.indexOf(row.inSet);
            }
            // None in a set's bin (its entries as they were, not to switch).
            if (row.setEntry != null && row.setEntry != titled && !recycleBin) {
                lines.add(new Group(row.inSet, titled = row.setEntry));
            }
            lines.add(row);
        }
        columns(container, showInfo ? INFO_HEADINGS : HEADINGS, showInfo ? INFO_SIZED_FOR : SIZED_FOR, LABEL);
        table(container, true, lines.size(), (i, y) -> {
            if (lines.get(i) instanceof Entry) {
                addEntry(container, (Entry) lines.get(i), y);
            } else {
                addGroup(container, (Group) lines.get(i), "", y);
            }
        }, (i, y) -> {
            Entry row = lines.get(i) instanceof Entry ? (Entry) lines.get(i) : null;
            if (row == null || row.setEntry == null && !sections || recycleBin) {
                return false;
            }
            addGroup(container, new Group(row.inSet, row.setEntry), sections && row.inSet != null ? row.inSet.title + " > " : "", y);
            return true;
        });
        infoToggle(container);
    }

    // A title row: a section (a set's, else saved markers'), or with entry, a set entry's.
    @RequiredArgsConstructor
    private static final class Group {
        @Nullable
        private final MarkerSet set;
        @Nullable
        private final MarkerSet.Entry entry;
    }

    // A section's name (a set's opening it, or finding it in Marker Sets), or an entry's checkbox (switching it) and name
    // after prefix (finding it in Marker Sets); your own sets' and entries' retitled from it too. Cut before the tile info's heading icons (which stay on the fixed line), and
    // grayed while off.
    private void addGroup(Widget container, Group group, String prefix, int y) {
        int x = 6;
        if (group.entry != null) {
            boolean on = markerSets.enabled(group.set, group.entry);
            Widget checkbox = checkbox(container, on, x, y);
            addAction(checkbox, new ArrayList<>(), on ? "Disable" : "Enable", () -> {
                markerSets.setEnabled(group.set, group.entry, !on);
                update();
            });
            x += checkbox.getOriginalWidth() + 4;
        }
        boolean on = group.set == null || (group.entry == null ? markerSets.enabled(group.set) : entryOn(group.set, group.entry));
        int color = on ? ACTIVE_COLOR : ChatboxWidgets.DISABLED_COLOR;
        String name = group.entry != null ? prefix + group.entry.name() : group.set != null ? "Set: " + group.set.title : "Ground Markers";
        int width = columnX(showInfo ? LABEL + 1 : HEADINGS.length) - x;
        Widget text = ChatboxWidgets.text(container, Text.escapeJagex(fit(name, width)), widgets.fontId(), color, x, y, width, ROW_HEIGHT);
        if (group.set != null) {
            List<Runnable> actions = new ArrayList<>();
            if (group.entry == null) {
                addAction(text, actions, "Show set", () -> show(group.set, query));
            }
            if (own(group.set)) {
                MarkerSetManager sets = markerSetManagerProvider.get();
                if (group.entry == null) {
                    addAction(text, actions, "Set title", () -> sets.askTitle(group.set, this::reopen));
                } else {
                    addSubmenu(text, actions, "Edit", Map.of("Set Title", () -> sets.askTitle(group.set, group.entry, group.entry.tiles, this::reopen)));
                    addSubmenu(text, actions, "Move", sets.moves(group.set, group.entry, this::reopen));
                }
            }
            addAction(text, actions, "Find in Marker Sets", () -> findInSets(group.set, group.entry));
            ChatboxWidgets.hover(text, color);
        }
    }

    // Marker Sets searching for set, or just its entry (under it) if given.
    private void findInSets(MarkerSet set, @Nullable MarkerSet.Entry entry) {
        chatboxPanelManager.openInput(markerSetManagerProvider.get().search(entry == null ? set.title : set.title + " " + entry.name()));
    }

    // In the header: shown, each tile info column's icon (clicked, hides them); hidden, one at Label's end to show them.
    private void infoToggle(Widget container) {
        Widget toggle = ChatboxWidgets.text(container, showInfo ? "-" : "+", widgets.fontId(), 0x000000,
            container.getWidth() - ChatboxWidgets.SCROLLBAR_WIDTH, RESULTS_Y, ChatboxWidgets.SCROLLBAR_WIDTH, ROW_HEIGHT);
        toggle.setXTextAlignment(WidgetTextAlignment.CENTER);
        addAction(toggle, new ArrayList<>(), showInfo ? "Hide Tile Facts" : "Show Tile Facts",
            () -> {
                showInfo = !showInfo;
                // Regrouped by (or no longer by) tile info.
                load();
                update();
            });
        ChatboxWidgets.hover(toggle, 0x000000);
    }

    @Override
    @Nullable
    protected Supplier<Completion.Icon> headingIcon(int column) {
        return showInfo && column > LABEL ? () -> completer.icon(INFO_ICONS[column - LABEL - 1]) : null;
    }

    // A tile info column's value for point (info i): the map id (-1 for none), 1 or 0, or the Wilderness level; UNKNOWN until
    // known. A row's tiles all share them (see infoKey), so its first's is the row's.
    private int infoValue(GroundMarkerPointData point, int i) {
        if (i == MAP) {
            Integer map = mapPins.mapAt(point.worldPoint(), this::mapChecked);
            return map == null ? UNKNOWN : map;
        }
        Integer facts = tileFacts.get(point);
        return i == WILDERNESS ? TileFacts.wildernessLevel(point.worldPoint()) : facts == null ? UNKNOWN : (facts & INFO_BITS[i]) != 0 ? 1 : 0;
    }

    // A Map value's place in order: the overworld, other maps, none, then unknown.
    private static int mapRank(int map) {
        return map == RegionNames.SURFACE_MAP ? 0 : map > 0 ? 1 : map == UNKNOWN ? 3 : 2;
    }

    // A world map lookup finished: reload on the next draw (this may not have opened yet; openInput waits a tick too).
    private void mapChecked() {
        namesStale = true;
        updateIfOpen();
    }

    // With tile info shown, what else a row's tiles share: each info column's value.
    private String infoKey(GroundMarkerPointData point) {
        StringBuilder key = new StringBuilder();
        for (int i = 0; showInfo && i < INFO_ICONS.length; i++) {
            key.append('\0').append(infoValue(point, i));
        }
        return key.toString();
    }

    // Each tile info column's icon (Wilderness: the level, if any) and its tooltip, else "?" until it's known.
    private void addInfo(Widget container, Entry entry, int y) {
        for (int i = 0; i < INFO_ICONS.length; i++) {
            int column = LABEL + 1 + i;
            String name = INFO_HEADINGS[column];
            int value = infoValue(entry.points.get(0), i);
            String syntax = null;
            String tip;
            if (value == UNKNOWN) {
                cell(container, "?", DETAIL_COLOR, column, y);
                tip = i == MAP ? "Finding its world map." : "Go near this tile to load its info.";
            } else if (i == MAP) {
                syntax = value == RegionNames.SURFACE_MAP ? "<sprite=Map Link Upstairs (Map Icon)>" : value > 0 ? "<sprite=Map Link Downstairs (Map Icon)>"
                    : "<sprite=Dungeon (Map Icon)>";
                tip = value < 0 ? "Not on the world map" : String.join("</br>", entry.maps);
            } else if (i == WILDERNESS && value > 0) {
                cell(container, String.valueOf(value), DETAIL_COLOR, column, y);
                tip = name + " " + value;
            } else {
                syntax = value > 0 ? YES : NO;
                tip = value > 0 ? INFO_YES[i] : INFO_NO[i];
            }
            if (syntax != null) {
                String icon = syntax;
                rowIcon(container, columnX(column), y, INFO_ICON, () -> completer.icon(icon));
            }
            tips.add(new SuggestionIconOverlay.Tip(container, cellArea(column, y), tip));
        }
    }

    // text's whole as column's tooltip at y (see longTip), if cell() cut it drawing cell.
    private void tipIfCut(Widget container, Widget cell, String text, int column, int y) {
        if (!cell.getText().equals(Text.escapeJagex(text))) {
            tips.add(new SuggestionIconOverlay.Tip(container, cellArea(column, y), longTip(text)));
        }
    }

    // Its checkbox, area, count (right-aligned), color diamond (none for the default color) and label (escaped, so a label's
    // own <col=> etc. shows as written). The whole row is clicked to select it, or a single marker's to edit it, and grays
    // while hovered. In the recycling bin, its crossed check box (or the row) restores it, and right-click deletes it for good.
    // A set's tile has no checkbox (its entry's row switches it), and is copied or removed by right-click.
    private void addEntry(Widget container, Entry entry, int y) {
        boolean checked = selected.contains(entry);
        // A set's tile: grayed while its entry (or the set) is off.
        int color = entry.inSet == null || recycleBin || entryOn(entry.inSet, entry.setEntry) ? 0x000000 : ChatboxWidgets.DISABLED_COLOR;
        if (entry.inSet == null || recycleBin) {
            checkbox(container, checked, 6, y);
        }
        Widget area = cell(container, entry.area, color, 0, y);
        cell(container, String.valueOf(entry.points.size()), DETAIL_COLOR, 1, y).setXTextAlignment(WidgetTextAlignment.RIGHT);
        if (entry.color != null) {
            Completion.Icon diamond = Completion.Icon.diamond(entry.color);
            rowIcon(container, columnX(2), y, DIAMOND, () -> diamond);
        }
        Widget label = cell(container, entry.shownLabel(), color, LABEL, y);
        tipIfCut(container, area, entry.area, 0, y);
        tipIfCut(container, label, entry.shownLabel(), LABEL, y);
        if (showInfo) {
            addInfo(container, entry, y);
        }

        // The row's options, made once: select (or restore), edit (Label for one marker; Color for all of the row's; a set
    // tile's entry's title), map,
        // delete for good.
        Rectangle labelArea = cellArea(LABEL, y);
        Map<String, Runnable> edit = new LinkedHashMap<>();
        if (entry.points.size() == 1) {
            edit.put("Label", () -> edit(entry));
        }
        edit.put("Color", () -> pickColor(entry));
        // A set row's tiles titled (split from their entry unless they're all of it), or moved into another titled entry.
        Map<String, Runnable> moveTo = new LinkedHashMap<>();
        if (own(entry.inSet)) {
            edit.put("Set Title", () -> markerSetManagerProvider.get().askTitle(entry.inSet, entry.setEntry, entry.points, this::reopen));
            entry.inSet.entries.stream().filter(other -> other != entry.setEntry && other.title != null).forEach(other -> moveTo.put(other.title, () -> {
                markerSets.moveTiles(entry.inSet, entry.points, other);
                reopen();
            }));
        }
        Map<String, Runnable> map = mapPins.mapChoices(entry.points, entry.points, () -> {
            shown = entry;
            updatePins();
        }, text -> notice(labelArea, text), this::updateIfOpen);
        // In the bin, its one marker's key there.
        String binKey = DeletedMarkers.key(setKey(), entry.setEntry, entry.points.get(0));
        String what = set == null ? "ground marker" : "tile";
        Runnable delete = () -> confirmPurge(deletedMarkers.minutesLeft(binKey), labelArea, "this " + what, () -> {
            deletedMarkers.remove(binKey);
            sendChatMessage("The " + what + " was deleted permanently.");
        });

        Map<String, Runnable> manage = new LinkedHashMap<>();
        manage.put("Copy", () -> export(entry.points));
        manage.put("Find in Marker Sets", () -> findInSets(entry.inSet, entry.setEntry));

        // Over the whole row (tile info too), on top of its cells: a part per column, each with those and Filter by its column.
        String[] columns = showInfo ? INFO_HEADINGS : HEADINGS;
        for (int column = 0; column < columns.length; column++) {
            int x = column == 0 ? 6 : columnX(column);
            Widget part = ChatboxWidgets.child(container, WidgetType.TEXT, x, y, columnX(column + 1) - x, ROW_HEIGHT);
            List<Runnable> actions = new ArrayList<>();
            if (entry.inSet == null || recycleBin) {
                addAction(part, actions, recycleBin ? "Restore" : checked ? "Deselect" : "Select",
                    recycleBin ? () -> restore(entry, binKey) : () -> toggle(entry, !checked));
            }
            Map.Entry<String, String> filter = recycleBin ? null : filter(entry, column);
            if (!recycleBin && (entry.inSet == null || own(entry.inSet))) {
                addSubmenu(part, actions, "Edit", edit);
            }
            if (!recycleBin) {
                addSubmenu(part, actions, "Move Marker", moveTo);
            }
            if (entry.inSet != null && !recycleBin) {
                addSubmenu(part, actions, "Manage", manage);
            }
            addSubmenu(part, actions, "Map", map);
            if (filter != null) {
                addAction(part, actions, "Filter By " + filter.getKey(), () -> addToSearch(filter.getValue()));
            }
            if (own(entry.inSet) && !recycleBin) {
                addAction(part, actions, "Delete from Set", () -> confirmDelete(entry.inSet, entry.points));
            }
            if (recycleBin) {
                addAction(part, actions, "Delete", delete);
            }
            ChatboxWidgets.hover(part, color, area, label);
        }
    }

    // A set's view: the whole set, always. Else the selected rows and the one shown.
    private void updatePins() {
        if (set != null && !recycleBin) {
            mapPins.pin(set);
            return;
        }
        Set<Entry> rows = new HashSet<>(selected);
        if (shown != null) {
            rows.add(shown);
        }
        Map<String, List<GroundMarkerPointData>> tiles = new HashMap<>();
        rows.forEach(entry -> tiles.computeIfAbsent(entry.label.isEmpty() ? entry.area : entry.label, label -> new ArrayList<>())
            .addAll(entry.points));
        mapPins.pin(rows, tiles);
    }

    @Override
    protected void close() {
        mapPins.clear();
        super.close();
    }

    // A single marker's row's label.
    private void edit(Entry entry) {
        GroundMarkerPointData point = entry.points.get(0);
        editLabel(point, entry.inSet != null
            ? label -> markerSets.setLabel(entry.inSet, List.of(point), label)
            : label -> plugin.saveExpandedLabel(point.worldPoint(), label));
    }

    // RuneLite's color picker, starting at the row's color; the picked one goes to every marker in the row.
    private void pickColor(Entry entry) {
        Color start = entry.color != null ? entry.color : config.markerColor();
        SwingUtilities.invokeLater(() -> {
            RuneliteColorPicker picker = colorPickerManager.create(client, start, "Tile marker color", false);
            picker.setOnClose(color -> clientThread.invoke(() -> {
                if (!color.equals(entry.color)) {
                    if (entry.inSet != null) {
                        markerSets.editTiles(entry.inSet, tile -> entry.points.contains(tile) ? tile.withColor(color) : tile);
                    } else {
                        markerStorage.replacePoints(entry.points.stream().map(point -> point.withColor(color)).collect(Collectors.toList()));
                        plugin.loadPoints();
                    }
                    // Reloaded, for the row's new color.
                    if (chatboxPanelManager.getCurrentInput() == this) {
                        reopen();
                    }
                }
            }));
            picker.setVisible(true);
        });
    }

    @Override
    public void pinClicked(WorldPoint tile) {
        search(query -> tileSearch(tile));
    }

    // Reloaded, e.g. to show an edited label.
    @Override
    protected void reopen() {
        load();
        super.reopen();
    }

    private void toggle(Entry entry, boolean select) {
        if (select) {
            selected.add(entry);
        } else {
            selected.remove(entry);
        }
        update();
    }

    private void selectAll() {
        selected.addAll(viewRows());
        update();
    }

    private void deselectAll() {
        selected.clear();
        update();
    }

    // Hidden rows are deselected, so Del/Copy never act on something not shown.
    private void toggleVariablesFilter() {
        variablesOnly = !variablesOnly;
        if (variablesOnly) {
            selected.removeIf(entry -> !hasVariable(entry));
        }
        scrollOffset = 0;
        update();
    }

    private static boolean hasVariable(Entry entry) {
        return HAS_VARIABLE.matcher(entry.label).find();
    }

    private List<Entry> visibleEntries() {
        List<Entry> visible = new ArrayList<>();
        for (Entry entry : entries) {
            if (shown(entry)) {
                visible.add(entry);
            }
        }
        return visible;
    }

    // The shown rows of what's listed itself, not other sets' a search found.
    private List<Entry> viewRows() {
        return visibleEntries().stream().filter(entry -> entry.inSet == set).collect(Collectors.toList());
    }

    // Passes the { } filter and the search: tile words (see tileMatch), *<info> / !<info> words, then its text.
    private boolean shown(Entry entry) {
        if (variablesOnly && !hasVariable(entry) || entry.inSet != set && query.isBlank()) {
            return false;
        }
        List<WorldPoint> tiles = entry.points.stream().map(GroundMarkerPointData::worldPoint).collect(Collectors.toList());
        StringBuilder words = new StringBuilder();
        for (String word : normalized(query).split(" ")) {
            Boolean tile = tileMatch(word, tiles);
            if (tile != null) {
                if (!tile) {
                    return false;
                }
                continue;
            }
            boolean yes = word.startsWith("*");
            if (!yes && !word.startsWith("!")) {
                words.append(word).append(' ');
                continue;
            }
            int info = infoSearch(word.substring(1));
            if (info >= 0 && !Boolean.valueOf(yes).equals(rowYes(entry, info))) {
                return false;
            }
        }
        return ChatboxWidgets.hasWords(entry.area + "\n" + entry.shownLabel() + "\n" + String.join("\n", entry.maps) + "\n" + colorHex(entry)
            + (entry.inSet == null ? "" : "\n" + entry.inSet.title + "\n" + entry.setEntry.name()), words.toString().trim());
    }

    // The names of every world map drawing point, preferred first ("Overworld" for the surface; unnamed ones left out).
    private List<String> mapNames(WorldPoint point) {
        List<String> names = new ArrayList<>();
        for (int map : Objects.requireNonNullElse(mapPins.mapsAt(point, this::mapChecked), List.<Integer>of())) {
            String name = map == RegionNames.SURFACE_MAP ? "Overworld" : RegionNames.mapName(map);
            if (name != null) {
                names.add(name);
            }
        }
        return names;
    }

    // Its color as saved, #AARRGGBB (so "ff0000" finds red too); the default marker color for none.
    private String colorHex(Entry entry) {
        return String.format("#%08X", (entry.color != null ? entry.color : config.markerColor()).getRGB());
    }

    // Filter by column: its name -> what it adds to the search (entry's region, label's first line, color, map name, or a
    // tile info its tiles all share as *<info> / !<info>); null for the count or an info not known.
    @Nullable
    private Map.Entry<String, String> filter(Entry entry, int column) {
        switch (column) {
            case 0:
                return Map.entry("Region", entry.area);
            case 1:
                return null;
            case 2:
                return Map.entry("Color", colorHex(entry));
            case LABEL:
                String label = MarkerSet.firstLine(entry.label);
                return Map.entry("Label", label.isEmpty() ? NO_LABEL : label);
        }
        int info = column - LABEL - 1;
        Boolean yes = rowYes(entry, info);
        if (yes == null) {
            return null;
        }
        String map = info == MAP && yes ? entry.mapName() : "";
        return Map.entry(INFO_HEADINGS[column], !map.isEmpty() ? map : (yes ? "*" : "!") + INFO_SEARCH[info]);
    }

    // The search's tiles, written as tiles or packed (see packed).
    private Set<WorldPoint> searchedTiles() {
        Set<WorldPoint> tiles = new HashSet<>();
        for (String word : normalized(query).split(" ")) {
            WorldPoint tile = tileWord(word);
            if (tile != null) {
                tiles.add(tile);
            } else if (word.matches("-?\\d{6,10}")) {
                tiles.addAll(packed(Long.parseLong(word)));
            }
        }
        return tiles;
    }

    // The tile info column a search word names (all of an INFO_SEARCH name, or a start only one has), else -1.
    private static int infoSearch(String word) {
        int found = -1;
        for (int i = 0; i < INFO_SEARCH.length && !word.isEmpty(); i++) {
            if (INFO_SEARCH[i].equals(word)) {
                return i;
            }
            if (INFO_SEARCH[i].startsWith(word)) {
                found = found == -1 ? i : -2;
            }
        }
        return Math.max(found, -1);
    }

    // Whether entry's tiles are all yes for tile info i (Map: on some world map), or all no; null if any is unknown or they differ.
    @Nullable
    private Boolean rowYes(Entry entry, int i) {
        Set<Boolean> answers = new HashSet<>();
        for (GroundMarkerPointData point : entry.points) {
            int value = infoValue(point, i);
            answers.add(value == UNKNOWN ? null : i == MAP ? value >= 0 : value > 0);
        }
        return answers.size() == 1 ? answers.iterator().next() : null;
    }

    private void export(List<GroundMarkerPointData> points) {
        clip(markerMinifier.exportGmvPlain(points), points.size() + " ground markers were copied to your clipboard.");
    }

    // Asks for a title, starting as (and blank is) MarkerSets#defaultTitle's, then whether to keep the markers or move them
    // into the set. The author is the logged-in name, else anon.
    private void makeSet() {
        List<Entry> chosen = chosenEntries();
        String defaultTitle = MarkerSets.defaultTitle(selectedPoints());
        Player player = client.getLocalPlayer();
        String author = player != null && player.getName() != null ? player.getName() : MarkerSet.ANON;
        askText("Marker set title", defaultTitle, title -> keepOrMove(chosen,
            move -> addToSet(chosen, move, entries -> markerSets.create(title.isBlank() ? defaultTitle : title.trim(), author, entries))));
    }

    // Picks one of the user's own sets in Marker Sets (searchable, however many there are), then asks whether to keep the
    // markers or move them into it.
    private void addToExistingSet() {
        List<Entry> chosen = chosenEntries();
        chatboxPanelManager.openInput(markerSetManagerProvider.get().pick(set ->
            keepOrMove(chosen, move -> addToSet(chosen, move, entries -> markerSets.add(set, entries)))));
    }

    private void keepOrMove(List<Entry> chosen, Consumer<Boolean> then) {
        new ConfirmChatbox(chatboxPanelManager, "Keep these markers as your own too, or move them into the set?"
                + "<br>Moving deletes them from core Ground Markers if this plugin is off.")
            .option("Keep them", () -> then.accept(false))
            .option("Move them", () -> then.accept(true))
            .option("Cancel", () -> chatboxPanelManager.openInput(this))
            .build();
    }

    // One entry per row (a row of same-label markers becomes a group), given to save, which returns the set; then shows
    // Marker Sets.
    private void addToSet(List<Entry> chosen, boolean move, Function<List<MarkerSet.Entry>, MarkerSet> save) {
        clientThread.invoke(() -> {
            List<MarkerSet.Entry> entries = new ArrayList<>();
            List<GroundMarkerPointData> points = new ArrayList<>();
            for (Entry entry : chosen) {
                entries.add(new MarkerSet.Entry(null, null, entry.points, entry.points.size() > 1, true, entries.size()));
                points.addAll(entry.points);
            }
            MarkerSet set = save.apply(entries);
            // Too large: nothing saved, so nothing moved.
            if (set == null) {
                sendChatMessage(MarkerSets.TOO_LARGE);
                chatboxPanelManager.openInput(this);
                return;
            }
            // Moved, not deleted: they're in the set, so not recycled.
            if (move) {
                markerStorage.deletePoints(points);
            }
            sendChatMessage(points.size() + " ground markers were added to the marker set \"" + set.title + "\".");
            chatboxPanelManager.openInput(markerSetManagerProvider.get());
        });
    }

    private List<Entry> chosenEntries() {
        return visibleEntries().stream().filter(selected::contains).collect(Collectors.toList());
    }

    // Saved markers to the recycling bin, or tiles out of inSet.
    private void confirmDelete(@Nullable MarkerSet inSet, List<GroundMarkerPointData> points) {
        int n = points.size();
        String from = inSet == null ? null : "the marker set \"" + inSet.title + "\"";
        new ConfirmChatbox(chatboxPanelManager, from != null ? "Are you sure you want to delete " + n + (n == 1 ? " tile" : " tiles") + " from "
                + Text.escapeJagex(from) + "?"
                : "Are you sure you want to delete " + n + (n == 1 ? " ground marker?" : " ground markers?") + "<br>This deletes the Core RuneLite Ground Markers.")
            .yes(() -> {
                if (from != null) {
                    if (own(inSet)) {
                        markerSets.deleteTiles(inSet, points);
                    }
                    sendChatMessage(n + (n == 1 ? " tile was" : " tiles were") + " deleted from " + from
                        + " and moved to its recycling bin, where they can be restored until you close the client.");
                } else {
                    deletedMarkers.addAll(points);
                    markerStorage.deletePoints(points);
                    sendChatMessage(n + " ground markers were moved to the recycling bin, where they can be restored until you close the client.");
                }
                reopen();
            })
            .no(() -> chatboxPanelManager.openInput(this))
            .build();
    }

    // Its one marker (binKey in the bin) back into saved markers, through the same confirmation as a clipboard import, or
    // back into its set. Back to them once the bin's empty.
    private void restore(Entry entry, String binKey) {
        DeletedMarkers.Tile deleted = deletedMarkers.get(binKey);
        if (set != null && deleted != null) {
            markerSets.restoreTile(deleted);
            reopen();
            return;
        }
        sharingManager.confirmImport(entry.points, " ground markers were restored.", () -> {
            deletedMarkers.remove(binKey);
            reopen();
        }, () -> chatboxPanelManager.openInput(this));
    }

    // The selected markers shown: hidden ones stay selected, but aren't copied or deleted.
    private List<GroundMarkerPointData> selectedPoints() {
        return chosenEntries().stream().flatMap(entry -> entry.points.stream()).collect(Collectors.toList());
    }

}
