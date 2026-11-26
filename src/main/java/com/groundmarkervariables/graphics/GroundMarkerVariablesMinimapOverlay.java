package com.groundmarkervariables.graphics;

import com.groundmarkervariables.GroundMarkerVariablesConfig;
import com.groundmarkervariables.markers.CachedMarker;
import com.groundmarkervariables.markers.RenderedTiles;
import com.groundmarkervariables.variables.meta.MetaState;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.time.Instant;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ColorUtil;

// Mirrors core's GroundMarkerMinimapOverlay — draws every drawn marker's outline (see RenderedTiles) on the minimap,
// gated by the "Draw tiles on minimap" config item.
public class GroundMarkerVariablesMinimapOverlay extends Overlay {
    private final Client client;
    private final GroundMarkerVariablesConfig config;
    private final RenderedTiles renderedTiles;

    @Inject
    private GroundMarkerVariablesMinimapOverlay(Client client, GroundMarkerVariablesConfig config, RenderedTiles renderedTiles) {
        this.client = client;
        this.config = config;
        this.renderedTiles = renderedTiles;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(PRIORITY_LOW);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.drawTileOnMinimap()) {
            return null;
        }

        // The same marker per tile as the main overlay.
        renderedTiles.forEachShown(Instant.now(), (wv, worldPoint, marker, opacity) -> drawMarker(graphics, wv, worldPoint, marker, opacity));

        return null;
    }

    private void drawMarker(Graphics2D graphics, WorldView wv, WorldPoint worldPoint, CachedMarker marker, float opacity) {
        LocalPoint lp = worldPoint.getPlane() == wv.getPlane() ? LocalPoint.fromWorld(wv, worldPoint) : null;
        if (lp == null) {
            return;
        }

        // The tile's corners: south-west, north-west, north-east, south-east.
        int size = Perspective.LOCAL_TILE_SIZE, x = lp.getX() & -size, y = lp.getY() & -size;
        Polygon poly = new Polygon();
        for (int corner = 0; corner < 4; corner++) {
            Point p = Perspective.localToMinimap(client, new LocalPoint(x + corner / 2 * size, y + (corner + 1) / 2 % 2 * size, wv.getId()));
            if (p == null) {
                return;
            }
            poly.addPoint(p.getX(), p.getY());
        }

        MetaState metaState = marker.getMetaState();
        Color color = marker.tileColor(config.markerColor());
        if (metaState.getTileOpacity() != null) {
            color = ColorUtil.colorWithAlpha(color, Math.round(metaState.getTileOpacity() * 255));
        }
        if (opacity < 1f) {
            color = ColorUtil.colorWithAlpha(color, Math.round(color.getAlpha() * opacity));
        }
        graphics.setStroke(new BasicStroke(1f));
        graphics.setColor(color);
        graphics.drawPolygon(poly);
    }
}
