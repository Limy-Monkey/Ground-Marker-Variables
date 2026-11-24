package com.groundmarkervariables.markers;

import java.time.LocalDate;
import java.util.List;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;

// A titled set of markers by an author. Its entries are what can be switched on and off: one tile, or a group of tiles.
public final class MarkerSet
{
	public static final String ANON = "anon";
	// Imported ground markers' author, until claimed.
	public static final String UNKNOWN = "Unknown";

	@RequiredArgsConstructor
	public enum Kind
	{
		BUILT_IN("Built-in", "builtin"),
		SHARED("Imported", "shared"),
		OWN("Own", "own"),
		// Another plugin's markers, drawn through us and never saved (see ExternalMarkerManager#sets).
		PLUGIN("Plugin", "plugin");

		public final String title;
		final String prefix;
	}

	public static final class Entry
	{
		@Nullable
		public final String title;
		public final List<GroundMarkerPointData> tiles;
		public final boolean group;
		// On until the user switches it, unless the set says otherwise.
		public final boolean enabledByDefault;
		// For its on/off state: its title, else its position.
		public final String key;

		public Entry(@Nullable String title, List<GroundMarkerPointData> tiles, boolean group, boolean enabledByDefault, int index)
		{
			this.title = title;
			this.tiles = List.copyOf(tiles);
			this.group = group;
			this.enabledByDefault = enabledByDefault;
			this.key = title != null ? title : "#" + index;
		}

		// Its title, else its first tile's label's first line.
		public String name()
		{
			if (title != null)
			{
				return title;
			}
			String label = tiles.isEmpty() ? "" : firstLine(tiles.get(0).getLabel());
			return label.isEmpty() ? "(no label)" : label.length() > 40 ? label.substring(0, 40) + "..." : label;
		}
	}

	// A label's first line (before a "\n", typed or real), trimmed; "" for none.
	public static String firstLine(@Nullable String label)
	{
		return label == null ? "" : label.split("\\\\n|\n", 2)[0].trim();
	}

	public final Kind kind;
	// Unique within its kind; config-key safe.
	public final String id;
	public final String title;
	public final String author;
	public final int version;
	// When its author last changed it; null if the set doesn't say.
	@Nullable
	public final LocalDate updated;
	public final List<Entry> entries;

	public MarkerSet(Kind kind, String id, String title, String author, int version, @Nullable LocalDate updated, List<Entry> entries)
	{
		this.kind = kind;
		this.id = id;
		this.title = title;
		this.author = author;
		this.version = version;
		this.updated = updated;
		this.entries = List.copyOf(entries);
	}

	// Where it's stored, and its key in the on/off state.
	public String key()
	{
		return kind.prefix + "_" + id;
	}

	public int tileCount()
	{
		return entries.stream().mapToInt(entry -> entry.tiles.size()).sum();
	}
}
