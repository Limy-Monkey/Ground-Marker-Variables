package com.groundmarkervariables.markers;

// A marker a TileSource draws, with its priority and tie-break order (higher wins), and the source's own data for it.
public final class SourceMarker
{
	public final CachedMarker marker;
	public final TileSource source;
	public final float priority;
	public final long order;
	public final Object owner;

	public SourceMarker(CachedMarker marker, TileSource source, float priority, long order, Object owner)
	{
		this.marker = marker;
		this.source = source;
		this.priority = priority;
		this.order = order;
		this.owner = owner;
	}
}
