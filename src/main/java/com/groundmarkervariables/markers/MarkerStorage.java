package com.groundmarkervariables.markers;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.groundmarkervariables.GroundMarkerVariablesConfig;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;
import javax.inject.Singleton;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;

// CRUD against core's and our own "region_<id>" config keys.
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class MarkerStorage {
	private static final String CORE_CONFIG_GROUP = "groundMarker";
	private static final String REGION_PREFIX = "region_";
	private static final Pattern VARIABLE = Pattern.compile("\\{[^{}]*\\}");

	private final ConfigManager configManager;
	private final GroundMarkerVariablesConfig config;
	private final Gson gson;

	// Every region with saved markers, in core's keys or ours.
	public List<Integer> storedRegionIds() {
		Set<Integer> regionIds = new LinkedHashSet<>(regionIds(CORE_CONFIG_GROUP, REGION_PREFIX));
		regionIds.addAll(regionIds(GroundMarkerVariablesConfig.GROUP, REGION_PREFIX));
		return new ArrayList<>(regionIds);
	}

	// Every region with a value in group under prefix + regionId (see readRegion).
	public List<Integer> regionIds(String group, String prefix) {
		String full = group + "." + prefix;
		List<Integer> regionIds = new ArrayList<>();
		for (String key : configManager.getConfigurationKeys(full)) {
			try {
				regionIds.add(Integer.parseInt(key.substring(full.length())));
			} catch (NumberFormatException e) {
				// Not one of ours.
			}
		}
		return regionIds;
	}

	// Core's points with our labels merged in, plus ours core no longer has.
	public Collection<GroundMarkerPointData> getStoredPoints(int regionId) {
		List<GroundMarkerPointData> core = read(CORE_CONFIG_GROUP, regionId);
		List<GroundMarkerPointData> gmv = read(GroundMarkerVariablesConfig.GROUP, regionId);
		if (gmv.isEmpty()) {
			return core;
		}

		List<GroundMarkerPointData> merged = new ArrayList<>();
		for (GroundMarkerPointData point : core) {
			GroundMarkerPointData gmvPoint = find(gmv, point);
			merged.add(gmvPoint != null && isEmpty(point.getLabel()) ? point.withLabel(gmvPoint.getLabel()) : point);
		}
		for (GroundMarkerPointData gmvPoint : gmv) {
			if (find(core, gmvPoint) == null) {
				merged.add(gmvPoint);
			}
		}
		return merged;
	}

	private List<GroundMarkerPointData> read(String group, int regionId) {
		List<GroundMarkerPointData> points = readRegion(group, REGION_PREFIX, regionId, new TypeToken<List<GroundMarkerPointData>>() {
		}.getType());
		return points == null ? Collections.emptyList() : points;
	}

	// A region's value in group under prefix + regionId (markers' "region_", TileFacts' "tileFacts_"), as type; null if none.
	@Nullable
	public <T> T readRegion(String group, String prefix, int regionId, Type type) {
		String json = configManager.getConfiguration(group, prefix + regionId);
		return json == null || json.isEmpty() ? null : gson.fromJson(json, type);
	}

	// Saves a region's value (see readRegion), or removes it if empty.
	public void writeRegion(String group, String prefix, int regionId, Object value, boolean empty) {
		if (!empty) {
			configManager.setConfiguration(group, prefix + regionId, gson.toJson(value));
		} else if (configManager.getConfiguration(group, prefix + regionId) != null) {
			configManager.unsetConfiguration(group, prefix + regionId);
		}
	}

	// Merges in points not already at an existing point's location, grouped by their own regionId.
	public void addPoints(Collection<GroundMarkerPointData> newPoints) {
		newPoints.stream().collect(Collectors.groupingBy(GroundMarkerPointData::getRegionId)).forEach((regionId, grouped) -> {
			List<GroundMarkerPointData> merged = new ArrayList<>(getStoredPoints(regionId));
			for (GroundMarkerPointData point : grouped) {
				if (find(merged, point) == null) {
					merged.add(point);
				}
			}

			savePoints(regionId, merged);
		});
	}

	// Like addPoints, but each new point replaces any stored point at its location.
	public void replacePoints(Collection<GroundMarkerPointData> newPoints) {
		newPoints.stream().collect(Collectors.groupingBy(GroundMarkerPointData::getRegionId)).forEach((regionId, grouped) -> {
			List<GroundMarkerPointData> merged = new ArrayList<>(getStoredPoints(regionId));
			for (GroundMarkerPointData point : grouped) {
				merged.remove(find(merged, point));
				merged.add(point);
			}

			savePoints(regionId, merged);
		});
	}

	// Removes any stored point at the same location as one of toRemove, grouped by their own regionId.
	public void deletePoints(Collection<GroundMarkerPointData> toRemove) {
		toRemove.stream().collect(Collectors.groupingBy(GroundMarkerPointData::getRegionId)).forEach((regionId, grouped) -> {
			List<GroundMarkerPointData> remaining = new ArrayList<>(getStoredPoints(regionId));
			remaining.removeIf(point -> find(grouped, point) != null);
			savePoints(regionId, remaining);
		});
	}

	// With Smart Save, {variable} labels go to ours and core gets the point unlabeled.
	public void savePoints(int regionId, Collection<GroundMarkerPointData> points) {
		List<GroundMarkerPointData> core = new ArrayList<>();
		List<GroundMarkerPointData> gmv = new ArrayList<>();
		for (GroundMarkerPointData point : points) {
			if (config.smartSave() && point.getLabel() != null && VARIABLE.matcher(point.getLabel()).find()) {
				core.add(point.withLabel(null));
				gmv.add(point);
			} else {
				core.add(point);
			}
		}

		write(CORE_CONFIG_GROUP, regionId, core);
		write(GroundMarkerVariablesConfig.GROUP, regionId, gmv);
	}

	private void write(String group, int regionId, List<GroundMarkerPointData> points) {
		writeRegion(group, REGION_PREFIX, regionId, points, points.isEmpty());
	}

	// The stored point at worldPoint changed in place, saved; false if there's none.
	public boolean updatePoint(WorldPoint worldPoint, UnaryOperator<GroundMarkerPointData> change) {
		List<GroundMarkerPointData> points = new ArrayList<>(getStoredPoints(worldPoint.getRegionID()));
		GroundMarkerPointData point = findStoredPoint(worldPoint);
		if (point == null) {
			return false;
		}
		points.set(points.indexOf(point), change.apply(point));
		savePoints(worldPoint.getRegionID(), points);
		return true;
	}

	public GroundMarkerPointData findStoredPoint(WorldPoint worldPoint) {
		for (GroundMarkerPointData point : getStoredPoints(worldPoint.getRegionID())) {
			if (point.getRegionX() == worldPoint.getRegionX() && point.getRegionY() == worldPoint.getRegionY()
				&& point.getZ() == worldPoint.getPlane()) {
				return point;
			}
		}

		return null;
	}

	private static boolean isEmpty(String label) {
		return label == null || label.isEmpty();
	}


	private static GroundMarkerPointData find(Collection<GroundMarkerPointData> points, GroundMarkerPointData point) {
		for (GroundMarkerPointData existing : points) {
			if (existing.getRegionX() == point.getRegionX() && existing.getRegionY() == point.getRegionY()
				&& existing.getZ() == point.getZ()) {
				return existing;
			}
		}

		return null;
	}
}
