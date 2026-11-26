package com.groundmarkervariables.markers;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.runelite.api.coords.WorldPoint;

// A source's marker at an instanced point, and the stored (region) point it came from.
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class RenderedTile {
    public final WorldPoint worldPoint;
    public final WorldPoint stored;
    public final SourceMarker marker;
}
