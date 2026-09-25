package com.groundmarkervariables;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
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

// Mirrors core's GroundMarkerMinimapOverlay — draws every marker's outline on the minimap,
// gated by the "Draw tiles on minimap" config item.
class GroundMarkerVariablesMinimapOverlay extends Overlay
{
	private final Client client;
	private final GroundMarkerVariablesPlugin plugin;
	private final GroundMarkerVariablesConfig config;

	@Inject
	private GroundMarkerVariablesMinimapOverlay(Client client, GroundMarkerVariablesPlugin plugin, GroundMarkerVariablesConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
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

		for (WorldView wv : plugin.getTrackedWorldViews())
		{
			for (TranslatedMarker translated : plugin.getTranslatedMarkers(wv))
			{
				WorldPoint worldPoint = translated.worldPoint;
				if (worldPoint.getPlane() != wv.getPlane())
				{
					continue;
				}

				Color color = translated.marker.source.getColor() != null ? translated.marker.source.getColor() : config.markerColor();
				drawOnMinimap(graphics, wv, worldPoint, color);
			}
		}

		return null;
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
