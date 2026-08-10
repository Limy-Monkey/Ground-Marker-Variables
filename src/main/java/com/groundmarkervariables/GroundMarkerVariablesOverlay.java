package com.groundmarkervariables;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.groundmarkervariables.variables.LabelResolver;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.groundmarkers.GroundMarkerConfig;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

public class GroundMarkerVariablesOverlay extends Overlay
{
	private static final int MAX_DRAW_DISTANCE = 32;
	private static final String CORE_CONFIG_GROUP = "groundMarker";
	private static final String REGION_PREFIX = "region_";

	private final Client client;
	private final ConfigManager configManager;
	private final Gson gson;
	// Ground Markers' own config, so a marker saved without an explicit color falls back
	// to whatever "Tile color" the user has set in Ground Markers, not a color we invent.
	private final GroundMarkerConfig groundMarkerConfig;
	private final LabelResolver labelResolver;

	@Inject
	private GroundMarkerVariablesOverlay(Client client, ConfigManager configManager, Gson gson, GroundMarkerConfig groundMarkerConfig, LabelResolver labelResolver)
	{
		this.client = client;
		this.configManager = configManager;
		this.gson = gson;
		this.groundMarkerConfig = groundMarkerConfig;
		this.labelResolver = labelResolver;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		WorldView wv = client.getTopLevelWorldView();
		if (wv == null)
		{
			return null;
		}

		int[] regions = wv.getMapRegions();
		if (regions == null)
		{
			return null;
		}

		// Built once per frame, same as the core overlay, rather than once per tile.
		Stroke borderStroke = new BasicStroke((float) groundMarkerConfig.borderWidth());
		for (int regionId : regions)
		{
			for (GroundMarkerPointData point : getPoints(regionId))
			{
				drawTile(graphics, wv, point, borderStroke);
			}
		}

		return null;
	}

	private Collection<GroundMarkerPointData> getPoints(int regionId)
	{
		String json = configManager.getConfiguration(CORE_CONFIG_GROUP, REGION_PREFIX + regionId);
		if (json == null || json.isEmpty())
		{
			return Collections.emptyList();
		}

		List<GroundMarkerPointData> points = gson.fromJson(json, new TypeToken<List<GroundMarkerPointData>>()
		{
		}.getType());
		return points == null ? Collections.emptyList() : points;
	}

	private void drawTile(Graphics2D graphics, WorldView wv, GroundMarkerPointData point, Stroke borderStroke)
	{
		WorldPoint worldPoint = WorldPoint.fromRegion(point.getRegionId(), point.getRegionX(), point.getRegionY(), point.getZ());
		if (worldPoint.getPlane() != wv.getPlane())
		{
			return;
		}

		if (client.getLocalPlayer() == null || worldPoint.distanceTo(client.getLocalPlayer().getWorldLocation()) > MAX_DRAW_DISTANCE)
		{
			return;
		}

		LocalPoint localPoint = LocalPoint.fromWorld(wv, worldPoint);
		if (localPoint == null)
		{
			return;
		}

		Polygon poly = Perspective.getCanvasTilePoly(client, localPoint);
		if (poly == null)
		{
			return;
		}

		Color color = point.getColor() != null ? point.getColor() : groundMarkerConfig.markerColor();
		// Fill is a flat black overlay at the configured opacity, not the marker's own color
		// at reduced alpha — matches the core overlay's own drawTile() exactly.
		OverlayUtil.renderPolygon(graphics, poly, color, new Color(0, 0, 0, groundMarkerConfig.fillOpacity()), borderStroke);

		String label = labelResolver.resolve(point.getLabel());
		if (label != null && !label.isEmpty())
		{
			Point textLocation = Perspective.getCanvasTextLocation(client, graphics, localPoint, label, 0);
			if (textLocation != null)
			{
				OverlayUtil.renderTextLocation(graphics, textLocation, label, color);
			}
		}
	}
}
