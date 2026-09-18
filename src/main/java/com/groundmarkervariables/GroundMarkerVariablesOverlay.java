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
import net.runelite.api.WorldEntity;
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
		setPriority(PRIORITY_LOW);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		WorldView topLevelWv = client.getTopLevelWorldView();
		if (topLevelWv == null)
		{
			return null;
		}

		// Built once per frame, same as the core overlay, rather than once per tile.
		Stroke borderStroke = new BasicStroke((float) groundMarkerConfig.borderWidth());

		// Markers are stored by region, not by worldview, so the same stored point can
		// apply inside multiple worldviews at once — the top-level one and any world
		// entity's own (e.g. a ship), each with its own scene to translate the point into.
		drawWorldView(graphics, topLevelWv, borderStroke);
		for (WorldEntity worldEntity : topLevelWv.worldEntities())
		{
			drawWorldView(graphics, worldEntity.getWorldView(), borderStroke);
		}

		return null;
	}

	private void drawWorldView(Graphics2D graphics, WorldView wv, Stroke borderStroke)
	{
		int[] regions = wv.getMapRegions();
		if (regions == null)
		{
			return;
		}

		for (int regionId : regions)
		{
			for (GroundMarkerPointData point : getPoints(regionId))
			{
				drawTile(graphics, wv, point, borderStroke);
			}
		}
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
		WorldPoint storedPoint = WorldPoint.fromRegion(point.getRegionId(), point.getRegionX(), point.getRegionY(), point.getZ());
		// Inside an instance, the stored (template) point doesn't map to on-screen coordinates
		// directly — it has to be translated to wherever the instance actually placed that
		// template chunk, and (per toLocalInstance's own docs) the same template chunk can
		// appear more than once on the scene, so this can yield 0, 1, or several tiles to draw.
		for (WorldPoint worldPoint : WorldPoint.toLocalInstance(wv, storedPoint))
		{
			drawTileAt(graphics, wv, worldPoint, point, borderStroke);
		}
	}

	private void drawTileAt(Graphics2D graphics, WorldView wv, WorldPoint worldPoint, GroundMarkerPointData point, Stroke borderStroke)
	{
		if (worldPoint.getPlane() != wv.getPlane())
		{
			return;
		}

		// Distance-cull against the player's own position only when they're on the top-level
		// worldview — that position isn't meaningful for culling tiles in a world entity's own
		// (e.g. a ship's) worldview, so those are never distance-culled, same as core.
		if (client.getLocalPlayer().getWorldView().isTopLevel()
			&& worldPoint.distanceTo(client.getLocalPlayer().getWorldLocation()) >= MAX_DRAW_DISTANCE)
		{
			return;
		}

		LocalPoint localPoint = LocalPoint.fromWorld(wv, worldPoint);
		if (localPoint == null)
		{
			return;
		}

		Color color = point.getColor() != null ? point.getColor() : groundMarkerConfig.markerColor();

		Polygon poly = Perspective.getCanvasTilePoly(client, localPoint);
		if (poly != null)
		{
			// Fill is a flat black overlay at the configured opacity, not the marker's own color
			// at reduced alpha — matches the core overlay's own drawTile() exactly.
			OverlayUtil.renderPolygon(graphics, poly, color, new Color(0, 0, 0, groundMarkerConfig.fillOpacity()), borderStroke);
		}

		// Label rendering doesn't depend on the tile poly resolving — a missing poly only
		// means we can't draw an outline, not that the label's canvas location is unavailable.
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
