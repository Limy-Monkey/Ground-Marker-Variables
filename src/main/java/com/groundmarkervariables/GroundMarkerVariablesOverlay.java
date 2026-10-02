package com.groundmarkervariables;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.components.TextComponent;
import net.runelite.client.util.ColorUtil;

public class GroundMarkerVariablesOverlay extends Overlay
{
	private static final int MAX_DRAW_DISTANCE = 32;
	private static final float PREVIEW_OPACITY = 0.5f;
	// Matches TextComponent's own tag format, so a label can color individual runs of text
	// (e.g. "<col=ff0000>Danger</col>") without affecting the tile's own fill/outline color.
	private static final Pattern COLOR_TAG_PATTERN = Pattern.compile("<col=[0-9a-fA-F]{2,6}>");
	// LootLabelVariable's own inline-icon tag -- see its resolveRich for where this is emitted.
	private static final Pattern ITEM_ICON_PATTERN = Pattern.compile("<item=(\\d+)>");

	private final Client client;
	private final GroundMarkerVariablesPlugin plugin;
	private final GroundMarkerVariablesConfig config;
	private final ItemManager itemManager;
	private final ImportPreviewManager importPreviewManager;
	private final PingedTileManager pingedTileManager;

	@Inject
	private GroundMarkerVariablesOverlay(Client client, GroundMarkerVariablesPlugin plugin, GroundMarkerVariablesConfig config,
		ItemManager itemManager, ImportPreviewManager importPreviewManager, PingedTileManager pingedTileManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		this.importPreviewManager = importPreviewManager;
		this.pingedTileManager = pingedTileManager;
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
		Stroke borderStroke = new BasicStroke((float) config.borderWidth());

		Instant now = Instant.now();
		for (WorldView wv : plugin.getTrackedWorldViews())
		{
			for (TranslatedMarker translated : plugin.getTranslatedMarkers(wv))
			{
				drawTileAt(graphics, wv, translated.worldPoint, translated.marker, borderStroke, 1f);
			}

			for (TranslatedPingedTile translated : pingedTileManager.getTranslatedPingedTiles(wv))
			{
				float opacity = translated.tile.opacity(now);
				if (opacity > 0f)
				{
					drawTileAt(graphics, wv, translated.worldPoint, translated.tile.marker, borderStroke, opacity);
				}
			}

			for (TranslatedMarker translated : importPreviewManager.getPreviewMarkers(wv))
			{
				drawTileAt(graphics, wv, translated.worldPoint, translated.marker, borderStroke, PREVIEW_OPACITY);
			}
		}

		return null;
	}

	private void drawTileAt(Graphics2D graphics, WorldView wv, WorldPoint worldPoint, CachedMarker marker, Stroke borderStroke, float opacity)
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

		Color color = marker.source.getColor() != null ? marker.source.getColor() : config.markerColor();
		Color fadedColor = opacity >= 1f ? color : ColorUtil.colorWithAlpha(color, (int) (color.getAlpha() * opacity));

		Polygon poly = Perspective.getCanvasTilePoly(client, localPoint);
		if (poly != null)
		{
			// Fill is a flat black overlay at the configured opacity, not the marker's own color
			// at reduced alpha — matches the core overlay's own drawTile() exactly.
			Color fill = new Color(0, 0, 0, (int) (config.fillOpacity() * opacity));
			OverlayUtil.renderPolygon(graphics, poly, fadedColor, fill, borderStroke);
		}

