package com.groundmarkervariables.markers;

import java.time.Instant;
import java.util.Collection;

// Something that draws markers, e.g. saved markers or party pings. RenderedTiles picks one per tile across all sources.
public interface TileSource {
    // The markers stored in regionId.
    Collection<SourceMarker> markers(int regionId);

    default float opacity(SourceMarker marker, Instant now) {
        return 1f;
    }

    // Hidden by the user, so the next best marker on the tile is drawn.
    default boolean hidden(RenderedTile tile) {
        return false;
    }
}
