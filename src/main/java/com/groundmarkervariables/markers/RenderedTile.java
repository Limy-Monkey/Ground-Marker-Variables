package com.groundmarkervariables.markers;

import net.runelite.api.coords.WorldPoint;

// A source's marker at an instanced point, and the stored (region) point it came from.
public final class RenderedTile
{
	public final WorldPoint worldPoint;
	public final WorldPoint stored;
	public final SourceMarker marker;

	RenderedTile(WorldPoint worldPoint, WorldPoint stored, SourceMarker marker)
	{
		this.worldPoint = worldPoint;
		this.stored = stored;
		this.marker = marker;
	}
}
