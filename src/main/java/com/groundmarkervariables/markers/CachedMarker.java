package com.groundmarkervariables.markers;

import com.groundmarkervariables.variables.LabelResolver;
import com.groundmarkervariables.variables.meta.MetaState;
import com.groundmarkervariables.variables.meta.MetaVariableResolver;
import java.awt.Color;
import net.runelite.client.util.ColorUtil;

// Pre-resolved label and meta state so render() doesn't re-run resolution every frame. Resolved when made; drawn
// ones are refreshed once per game tick at their own tile (see GroundMarkerVariablesPlugin#onGameTick).
public class CachedMarker {
    public final GroundMarkerPointData source;
    // source's label made ready to resolve, once (the label never changes).
    private final PreparedLabel preparedLabel;
    private volatile String resolvedLabel;
    private volatile MetaState metaState = new MetaState();

    public CachedMarker(GroundMarkerPointData source, LabelResolver labelResolver, MetaVariableResolver metaVariableResolver) {
        this.source = source;
        this.preparedLabel = labelResolver.prepareMarker(source.getLabel());
        refresh(labelResolver, metaVariableResolver);
    }

    // Meta variables strip from the resolved text, not the raw label, so one written inside a
    // conditional's winning branch still applies.
    public void refresh(LabelResolver labelResolver, MetaVariableResolver metaVariableResolver) {
        String resolved = labelResolver.resolveMarker(preparedLabel);
        MetaVariableResolver.Result meta = metaVariableResolver.resolve(resolved);
        metaState = meta.state;
        resolvedLabel = meta.label;
    }

    public String getResolvedLabel() {
        return resolvedLabel;
    }

    public MetaState getMetaState() {
        return metaState;
    }

    // Its tile's color: {tileCol}'s with the saved alpha (tileOpacity is the outline's, not this), else the saved
    // color, else defaultColor.
    public Color tileColor(Color defaultColor) {
        Color base = source.getColor() != null ? source.getColor() : defaultColor;
        return metaState.getTileColor() != null ? ColorUtil.colorWithAlpha(metaState.getTileColor(), base.getAlpha()) : base;
    }
}
