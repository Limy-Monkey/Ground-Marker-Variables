package com.groundmarkervariables;

import net.runelite.api.coords.WorldPoint;

// A marker's position after instance translation, computed once per cache rebuild
// instead of every render() call. Mirrors core's ColorTileMarker.
class TranslatedMarker
{
	final WorldPoint worldPoint;
	final CachedMarker marker;

	TranslatedMarker(WorldPoint worldPoint, CachedMarker marker)
	{
		this.worldPoint = worldPoint;
		this.marker = marker;
	}
}
