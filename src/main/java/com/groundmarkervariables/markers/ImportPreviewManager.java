package com.groundmarkervariables.markers;

import com.groundmarkervariables.variables.LabelResolver;
import com.groundmarkervariables.variables.meta.MetaVariableResolver;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;
import javax.inject.Singleton;

// Unsaved preview of tiles an import (or an unmarked tile's label) is about to add, drawn half see-through over
// everything else -- cleared via the chatbox input's own onClose, which fires on every dismissal path.
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ImportPreviewManager implements TileSource {
    private static final float OPACITY = 0.5f;

    private final RenderedTiles renderedTiles;
    private final LabelResolver labelResolver;
    private final MetaVariableResolver metaVariableResolver;

    // Replaced whole, so a concurrent read never sees a partly built map.
    private volatile Map<Integer, List<SourceMarker>> byRegion = Collections.emptyMap();

    public void show(Collection<GroundMarkerPointData> points) {
        Map<Integer, List<SourceMarker>> markers = new HashMap<>();
        for (GroundMarkerPointData point : points) {
            CachedMarker marker = new CachedMarker(point, labelResolver, metaVariableResolver);
            markers.computeIfAbsent(point.getRegionId(), r -> new ArrayList<>())
                .add(new SourceMarker(marker, this, RenderedTiles.PREVIEW_PRIORITY, 0, null));
        }
        byRegion = markers;
        renderedTiles.invalidate();
    }

    public void clear() {
        byRegion = Collections.emptyMap();
        renderedTiles.invalidate();
    }

    @Override
    public Collection<SourceMarker> markers(int regionId) {
        return byRegion.getOrDefault(regionId, Collections.emptyList());
    }

    @Override
    public float opacity(SourceMarker marker, Instant now) {
        return OPACITY;
    }
}
