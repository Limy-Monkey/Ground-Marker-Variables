package com.groundmarkervariables;

import java.awt.Color;
import javax.annotation.Nullable;
import lombok.Value;

// Mirrors the JSON shape the core Ground Markers plugin persists per region
// (config group "groundMarker", key "region_<regionId>"). Kept as our own type
// rather than importing theirs because net.runelite.client.plugins.groundmarkers
// .GroundMarkerPoint is package-private.
@Value
class GroundMarkerPointData
{
	int regionId;
	int regionX;
	int regionY;
	int z;
	@Nullable
	Color color;
	@Nullable
	String label;
}
