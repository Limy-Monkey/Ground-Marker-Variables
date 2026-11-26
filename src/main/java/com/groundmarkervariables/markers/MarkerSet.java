package com.groundmarkervariables.markers;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;

// A titled set of markers by an author. Its entries are what can be switched on and off: one tile, or a group of tiles.
@AllArgsConstructor
public final class MarkerSet {
    public static final String ANON = "anon";
    // Imported ground markers' author, until claimed.
    public static final String UNKNOWN = "Unknown";
    private static final int MAX_DESCRIPTION_LENGTH = 300;
    private static final int MAX_DESCRIPTION_LINES = 5;

    @RequiredArgsConstructor
    public enum Kind {
        BUILT_IN("Built-in", "builtin"),
        SHARED("Imported", "shared"),
        OWN("Own", "own"),
        // Another plugin's markers, drawn through us and never saved (see ExternalMarkerManager#sets).
        PLUGIN("Plugin", "plugin");

        public final String title;
        final String prefix;
    }

    public static final class Entry {
        @Nullable
        public final String title;
        // Shown when its name is hovered in Marker Sets (see description()).
        @Nullable
        public final String description;
        public final List<GroundMarkerPointData> tiles;
        public final boolean group;
        // On until the user switches it, unless the set says otherwise.
        public final boolean enabledByDefault;
        // For its on/off state: its title, else its position.
        public final String key;

        public Entry(@Nullable String title, @Nullable String description, List<GroundMarkerPointData> tiles, boolean group,
            boolean enabledByDefault, int index) {
            this.title = title;
            this.description = description;
            this.tiles = List.copyOf(tiles);
            this.group = group;
            this.enabledByDefault = enabledByDefault;
            this.key = title != null ? title : "#" + index;
        }

        // The same entry at index (its key follows it if untitled).
        public Entry at(int index) {
            return new Entry(title, description, tiles, group, enabledByDefault, index);
        }

        // Its title, else its first tile's label's first line.
        public String name() {
            if (title != null) {
                return title;
            }
            String label = tiles.isEmpty() ? "" : firstLine(tiles.get(0).getLabel());
            return label.isEmpty() ? "(no label)" : label.length() > 40 ? label.substring(0, 40) + "..." : label;
        }
    }

    // A label's first line (before a "\n", typed or real), trimmed; "" for none.
    public static String firstLine(@Nullable String label) {
        return label == null ? "" : label.split("\\\\n|\n", 2)[0].trim();
    }

    public final Kind kind;
    // Unique within its kind; config-key safe.
    public final String id;
    public final String title;
    // Shown when its title is hovered in Marker Sets (see description()).
    @Nullable
    public final String description;
    public final String author;
    public final int version;
    // When its author last changed it; null if the set doesn't say.
    @Nullable
    public final LocalDate updated;
    public final List<Entry> entries;

    // A description as kept (given to the constructors as one): trimmed, at most MAX_DESCRIPTION_LINES lines (split at "\n", typed or real, kept as a typed
    // "\n") and MAX_DESCRIPTION_LENGTH characters; null if blank.
    @Nullable
    public static String description(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] lines = text.trim().split("\\\\n|\n", MAX_DESCRIPTION_LINES + 1);
        String kept = String.join("\\n", Arrays.asList(lines).subList(0, Math.min(lines.length, MAX_DESCRIPTION_LINES))).trim();
        return kept.length() > MAX_DESCRIPTION_LENGTH ? kept.substring(0, MAX_DESCRIPTION_LENGTH).trim() : kept;
    }

    // Where it's stored, and its key in the on/off state.
    public String key() {
        return kind.prefix + "_" + id;
    }

    public int tileCount() {
        return entries.stream().mapToInt(entry -> entry.tiles.size()).sum();
    }
}
