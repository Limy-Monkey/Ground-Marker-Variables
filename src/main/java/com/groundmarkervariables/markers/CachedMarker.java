package com.groundmarkervariables.markers;

import com.groundmarkervariables.variables.LabelResolver;
import com.groundmarkervariables.variables.meta.MetaState;
import com.groundmarkervariables.variables.meta.MetaVariableResolver;

// Pre-resolved label and meta state so render() doesn't re-run resolution every frame. Resolved when made; drawn
// ones are refreshed once per game tick at their own tile (see GroundMarkerVariablesPlugin#onGameTick).
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
		String resolved = labelResolver.resolveMarker(source.getLabel());
		MetaVariableResolver.Result meta = metaVariableResolver.resolve(resolved);
		metaState = meta.state;
		resolvedLabel = meta.label;
	}

	public String getResolvedLabel()
	{
		return resolvedLabel;
	}

	public MetaState getMetaState()
	{
		return metaState;
	}
}
