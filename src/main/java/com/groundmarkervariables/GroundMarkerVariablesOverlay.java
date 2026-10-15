package com.groundmarkervariables;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.groundmarkervariables.party.PingedTileManager;
import com.groundmarkervariables.party.TranslatedPingedTile;
import com.groundmarkervariables.variables.meta.HighlightCorner;
import com.groundmarkervariables.variables.meta.MetaState;
import com.groundmarkervariables.variables.meta.TextAlign;
import com.groundmarkervariables.variables.support.NamedColors;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.components.TextComponent;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;

// Shared tile/label drawing code behind GroundMarkerVariablesTileOverlay and
// GroundMarkerVariablesLabelOverlay -- not an Overlay itself.
@Singleton
public class GroundMarkerVariablesOverlay
{
	private static final int MAX_DRAW_DISTANCE = 32;
	private static final float PREVIEW_OPACITY = 0.5f;
	// Matches TextComponent's own tag format, so a label can color individual runs of text
	// (e.g. "<col=ff0000>Danger</col>") without affecting the tile's own fill/outline color.
	private static final Pattern COLOR_TAG_PATTERN = Pattern.compile("<col=([0-9a-fA-F]{2,6})>");
	// <item=ID> icon, {b}/{/b}/{i}/{/i} toggle, or <flag=region> icon.
	private static final Pattern INLINE_TAG_PATTERN = Pattern.compile(
		"<item=(\\d+)>|\\{(/?)([bi])\\}|<flag=(us_west|us_east|us|uk|aus|ger|br|jp|sg|za)>", Pattern.CASE_INSENSITIVE);
	// The game's own world switcher flags, drawn at native size.
	private static final Map<String, Integer> FLAG_SPRITES = Map.of(
		"us", SpriteID.WorldswitcherFlags._1,
		"uk", SpriteID.WorldswitcherFlags._3,
		"aus", SpriteID.WorldswitcherFlags._5,
		"ger", SpriteID.WorldswitcherFlags._8,
		"us_west", SpriteID.WorldswitcherFlags._9,
		"us_east", SpriteID.WorldswitcherFlags._10,
		"br", SpriteID.WorldswitcherFlags._11,
		"jp", SpriteID.WorldswitcherFlags._12,
		"sg", SpriteID.WorldswitcherFlags._13,
		"za", SpriteID.WorldswitcherFlags._15);

	private final Client client;
	private final GroundMarkerVariablesPlugin plugin;
	private final GroundMarkerVariablesConfig config;
	private final ItemManager itemManager;
	private final SpriteManager spriteManager;
	private final ImportPreviewManager importPreviewManager;
	private final PingedTileManager pingedTileManager;

	// Keyed by SpriteManager's own (cached) flag image.
	private final Map<BufferedImage, BufferedImage> scaledFlags = new IdentityHashMap<>();

	@Inject
	private GroundMarkerVariablesOverlay(Client client, GroundMarkerVariablesPlugin plugin, GroundMarkerVariablesConfig config,
		ItemManager itemManager, SpriteManager spriteManager, ImportPreviewManager importPreviewManager, PingedTileManager pingedTileManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		this.spriteManager = spriteManager;
		this.importPreviewManager = importPreviewManager;
		this.pingedTileManager = pingedTileManager;
	}

	// Called by both wrapper overlays, once per pass, so other overlays can draw between tiles
	// and labels. Both passes walk the same cached marker lists.
	void renderMarkers(Graphics2D graphics, boolean labels)
	{
		if (client.getLocalPlayer() == null)
		{
			return;
		}

		// Built once per frame, same as the core overlay, rather than once per tile.
		Stroke borderStroke = new BasicStroke((float) config.borderWidth());

		Instant now = Instant.now();
		for (WorldView wv : plugin.getTrackedWorldViews())
		{
			for (TranslatedMarker translated : plugin.getTranslatedMarkers(wv))
			{
				drawTileAt(graphics, wv, translated.worldPoint, translated.marker, borderStroke, 1f, labels);
			}

			for (TranslatedPingedTile translated : pingedTileManager.getTranslatedPingedTiles(wv))
			{
				float opacity = translated.tile.opacity(now);
				if (opacity > 0f)
				{
					drawTileAt(graphics, wv, translated.worldPoint, translated.tile.marker, borderStroke, opacity, labels);
				}
			}

			for (TranslatedMarker translated : importPreviewManager.getPreviewMarkers(wv))
			{
				drawTileAt(graphics, wv, translated.worldPoint, translated.marker, borderStroke, PREVIEW_OPACITY, labels);
			}
		}
	}

