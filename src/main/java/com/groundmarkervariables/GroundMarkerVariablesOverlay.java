package com.groundmarkervariables;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.groundmarkers.GroundMarkerConfig;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.components.TextComponent;
import net.runelite.client.util.ColorUtil;

public class GroundMarkerVariablesOverlay extends Overlay
{
	private static final int MAX_DRAW_DISTANCE = 32;
	// Matches TextComponent's own tag format, so a label can color individual runs of text
	// (e.g. "<col=ff0000>Danger</col>") without affecting the tile's own fill/outline color.
	private static final Pattern COLOR_TAG_PATTERN = Pattern.compile("<col=[0-9a-fA-F]{2,6}>");
	// <col=NAME> for any of NamedColors' standard names — TextComponent only understands hex,
	// so this expands to <col=RRGGBB> before COLOR_TAG_PATTERN or TextComponent ever see it.
	private static final Pattern NAMED_COLOR_TAG_PATTERN = Pattern.compile(
		"<col=(" + String.join("|", NamedColors.HEX_BY_NAME.keySet()) + ")>", Pattern.CASE_INSENSITIVE);

	private final Client client;
	private final GroundMarkerVariablesPlugin plugin;
	// Ground Markers' own config, so a marker saved without an explicit color falls back
	// to whatever "Tile color" the user has set in Ground Markers, not a color we invent.
	private final GroundMarkerConfig groundMarkerConfig;

	@Inject
	private GroundMarkerVariablesOverlay(Client client, GroundMarkerVariablesPlugin plugin, GroundMarkerConfig groundMarkerConfig)
	{
		this.client = client;
		this.plugin = plugin;
		this.groundMarkerConfig = groundMarkerConfig;
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

		// Built once per frame, same as the core overlay, rather than once per tile.
		Stroke borderStroke = new BasicStroke((float) groundMarkerConfig.borderWidth());

		for (WorldView wv : plugin.getTrackedWorldViews())
		{
			for (TranslatedMarker translated : plugin.getTranslatedMarkers(wv))
			{
				drawTileAt(graphics, wv, translated.worldPoint, translated.marker, borderStroke);
			}
		}

		return null;
	}

	private void drawTileAt(Graphics2D graphics, WorldView wv, WorldPoint worldPoint, CachedMarker marker, Stroke borderStroke)
	{
		if (worldPoint.getPlane() != wv.getPlane())
		{
			return;
		}

		// Only distance-cull on the top-level worldview — the player's position isn't
		// meaningful for culling a world entity's own (e.g. a ship's) worldview.
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

		Color color = marker.source.getColor() != null ? marker.source.getColor() : groundMarkerConfig.markerColor();

		Polygon poly = Perspective.getCanvasTilePoly(client, localPoint);
		if (poly != null)
		{
			// Fill is a flat black overlay at the configured opacity, not the marker's own color
			// at reduced alpha — matches the core overlay's own drawTile() exactly.
			OverlayUtil.renderPolygon(graphics, poly, color, new Color(0, 0, 0, groundMarkerConfig.fillOpacity()), borderStroke);
		}

		// Label rendering doesn't depend on the tile poly resolving — a missing poly only
		// means we can't draw an outline, not that the label's canvas location is unavailable.
		String label = marker.getResolvedLabel();
		if (label != null && !label.isEmpty())
		{
			// </col> reverts to the tile's own color — TextComponent has no real concept of a
			// closing tag, so this just expands to another <col=> for the tile's own color.
			label = label.replace("</col>", "<col=" + ColorUtil.colorToHexCode(color) + ">");
			label = expandNamedColors(label);

			// Center against the visible text width, not the raw string with <col=> tags in it.
			String visibleLabel = COLOR_TAG_PATTERN.matcher(label).replaceAll("");
			Point textLocation = Perspective.getCanvasTextLocation(client, graphics, localPoint, visibleLabel, 0);
			if (textLocation != null)
			{
				// Full opacity regardless of the marker's own (possibly transparent) color —
				// only the tile fill/outline should ever be see-through, never the label text.
				TextComponent textComponent = new TextComponent();
				textComponent.setText(label);
				textComponent.setColor(ColorUtil.colorWithAlpha(color, 0xFF));
				textComponent.setPosition(textLocation.getX(), textLocation.getY());
				textComponent.render(graphics);
			}
		}
	}

	private static String expandNamedColors(String label)
	{
		Matcher matcher = NAMED_COLOR_TAG_PATTERN.matcher(label);
		if (!matcher.find())
		{
			return label;
		}

		StringBuilder result = new StringBuilder();
		matcher.reset();
		while (matcher.find())
		{
			String hex = NamedColors.HEX_BY_NAME.get(matcher.group(1).toLowerCase());
			matcher.appendReplacement(result, Matcher.quoteReplacement("<col=" + hex + ">"));
		}
		matcher.appendTail(result);
		return result.toString();
	}
}
