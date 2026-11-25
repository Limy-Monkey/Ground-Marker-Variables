package com.groundmarkervariables.variables.support;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

// Ported from RuneLite's GameArea area list (runelite/runelite PR #14808, BSD 2-Clause License):
// Copyright (c) 2018, Tomas Slusny <slusnucky@gmail.com>
// Copyright (c) 2018, PandahRS <https://github.com/PandahRS>
// Copyright (c) 2020, Brooklyn <https://github.com/Broooklyn>
// Copyright (c) 2025, Jordan Atwood <nightfirecat@nightfirec.at>
// All rights reserved.
// See https://github.com/runelite/runelite/blob/master/LICENSE
//
// A tile's area name, e.g. "Lumbridge", from region-names.json (the bundled one, or GitHub's newer one): area name -> its
// whole regions (ids) and bounds within regions ({region, sw, ne, planes}, each optional but region). Its
// "worldMapLocations" (scripts/build-region-names.py, from the game cache) name the rest, by the world map drawing the
// tile (see name(point, map)): "maps", each but the surface's name by id; "labels", the surface's [name, x, y].
@Slf4j
public final class RegionNames {
	// The world map list's Gielinor Surface (see WorldMapPins#mapAt).
	public static final int SURFACE_MAP = 0;
	private static final String LOCATIONS = "worldMapLocations";
	// Within this many tiles of a label, its name alone; further, with a direction.
	private static final int NEAR = 48;
	private static final String[] DIRECTIONS = {"East", "North-east", "North", "North-west", "West", "South-west", "South", "South-east"};

	// Bounds within a region: a box (inclusive world x/y, any plane) and/or a range of planes.
	@RequiredArgsConstructor
	private static final class Bounds {
		private final String name;
		private final int minX;
		private final int minY;
		private final int maxX;
		private final int maxY;
		private final int minPlane;
		private final int maxPlane;

		private boolean contains(WorldPoint point) {
			return point.getX() >= minX && point.getX() <= maxX && point.getY() >= minY && point.getY() <= maxY
				&& point.getPlane() >= minPlane && point.getPlane() <= maxPlane;
		}
	}

	private static final class Names {
		// Whole regions; a region listed twice takes the later name.
		private final Map<Integer, String> byRegion = new HashMap<>();
		// Checked before whole regions, the first containing a tile naming it.
		private final Map<Integer, List<Bounds>> bounds = new HashMap<>();
		// worldMapLocations': map id -> name, and the surface's labels.
		private final Map<Integer, String> maps = new HashMap<>();
		private final List<Bounds> labels = new ArrayList<>();
	}

	// The bundled file's, loaded on first use, until update() replaces them.
	@Nullable
	private static volatile Names names;

	private RegionNames() {
	}

	@Nullable
	public static String name(WorldPoint point) {
		Names names = names();
		List<Bounds> boundsList = names.bounds.get(point.getRegionID());
		if (boundsList != null) {
			for (Bounds bounds : boundsList) {
				if (bounds.contains(point)) {
					return bounds.name;
				}
			}
		}
		return names.byRegion.get(point.getRegionID());
	}

	// As name(point), else by map, the world map drawing it (WorldMapPins#mapAt; null while unknown): another map's name,
	// e.g. "Brimhaven Dungeon", or on the surface the nearest label's, e.g. "North-east of Varrock".
	@Nullable
	public static String name(WorldPoint point, @Nullable Integer map) {
		String name = name(point);
		if (name != null) { return name; }
		if (map == null) { return null; }
		if (map != SURFACE_MAP) {return names().maps.get(map); }
		Bounds nearest = null;
		double distance = Double.MAX_VALUE;
		for (Bounds label : names().labels) {
			double d = Math.hypot(point.getX() - label.minX, point.getY() - label.minY);
			if (d < distance) {
				nearest = label;
				distance = d;
			}
		}
		if (nearest == null || distance <= NEAR) {
			return nearest == null ? null : nearest.name;
		}
		double angle = Math.atan2(point.getY() - nearest.minY, point.getX() - nearest.minX);
		return DIRECTIONS[(int) Math.floorMod(Math.round(angle / (Math.PI / 4)), 8)] + " of " + nearest.name;
	}

	// A newer region-names.json (see LatestData).
	public static void update(String json) {
		names = parse(new StringReader(json));
	}

	private static Names names() {
		Names loaded = names;
		if (loaded == null) {
			try (Reader reader = new InputStreamReader(RegionNames.class.getResourceAsStream("region-names.json"), StandardCharsets.UTF_8)) {
				loaded = parse(reader);
			} catch (Exception e) {
				log.warn("Couldn't load region names", e);
				loaded = new Names();
			}
			names = loaded;
		}
		return loaded;
	}

	private static Names parse(Reader reader) {
		Names names = new Names();
		for (Map.Entry<String, JsonElement> area : new JsonParser().parse(reader).getAsJsonObject().entrySet()) {
			String name = area.getKey();
			if (name.equals(LOCATIONS)) {
				JsonObject locations = area.getValue().getAsJsonObject();
				locations.getAsJsonObject("maps").entrySet().forEach(map -> names.maps.put(Integer.parseInt(map.getKey()), map.getValue().getAsString()));
				// A label as a point: its name and x, y.
				for (JsonElement element : locations.getAsJsonArray("labels")) {
					JsonArray label = element.getAsJsonArray();
					int x = label.get(1).getAsInt();
					int y = label.get(2).getAsInt();
					names.labels.add(new Bounds(label.get(0).getAsString(), x, y, x, y, 0, 0));
				}
				continue;
			}
			for (JsonElement region : area.getValue().getAsJsonArray()) {
				if (region.isJsonPrimitive()) {
					names.byRegion.put(region.getAsInt(), name);
					continue;
				}
				JsonObject bounds = region.getAsJsonObject();
				int regionId = bounds.get("region").getAsInt();
				// Without a box, the whole region: its sw corner and 64 tiles across.
				int baseX = (regionId >>> 8) << 6;
				int baseY = (regionId & 0xFF) << 6;
				int[] sw = pair(bounds, "sw", baseX, baseY);
				int[] ne = pair(bounds, "ne", baseX + 63, baseY + 63);
				int[] planes = pair(bounds, "planes", 0, 3);
				names.bounds.computeIfAbsent(regionId, k -> new ArrayList<>())
					.add(new Bounds(name, sw[0], sw[1], ne[0], ne[1], planes[0], planes[1]));
			}
		}
		return names;
	}

	// bounds' two-number field, or first/second if it has none.
	private static int[] pair(JsonObject bounds, String field, int first, int second) {
		if (!bounds.has(field)) {
			return new int[]{first, second};
		}
		return new int[]{bounds.getAsJsonArray(field).get(0).getAsInt(), bounds.getAsJsonArray(field).get(1).getAsInt()};
	}
}
