package com.groundmarkervariables.markers;

import com.google.gson.reflect.TypeToken;
import com.groundmarkervariables.GroundMarkerVariablesConfig;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.Constants;
import net.runelite.api.SceneTileModel;
import net.runelite.api.SceneTilePaint;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

// What a saved marker's tile is: indoors, on water, walkable, and whether it's ever been seen in an instance. Only readable
// near it, so read once a session (in an instance and out of one, see sample()) and saved per region, like markers
// ("tileFacts_<region>": key() -> bits); Wilderness level works anywhere. For the Marker Manager's tile info columns.
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class TileFacts {
    public static final int INDOORS = 1;
    public static final int WATER = 2;
    public static final int WALKABLE = 4;
    public static final int SEEN_INSTANCED = 8;
    private static final String KEY_PREFIX = "tileFacts_";
    // The game's water floor textures, as 117 HD (BSD 2-Clause) classifies them: classic flat and swamp water, then the
    // Sailing seas (each sea's surface, open, shelf, dropoff and deep) and out-of-bounds water.
    private static final Set<Integer> WATER_TEXTURES = IntStream.concat(IntStream.of(1, 24, 25, 208), IntStream.rangeClosed(130, 189))
        .boxed().collect(Collectors.toSet());

    private final MarkerStorage markerStorage;
    // Each region's facts by key(), loaded on first use.
    private final Map<Integer, Map<Integer, Integer>> regions = new HashMap<>();
    // Tiles read this session (once each, see sample()), and regions changed since save().
    private final Set<Integer> read = new HashSet<>();
    private final Set<Integer> changed = new HashSet<>();

    // The facts known for a stored tile (bits of INDOORS, WATER, WALKABLE, SEEN_INSTANCED), or null if it hasn't been seen.
    @Nullable
    public Integer get(GroundMarkerPointData tile) {
        return region(tile.getRegionId()).get(key(tile.getRegionId(), tile.getRegionX(), tile.getRegionY(), tile.getZ()));
    }

    // Reads stored's tile where wv draws it (at worldPoint, e.g. in an instance), unless it's been read this session in an
    // instance (or out of one) as now; SEEN_INSTANCED, once set, stays. On the client thread.
    public void sample(WorldView wv, WorldPoint worldPoint, WorldPoint stored) {
        // Keyed as stored (an instance draws a template, maybe on another plane), read where drawn.
        int plane = worldPoint.getPlane();
        int key = key(stored.getRegionID(), stored.getRegionX(), stored.getRegionY(), stored.getPlane());
        int readAs = key | (wv.isInstance() ? 1 << 30 : 0);
        LocalPoint local = read.contains(readAs) ? null : LocalPoint.fromWorld(wv, worldPoint);
        CollisionData[] collision = wv.getCollisionMaps();
        if (local == null || collision == null || collision[plane] == null || wv.getTileSettings() == null) {
            return;
        }
        read.add(readAs);
        int x = local.getSceneX();
        int y = local.getSceneY();
        Map<Integer, Integer> region = region(stored.getRegionID());
        Integer before = region.get(key);
        int bits = (before == null ? 0 : before & SEEN_INSTANCED) | (wv.isInstance() ? SEEN_INSTANCED : 0);
        bits |= (wv.getTileSettings()[plane][x][y] & Constants.TILE_FLAG_UNDER_ROOF) != 0 ? INDOORS : 0;
        bits |= (collision[plane].getFlags()[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0 ? WALKABLE : 0;
        bits |= onWater(wv.getScene().getTiles()[plane][x][y]) ? WATER : 0;
        region.put(key, bits);
        if (before == null || before != bits) {
            changed.add(stored.getRegionID());
        }
    }

    // Drops the facts of every tile not in kept, removing regions left with none.
    public void sweep(Collection<GroundMarkerPointData> kept) {
        Set<Integer> keys = kept.stream().map(tile -> key(tile.getRegionId(), tile.getRegionX(), tile.getRegionY(), tile.getZ()))
            .collect(Collectors.toSet());
        for (int regionId : markerStorage.regionIds(GroundMarkerVariablesConfig.GROUP, KEY_PREFIX)) {
            Map<Integer, Integer> facts = region(regionId);
            if (facts.keySet().removeIf(key -> !keys.contains(key))) {
                markerStorage.writeRegion(GroundMarkerVariablesConfig.GROUP, KEY_PREFIX, regionId, facts, facts.isEmpty());
            }
        }
    }

    // Saves each region sample() changed.
    public void save() {
        changed.forEach(region -> markerStorage.writeRegion(GroundMarkerVariablesConfig.GROUP, KEY_PREFIX, region, regions.get(region), false));
        changed.clear();
    }

    // The Wilderness level at point (above or below ground: level n starts 8 * (n - 1) tiles north of its edge), else 0.
    public static int wildernessLevel(WorldPoint point) {
        int x = point.getX();
        int y = point.getY();
        int edge = y >= 9920 ? 9920 : 3520;
        return x >= 2944 && x <= 3391 && y >= edge && y < edge + 448 ? (y - edge) / 8 + 1 : 0;
    }

    // A tile drawn with a water texture, flat or (on a shaped tile) on any of its triangles.
    private static boolean onWater(@Nullable Tile tile) {
        if (tile == null) {
            return false;
        }
        SceneTilePaint paint = tile.getSceneTilePaint();
        if (paint != null) {
            return WATER_TEXTURES.contains(paint.getTexture());
        }
        SceneTileModel model = tile.getSceneTileModel();
        int[] textures = model == null ? null : model.getTriangleTextureId();
        if (textures != null) {
            for (int texture : textures) {
                if (WATER_TEXTURES.contains(texture)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Map<Integer, Integer> region(int region) {
        return regions.computeIfAbsent(region, r -> {
            Map<Integer, Integer> saved = markerStorage.readRegion(GroundMarkerVariablesConfig.GROUP, KEY_PREFIX, r,
                new TypeToken<HashMap<Integer, Integer>>() {
                }.getType());
            return saved != null ? saved : new HashMap<>();
        });
    }

    // region << 14 | x << 8 | y << 2 | plane.
    private static int key(int region, int x, int y, int plane) {
        return region << 14 | x << 8 | y << 2 | plane;
    }
}
