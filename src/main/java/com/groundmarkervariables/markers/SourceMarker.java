package com.groundmarkervariables.markers;

import lombok.RequiredArgsConstructor;

// A marker a TileSource draws, with its priority and tie-break order (higher wins), and the source's own data for it.
@RequiredArgsConstructor
public final class SourceMarker {
	public final CachedMarker marker;
	public final TileSource source;
	public final float priority;
	public final long order;
	public final Object owner;
}
