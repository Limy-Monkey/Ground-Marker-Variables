package com.groundmarkervariables.markers;

import java.awt.Color;
import javax.annotation.Nullable;
import lombok.Value;
import lombok.With;
import net.runelite.api.coords.WorldPoint;

// Mirrors the JSON shape the core Ground Markers plugin persists per region
// (config group "groundMarker", key "region_<regionId>"). Kept as our own type
// rather than importing theirs because net.runelite.client.plugins.groundmarkers
// .GroundMarkerPoint is package-private.
@Value
public class GroundMarkerPointData
{
	int regionId;
	int regionX;
	int regionY;
	int z;
	@Nullable
	@With
	Color color;
	@Nullable
	@With
	String label;

	public WorldPoint worldPoint()
	{
		return WorldPoint.fromRegion(regionId, regionX, regionY, z);
	}
}
