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
		this.resolvedLabel = labelResolver.resolve(expandColorAliases(source.getLabel()));
	}

	void refresh(LabelResolver labelResolver)
	{
		resolvedLabel = labelResolver.resolve(expandColorAliases(source.getLabel()));
	}

	// {col=...}/{/col} are curly-brace aliases for <col=...>/</col> — expand before resolve()
	// so LabelResolver's variables don't mistake them for {variable}/{cond ? a : b} syntax.
	private static String expandColorAliases(String label)
	{
		return label == null ? null : NamedColors.expandColorAliases(label);
	}

	String getResolvedLabel()
	{
		return resolvedLabel;
	}
}
