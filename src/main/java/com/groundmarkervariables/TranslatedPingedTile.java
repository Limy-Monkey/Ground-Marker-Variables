package com.groundmarkervariables;

import net.runelite.api.coords.WorldPoint;

// A pinged tile's position after instance translation. Mirrors TranslatedMarker, computed the
// same way (once per tick, not per render() call) by PingedTileManager.
class TranslatedPingedTile
{
	final WorldPoint worldPoint;
	final PingedTile tile;

	TranslatedPingedTile(WorldPoint worldPoint, PingedTile tile)
	{
		this.worldPoint = worldPoint;
		this.tile = tile;
	}
}
