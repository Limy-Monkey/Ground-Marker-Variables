package com.groundmarkervariables.markers;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.With;

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
        // From a GitHub gist (see Gist), kept up to date.
        GIST("Subscribed", "gist"),
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

    // A set's GitHub gist (see Gists): a GIST set's source, or where an own set is published.
    @AllArgsConstructor
    @With
    public static final class Gist {
        public final String owner;
        public final String id;
        public final String file;
        // GIST: the downloaded text's hash, and whether its author deleted it (it's kept, no longer updated).
        public final int hash;
        public final boolean gone;
        // Own: published by link only, and the version and date last shared.
        public final boolean secret;
        public final int sharedVersion;
        @Nullable
        public final String shared;

        // Its raw file's link (the set's text, which Import also takes).
        public String link() {
            return "https://gist.githubusercontent.com/" + owner + "/" + id + "/raw";
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
    @Nullable
    @With
    public final Gist gist;

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
