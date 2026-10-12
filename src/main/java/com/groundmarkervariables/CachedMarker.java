package com.groundmarkervariables;

import com.groundmarkervariables.variables.LabelResolver;
import com.groundmarkervariables.variables.meta.MetaState;
import com.groundmarkervariables.variables.meta.MetaVariableResolver;
import com.groundmarkervariables.variables.support.NamedColors;

// Pre-resolved label and meta state so render() doesn't re-run resolution every frame.
// Refreshed once per game tick (see GroundMarkerVariablesPlugin#onGameTick), not per frame.
public class CachedMarker
{
	public final GroundMarkerPointData source;
	private volatile String resolvedLabel;
	private volatile MetaState metaState = new MetaState();

	public CachedMarker(GroundMarkerPointData source, LabelResolver labelResolver, MetaVariableResolver metaVariableResolver)
	{
		this.source = source;
		refresh(labelResolver, metaVariableResolver);
	}

	// Meta variables strip from the resolved text, not the raw label, so one written inside a
	// conditional's winning branch still applies.
	public void refresh(LabelResolver labelResolver, MetaVariableResolver metaVariableResolver)
	{
		String resolved = labelResolver.resolve(expandColorAliases(source.getLabel()));
		MetaVariableResolver.Result meta = metaVariableResolver.resolve(resolved);
		metaState = meta.state;
		resolvedLabel = meta.label;
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

	MetaState getMetaState()
	{
		return metaState;
	}
}
