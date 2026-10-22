package com.groundmarkervariables;

import com.groundmarkervariables.variables.LabelResolver;
import com.groundmarkervariables.variables.meta.MetaState;
import com.groundmarkervariables.variables.meta.MetaVariableResolver;

// Pre-resolved label and meta state so render() doesn't re-run resolution every frame.
// Refreshed once per game tick (see GroundMarkerVariablesPlugin#onGameTick), not per frame.
public class CachedMarker
{
	public final GroundMarkerPointData source;
	private volatile String resolvedLabel;
	private volatile MetaState metaState = new MetaState();

	public CachedMarker(GroundMarkerPointData source, LabelResolver labelResolver, MetaVariableResolver metaVariableResolver)
	{
		this(source);
		refresh(labelResolver, metaVariableResolver);
	}

	// Unresolved until refresh(), e.g. once its tile is known.
	public CachedMarker(GroundMarkerPointData source)
	{
		this.source = source;
	}

	// Meta variables strip from the resolved text, not the raw label, so one written inside a
	// conditional's winning branch still applies.
	public void refresh(LabelResolver labelResolver, MetaVariableResolver metaVariableResolver)
	{
		String resolved = labelResolver.resolveMarker(source.getLabel());
		MetaVariableResolver.Result meta = metaVariableResolver.resolve(resolved);
		metaState = meta.state;
		resolvedLabel = meta.label;
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
