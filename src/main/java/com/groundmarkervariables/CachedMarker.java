package com.groundmarkervariables;

import com.groundmarkervariables.variables.LabelResolver;

// Pre-resolved label so render() doesn't re-run LabelResolver every frame.
// Refreshed once per game tick (see GroundMarkerVariablesPlugin#onGameTick), not per frame.
class CachedMarker
{
	final GroundMarkerPointData source;
	private volatile String resolvedLabel;

	CachedMarker(GroundMarkerPointData source, LabelResolver labelResolver)
	{
		this.source = source;
		this.resolvedLabel = labelResolver.resolve(source.getLabel());
	}

	void refresh(LabelResolver labelResolver)
	{
		resolvedLabel = labelResolver.resolve(source.getLabel());
	}

	String getResolvedLabel()
	{
		return resolvedLabel;
	}
}
