package com.groundmarkervariables.variables.support;

import javax.annotation.Nullable;
import javax.inject.Singleton;
import net.runelite.api.coords.WorldPoint;

// The tile whose label is being resolved, set by the plugin's refresh loops. Null elsewhere.
@Singleton
public class ResolvingTile {
	@Nullable
	public WorldPoint tile;
}
