package com.groundmarkervariables;

import com.groundmarkervariables.markers.CachedMarker;
import com.groundmarkervariables.markers.RenderedTile;
import com.groundmarkervariables.markers.RenderedTiles;
import com.groundmarkervariables.variables.meta.MetaState;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
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
class GroundMarkerVariablesMinimapOverlay extends Overlay
{
	private final Client client;
	private final GroundMarkerVariablesConfig config;
	private final RenderedTiles renderedTiles;

	@Inject
	private GroundMarkerVariablesMinimapOverlay(Client client, GroundMarkerVariablesConfig config, RenderedTiles renderedTiles)
	{
		this.client = client;
		this.config = config;
		this.renderedTiles = renderedTiles;
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(PRIORITY_LOW);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.drawTileOnMinimap())
		{
			return null;
		}

		// The same marker per tile as the main overlay.
		Instant now = Instant.now();
		for (WorldView wv : renderedTiles.worldViews())
		{
			for (RenderedTile rendered : renderedTiles.winners(wv))
			{
				float opacity = rendered.marker.source.opacity(rendered.marker, now);
				if (opacity > 0f)
				{
					drawMarker(graphics, wv, rendered.worldPoint, rendered.marker.marker, opacity);
				}
			}
		}

		return null;
	}

	private void drawMarker(Graphics2D graphics, WorldView wv, WorldPoint worldPoint, CachedMarker marker, float opacity)
	{
		if (worldPoint.getPlane() != wv.getPlane())
		{
			return;
		}

		MetaState metaState = marker.getMetaState();
		Color baseColor = marker.source.getColor() != null ? marker.source.getColor() : config.markerColor();

		// No tileOpacity override -- keep the tile's own saved alpha, not fully opaque.
		Color color = metaState.getTileColor() != null
			? ColorUtil.colorWithAlpha(metaState.getTileColor(), baseColor.getAlpha()) : baseColor;
		if (metaState.getTileOpacity() != null)
		{
			color = ColorUtil.colorWithAlpha(color, Math.round(metaState.getTileOpacity() * 255));
		}
		if (opacity < 1f)
		{
			color = ColorUtil.colorWithAlpha(color, Math.round(color.getAlpha() * opacity));
		}
		drawOnMinimap(graphics, wv, worldPoint, color);
	}

	private void drawOnMinimap(Graphics2D graphics, WorldView wv, WorldPoint point, Color color)
	{
		LocalPoint lp = LocalPoint.fromWorld(wv, point);
		if (lp == null)
		{
			return;
		}

		int x = lp.getX() & -Perspective.LOCAL_TILE_SIZE;
		int y = lp.getY() & -Perspective.LOCAL_TILE_SIZE;

		Point mp1 = Perspective.localToMinimap(client, new LocalPoint(x, y, wv.getId()));
		Point mp2 = Perspective.localToMinimap(client, new LocalPoint(x, y + Perspective.LOCAL_TILE_SIZE, wv.getId()));
		Point mp3 = Perspective.localToMinimap(client, new LocalPoint(x + Perspective.LOCAL_TILE_SIZE, y + Perspective.LOCAL_TILE_SIZE, wv.getId()));
		Point mp4 = Perspective.localToMinimap(client, new LocalPoint(x + Perspective.LOCAL_TILE_SIZE, y, wv.getId()));

		if (mp1 == null || mp2 == null || mp3 == null || mp4 == null)
		{
			return;
		}

		Polygon poly = new Polygon();
		poly.addPoint(mp1.getX(), mp1.getY());
		poly.addPoint(mp2.getX(), mp2.getY());
		poly.addPoint(mp3.getX(), mp3.getY());
		poly.addPoint(mp4.getX(), mp4.getY());

		Stroke stroke = new BasicStroke(1f);
		graphics.setStroke(stroke);
		graphics.setColor(color);
		graphics.drawPolygon(poly);
	}
}
