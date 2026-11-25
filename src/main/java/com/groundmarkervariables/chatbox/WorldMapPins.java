package com.groundmarkervariables.chatbox;

import com.google.common.base.Strings;
import com.groundmarkervariables.GroundMarkerVariablesConfig;
import com.groundmarkervariables.markers.GroundMarkerPointData;
import com.groundmarkervariables.markers.MarkerSet;
import com.groundmarkervariables.markers.minify.MarkerMinifier;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.Text;

// The Marker Manager's and Marker Sets' world map: pins for their markers (drawn while the map is open), and moving the
// open map to markers, switching maps (e.g. to Prifddinas) through the map's own link handling.
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class WorldMapPins {
	// The world map's list of maps, and its proc returning a coord's place in one map (-1 if none); unnamed in RuneLite.
	private static final int WORLDMAP_LIST_ENUM = 1697;
	private static final int FIND_COORD_IN_MAP_SCRIPT = 1715;
	private static final String RUNELITE_TILE_MAP = "https://runelite.net/tile/show/#";

	private final Client client;
	private final ClientThread clientThread;
	private final WorldMapPointManager worldMapPointManager;
	private final GroundMarkerVariablesConfig config;
	private final MarkerMinifier markerMinifier;
	private final List<WorldMapPoint> pins = new ArrayList<>();

	// Our pins' own class, telling them apart from other world map points.
	public static final class Pin extends WorldMapPoint {
		private Pin(WorldPoint worldPoint, BufferedImage image) {
			super(worldPoint, image);
		}
	}
	// What pinned them, so the same rows aren't pinned again.
	@Nullable
	private Object pinnedKey;
	// Whether a region's plane is on some world map, by region << 2 | plane; and the regions still to check.
	private final Map<Integer, Boolean> mapped = new HashMap<>();
	private final Map<Integer, GroundMarkerPointData> unchecked = new LinkedHashMap<>();
	@Nullable
	private Runnable afterCheck;

	// The Map submenu's choices for tiles (see SearchableChatbox#addSubmenu). On World Map, if they're on one: runs pin, then moves
	// the map to them if it's open (else notice says they're pinned). On RuneLite.net: browsed, there.
	public Map<String, Runnable> mapChoices(List<GroundMarkerPointData> tiles, List<GroundMarkerPointData> browsed, Runnable pin,
		Consumer<String> notice, Runnable onChecked) {
		Map<String, Runnable> choices = new LinkedHashMap<>();
		if (onAnyMap(tiles, onChecked)) {
			choices.put("On World Map", () -> {
				pin.run();
				if (mapOpen()) {
					moveTo(tiles);
				} else {
					notice.accept("Pinned: open the world map to see them.");
				}
			});
		}
		choices.put("On RuneLite.net", () -> browse(browsed));
		return choices;
	}

	// Pins each tooltip's tiles, in their color made solid, unless key's are pinned already.
	public void pin(Object key, Map<String, List<GroundMarkerPointData>> tilesByTooltip) {
		if (key.equals(pinnedKey)) {
			return;
		}
		clear();
		pinnedKey = key;
		Map<Integer, BufferedImage> images = new HashMap<>();
		tilesByTooltip.forEach((tooltip, tiles) -> tiles.forEach(tile -> {
			int rgb = (tile.getColor() != null ? tile.getColor() : config.markerColor()).getRGB() | 0xFF000000;
			WorldMapPoint pin = new Pin(tile.worldPoint(), images.computeIfAbsent(rgb, c -> SuggestionIconOverlay.diamond(new Color(c))));
			pin.setTooltip(Text.escapeJagex(tooltip));
			pins.add(pin);
			worldMapPointManager.add(pin);
		}));
	}

	public void clear() {
		pins.forEach(worldMapPointManager::remove);
		pins.clear();
		pinnedKey = null;
	}

	// tiles on runelite.net's tile map, which draws every area at its real coordinates (unlike the world map), each labeled
	// by its label's first line, as written. The tiles go after "#", so they stay in the browser.
	private void browse(List<GroundMarkerPointData> tiles) {
		List<GroundMarkerPointData> shown = new ArrayList<>();
		tiles.forEach(tile -> shown.add(tile.withLabel(Strings.emptyToNull(MarkerSet.firstLine(tile.getLabel())))));
		LinkBrowser.browse(RUNELITE_TILE_MAP + markerMinifier.exportPlainBase64(shown));
	}

	private boolean mapOpen() {
		Widget map = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
		return map != null && !map.isHidden();
	}

	// Moves the open map, as its own links do (VarClientID.WORLDMAP_INTERMAPLINK), to the middle of tiles on a map (see
	// onAnyMap), on their most common floor.
	private void moveTo(List<GroundMarkerPointData> tiles) {
		List<WorldPoint> points = new ArrayList<>();
		Map<Integer, Integer> floors = new HashMap<>();
		tiles.stream().filter(tile -> Boolean.TRUE.equals(mapped.get(key(tile)))).forEach(tile -> {
			points.add(tile.worldPoint());
			floors.merge(tile.getZ(), 1, Integer::sum);
		});
		if (points.isEmpty()) {
			return;
		}
		int x = (int) points.stream().mapToInt(WorldPoint::getX).average().orElse(0);
		int y = (int) points.stream().mapToInt(WorldPoint::getY).average().orElse(0);
		int floor = floors.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
		client.setVarcIntValue(VarClientID.WORLDMAP_INTERMAPLINK, coord(new WorldPoint(x, y, floor)));
	}

	// Whether any of tiles' regions is on some world map (see onAnyMap(tile)); checks them all, for moveTo().
	private boolean onAnyMap(List<GroundMarkerPointData> tiles, Runnable onChecked) {
		boolean any = false;
		for (GroundMarkerPointData tile : tiles) {
			any |= onAnyMap(tile, onChecked);
		}
		return any;
	}

	private static int key(GroundMarkerPointData tile) {
		return tile.getRegionId() << 2 | tile.getZ();
	}

	// Whether tile's region is on some world map (not e.g. a raid's rooms). Unknown is false: it's checked a tick later
	// (it runs clientscripts, and this may be inside one), all at once, then onChecked runs.
	private boolean onAnyMap(GroundMarkerPointData tile, Runnable onChecked) {
		int key = key(tile);
		Boolean known = mapped.get(key);
		if (known != null) {
			return known;
		}
		if (unchecked.isEmpty()) {
			clientThread.invokeLater(this::check);
		}
		unchecked.putIfAbsent(key, tile);
		afterCheck = onChecked;
		return false;
	}

	// worldmap_findcoordinmap over each map in its list, for each unchecked region.
	private void check() {
		unchecked.forEach((key, tile) -> {
			mapped.put(key, false);
			for (int map : client.getEnum(WORLDMAP_LIST_ENUM).getIntVals()) {
				client.runScript(FIND_COORD_IN_MAP_SCRIPT, map, coord(tile.worldPoint()));
				if (client.getIntStack()[0] != -1) {
					mapped.put(key, true);
					break;
				}
			}
		});
		unchecked.clear();
		if (afterCheck != null) {
			afterCheck.run();
		}
	}

	// A clientscript coord: plane, x and y packed.
	private static int coord(WorldPoint point) {
		return point.getPlane() << 28 | point.getX() << 14 | point.getY();
	}
}