	private void drawTileAt(Graphics2D graphics, WorldView wv, WorldPoint worldPoint, CachedMarker marker, Stroke borderStroke, float opacity, boolean labels)
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

		MetaState metaState = marker.getMetaState();
		Color baseColor = marker.source.getColor() != null ? marker.source.getColor() : config.markerColor();

		// No tileOpacity override -- keep the tile's own saved alpha, not fully opaque.
		Color color = metaState.getTileColor() != null
			? ColorUtil.colorWithAlpha(metaState.getTileColor(), baseColor.getAlpha()) : baseColor;

		// tileOpacity only ever affects the outline below, never the fill or label text.
		Color outlineColor = metaState.getTileOpacity() != null
			? ColorUtil.colorWithAlpha(color, Math.round(metaState.getTileOpacity() * 255)) : color;
		Color fadedColor = opacity >= 1f ? outlineColor : ColorUtil.colorWithAlpha(outlineColor, (int) (outlineColor.getAlpha() * opacity));

		if (!labels)
		{
			Polygon poly = Perspective.getCanvasTilePoly(client, localPoint);
			if (poly != null)
			{
				// Fill is a flat black overlay at the configured opacity, not the marker's own color
				// at reduced alpha — matches the core overlay's own drawTile() exactly.
				Color fill = new Color(0, 0, 0, (int) (config.fillOpacity() * opacity));
				Stroke stroke = metaState.getBorderWidth() != null ? new BasicStroke(metaState.getBorderWidth()) : borderStroke;
				OverlayUtil.renderPolygon(graphics, poly, fadedColor, fill, stroke);
				drawHighlights(graphics, poly, metaState.getHighlights(), stroke, opacity);
			}
			return;
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
			for (int i = 0; i < lines.length; i++)
			{
				lines[i] = lines[i].trim();
			}

			// A Composite fades every pixel actually drawn -- needed since TextComponent's
			// shadow and <col=> spans both hardcode full opacity regardless of our own color.
			Composite originalComposite = graphics.getComposite();
			if (opacity < 1f)
			{
				graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
			}

			drawLines(graphics, localPoint, lines, color, metaState.getLineAlignments());

			graphics.setComposite(originalComposite);
		}
	}

	// poly's vertex order is SW, NW, NE, SE, matching HighlightCorner's ordinals.
	private static void drawHighlights(Graphics2D graphics, Polygon poly, Map<HighlightCorner, Color> highlights, Stroke stroke, float opacity)
	{
		if (highlights.isEmpty())
		{
			return;
		}

		graphics.setStroke(stroke);
		for (Map.Entry<HighlightCorner, Color> entry : highlights.entrySet())
		{
			int i = entry.getKey().ordinal();
			Color color = entry.getValue();
			graphics.setColor(opacity >= 1f ? color : ColorUtil.colorWithAlpha(color, (int) (color.getAlpha() * opacity)));

			drawCornerEdge(graphics, poly, i, (i + 1) % 4);
			drawCornerEdge(graphics, poly, i, (i + 3) % 4);
		}
	}

	// Draws 1/4 of the edge from poly[from] toward poly[to].
	private static void drawCornerEdge(Graphics2D graphics, Polygon poly, int from, int to)
	{
		int x1 = poly.xpoints[from];
		int y1 = poly.ypoints[from];
		int x2 = x1 + (poly.xpoints[to] - x1) / 4;
		int y2 = y1 + (poly.ypoints[to] - y1) / 4;
		graphics.drawLine(x1, y1, x2, y2);
	}

	// One line per row, stacked as a block centered on localPoint (not anchored at the first
	// line). Each line's own X then depends on its alignment -- center (the default) centers
	// it on its own width like before, left/right hug the widest line's own edge instead, but
	// only among the contiguous run of lines sharing that same alignment (see blockMaxWidths).
	private void drawLines(Graphics2D graphics, LocalPoint localPoint, String[] lines, Color color, List<TextAlign> alignments)
	{
		int lineHeight = graphics.getFontMetrics().getHeight();
		int startY = -((lines.length - 1) * lineHeight) / 2;

		// {b}/{i} carry across lines until closed.
		List<LineLayout> layouts = new ArrayList<>();
		int style = Font.PLAIN;
		for (String line : lines)
		{
			LineLayout layout = layoutLine(graphics, line, style);
			layouts.add(layout);
			style = layout.endStyle;
		}

		int[] blockMaxWidths = blockMaxWidths(layouts, alignments);

		for (int i = 0; i < lines.length; i++)
		{
			drawLine(graphics, localPoint, layouts.get(i), blockMaxWidths[i], lineAlign(alignments, i), color, startY + i * lineHeight);
		}
	}

	// For each line, the max width among the contiguous run of lines sharing its own
	// alignment -- not the whole label, so separate {align} blocks don't affect each other.
	private static int[] blockMaxWidths(List<LineLayout> layouts, List<TextAlign> alignments)
	{
		int n = layouts.size();
		int[] result = new int[n];

		int i = 0;
		while (i < n)
		{
			TextAlign align = lineAlign(alignments, i);
			int max = 0;
			int j = i;
			while (j < n && lineAlign(alignments, j) == align)
			{
				max = Math.max(max, layouts.get(j).width);
				j++;
			}
			for (int k = i; k < j; k++)
			{
				result[k] = max;
			}
			i = j;
		}

		return result;
	}

	private static TextAlign lineAlign(List<TextAlign> alignments, int index)
	{
		return index < alignments.size() ? alignments.get(index) : TextAlign.CENTER;
	}

	// A line's text runs, <item=ID> icon images and {b}/{i} toggles, pre-measured once so both
	// the per-line width (for maxWidth) and the actual draw pass can reuse the same icon lookups.
	private static final class LineLayout
	{
		private final List<Object> segments;
		private final List<BufferedImage> images;
		private final List<Integer> iconWidths;
		private final int width;
		// Font style bits in effect at the start/end of this line.
		private final int startStyle;
		private final int endStyle;

		private LineLayout(List<Object> segments, List<BufferedImage> images, List<Integer> iconWidths, int width, int startStyle, int endStyle)
		{
			this.segments = segments;
			this.images = images;
			this.iconWidths = iconWidths;
			this.width = width;
			this.startStyle = startStyle;
			this.endStyle = endStyle;
		}
	}

	// {b}/{/b}/{i}/{/i} -- switches one Font style bit on or off from here onward.
	private static final class StyleToggle
	{
		private final int bit;
		private final boolean on;

		private StyleToggle(int bit, boolean on)
		{
			this.bit = bit;
			this.on = on;
		}

		private int apply(int style)
		{
			return on ? style | bit : style & ~bit;
		}
	}

	private LineLayout layoutLine(Graphics2D graphics, String line, int startStyle)
	{
		List<Object> segments = splitSegments(line);
		FontMetrics fontMetrics = graphics.getFontMetrics();

		// Icons are scaled to font height (aspect preserved), not drawn at native sprite size.
		int iconHeight = lineHeight(fontMetrics);

		int style = startStyle;
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
				continue;
			}

			if (segment instanceof BufferedImage)
			{
				BufferedImage flag = scaledFlag((BufferedImage) segment, iconHeight);
				images.add(flag);
				iconWidths.add(flag.getWidth());
				totalWidth += flag.getWidth();
				continue;
			}

			images.add(null);
			iconWidths.add(0);
			if (segment instanceof StyleToggle)
			{
				style = ((StyleToggle) segment).apply(style);
			}
			else
			{
				Font font = styledFont(graphics.getFont(), style);
				totalWidth += graphics.getFontMetrics(font).stringWidth(COLOR_TAG_PATTERN.matcher((String) segment).replaceAll(""));
			}
		}

		return new LineLayout(segments, images, iconWidths, totalWidth, startStyle, style);
	}

	// Native size, or smoothly scaled down to the text height when that's shorter.
	private BufferedImage scaledFlag(BufferedImage flag, int textHeight)
	{
		if (textHeight >= flag.getHeight())
		{
			return flag;
		}

		BufferedImage scaled = scaledFlags.get(flag);
		if (scaled == null || scaled.getHeight() != textHeight)
		{
			int width = Math.round(flag.getWidth() * textHeight / (float) flag.getHeight());
			scaled = ImageUtil.resizeImage(flag, width, textHeight);
			scaledFlags.put(flag, scaled);
		}
		return scaled;
	}

	private static Font styledFont(Font base, int style)
	{
		return style == Font.PLAIN ? base : base.deriveFont(base.getStyle() | style);
	}

	// center: same tile-anchored canvas point plain text lines have always centered against.
	// left/right instead hug maxWidth's own edge, so every line lines up along a shared margin.
	private static int lineX(int anchorX, TextAlign align, int lineWidth, int maxWidth)
	{
		switch (align)
		{
			case LEFT:
				return anchorX - maxWidth / 2;
			case RIGHT:
				return anchorX + maxWidth / 2 - lineWidth;
			default:
				return anchorX - lineWidth / 2;
		}
	}

	private void drawLine(Graphics2D graphics, LocalPoint localPoint, LineLayout layout, int maxWidth, TextAlign align, Color color, int yOffset)
	{
		Point anchor = Perspective.getCanvasTextLocation(client, graphics, localPoint, "", 0);
		if (anchor == null)
		{
			return;
		}

		FontMetrics fontMetrics = graphics.getFontMetrics();
		int x = lineX(anchor.getX(), align, layout.width, maxWidth);
		int y = anchor.getY() + yOffset;

		// Full opacity regardless of the marker's own (possibly transparent) color -- the
		// caller's Composite (see drawTileAt) is what applies any fade, not this.
		Color textColor = ColorUtil.colorWithAlpha(color, 0xFF);
		int style = layout.startStyle;

		for (int i = 0; i < layout.segments.size(); i++)
		{
			Object segment = layout.segments.get(i);
			if (segment instanceof StyleToggle)
			{
				style = ((StyleToggle) segment).apply(style);
			}
			else if (segment instanceof BufferedImage)
			{
				BufferedImage flag = layout.images.get(i);
				int flagY = y - fontMetrics.getAscent() + (lineHeight(fontMetrics) - flag.getHeight()) / 2;
				graphics.drawImage(flag, x, flagY, null);
				x += flag.getWidth();
			}
			else if (segment instanceof Integer)
			{
				BufferedImage image = layout.images.get(i);
				int iconWidth = layout.iconWidths.get(i);
				if (image != null)
				{
					int iconHeight = lineHeight(fontMetrics);
					int imageY = y - fontMetrics.getAscent();
					graphics.drawImage(image, x, imageY, iconWidth, iconHeight, null);
					x += iconWidth;
				}
			}
			else
			{
				String text = (String) segment;
				Font font = styledFont(graphics.getFont(), style);
				TextComponent textComponent = new TextComponent();
				textComponent.setText(text);
				textComponent.setFont(font);
				textComponent.setColor(textColor);
				textComponent.setPosition(x, y);
				textComponent.render(graphics);
				x += graphics.getFontMetrics(font).stringWidth(COLOR_TAG_PATTERN.matcher(text).replaceAll(""));

				// An open <col=> carries past a toggle into the next run instead of resetting.
				Matcher colorTag = COLOR_TAG_PATTERN.matcher(text);
				while (colorTag.find())
				{
					textColor = Color.decode("#" + colorTag.group(1));
				}
			}
		}
	}

	private static int lineHeight(FontMetrics fontMetrics)
	{
		return fontMetrics.getAscent() + fontMetrics.getDescent();
	}

	// Splits a line into text (String), item ids (Integer), flags (BufferedImage) and StyleToggles.
	private List<Object> splitSegments(String line)
	{
		List<Object> segments = new ArrayList<>();
		Matcher matcher = INLINE_TAG_PATTERN.matcher(line);
		int last = 0;
		while (matcher.find())
		{
			if (matcher.start() > last)
			{
				segments.add(line.substring(last, matcher.start()));
			}

			if (matcher.group(1) != null)
			{
				segments.add(Integer.valueOf(matcher.group(1)));
			}
			else if (matcher.group(4) != null)
			{
				BufferedImage flag = spriteManager.getSprite(FLAG_SPRITES.get(matcher.group(4).toLowerCase(Locale.ROOT)), 0);
				if (flag != null)
				{
					segments.add(flag);
				}
			}
			else
			{
				segments.add(new StyleToggle("b".equalsIgnoreCase(matcher.group(3)) ? Font.BOLD : Font.ITALIC, matcher.group(2).isEmpty()));
			}
			last = matcher.end();
		}
		if (last < line.length())
		{
			segments.add(line.substring(last));
		}
		return segments;
	}
}
