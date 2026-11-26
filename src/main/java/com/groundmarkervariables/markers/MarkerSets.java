package com.groundmarkervariables.markers;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.groundmarkervariables.GroundMarkerVariablesConfig;
import com.groundmarkervariables.markers.minify.MarkerMinifier;
import com.groundmarkervariables.markers.minify.NewerFormatException;
import com.groundmarkervariables.variables.LabelResolver;
import com.groundmarkervariables.variables.meta.MetaVariableResolver;
import com.groundmarkervariables.variables.support.RegionNames;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

// Marker sets, stored in config, drawn over saved markers while enabled. Built-in sets come from GitHub's
// data/marker-sets: index.json lists [{"id", "version"}], and <id>.json is the set. Changes on the client thread.
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class MarkerSets implements TileSource {
    private static final String GROUP = GroundMarkerVariablesConfig.GROUP;
    private static final String PLUGIN_NAME = "Ground Marker Variables";
    private static final String SET_PREFIX = "set_";
    private static final String STATE_KEY = "setState";
    private static final String BASE_URL = "https://raw.githubusercontent.com/Limy-Monkey/Ground-Marker-Variables/master/data/marker-sets/";
    private static final Duration CHECK_EVERY = Duration.ofHours(6);
    private static final Duration RETRY_AFTER = Duration.ofMinutes(15);
    // A set's stored JSON stays under this, half of a config value's ~200k limit, so later edits still fit. Sets that would
    // be longer aren't created, imported or added to (the methods saying so return null or false), with TOO_LARGE shown.
    private static final int MAX_SET_LENGTH = 100_000;
    public static final String TOO_LARGE = "That marker set would be too large to save. Try splitting it into smaller sets.";

    // Sets switched on, and each set's entries the user switched away from their default.
    private static final class State {
        Set<String> enabled = new HashSet<>();
        Map<String, Map<String, Boolean>> entries = new HashMap<>();
    }

    // A deleted set: as stored, whether it and its entries were switched on, and whether it was dissolved (its tiles went
    // back into saved markers).
    @RequiredArgsConstructor
    public static final class Recycled {
        public final MarkerSet set;
        private final String json;
        private final boolean enabled;
        @Nullable
        private final Map<String, Boolean> entries;
        public final boolean dissolved;
    }

    // A drawn marker's set and entry.
    @RequiredArgsConstructor
    private static final class Placed {
        final MarkerSet set;
        final MarkerSet.Entry entry;
    }

    private final ConfigManager configManager;
    private final Gson gson;
    private final OkHttpClient okHttpClient;
    private final ClientThread clientThread;
    private final RenderedTiles renderedTiles;
    private final LabelResolver labelResolver;
    private final MetaVariableResolver metaVariableResolver;
    private final MarkerMinifier markerMinifier;
    private final ExternalMarkerManager externalMarkerManager;
    private final DeletedMarkers deletedMarkers;

    private volatile List<MarkerSet> sets = List.of();
    private State state = new State();
    private volatile Map<Integer, List<SourceMarker>> byRegion = Map.of();
    // The last successful check, this session only: each client start checks again.
    private volatile long lastChecked;
    private final AtomicBoolean checking = new AtomicBoolean();
    // Shown in the set list while checking.
    @Nullable
    private volatile String status;
    // The last check failed.
    private volatile boolean failed;
    // Run on the client thread after any change, e.g. to redraw the set list.
    @Nullable
    private volatile Runnable listener;
    private final RecyclingBin<String, Recycled> bin = new RecyclingBin<>();

    public void startUp() {
        clientThread.invoke(this::load);
    }

    public void shutDown() {
        sets = List.of();
        byRegion = Map.of();
        listener = null;
    }

    // Reads every stored set and the on/off state, e.g. after a profile change.
    public void load() {
        String prefix = GROUP + "." + SET_PREFIX;
        List<MarkerSet> loaded = new ArrayList<>();
        for (String fullKey : configManager.getConfigurationKeys(prefix)) {
            String key = fullKey.substring(prefix.length());
            int split = key.indexOf('_');
            MarkerSet.Kind kind = split < 0 ? null : kind(key.substring(0, split));
            if (kind == null) {
                continue;
            }
            try {
                MarkerSet set = parse(configManager.getConfiguration(GROUP, SET_PREFIX + key), kind, key.substring(split + 1));
                if (set != null) {
                    loaded.add(set);
                }
            } catch (JsonParseException e) {
                log.warn("Unreadable marker set {}", key, e);
            }
        }
        loaded.sort(Comparator.comparing((MarkerSet set) -> set.kind).thenComparing(set -> set.title, String.CASE_INSENSITIVE_ORDER));
        sets = List.copyOf(loaded);

        String json = configManager.getConfiguration(GROUP, STATE_KEY);
        State read = json == null ? null : gson.fromJson(json, State.class);
        state = read != null && read.enabled != null && read.entries != null ? read : new State();
        rebuild();
    }

    public List<MarkerSet> sets() {
        return sets;
    }

    // For Marker Sets: these, then other plugins' groups (see ExternalMarkerManager#sets).
    public List<MarkerSet> listed() {
        List<MarkerSet> listed = new ArrayList<>(sets);
        listed.addAll(externalMarkerManager.sets());
        return listed;
    }

    @Nullable
    public String status() {
        return status;
    }

    public boolean failed() {
        return failed;
    }

    // Also for other plugins' groups changing.
    public void setListener(@Nullable Runnable listener) {
        this.listener = listener;
        externalMarkerManager.setListener(listener);
    }

    // A plugin's group's on/off are its hides (see ExternalMarkerManager).
    public boolean enabled(MarkerSet set) {
        return set.kind == MarkerSet.Kind.PLUGIN ? externalMarkerManager.shown(set.id) : state.enabled.contains(set.key());
    }

    public boolean enabled(MarkerSet set, MarkerSet.Entry entry) {
        return set.kind == MarkerSet.Kind.PLUGIN ? externalMarkerManager.shown(set.id, entry.tiles)
            : state.entries.getOrDefault(set.key(), Map.of()).getOrDefault(entry.key, entry.enabledByDefault);
    }

    public void setEnabled(MarkerSet set, boolean enabled) {
        if (set.kind == MarkerSet.Kind.PLUGIN) {
            externalMarkerManager.show(set.id, enabled);
            return;
        }
        if (enabled) {
            state.enabled.add(set.key());
        } else {
            state.enabled.remove(set.key());
        }
        saveState();
    }

    public void setEnabled(MarkerSet set, MarkerSet.Entry entry, boolean enabled) {
        if (set.kind == MarkerSet.Kind.PLUGIN) {
            externalMarkerManager.show(set.id, entry.tiles, enabled);
            return;
        }
        Map<String, Boolean> changed = state.entries.computeIfAbsent(set.key(), key -> new HashMap<>());
        if (enabled == entry.enabledByDefault) {
            changed.remove(entry.key);
        } else {
            changed.put(entry.key, enabled);
        }
        if (changed.isEmpty()) {
            state.entries.remove(set.key());
        }
        saveState();
    }

    // The stored set it would replace on import, if any.
    @Nullable
    public MarkerSet existing(MarkerSet set) {
        return sets.stream().filter(stored -> stored.key().equals(set.key())).findFirst().orElse(null);
    }

    // Adds (or replaces) an imported set, switched on, keeping text's author history.
    public boolean addShared(MarkerSet set, String text) {
        return addSet(set, history(text));
    }

    // false, adding nothing, if it's too large to store.
    private boolean addSet(MarkerSet set, JsonArray history) {
        if (!store(set, history)) {
            return false;
        }
        state.enabled.add(set.key());
        saveState();
        load();
        return true;
    }

    // A new set of the user's own, switched on.
    @Nullable
    public MarkerSet create(String title, String author, List<MarkerSet.Entry> entries) {
        return create(title, null, author, entries, new JsonArray());
    }

    // points as a new set of the user's own, one entry per label; blank title is defaultTitle's.
    @Nullable
    public MarkerSet createFrom(String title, String author, List<GroundMarkerPointData> points) {
        return create(title.isBlank() ? defaultTitle(points) : title.trim(), author, entriesByLabel(points));
    }

    // One entry per label, same-label markers grouped.
    public static List<MarkerSet.Entry> entriesByLabel(List<GroundMarkerPointData> points) {
        Map<String, List<GroundMarkerPointData>> byLabel = new LinkedHashMap<>();
        points.forEach(point -> byLabel.computeIfAbsent(Strings.nullToEmpty(point.getLabel()), label -> new ArrayList<>()).add(point));
        List<MarkerSet.Entry> entries = new ArrayList<>();
        byLabel.values().forEach(tiles -> entries.add(new MarkerSet.Entry(null, null, tiles, tiles.size() > 1, true, entries.size())));
        return entries;
    }

    // The RegionNames name most of points are in, else "Import <date>".
    public static String defaultTitle(List<GroundMarkerPointData> points) {
        Map<String, Integer> counts = new HashMap<>();
        for (GroundMarkerPointData point : points) {
            String name = RegionNames.name(point.worldPoint());
            if (name != null) {
                counts.merge(name, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("Import " + LocalDate.now());
    }

    // A set copied as the user's own, "<title> (clone)", keeping its author and history until claimed (see claim()).
    @Nullable
    public MarkerSet cloneSet(MarkerSet set) {
        return create(set.title + " (clone)", set.description, set.author, set.entries, history(set));
    }

    @Nullable
    private MarkerSet create(String title, @Nullable String description, String author, List<MarkerSet.Entry> entries, JsonArray history) {
        String id = slug(author + " " + title);
        Set<String> taken = new HashSet<>();
        sets.forEach(set -> taken.add(set.key()));
        String unique = id;
        for (int n = 2; taken.contains(MarkerSet.Kind.OWN.prefix + "_" + unique); n++) {
            unique = id + "_" + n;
        }
        MarkerSet set = new MarkerSet(MarkerSet.Kind.OWN, unique, title, description, author, 1, LocalDate.now(), entries);
        return addSet(set, history) ? set : null;
    }

    // set with entries added at the end.
    @Nullable
    public MarkerSet add(MarkerSet set, List<MarkerSet.Entry> entries) {
        List<MarkerSet.Entry> all = new ArrayList<>(set.entries);
        for (MarkerSet.Entry entry : entries) {
            all.add(entry.at(all.size()));
        }
        return edit(set, set.title, set.description, set.author, all);
    }

    public void setAuthor(MarkerSet set, String author) {
        edit(set, set.title, set.description, author, set.entries);
    }

    // Blank for none; see MarkerSet#description for its limits.
    public void setDescription(MarkerSet set, String description) {
        edit(set, set.title, MarkerSet.description(description), set.author, set.entries);
    }

    public void setDescription(MarkerSet set, MarkerSet.Entry described, String description) {
        Map<MarkerSet.Entry, MarkerSet.Entry> moved = new LinkedHashMap<>();
        for (MarkerSet.Entry entry : set.entries) {
            moved.put(entry, entry != described ? entry
                : new MarkerSet.Entry(entry.title, MarkerSet.description(description), entry.tiles, entry.group, entry.enabledByDefault,
                moved.size()));
        }
        edit(set, moved);
    }

    // author takes over an own set: author leaves its history, then its old author joins it ("Imported" for an Unknown set,
    // nothing for anon).
    public void claim(MarkerSet set, String author) {
        JsonArray history = new JsonArray();
        for (JsonElement name : history(set)) {
            if (!name.getAsString().equalsIgnoreCase(author)) {
                history.add(name);
            }
        }
        if (!MarkerSet.ANON.equals(set.author)) {
            history.add(MarkerSet.UNKNOWN.equals(set.author) ? "Imported" : set.author);
        }
        store(new MarkerSet(set.kind, set.id, set.title, set.description, author, set.version + 1, LocalDate.now(), set.entries), history);
        load();
    }

    // Its id (where it's stored, and its on/off state) stays.
    public void setTitle(MarkerSet set, String title) {
        edit(set, title.trim(), set.description, set.author, set.entries);
    }

    // An entry retitled (blank for none). False if another entry already has that title, as their states would be shared.
    public boolean setTitle(MarkerSet set, MarkerSet.Entry retitled, String title) {
        String newTitle = title.isBlank() ? null : title.trim();
        Map<MarkerSet.Entry, MarkerSet.Entry> moved = new LinkedHashMap<>();
        for (MarkerSet.Entry entry : set.entries) {
            if (entry != retitled && newTitle != null && newTitle.equals(entry.title)) {
                return false;
            }
            moved.put(entry, entry != retitled ? entry
                : new MarkerSet.Entry(newTitle, entry.description, entry.tiles, entry.group, entry.enabledByDefault, moved.size()));
        }
        edit(set, moved);
        return true;
    }

    // An own set's tiles' label (blank for none).
    public void setLabel(MarkerSet set, Collection<GroundMarkerPointData> tiles, String label) {
        editTiles(set, tile -> tiles.contains(tile) ? tile.withLabel(label.isEmpty() ? null : label) : tile);
    }

    // An own set's tiles deleted into DeletedMarkers.
    public void deleteTiles(MarkerSet set, Collection<GroundMarkerPointData> tiles) {
        set.entries.forEach(entry -> entry.tiles.stream().filter(tiles::contains)
            .forEach(tile -> deletedMarkers.add(new DeletedMarkers.Tile(set.key(), entry, tile))));
        editTiles(set, tile -> tiles.contains(tile) ? null : tile);
    }

    // A deleted tile back into its set (if it's still there): into its titled entry if that's still there, else into its
    // entry recreated last (an untitled one's key is only its old position).
    public void restoreTile(DeletedMarkers.Tile deleted) {
        MarkerSet set = sets.stream().filter(stored -> stored.key().equals(deleted.setKey)).findFirst().orElse(null);
        if (set == null) {
            return;
        }
        deletedMarkers.remove(DeletedMarkers.key(deleted.setKey, deleted.entry, deleted.tile));
        MarkerSet.Entry was = deleted.entry;
        boolean into = was.title != null && set.entries.stream().anyMatch(entry -> entry.key.equals(was.key));
        Map<MarkerSet.Entry, MarkerSet.Entry> moved = new LinkedHashMap<>();
        for (MarkerSet.Entry entry : set.entries) {
            List<GroundMarkerPointData> tiles = new ArrayList<>(entry.tiles);
            if (into && entry.key.equals(was.key)) {
                tiles.add(deleted.tile);
            }
            moved.put(entry, new MarkerSet.Entry(entry.title, entry.description, tiles, entry.group, entry.enabledByDefault, moved.size()));
        }
        edit(set, moved, into ? List.of()
            : List.of(new MarkerSet.Entry(was.title, was.description, List.of(deleted.tile), was.group, was.enabledByDefault, moved.size())));
    }

    // An own set's tiles, each replaced by change's (null drops it; an entry left without any goes).
    public void editTiles(MarkerSet set, UnaryOperator<GroundMarkerPointData> change) {
        Map<MarkerSet.Entry, MarkerSet.Entry> moved = new LinkedHashMap<>();
        for (MarkerSet.Entry entry : set.entries) {
            List<GroundMarkerPointData> tiles = entry.tiles.stream().map(change).filter(Objects::nonNull).collect(Collectors.toList());
            if (!tiles.isEmpty()) {
                moved.put(entry, new MarkerSet.Entry(entry.title, entry.description, tiles, entry.group, entry.enabledByDefault, moved.size()));
            }
        }
        edit(set, moved);
    }

    // An own set's entries replaced, each old entry by its new one (in order); their on/off changes follow them to their
    // new keys (an untitled entry's key is its position).
    private void edit(MarkerSet set, Map<MarkerSet.Entry, MarkerSet.Entry> moved) {
        edit(set, moved, List.of());
    }

    // As edit(set, moved), then added (new, with no on/off state, at their positions after moved's). Null, changing
    // nothing but on/off state, if too large.
    @Nullable
    private MarkerSet edit(MarkerSet set, Map<MarkerSet.Entry, MarkerSet.Entry> moved, List<MarkerSet.Entry> added) {
        Map<String, Boolean> before = state.entries.remove(set.key());
        Map<String, Boolean> after = new HashMap<>();
        moved.forEach((from, to) -> {
            if (before != null && before.containsKey(from.key)) {
                after.put(to.key, before.get(from.key));
            }
        });
        if (!after.isEmpty()) {
            state.entries.put(set.key(), after);
        }
        configManager.setConfiguration(GROUP, STATE_KEY, gson.toJson(state));
        List<MarkerSet.Entry> entries = new ArrayList<>(moved.values());
        entries.addAll(added);
        return edit(set, set.title, set.description, set.author, entries);
    }

    // markers added as entries, one per label (as entriesByLabel). replace: tiles already in set at their places are
    // dropped from their entries first (an emptied entry goes); else those markers are skipped. Null, changing nothing, if
    // too large.
    @Nullable
    public MarkerSet paste(MarkerSet set, List<GroundMarkerPointData> markers, boolean replace) {
        Set<WorldPoint> pasted = markers.stream().map(GroundMarkerPointData::worldPoint).collect(Collectors.toSet());
        Set<WorldPoint> there = new HashSet<>();
        Map<MarkerSet.Entry, MarkerSet.Entry> moved = new LinkedHashMap<>();
        // Replaced tiles, for the recycling bin once saved.
        List<DeletedMarkers.Tile> replaced = new ArrayList<>();
        for (MarkerSet.Entry entry : set.entries) {
            List<GroundMarkerPointData> kept = new ArrayList<>();
            for (GroundMarkerPointData tile : entry.tiles) {
                there.add(tile.worldPoint());
                if (!replace || !pasted.contains(tile.worldPoint())) {
                    kept.add(tile);
                } else {
                    replaced.add(new DeletedMarkers.Tile(set.key(), entry, tile));
                }
            }
            if (!kept.isEmpty()) {
                moved.put(entry, new MarkerSet.Entry(entry.title, entry.description, kept, entry.group, entry.enabledByDefault, moved.size()));
            }
        }
        List<MarkerSet.Entry> added = new ArrayList<>();
        entriesByLabel(replace ? markers : markers.stream().filter(marker -> !there.contains(marker.worldPoint())).collect(Collectors.toList()))
            .forEach(entry -> added.add(entry.at(moved.size() + added.size())));
        MarkerSet pastedInto = edit(set, moved, added);
        if (pastedInto != null) {
            replaced.forEach(deletedMarkers::add);
        }
        return pastedInto;
    }

    // How many of markers are at a tile already in set.
    public static long duplicates(MarkerSet set, List<GroundMarkerPointData> markers) {
        Set<WorldPoint> there = set.entries.stream().flatMap(entry -> entry.tiles.stream()).map(GroundMarkerPointData::worldPoint)
            .collect(Collectors.toSet());
        return markers.stream().filter(marker -> there.contains(marker.worldPoint())).count();
    }

    // An own set changed, stored as its next version, dated today.
    // null, changing nothing, if it would be too large to store.
    @Nullable
    private MarkerSet edit(MarkerSet set, String title, @Nullable String description, String author, List<MarkerSet.Entry> entries) {
        MarkerSet edited = new MarkerSet(set.kind, set.id, title, description, author, set.version + 1, LocalDate.now(), entries);
        if (!store(edited)) {
            return null;
        }
        load();
        return edited;
    }

    // Into the recycling bin (see bin()), as stored and with its on/off state.
    public void delete(MarkerSet set) {
        delete(set, false);
    }

    // dissolved: its tiles went back into saved markers.
    public void delete(MarkerSet set, boolean dissolved) {
        String json = configManager.getConfiguration(GROUP, SET_PREFIX + set.key());
        if (json != null) {
            bin.add(set.key(), new Recycled(set, json, state.enabled.contains(set.key()), state.entries.get(set.key()), dissolved));
        }
        configManager.unsetConfiguration(GROUP, SET_PREFIX + set.key());
        state.enabled.remove(set.key());
        state.entries.remove(set.key());
        saveState();
        load();
    }

    // The recycling bin, by set key: restored with restore(), and deleted permanently by removing from it.
    public RecyclingBin<String, Recycled> bin() {
        return bin;
    }

    // A recycled set back as it was stored, switched on or off as it was.
    public void restore(MarkerSet set) {
        Recycled recycled = bin.remove(set.key());
        if (recycled == null) {
            return;
        }
        configManager.setConfiguration(GROUP, SET_PREFIX + set.key(), recycled.json);
        if (recycled.enabled) {
            state.enabled.add(set.key());
        }
        if (recycled.entries != null) {
            state.entries.put(set.key(), recycled.entries);
        }
        saveState();
        load();
    }

    // An imported set, or null if text isn't one. Throws JsonSyntaxException if it's damaged.
    @Nullable
    public MarkerSet parse(String text) {
        return parse(text, MarkerSet.Kind.SHARED, null);
    }

    // Copy's array (see export(MarkerSet)), or the stored {"title", "author", "version", "updated": "yyyy-mm-dd", "tiles":
    // [marker, or {"title", "tiles": [marker...]}]}, where a marker may have a "title", and either "enabled": false.
    @Nullable
    private MarkerSet parse(String text, MarkerSet.Kind kind, @Nullable String id) {
        try {
            // Labels may be laid out over lines, as clipboard imports (see MarkerMinifier#normalizeStrings).
            JsonElement element = gson.fromJson(MarkerMinifier.normalizeStrings(text), JsonElement.class);
            JsonObject json = element != null && element.isJsonArray() ? copiedSet(element.getAsJsonArray())
                : element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
            if (json == null || !element.isJsonArray() && !(json.has("tiles") && json.get("tiles").isJsonArray())) {
                return null;
            }
            List<MarkerSet.Entry> entries = element.isJsonArray() ? copiedEntries(text, element.getAsJsonArray()) : storedEntries(json);
            String title = string(json, "title");
            String author = string(json, "author");
            title = title == null ? "Untitled" : title;
            author = author == null ? MarkerSet.ANON : author;
            int version = json.has("version") ? json.get("version").getAsInt() : 0;
            String updated = string(json, "updated");
            return new MarkerSet(kind, id != null ? id : slug(author + " " + title), title, MarkerSet.description(string(json, "description")),author, version,
                updated == null ? null : LocalDate.parse(updated), entries);
        } catch (IllegalStateException | ClassCastException | NumberFormatException | DateTimeParseException | NewerFormatException e) {
            throw new JsonSyntaxException(e);
        }
    }

    // A copied set's own fields: its first tile's "set", or null if it's just markers.
    @Nullable
    private static JsonObject copiedSet(JsonArray tiles) {
        JsonElement set = tiles.size() > 0 && tiles.get(0).isJsonObject() ? tiles.get(0).getAsJsonObject().get("set") : null;
        return set != null && set.isJsonObject() ? set.getAsJsonObject() : null;
    }

    // Its tiles by "entry", in order: a title, description and on/off from each entry's first tile, a group if it has several.
    private List<MarkerSet.Entry> copiedEntries(String text, JsonArray array) throws NewerFormatException {
        List<GroundMarkerPointData> tiles = markerMinifier.parse(text);
        Map<Integer, JsonObject> firsts = new LinkedHashMap<>();
        Map<Integer, List<GroundMarkerPointData>> byEntry = new HashMap<>();
        for (int i = 0; i < tiles.size(); i++) {
            JsonObject tile = array.get(i).getAsJsonObject();
            // Without "entry", a tile is its own.
            int entry = tile.has("entry") ? tile.get("entry").getAsInt() : -1 - i;
            firsts.putIfAbsent(entry, tile);
            byEntry.computeIfAbsent(entry, key -> new ArrayList<>()).add(tiles.get(i));
        }
        List<MarkerSet.Entry> entries = new ArrayList<>();
        firsts.forEach((entry, first) -> entries.add(new MarkerSet.Entry(string(first, "entryTitle"),
            MarkerSet.description(string(first, "entryDescription")), byEntry.get(entry), byEntry.get(entry).size() > 1,
            !first.has("enabled") || first.get("enabled").getAsBoolean(), entries.size())));
        return entries;
    }

    private List<MarkerSet.Entry> storedEntries(JsonObject json) {
        List<MarkerSet.Entry> entries = new ArrayList<>();
        for (JsonElement entryJson : json.getAsJsonArray("tiles")) {
            JsonObject entry = entryJson.getAsJsonObject();
            boolean group = entry.has("tiles");
            List<GroundMarkerPointData> tiles = new ArrayList<>();
            for (JsonElement tile : group ? entry.getAsJsonArray("tiles") : List.<JsonElement>of(entry)) {
                tiles.add(gson.fromJson(tile, GroundMarkerPointData.class));
            }
            boolean enabled = !entry.has("enabled") || entry.get("enabled").getAsBoolean();
            entries.add(new MarkerSet.Entry(string(entry, "title"), MarkerSet.description(string(entry, "description")), tiles, group,
                enabled, entries.size()));
        }
        return entries;
    }

    // Copy's text: core's marker array, so core and Tile Packs can import it, with "entry"/"entryTitle"/"entryDescription"/
    // "enabled" per tile and the set's fields in the first tile's "set" for parse(). Own sets use their current on/off; empty
    // sets, the stored form.
    public String export(MarkerSet set) {
        List<GroundMarkerPointData> tiles = new ArrayList<>();
        set.entries.forEach(entry -> tiles.addAll(entry.tiles));
        if (tiles.isEmpty()) {
            return export(set, history(set), set.kind == MarkerSet.Kind.OWN);
        }
        JsonArray array = gson.fromJson(markerMinifier.exportGmvPlain(tiles), JsonArray.class);
        int index = 0;
        for (int entryIndex = 0; entryIndex < set.entries.size(); entryIndex++) {
            MarkerSet.Entry entry = set.entries.get(entryIndex);
            for (int tile = 0; tile < entry.tiles.size(); tile++, index++) {
                JsonObject json = array.get(index).getAsJsonObject();
                json.addProperty("entry", entryIndex);
                if (tile == 0) {
                    json.addProperty("entryTitle", entry.title);
                    json.addProperty("entryDescription", entry.description);
                }
                if (tile == 0 && !(set.kind == MarkerSet.Kind.OWN || set.kind == MarkerSet.Kind.PLUGIN ? enabled(set, entry)
                    : entry.enabledByDefault)) {
                    json.addProperty("enabled", false);
                }
            }
        }
        JsonObject setJson = gson.fromJson(export(set, history(set), false), JsonObject.class);
        setJson.remove("tiles");
        array.get(0).getAsJsonObject().add("set", setJson);
        return gson.newBuilder().disableHtmlEscaping().create().toJson(array);
    }

    // history: earlier authors, oldest first, written only if any. Starts by naming the plugin it's for (not read back).
    // current: each entry's on/off now as its default, else the set's own default.
    private String export(MarkerSet set, JsonArray history, boolean current) {
        JsonObject json = new JsonObject();
        json.addProperty("plugin", PLUGIN_NAME);
        json.addProperty("title", set.title);
        json.addProperty("description", set.description);
        json.addProperty("author", set.author);
        if (history.size() > 0) {
            json.add("history", history);
        }
        json.addProperty("version", set.version);
        if (set.updated != null) {
            json.addProperty("updated", set.updated.toString());
        }
        JsonArray entries = new JsonArray();
        for (MarkerSet.Entry entry : set.entries) {
            JsonObject entryJson = new JsonObject();
            entryJson.addProperty("title", entry.title);
            entryJson.addProperty("description", entry.description);
            if (!(current ? enabled(set, entry) : entry.enabledByDefault)) {
                entryJson.addProperty("enabled", false);
            }
            if (entry.group) {
                entryJson.add("tiles", gson.toJsonTree(entry.tiles));
            } else {
                gson.toJsonTree(entry.tiles.get(0)).getAsJsonObject().entrySet().forEach(field -> entryJson.add(field.getKey(), field.getValue()));
            }
            entries.add(entryJson);
        }
        json.add("tiles", entries);
        return gson.newBuilder().disableHtmlEscaping().create().toJson(json);
    }

    // Fetches newer built-in sets from GitHub when forced, or 6+ hours since the last check. Never blocks the caller.
    public void checkForUpdates(boolean force) {
        if (!force && System.currentTimeMillis() - lastChecked < CHECK_EVERY.toMillis() || !checking.compareAndSet(false, true)) {
            return;
        }
        status = "Checking GitHub...";
        failed = false;
        clientThread.invoke(this::changed);
        okHttpClient.newCall(new Request.Builder().url(BASE_URL + "index.json").build()).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                failed(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (response) {
                    JsonArray index = gson.fromJson(body(response), JsonArray.class);
                    Map<String, Integer> versions = new HashMap<>();
                    Map<MarkerSet, JsonArray> downloaded = new LinkedHashMap<>();
                    for (JsonElement listed : index) {
                        String id = string(listed.getAsJsonObject(), "id");
                        int version = listed.getAsJsonObject().get("version").getAsInt();
                        if (id == null || !id.matches("[a-z0-9_]+")) {
                            continue;
                        }
                        versions.put(id, version);
                        MarkerSet cached = sets.stream().filter(set -> set.kind == MarkerSet.Kind.BUILT_IN && set.id.equals(id))
                            .findFirst().orElse(null);
                        if (cached == null || cached.version < version) {
                            download(id, version, downloaded);
                        }
                    }
                    clientThread.invoke(() -> updated(versions, downloaded));
                } catch (IOException | RuntimeException e) {
                    failed(e);
                }
            }
        });
    }

    // Off the client thread, into downloaded with its author history. Stored with the index's version, so an update is
    // downloaded once.
    private void download(String id, int version, Map<MarkerSet, JsonArray> downloaded) throws IOException {
        try (Response response = okHttpClient.newCall(new Request.Builder().url(BASE_URL + id + ".json").build()).execute()) {
            String text = body(response);
            MarkerSet set = parse(text, MarkerSet.Kind.BUILT_IN, id);
            if (set == null) {
                throw new IOException("Not a marker set: " + id);
            }
            downloaded.put(new MarkerSet(set.kind, id, set.title, set.description, set.author, version, set.updated, set.entries),
                history(text));
        }
    }

    private static String body(Response response) throws IOException {
        ResponseBody body = response.body();
        if (!response.isSuccessful() || body == null) {
            throw new IOException("HTTP " + response.code());
        }
        return body.string();
    }

    // Stores the downloads and drops built-in sets no longer listed.
    private void updated(Map<String, Integer> versions, Map<MarkerSet, JsonArray> downloaded) {
        downloaded.forEach(this::store);
        for (MarkerSet set : sets) {
            if (set.kind == MarkerSet.Kind.BUILT_IN && !versions.containsKey(set.id)) {
                configManager.unsetConfiguration(GROUP, SET_PREFIX + set.key());
            }
        }
        lastChecked = System.currentTimeMillis();
        status = null;
        checking.set(false);
        load();
    }

    private void failed(Exception e) {
        log.debug("Marker set update failed", e);
        status = null;
        failed = true;
        lastChecked = System.currentTimeMillis() - CHECK_EVERY.toMillis() + RETRY_AFTER.toMillis();
        checking.set(false);
        clientThread.invoke(this::changed);
    }

    // Keeps the history already stored for it, e.g. through an edit.
    private boolean store(MarkerSet set) {
        return store(set, history(set));
    }

    // false, storing nothing, if its JSON would be over MAX_SET_LENGTH.
    private boolean store(MarkerSet set, JsonArray history) {
        String json = export(set, history, false);
        if (json.length() > MAX_SET_LENGTH) {
            return false;
        }
        configManager.setConfiguration(GROUP, SET_PREFIX + set.key(), json);
        return true;
    }

    // The author history stored for set.
    private JsonArray history(MarkerSet set) {
        return history(configManager.getConfiguration(GROUP, SET_PREFIX + set.key()));
    }

    // A set's JSON's "history" (a copied one's in its "set"), or none.
    private JsonArray history(@Nullable String text) {
        try {
            JsonElement json = text == null ? null : gson.fromJson(text, JsonElement.class);
            json = json != null && json.isJsonArray() ? copiedSet(json.getAsJsonArray()) : json;
            JsonElement history = json != null && json.isJsonObject() ? json.getAsJsonObject().get("history") : null;
            return history != null && history.isJsonArray() ? history.getAsJsonArray() : new JsonArray();
        } catch (JsonParseException e) {
            return new JsonArray();
        }
    }

    private void saveState() {
        configManager.setConfiguration(GROUP, STATE_KEY, gson.toJson(state));
        rebuild();
    }

    // Later sets win ties.
    private void rebuild() {
        Map<Integer, List<SourceMarker>> regions = new HashMap<>();
        long order = 0;
        for (MarkerSet set : sets) {
            order++;
            if (!enabled(set)) {
                continue;
            }
            for (MarkerSet.Entry entry : set.entries) {
                if (!enabled(set, entry)) {
                    continue;
                }
                Placed placed = new Placed(set, entry);
                for (GroundMarkerPointData tile : entry.tiles) {
                    regions.computeIfAbsent(tile.getRegionId(), r -> new ArrayList<>()).add(new SourceMarker(
                        new CachedMarker(tile, labelResolver, metaVariableResolver), this, RenderedTiles.SET_PRIORITY, order, placed));
                }
            }
        }
        byRegion = regions;
        renderedTiles.invalidate();
        changed();
    }

    private void changed() {
        Runnable listener = this.listener;
        if (listener != null) {
            listener.run();
        }
    }

    // Drawing a set's tiles is also when built-in sets are checked for updates.
    @Override
    public Collection<SourceMarker> markers(int regionId) {
        List<SourceMarker> markers = byRegion.getOrDefault(regionId, List.of());
        if (!markers.isEmpty()) {
            checkForUpdates(false);
        }
        return markers;
    }

    // Disable the drawn set tile's entry, or its whole set.
    public List<ExternalMarkerManager.MenuOption> menuOptions(WorldView wv, WorldPoint worldPoint) {
        RenderedTile drawn = renderedTiles.winner(wv, worldPoint);
        if (drawn == null || drawn.marker.source != this) {
            return List.of();
        }
        Placed placed = (Placed) drawn.marker.owner;
        return List.of(
            new ExternalMarkerManager.MenuOption("Disable", placed.set.title + ": " + placed.entry.name(),
                () -> setEnabled(placed.set, placed.entry, false)),
            new ExternalMarkerManager.MenuOption("Disable set", placed.set.title, () -> setEnabled(placed.set, false)));
    }

    @Nullable
    private static MarkerSet.Kind kind(String prefix) {
        for (MarkerSet.Kind kind : MarkerSet.Kind.values()) {
            if (kind.prefix.equals(prefix)) {
                return kind;
            }
        }
        return null;
    }

    @Nullable
    private static String string(JsonObject json, String field) {
        JsonElement value = json.get(field);
        return value == null || !value.isJsonPrimitive() || value.getAsString().isBlank() ? null : value.getAsString().trim();
    }

    private static String slug(String text) {
        String slug = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        return slug.isEmpty() ? "set" : slug;
    }
}
