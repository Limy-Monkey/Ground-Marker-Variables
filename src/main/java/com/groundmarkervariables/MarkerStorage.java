package com.groundmarkervariables;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;

// CRUD against core Ground Markers' own persisted "region_<id>" config keys.
@Singleton
public class MarkerStorage
{
	private static final String CORE_CONFIG_GROUP = "groundMarker";
	private static final String REGION_PREFIX = "region_";

	private final ConfigManager configManager;
	private final Gson gson;

	@Inject
	private MarkerStorage(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	public Collection<GroundMarkerPointData> getStoredPoints(int regionId)
	{
		String json = configManager.getConfiguration(CORE_CONFIG_GROUP, REGION_PREFIX + regionId);
		if (json == null || json.isEmpty())
		{
			return Collections.emptyList();
		}

		List<GroundMarkerPointData> points = gson.fromJson(json, new TypeToken<List<GroundMarkerPointData>>()
		{
		}.getType());
		return points == null ? Collections.emptyList() : points;
	}

	// Merges in points not already at an existing point's location, grouped by their own regionId.
	public void addPoints(Collection<GroundMarkerPointData> newPoints)
	{
		newPoints.stream().collect(Collectors.groupingBy(GroundMarkerPointData::getRegionId)).forEach((regionId, grouped) ->
		{
			List<GroundMarkerPointData> merged = new ArrayList<>(getStoredPoints(regionId));
			for (GroundMarkerPointData point : grouped)
			{
				if (!containsLocation(merged, point))
				{
					merged.add(point);
				}
			}

			savePoints(regionId, merged);
		});
	}

	// Removes any stored point at the same location as one of toRemove, grouped by their own regionId.
	public void deletePoints(Collection<GroundMarkerPointData> toRemove)
	{
		toRemove.stream().collect(Collectors.groupingBy(GroundMarkerPointData::getRegionId)).forEach((regionId, grouped) ->
		{
			List<GroundMarkerPointData> remaining = new ArrayList<>(getStoredPoints(regionId));
			remaining.removeIf(point -> containsLocation(grouped, point));
			savePoints(regionId, remaining);
		});
	}

	private void savePoints(int regionId, Collection<GroundMarkerPointData> points)
	{
		if (points == null || points.isEmpty())
		{
			configManager.unsetConfiguration(CORE_CONFIG_GROUP, REGION_PREFIX + regionId);
			return;
		}

		configManager.setConfiguration(CORE_CONFIG_GROUP, REGION_PREFIX + regionId, gson.toJson(points));
	}

	public GroundMarkerPointData findStoredPoint(WorldPoint worldPoint)
	{
		for (GroundMarkerPointData point : getStoredPoints(worldPoint.getRegionID()))
		{
			if (point.getRegionX() == worldPoint.getRegionX() && point.getRegionY() == worldPoint.getRegionY()
				&& point.getZ() == worldPoint.getPlane())
			{
				return point;
			}
		}

		return null;
	}

	private static boolean containsLocation(Collection<GroundMarkerPointData> points, GroundMarkerPointData point)
	{
		for (GroundMarkerPointData existing : points)
		{
			if (existing.getRegionX() == point.getRegionX() && existing.getRegionY() == point.getRegionY()
				&& existing.getZ() == point.getZ())
			{
				return true;
			}
		}

		return false;
	}
}