		// Label rendering doesn't depend on the tile poly resolving — a missing poly only
		// means we can't draw an outline, not that the label's canvas location is unavailable.
		String label = marker.getResolvedLabel();
		if (label != null && !label.isEmpty())
		{
			// </col> reverts to the tile's own color — TextComponent has no real concept of a
			// closing tag, so this just expands to another <col=> for the tile's own color.
			label = label.replace("</col>", "<col=" + ColorUtil.colorToHexCode(color) + ">");
			label = NamedColors.expandColorAliases(label);

			// Literal "\n" becomes a real line break, split into rows.
			String[] lines = label.replace("\\n", "\n").split("\n", -1);

			// A Composite fades every pixel actually drawn -- needed since TextComponent's
			// shadow and <col=> spans both hardcode full opacity regardless of our own color.
			Composite originalComposite = graphics.getComposite();
			if (opacity < 1f)
			{
				graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
			}

			drawLines(graphics, localPoint, lines, color);

			graphics.setComposite(originalComposite);
		}
	}

	// One line per row, each centered on its own width (text plus any inline item icons),
	// stacked as a block centered on localPoint (not anchored at the first line).
	private void drawLines(Graphics2D graphics, LocalPoint localPoint, String[] lines, Color color)
	{
		int lineHeight = graphics.getFontMetrics().getHeight();
		int startY = -((lines.length - 1) * lineHeight) / 2;

		for (int i = 0; i < lines.length; i++)
		{
			drawLine(graphics, localPoint, lines[i], color, startY + i * lineHeight);
		}
	}

	// A line is a sequence of text runs and <item=ID> icon tags. Both are measured up front so
	// the whole line -- icons included -- can be centered on the tile the same way a plain text
	// line always has been, then drawn left to right.
	private void drawLine(Graphics2D graphics, LocalPoint localPoint, String line, Color color, int yOffset)
	{
		List<Object> segments = splitIconSegments(line);
		FontMetrics fontMetrics = graphics.getFontMetrics();

		// Icons are scaled to font height (aspect preserved), not drawn at native sprite size.
		int iconHeight = lineHeight(fontMetrics);

		int totalWidth = 0;
		List<BufferedImage> images = new ArrayList<>();
		List<Integer> iconWidths = new ArrayList<>();
		for (Object segment : segments)
		{
			if (segment instanceof Integer)
			{
				// null on a lookup failure (see ItemManager#getImage) -- treated as zero-width
				// and simply skipped when drawing below, rather than dropping the whole line.
				BufferedImage image = itemManager.getImage((Integer) segment);
				images.add(image);
				int iconWidth = image == null ? 0 : image.getWidth() * iconHeight / image.getHeight();
				iconWidths.add(iconWidth);
				totalWidth += iconWidth;
			}
			else
			{
				totalWidth += fontMetrics.stringWidth(COLOR_TAG_PATTERN.matcher((String) segment).replaceAll(""));
			}
		}

		// An empty string has zero width, so this is the same tile-anchored canvas point plain
		// text lines have always centered against -- just without Perspective doing the centering.
		Point anchor = Perspective.getCanvasTextLocation(client, graphics, localPoint, "", 0);
		if (anchor == null)
		{
			return;
		}

		int x = anchor.getX() - totalWidth / 2;
		int y = anchor.getY() + yOffset;
		int imageIndex = 0;

		for (Object segment : segments)
		{
			if (segment instanceof Integer)
			{
				BufferedImage image = images.get(imageIndex);
				int iconWidth = iconWidths.get(imageIndex++);
				if (image != null)
				{
					int imageY = y - fontMetrics.getAscent();
					graphics.drawImage(image, x, imageY, iconWidth, iconHeight, null);
					x += iconWidth;
				}
			}
			else
			{
				String text = (String) segment;
				// Full opacity regardless of the marker's own (possibly transparent) color --
				// the caller's Composite (see drawTileAt) is what applies any fade, not this.
				TextComponent textComponent = new TextComponent();
				textComponent.setText(text);
				textComponent.setColor(ColorUtil.colorWithAlpha(color, 0xFF));
				textComponent.setPosition(x, y);
				textComponent.render(graphics);
				x += fontMetrics.stringWidth(COLOR_TAG_PATTERN.matcher(text).replaceAll(""));
			}
		}
	}

	private static int lineHeight(FontMetrics fontMetrics)
	{
		return fontMetrics.getAscent() + fontMetrics.getDescent();
	}

	// Splits a line into an ordered mix of text runs (String) and item icon tags (Integer item
	// id), so drawLine can measure and draw each in place without a second regex pass.
	private static List<Object> splitIconSegments(String line)
	{
		List<Object> segments = new ArrayList<>();
		Matcher matcher = ITEM_ICON_PATTERN.matcher(line);
		int last = 0;
		while (matcher.find())
		{
			if (matcher.start() > last)
			{
				segments.add(line.substring(last, matcher.start()));
			}
			segments.add(Integer.valueOf(matcher.group(1)));
			last = matcher.end();
		}
		if (last < line.length())
		{
			segments.add(line.substring(last));
		}
		return segments;
	}
}
