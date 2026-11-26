package com.groundmarkervariables;

import com.google.common.cache.CacheBuilder;
import com.groundmarkervariables.graphics.GroundImageRenderer;
import com.groundmarkervariables.graphics.GroundModelRenderer;
import com.groundmarkervariables.graphics.ItemModels;
import com.groundmarkervariables.graphics.LoadingImages;
import com.groundmarkervariables.graphics.NpcImages;
import com.groundmarkervariables.graphics.SplitIcons;
import com.groundmarkervariables.markers.CachedMarker;
import com.groundmarkervariables.markers.MarkerCulling;
import com.groundmarkervariables.markers.RenderedTiles;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.groundmarkervariables.variables.meta.HighlightCorner;
import com.groundmarkervariables.variables.meta.MetaState;
import com.groundmarkervariables.variables.meta.TextAlign;
import com.groundmarkervariables.variables.support.Emojis;
import com.groundmarkervariables.variables.support.NamedColors;
import net.runelite.api.Client;
import net.runelite.api.Model;
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
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;
import lombok.RequiredArgsConstructor;

// Shared tile/label drawing code behind GroundMarkerVariablesTileOverlay and
// GroundMarkerVariablesLabelOverlay -- not an Overlay itself.
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class GroundMarkerVariablesOverlay {
    static final int MAX_DRAW_DISTANCE = 32;
    // Matches TextComponent's own tag format, so a label can color individual runs of text
    // (e.g. "<col=ff0000>Danger</col>") without affecting the tile's own fill/outline color.
    private static final Pattern COLOR_TAG_PATTERN = Pattern.compile("<col=([0-9a-fA-F]{2,6})>");
    // <item=ID[@N%]> icon, {b}/{/b}/{i}/{/i} toggle, <flag=region> icon, <emoji=codepoint[@tint hex]> icon, or
    // <sprite=ID[,frame][@N%]> game sprite. @N% draws the icon at N% brightness. \t[@Npx] is a table cell
    // break, with N px before the next column. {tip=text}...{/tip} gives what's between a tooltip.
    // {split}...{/split} draws its icons as one, each cropped to its half (2 icons), pie third (3) or quadrant.
    // <badge[=N]> draws a dot over the previous icon's corner, or with =N a pie of N% left.
    // <npc=ID[,head]> is the NPC's model, or its chathead, drawn from the game's own model data.
    private static final Pattern INLINE_TAG_PATTERN = Pattern.compile(
        "<item=(\\d+)(?:@(\\d{1,3})%)?>|\\{(/?)([bi])\\}|<flag=(us_west|us_east|us|uk|aus|ger|br|jp|sg|za)>|<emoji=([0-9a-f]+)(?:@([0-9a-f]{6}))?>"
            + "|<sprite=(\\d+)(?:,(\\d+))?(?:@(\\d{1,3})%)?>|\\\\t(?:@(\\d{1,3})px)?|\\{tip=([^{}]*)\\}|\\{/tip\\}|\\{(/?)split\\}"
            + "|<badge(?:=(\\d{1,3}))?>|<npc=(\\d+)(,head)?>",
        Pattern.CASE_INSENSITIVE);
    // <badge>: its width as a share of the icon's, and its colors.
    private static final float BADGE_SIZE = 0.45f;
    private static final Color BADGE_BACKGROUND = new Color(0, 0, 0, 150);
    private static final Color BADGE_GREEN = new Color(0, 185, 0);
    private static final Color BADGE_YELLOW = new Color(255, 210, 0);
    private static final Color BADGE_RED = new Color(230, 0, 0);
    // {ground} icon rows are built at this multiple of the tallest icon.
    private static final int GROUND_ROW_SCALE = 4;
    // {ground} text: local units per text pixel at most, the tile share it may take below icons, the gap to them
    // (local units), and how many text images are kept.
    private static final double GROUND_TEXT_SCALE = 2.5;
    private static final double GROUND_TEXT_MAX_SHARE = 0.5;
    private static final double GROUND_TEXT_GAP = 4;
    private static final int MAX_GROUND_TEXTS = 64;
    // With Parallax icons, flat {ground} images' height over the ground, and their shadow's under it, in local units.
    private static final int GROUND_LIFT = 3;
    private static final int GROUND_SHADOW_DROP = 6;
    // Bounds on the screen px per text px a {ground} text image is rendered at.
    private static final double MIN_GROUND_TEXT_RESOLUTION = 0.25;
    private static final double MAX_GROUND_TEXT_RESOLUTION = 16;
    private static final int DEFAULT_CELL_PADDING = 4;
    // Space around a hovered label's text, inside its background.
    private static final int HOVER_MARGIN = 3;
    private static final int FULL_BRIGHTNESS = 100;
    // drawGlow: radius relative to half the icon, and its opacity from the center out.
    private static final float GLOW_SCALE = 1.0f;
    private static final float[] GLOW_FRACTIONS = {0f, 0.5f, 1f};
    private static final Color[] GLOW_COLORS = {new Color(128, 128, 128, 80), new Color(128, 128, 128, 70), new Color(128, 128, 128, 0)};
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
    private final GroundMarkerVariablesConfig config;
    private final ItemManager itemManager;
    private final SpriteManager spriteManager;
    private final Emojis emojis;
    private final RenderedTiles renderedTiles;

    private final TooltipManager tooltipManager;
    private final MarkerCulling markerCulling;

    // This frame's mouse position, and the {hover} label under it (see renderMarkers).
    @Nullable
    private Point mouse;
    @Nullable
    private PendingLabel hoveredLabel;
    // The label hovered last frame.
    @Nullable
    private CachedMarker lastHoveredMarker;
    @Nullable
    private WorldPoint lastHoveredPoint;
    // The item under the mouse in a {hover} label this frame, or -1, and whether the label being drawn has {hover}.
    private int tipItemId = -1;
    private boolean itemTips;
    // The {tip=} text under the mouse this frame, and the one open while drawing.
    @Nullable
    private String tipText;
    @Nullable
    private String openTip;

    // Keyed by the (already cached) flag, emoji, sprite or item image. Weak keys, by identity.
    private final Map<BufferedImage, BufferedImage> scaledIcons = CacheBuilder.newBuilder().weakKeys().<BufferedImage, BufferedImage>build().asMap();
    // Keyed by the original image, then brightness. Weak keys, by identity.
    private final Map<BufferedImage, Map<Integer, BufferedImage>> dimmedIcons =
        CacheBuilder.newBuilder().weakKeys().<BufferedImage, Map<Integer, BufferedImage>>build().asMap();
    // {split} icons, keyed by their parts (BufferedImage equality is identity).
    private final Map<List<BufferedImage>, BufferedImage> splitIcons = new HashMap<>();
    // {ground} icon rows, keyed the same way.
    private final Map<List<BufferedImage>, BufferedImage> groundRows = new HashMap<>();
    // {ground} text images, keyed by font, color and the {ground} label; least recently used dropped.
    private final Map<BufferedImage, BufferedImage> silhouettes = new WeakHashMap<>();
    private final Map<String, BufferedImage> groundTexts = new LinkedHashMap<String, BufferedImage>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
            return size() > MAX_GROUND_TEXTS;
        }
    };
    // Emoji and NPC images (cached by Emojis/NpcImages), drawn smooth on the ground rather than as pixel blocks.
    private final Set<BufferedImage> smoothImages = Collections.newSetFromMap(new IdentityHashMap<>());
    private final NpcImages npcImages;
    private final GroundModelRenderer groundModelRenderer;
    private final ItemModels itemModels;
    private final GroundImageRenderer groundImageRenderer;

    // Called by both wrapper overlays, once per pass, so other overlays can draw between tiles
    // and labels. Both passes walk the same cached marker lists.
    public void renderMarkers(Graphics2D graphics, boolean labels) {
        if (client.getLocalPlayer() == null) {
            return;
        }

        // Built once per frame, same as the core overlay, rather than once per tile.
        Stroke borderStroke = new BasicStroke((float) config.borderWidth());

        // Parallax icons off: free the {ground} models, on the thread that draws them.
        if (!labels && !config.parallaxIcons()) {
            groundModelRenderer.clear();
            itemModels.clear();
        }

        Instant now = Instant.now();
        mouse = labels ? client.getMouseCanvasPosition() : null;
        hoveredLabel = null;
        tipItemId = -1;
        tipText = null;
        // One marker per tile, across saved, group, set and preview markers (see RenderedTiles).
        renderedTiles.forEachShown(now, (wv, worldPoint, marker, opacity) -> drawTileAt(graphics, wv, worldPoint, marker, borderStroke, opacity, labels));

        if (labels) {
            lastHoveredMarker = hoveredLabel == null ? null : hoveredLabel.marker;
            lastHoveredPoint = hoveredLabel == null ? null : hoveredLabel.worldPoint;
        }
        if (hoveredLabel != null) {
            drawPending(graphics, hoveredLabel, true);
            hoveredLabel = null;
        }

        if (tipText != null) {
            tooltipManager.add(new Tooltip(tipText));
        } else if (tipItemId > 0) {
            showItemTooltip(tipItemId);
        }
    }

    // The item's name, of its canonical (unnoted, base) form.
    private void showItemTooltip(int itemId) {
        tooltipManager.add(new Tooltip(itemManager.getItemComposition(itemManager.canonicalize(itemId)).getName()));
    }

    private void drawTileAt(Graphics2D graphics, WorldView wv, WorldPoint worldPoint, CachedMarker marker, Stroke borderStroke, float opacity, boolean labels) {
        LocalPoint localPoint = worldPoint.getPlane() != wv.getPlane() || !markerCulling.inRange(worldPoint, MAX_DRAW_DISTANCE) ? null
            : LocalPoint.fromWorld(wv, worldPoint);
        if (localPoint == null) {
            return;
        }

        MetaState metaState = marker.getMetaState();
        Color color = marker.tileColor(config.markerColor());

        // tileOpacity only ever affects the outline below, never the fill or label text.
        Color outlineColor = metaState.getTileOpacity() != null
            ? ColorUtil.colorWithAlpha(color, Math.round(metaState.getTileOpacity() * 255)) : color;
        Color fadedColor = opacity >= 1f ? outlineColor : ColorUtil.colorWithAlpha(outlineColor, (int) (outlineColor.getAlpha() * opacity));

        if (!labels) {
            Polygon poly = Perspective.getCanvasTilePoly(client, localPoint);
            if (poly != null) {
                // Fill is a flat black overlay at the configured opacity, not the marker's own color
                // at reduced alpha — matches the core overlay's own drawTile() exactly.
                Color fill = new Color(0, 0, 0, (int) (config.fillOpacity() * opacity));
                Stroke stroke = metaState.getBorderWidth() != null ? new BasicStroke(metaState.getBorderWidth()) : borderStroke;
                OverlayUtil.renderPolygon(graphics, poly, fadedColor, fill, stroke);
                drawHighlights(graphics, poly, metaState.getHighlights(), stroke, opacity);
            }
            // {ground} is part of the tile, under every label.
            if (metaState.getGroundLabel() != null) {
                drawGround(graphics, localPoint, worldPoint.getPlane(), metaState.getGroundLabel(), metaState.getGroundFacing(), color, opacity);
            }
            return;
        }

        // Label rendering doesn't depend on the tile poly resolving — a missing poly only
        // means we can't draw an outline, not that the label's canvas location is unavailable.
        // Empty but for {hoverOnly} content still needs laying out, to be hovered.
        String label = marker.getResolvedLabel();
        String hoverLabel = metaState.getHoverLabel();
        if ((label != null && !label.isEmpty()) || hoverLabel != null) {
            LabelLayout layout = layoutLabel(graphics, localPoint, labelLines(label == null ? "" : label, color), metaState.getLineAlignments());
            if (layout == null) {
                return;
            }

            LabelLayout hoverLayout = hoverLabel == null ? null
                : layoutLabel(graphics, localPoint, labelLines(hoverLabel, color), metaState.getHoverLineAlignments());

            // Once hovered, a label's {hoverOnly} content counts as part of it, so moving onto that keeps it hovered.
            boolean wasHovered = marker == lastHoveredMarker && worldPoint.equals(lastHoveredPoint);
            Rectangle bounds = wasHovered && hoverLayout != null ? hoverLayout.bounds : layout.bounds;
            boolean hoverable = metaState.getHoverBackground() != null || hoverLayout != null;

            // The last hoverable label under the mouse is held back, to draw above the rest.
            PendingLabel pending = new PendingLabel(layout, hoverLayout, color, opacity, metaState.getHoverBackground(), marker, worldPoint);
            if (hoverable && mouse != null && (bounds.contains(mouse.getX(), mouse.getY()) || isOverTile(localPoint))) {
                if (hoveredLabel != null) {
                    drawPending(graphics, hoveredLabel, false);
                }
                hoveredLabel = pending;
            } else {
                drawPending(graphics, pending, false);
            }
        }
    }

    // The {ground} blocks flat on the tile: their icons in a row (a {split} is one icon, a lone <item=>/<npc=> its model),
    // and their text below the icons, over a model's part under the ground but under its part over it. Their top faces
    // facing (degrees clockwise from north).
    private void drawGround(Graphics2D graphics, LocalPoint localPoint, int plane, @Nullable String label, int facing, Color color,
        float opacity) {
        if (label == null || label.isEmpty()) {
            return;
        }

        List<Object> segments = new ArrayList<>();
        List<List<Object>> textLines = new ArrayList<>();
        for (String line : labelLines(label, color)) {
            List<Object> text = new ArrayList<>();
            boolean visible = false;
            for (Object segment : splitSegments(line)) {
                if (isIcon(segment)) {
                    segments.add(segment);
                } else if (!(segment instanceof TipToggle) && !(segment instanceof Badge)) {
                    text.add(segment);
                    visible |= segment instanceof String && !COLOR_TAG_PATTERN.matcher((String) segment).replaceAll("").trim().isEmpty();
                }
            }
            if (visible) {
                textLines.add(text);
            }
        }

        // Text alone fills the tile. With icons it runs along the bottom edge, shrunk to fit and to leave the icons at
        // least GROUND_TEXT_MAX_SHARE; icons above it. Turned diagonally, the square shrinks to stay inside the tile, and
        // text alone shrinks to its own turned extent.
        boolean diagonal = facing % 90 != 0;
        double size = GroundImageRenderer.SIZE / (diagonal && !segments.isEmpty() ? Math.sqrt(2) : 1);
        BufferedImage text = textLines.isEmpty() ? null : groundText(graphics, textLines, color, label, 1);
        double textScale = text == null ? 0 : segments.isEmpty()
            ? Math.min(Math.min(size / text.getWidth(), size / text.getHeight()),
                diagonal ? size * Math.sqrt(2) / (text.getWidth() + text.getHeight()) : Double.MAX_VALUE)
            : Math.min(GROUND_TEXT_SCALE, Math.min(size / text.getWidth(), size * GROUND_TEXT_MAX_SHARE / text.getHeight()));
        double textHeight = text == null ? 0 : text.getHeight() * textScale;
        double iconSize = text == null ? size : size - textHeight - GROUND_TEXT_GAP;
        double iconNorth = (size - iconSize) / 2;
        Runnable drawText = text == null ? () -> {} : () -> drawGroundText(graphics, localPoint, plane, textLines, color, label, opacity,
            facing, textScale, segments.isEmpty() ? 0 : -(size - textHeight) / 2, text.getWidth() * textScale, textHeight);

        // Parallax icons off: the flat image, far cheaper.
        if (segments.size() == 1 && config.parallaxIcons() && drawGroundModel(graphics, localPoint, plane, segments.get(0), opacity,
            facing, iconSize / GroundImageRenderer.SIZE, iconNorth, drawText)) {
            return;
        }

        List<BufferedImage> icons = new ArrayList<>();
        for (Object segment : segments) {
            // Lifted, a symbol's shadow is the row's.
            BufferedImage image = iconImage(segment, !config.parallaxIcons());
            if (image != null) {
                icons.add(image);
            }
        }

        if (!icons.isEmpty()) {
            // A row already holds each icon at its own sampling, so it's drawn smooth.
            BufferedImage image = icons.size() == 1 ? icons.get(0) : groundRow(icons);
            drawLifted(graphics, localPoint, plane, image, config.parallaxIcons(), 0,
                icons.size() > 1 || smoothImages.contains(image), opacity, facing, iconNorth, size, iconSize);
        }
        drawText.run();
    }

    // image flat on the tile, with shadow over a black copy of it moved shift image px right and down. With Parallax
    // icons the shadow is just under the ground, seen through the tile only (clipped to it), and the image just over it,
    // so they part with the camera angle. The image is rendered once and the shadow made from it.
    private void drawLifted(Graphics2D graphics, LocalPoint localPoint, int plane, BufferedImage image, boolean shadow, int shift,
        boolean smooth, float opacity, int facing, double offsetNorth, double width, double height) {
        boolean parallax = config.parallaxIcons();
        int lift = parallax ? GROUND_LIFT : 0;
        Polygon tile = shadow && parallax ? Perspective.getCanvasTilePoly(client, localPoint) : null;
        if (shadow && (!parallax || tile != null)) {
            groundImageRenderer.drawWithShadow(graphics, localPoint, plane, image, smooth, opacity, facing, offsetNorth, width, height, lift,
                parallax ? -GROUND_SHADOW_DROP : 0, shift, tile);
            return;
        }
        groundImageRenderer.draw(graphics, localPoint, plane, image, smooth, opacity, facing, offsetNorth, width, height, lift);
    }

    // The text in a width x height box offsetNorth north of the tile's center, textScale local units per text px. Drawn
    // from an image rendered at about its size on screen, so it stays sharp at any zoom.
    private void drawGroundText(Graphics2D graphics, LocalPoint localPoint, int plane, List<List<Object>> lines, Color color, String label,
        float opacity, int facing, double textScale, double offsetNorth, double width, double height) {
        double perUnit = groundImageRenderer.pixelsPerUnit(localPoint, plane, facing, offsetNorth, width, height);
        // Screen px per text px, in quarter-octave steps so zooming reuses a few images.
        double resolution = perUnit <= 0 ? 1 : perUnit * textScale;
        resolution = Math.pow(2, Math.round(Math.log(resolution) / Math.log(2) * 4) / 4.0);
        resolution = Math.max(MIN_GROUND_TEXT_RESOLUTION, Math.min(MAX_GROUND_TEXT_RESOLUTION, resolution));
        BufferedImage image = groundText(graphics, lines, color, label, resolution);
        if (image != null) {
            // The shadow one text px right and down, as label text's is.
            drawLifted(graphics, localPoint, plane, image, true, groundTextShadowOffset(resolution), true, opacity, facing, offsetNorth,
                width, height);
        }
    }

    // One text px, in a ground text image's px.
    private static int groundTextShadowOffset(double resolution) {
        return Math.max(1, (int) Math.round(resolution));
    }

    // The text lines as labels draw them (colors, {b}/{i}, tables) but without a shadow, centered on a transparent image,
    // resolution image px per text px, with room right and below for the shadow drawn from it. Cached by font, color and
    // label (which the lines come from), and resolution.
    @Nullable
    private BufferedImage groundText(Graphics2D graphics, List<List<Object>> lines, Color color, String label, double resolution) {
        Font font = graphics.getFont();
        String key = font + "|" + color.getRGB() + "|" + resolution + "|" + label;
        BufferedImage image = groundTexts.get(key);
        if (image != null) {
            return image;
        }

        int[] widths = new int[lines.size()];
        int[][] columnOffsets = new int[lines.size()][];
        List<LineLayout> layouts = layoutLines(graphics, lines, widths, columnOffsets);
        int maxWidth = Arrays.stream(widths).max().orElse(0);
        if (maxWidth == 0) {
            return null;
        }

        // 1px slack each side for glyphs scaled a little wider, and 1px below and right as before (cropped off).
        FontMetrics fontMetrics = graphics.getFontMetrics();
        int lineHeight = fontMetrics.getHeight();
        int width = maxWidth + 3;
        int height = (layouts.size() - 1) * lineHeight + lineHeight(fontMetrics) + 1;
        image = new BufferedImage((int) Math.ceil(width * resolution), (int) Math.ceil(height * resolution), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHints(graphics.getRenderingHints());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.scale(resolution, resolution);
        g.setFont(font);
        Point anchor = new Point(maxWidth / 2 + 1, fontMetrics.getAscent());
        for (int i = 0; i < layouts.size(); i++) {
            drawLine(g, anchor, layouts.get(i), widths[i], columnOffsets[i], maxWidth, TextAlign.CENTER, color, i * lineHeight, false);
        }
        g.dispose();
        // Fitted by its glyphs, not its line spacing and padding.
        BufferedImage glyphs = cropped(image);
        if (glyphs == null) {
            return null;
        }

        // Grown by the shadow's offset (1 text px), so the text keeps its place and size as before.
        int offset = groundTextShadowOffset(resolution);
        image = new BufferedImage(glyphs.getWidth() + offset, glyphs.getHeight() + offset, BufferedImage.TYPE_INT_ARGB);
        Graphics2D textGraphics = image.createGraphics();
        textGraphics.drawImage(glyphs, 0, 0, null);
        textGraphics.dispose();
        groundTexts.put(key, image);
        return image;
    }

    // image cut to its drawn pixels, or null if it has none.
    @Nullable
    private static BufferedImage cropped(BufferedImage image) {
        int width = image.getWidth(), height = image.getHeight();
        int minX = width, minY = height, maxX = -1, maxY = -1;
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            image.getRGB(0, y, width, 1, row, 0, width);
            for (int x = 0; x < width; x++) {
                if (row[x] >>> 24 != 0) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = y;
                }
            }
        }
        return maxX < 0 ? null : image.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    // The lone icon's model, if it's an item or NPC whose model loads, scale times its size and offsetNorth north of
    // the tile's center, top toward facing; onGround runs between its parts under and over the ground.
    private boolean drawGroundModel(Graphics2D graphics, LocalPoint localPoint, int plane, Object icon, float opacity,
        int facing, double scale, double offsetNorth, Runnable onGround) {
        if (icon instanceof ItemIcon) {
            ItemModels.Oriented item = itemModels.model(itemManager.canonicalize(((ItemIcon) icon).id));
            if (item == null) {
                return false;
            }
            groundModelRenderer.draw(graphics, localPoint, plane, item.model, item.x, item.y, item.z, opacity, facing, scale, offsetNorth,
                onGround);
            return true;
        }

        if (icon instanceof NpcIcon) {
            NpcIcon npc = (NpcIcon) icon;
            Model model = npcImages.model(npc.id, npc.head);
            // Turned as its image.
            float[][] turned = npcImages.turned(npc.id, npc.head);
            if (model != null && turned != null) {
                groundModelRenderer.draw(graphics, localPoint, plane, model, turned[0], turned[1], turned[2], opacity, facing, scale,
                    offsetNorth, onGround);
                return true;
            }
        }
        return false;
    }

    // Icons side by side, each scaled to the tallest's height, with a small gap.
    private BufferedImage groundRow(List<BufferedImage> icons) {
        BufferedImage row = groundRows.get(icons);
        if (row != null) {
            return row;
        }

        // Built at 4x the tallest icon; each icon blown up by a whole factor first, so mixed sizes keep even pixels.
        int height = 0;
        for (BufferedImage icon : icons) {
            height = Math.max(height, icon.getHeight() * GROUND_ROW_SCALE);
        }
        int gap = Math.max(1, height / 8);
        int width = -gap;
        for (BufferedImage icon : icons) {
            width += icon.getWidth() * height / icon.getHeight() + gap;
        }

        row = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = row.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int x = 0;
        for (BufferedImage icon : icons) {
            int iconWidth = icon.getWidth() * height / icon.getHeight();
            // Emoji smooth, items and sprites as even pixel blocks.
            BufferedImage source = smoothImages.contains(icon) ? icon : GroundImageRenderer.upscaled(icon, (int) Math.ceil(height / (double) icon.getHeight()));
            graphics.drawImage(source, x, 0, iconWidth, height, null);
            x += iconWidth + gap;
            // An item image still loading is drawn into later.
            LoadingImages.whenLoaded(icon, () -> groundRows.remove(icons));
        }
        graphics.dispose();
        groundRows.put(icons, row);
        return row;
    }

    private boolean isOverTile(LocalPoint localPoint) {
        Polygon poly = Perspective.getCanvasTilePoly(client, localPoint);
        return poly != null && poly.contains(mouse.getX(), mouse.getY());
    }

    private static String[] labelLines(String label, Color color) {
        // </col> reverts to the tile's own color -- TextComponent has no real concept of a
        // closing tag, so this just expands to another <col=> for the tile's own color.
        label = label.replace("</col>", "<col=" + ColorUtil.colorToHexCode(color) + ">");
        label = NamedColors.expandColorAliases(label);

        // Literal "\n" becomes a real line break, split into rows; "\s" a non-breaking space.
        String[] lines = label.replace("\\s", " ").replace("\\n", "\n").split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            lines[i] = lines[i].trim();
        }
        return lines;
    }

    // poly's vertex order is SW, NW, NE, SE, matching HighlightCorner's ordinals.
    private static void drawHighlights(Graphics2D graphics, Polygon poly, Map<HighlightCorner, Color> highlights, Stroke stroke, float opacity) {
        if (highlights.isEmpty()) {
            return;
        }

        graphics.setStroke(stroke);
        for (Map.Entry<HighlightCorner, Color> entry : highlights.entrySet()) {
            int i = entry.getKey().ordinal();
            Color color = entry.getValue();
            graphics.setColor(opacity >= 1f ? color : ColorUtil.colorWithAlpha(color, (int) (color.getAlpha() * opacity)));

            drawCornerEdge(graphics, poly, i, (i + 1) % 4);
            drawCornerEdge(graphics, poly, i, (i + 3) % 4);
        }
    }

    // Draws 1/4 of the edge from poly[from] toward poly[to].
    private static void drawCornerEdge(Graphics2D graphics, Polygon poly, int from, int to) {
        int x1 = poly.xpoints[from];
        int y1 = poly.ypoints[from];
        int x2 = x1 + (poly.xpoints[to] - x1) / 4;
        int y2 = y1 + (poly.ypoints[to] - y1) / 4;
        graphics.drawLine(x1, y1, x2, y2);
    }

    // A label laid out for this frame: its lines, where they go, and the screen area they cover.
    @RequiredArgsConstructor
    private static final class LabelLayout {
        private final List<LineLayout> layouts;
        private final int[] widths;
        private final int[][] columnOffsets;
        private final int[] blockMaxWidths;
        private final List<TextAlign> alignments;
        private final Point anchor;
        private final int startY;
        private final int lineHeight;
        private final Rectangle bounds;
    }

    // A label waiting to be drawn, e.g. the hovered one, drawn after all the others.
    @RequiredArgsConstructor
    private static final class PendingLabel {
        private final LabelLayout layout;
        // With its {hoverOnly} content, or null if it has none.
        @Nullable
        private final LabelLayout hoverLayout;
        private final Color color;
        private final float opacity;
        @Nullable
        private final Color hoverBackground;
        private final CachedMarker marker;
        private final WorldPoint worldPoint;
    }

    private void drawPending(Graphics2D graphics, PendingLabel label, boolean hovered) {
        // A Composite fades every pixel actually drawn -- needed since TextComponent's
        // shadow and <col=> spans both hardcode full opacity regardless of our own color.
        Composite originalComposite = graphics.getComposite();
        if (label.opacity < 1f) {
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, label.opacity));
        }

        LabelLayout layout = hovered && label.hoverLayout != null ? label.hoverLayout : label.layout;
        if (hovered && label.hoverBackground != null) {
            Rectangle bounds = layout.bounds;
            Color previous = graphics.getColor();
            graphics.setColor(label.hoverBackground);
            graphics.fillRect(bounds.x - HOVER_MARGIN, bounds.y - HOVER_MARGIN, bounds.width + HOVER_MARGIN * 2, bounds.height + HOVER_MARGIN * 2);
            graphics.setColor(previous);
        }

        itemTips = label.hoverBackground != null;
        drawLabel(graphics, layout, label.color);
        itemTips = false;
        graphics.setComposite(originalComposite);
    }

    // One line per row, stacked as a block centered on localPoint (not anchored at the first
    // line). Each line's own X then depends on its alignment -- center (the default) centers
    // it on its own width like before, left/right hug the widest line's own edge instead, but
    // only among the contiguous run of lines sharing that same alignment (see blockMaxWidths).
    // Null if localPoint isn't on screen.
    @Nullable
    private LabelLayout layoutLabel(Graphics2D graphics, LocalPoint localPoint, String[] lines, List<TextAlign> alignments) {
        Point anchor = Perspective.getCanvasTextLocation(client, graphics, localPoint, "", 0);
        if (anchor == null) {
            return null;
        }

        FontMetrics fontMetrics = graphics.getFontMetrics();
        int lineHeight = fontMetrics.getHeight();
        int startY = -((lines.length - 1) * lineHeight) / 2;

        int[] widths = new int[lines.length];
        int[][] columnOffsets = new int[lines.length][];
        List<LineLayout> layouts = layoutLines(graphics, Arrays.stream(lines).map(this::splitSegments).collect(Collectors.toList()), widths,
            columnOffsets);
        int[] blockMaxWidths = blockMaxWidths(widths, alignments);

        int left = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        for (int i = 0; i < lines.length; i++) {
            int lineStart = lineX(anchor.getX(), lineAlign(alignments, i), widths[i], blockMaxWidths[i]);
            left = Math.min(left, lineStart);
            right = Math.max(right, lineStart + widths[i]);
        }
        int top = anchor.getY() + startY - fontMetrics.getAscent();
        int bottom = anchor.getY() + startY + (lines.length - 1) * lineHeight + fontMetrics.getDescent();
        Rectangle bounds = new Rectangle(left, top, right - left, bottom - top);

        return new LabelLayout(layouts, widths, columnOffsets, blockMaxWidths, alignments, anchor, startY, lineHeight, bounds);
    }

    private void drawLabel(Graphics2D graphics, LabelLayout label, Color color) {
        openTip = null;
        for (int i = 0; i < label.layouts.size(); i++) {
            drawLine(graphics, label.anchor, label.layouts.get(i), label.widths[i], label.columnOffsets[i], label.blockMaxWidths[i],
                lineAlign(label.alignments, i), color, label.startY + i * label.lineHeight, true);
        }
    }

    // Consecutive lines with a \t form a table: each column is as wide as its widest cell, and each gap
    // the largest padding given there. Fills each line's width, and its column offsets (null if not a table).
    private static void layoutTables(List<LineLayout> layouts, int[] widths, int[][] columnOffsets) {
        int n = layouts.size();
        int i = 0;
        while (i < n) {
            if (layouts.get(i).cellWidths.size() < 2) {
                widths[i] = layouts.get(i).width;
                i++;
                continue;
            }

            int j = i;
            int columns = 0;
            while (j < n && layouts.get(j).cellWidths.size() > 1) {
                columns = Math.max(columns, layouts.get(j).cellWidths.size());
                j++;
            }

            int[] columnWidths = new int[columns];
            int[] gaps = new int[columns];
            for (int k = i; k < j; k++) {
                LineLayout layout = layouts.get(k);
                for (int c = 0; c < layout.cellWidths.size(); c++) {
                    columnWidths[c] = Math.max(columnWidths[c], layout.cellWidths.get(c));
                }
                for (int c = 0; c < layout.cellPaddings.size(); c++) {
                    gaps[c] = Math.max(gaps[c], layout.cellPaddings.get(c));
                }
            }

            int[] offsets = new int[columns];
            for (int c = 1; c < columns; c++) {
                offsets[c] = offsets[c - 1] + columnWidths[c - 1] + gaps[c - 1];
            }
            int tableWidth = offsets[columns - 1] + columnWidths[columns - 1];

            for (int k = i; k < j; k++) {
                widths[k] = tableWidth;
                columnOffsets[k] = offsets;
            }
            i = j;
        }
    }

    // For each line, the max width among the contiguous run of lines sharing its own
    // alignment -- not the whole label, so separate {align} blocks don't affect each other.
    private static int[] blockMaxWidths(int[] widths, List<TextAlign> alignments) {
        int n = widths.length;
        int[] result = new int[n];

        int i = 0;
        while (i < n) {
            TextAlign align = lineAlign(alignments, i);
            int max = 0;
            int j = i;
            while (j < n && lineAlign(alignments, j) == align) {
                max = Math.max(max, widths[j]);
                j++;
            }
            for (int k = i; k < j; k++) {
                result[k] = max;
            }
            i = j;
        }

        return result;
    }

    private static TextAlign lineAlign(List<TextAlign> alignments, int index) {
        return index < alignments.size() ? alignments.get(index) : TextAlign.CENTER;
    }

    // A line's text runs, <item=ID> icon images and {b}/{i} toggles, pre-measured once so both
    // the per-line width (for maxWidth) and the actual draw pass can reuse the same icon lookups.
    @RequiredArgsConstructor
    private static final class LineLayout {
        private final List<Object> segments;
        private final List<BufferedImage> images;
        private final List<Integer> iconWidths;
        private final int width;
        // Width of each \t-separated cell (one if no \t), and the padding after each but the last.
        private final List<Integer> cellWidths;
        private final List<Integer> cellPaddings;
        // Font style bits in effect at the start/end of this line.
        private final int startStyle;
        private final int endStyle;
    }

    // {b}/{/b}/{i}/{/i} -- switches one Font style bit on or off from here onward.
    @RequiredArgsConstructor
    private static final class StyleToggle {
        private final int bit;
        private final boolean on;

        private int apply(int style) {
            return on ? style | bit : style & ~bit;
        }
    }

    // <sprite=ID[,frame][@N%]>, kept apart from flags/emojis so only it gets drawGlow.
    @RequiredArgsConstructor
    private static final class SpriteIcon {
        private final BufferedImage image;
    }

    // <npc=ID[,head]>: its image, and what {ground} draws its model from.
    @RequiredArgsConstructor
    private static final class NpcIcon {
        private final BufferedImage image;
        private final int id;
        private final boolean head;
    }

    // A unicode symbol's <emoji=>: its white glyph, and the glyph with its shadow as drawn flat.
    @RequiredArgsConstructor
    private static final class SymbolIcon {
        private final BufferedImage glyph;
        private final BufferedImage shadowed;
    }

    // <badge[=N]> over the previous icon, percent null for a full dot.
    @RequiredArgsConstructor
    private static final class Badge {
        @Nullable
        private final Integer percent;
    }

    // {tip=text} opens a tooltip, {/tip} (null text) closes it.
    @RequiredArgsConstructor
    private static final class TipToggle {
        @Nullable
        private final String text;
    }

    // \t[@Npx]: ends a table cell, N px before the next column.
    @RequiredArgsConstructor
    private static final class CellBreak {
        private final int padding;
    }

    // <item=ID[@N%]>
    @RequiredArgsConstructor
    private static final class ItemIcon {
        private final int id;
        private final int brightness;
    }

    // Each line laid out, {b}/{i} carried across lines until closed, then its tables (filling widths and columnOffsets,
    // see layoutTables).
    private List<LineLayout> layoutLines(Graphics2D graphics, List<List<Object>> lines, int[] widths, int[][] columnOffsets) {
        List<LineLayout> layouts = new ArrayList<>();
        int style = Font.PLAIN;
        for (List<Object> line : lines) {
            LineLayout layout = layoutSegments(graphics, line, style);
            layouts.add(layout);
            style = layout.endStyle;
        }
        layoutTables(layouts, widths, columnOffsets);
        return layouts;
    }

    private LineLayout layoutSegments(Graphics2D graphics, List<Object> segments, int startStyle) {
        FontMetrics fontMetrics = graphics.getFontMetrics();

        // Icons are scaled to font height (aspect preserved), not drawn at native sprite size.
        int iconHeight = lineHeight(fontMetrics);

        int style = startStyle;
        int totalWidth = 0;
        List<BufferedImage> images = new ArrayList<>();
        List<Integer> iconWidths = new ArrayList<>();
        List<Integer> cellWidths = new ArrayList<>();
        List<Integer> cellPaddings = new ArrayList<>();
        int cellStart = 0;
        for (Object segment : segments) {
            if (isIcon(segment)) {
                // An item's null on a lookup failure (see ItemManager#getImage) -- treated as zero-width and simply
                // skipped when drawing below, rather than dropping the whole line. An item fills the line's height.
                BufferedImage image = iconImage(segment, false);
                image = image == null ? null : scaledIcon(image, iconHeight);
                images.add(image);
                int iconWidth = image == null ? 0 : segment instanceof ItemIcon && image.getHeight() != iconHeight
                    ? image.getWidth() * iconHeight / image.getHeight() : image.getWidth();
                iconWidths.add(iconWidth);
                totalWidth += iconWidth;
                continue;
            }

            images.add(null);
            iconWidths.add(0);
            if (segment instanceof StyleToggle) {
                style = ((StyleToggle) segment).apply(style);
            } else if (segment instanceof CellBreak) {
                cellWidths.add(totalWidth - cellStart);
                cellPaddings.add(((CellBreak) segment).padding);
                totalWidth += ((CellBreak) segment).padding;
                cellStart = totalWidth;
            } else if (!(segment instanceof TipToggle) && !(segment instanceof Badge)) {
                Font font = styledFont(graphics.getFont(), style);
                totalWidth += graphics.getFontMetrics(font).stringWidth(COLOR_TAG_PATTERN.matcher((String) segment).replaceAll(""));
            }
        }
        cellWidths.add(totalWidth - cellStart);

        return new LineLayout(segments, images, iconWidths, totalWidth, cellWidths, cellPaddings, startStyle, style);
    }

    private static boolean isIcon(Object segment) {
        return segment instanceof ItemIcon || segment instanceof SpriteIcon || segment instanceof SymbolIcon || segment instanceof NpcIcon
            || segment instanceof BufferedImage;
    }

    // An icon segment's image: an item's dimmed (null while it has none), a symbol's glyph, or with shadowed, the
    // glyph with its shadow.
    @Nullable
    private BufferedImage iconImage(Object segment, boolean shadowed) {
        if (segment instanceof ItemIcon) {
            BufferedImage image = itemManager.getImage(((ItemIcon) segment).id);
            return image == null ? null : dimmed(image, ((ItemIcon) segment).brightness);
        }
        return segment instanceof SpriteIcon ? ((SpriteIcon) segment).image
            : segment instanceof NpcIcon ? ((NpcIcon) segment).image
            : segment instanceof SymbolIcon ? (shadowed ? ((SymbolIcon) segment).shadowed : ((SymbolIcon) segment).glyph)
            : (BufferedImage) segment;
    }

    // "80" from "@80%", capped at 100; full brightness without it.
    private static int brightness(String percent) {
        return percent == null ? FULL_BRIGHTNESS : Math.min(Integer.parseInt(percent), FULL_BRIGHTNESS);
    }

    // A darkened copy, cached per image. An item image still loading is drawn into later, so its
    // copy is dropped once it loads.
    private BufferedImage dimmed(BufferedImage image, int brightness) {
        if (brightness >= FULL_BRIGHTNESS) {
            return image;
        }

        Map<Integer, BufferedImage> byBrightness = dimmedIcons.get(image);
        BufferedImage dimmed = byBrightness == null ? null : byBrightness.get(brightness);
        if (dimmed == null) {
            dimmed = ImageUtil.luminanceScale(image, brightness / 100f);
            LoadingImages.whenLoaded(image, () -> dimmedIcons.remove(image));
            dimmedIcons.computeIfAbsent(image, i -> new HashMap<>()).put(brightness, dimmed);
        }
        return dimmed;
    }

    // A black copy, cached while the image is in use.
    private BufferedImage silhouette(BufferedImage image) {
        return silhouettes.computeIfAbsent(image, i -> {
            int[] pixels = i.getRGB(0, 0, i.getWidth(), i.getHeight(), null, 0, i.getWidth());
            for (int p = 0; p < pixels.length; p++) {
                pixels[p] &= 0xFF000000;
            }
            BufferedImage black = new BufferedImage(i.getWidth(), i.getHeight(), BufferedImage.TYPE_INT_ARGB);
            black.setRGB(0, 0, i.getWidth(), i.getHeight(), pixels, 0, i.getWidth());
            return black;
        });
    }

    // Native size, or smoothly scaled down to the text height when that's shorter.
    private BufferedImage scaledIcon(BufferedImage icon, int textHeight) {
        if (textHeight >= icon.getHeight()) {
            return icon;
        }

        BufferedImage scaled = scaledIcons.get(icon);
        if (scaled == null || scaled.getHeight() != textHeight) {
            int width = Math.round(icon.getWidth() * textHeight / (float) icon.getHeight());
            scaled = ImageUtil.resizeImage(icon, width, textHeight);
            // An item image still loading is drawn into later.
            LoadingImages.whenLoaded(icon, () -> scaledIcons.remove(icon));
            scaledIcons.put(icon, scaled);
        }
        return scaled;
    }

    private static Font styledFont(Font base, int style) {
        return style == Font.PLAIN ? base : base.deriveFont(base.getStyle() | style);
    }

    // center: same tile-anchored canvas point plain text lines have always centered against.
    // left/right instead hug maxWidth's own edge, so every line lines up along a shared margin.
    private static int lineX(int anchorX, TextAlign align, int lineWidth, int maxWidth) {
        switch (align) {
            case LEFT:
                return anchorX - maxWidth / 2;
            case RIGHT:
                return anchorX + maxWidth / 2 - lineWidth;
            default:
                return anchorX - lineWidth / 2;
        }
    }

    // columnOffsets: each table column's x relative to the line's start, or null if not a table. shadow: text with
    // TextComponent's own shadow, else without.
    private void drawLine(Graphics2D graphics, Point anchor, LineLayout layout, int width, int[] columnOffsets, int maxWidth,
        TextAlign align, Color color, int yOffset, boolean shadow) {
        FontMetrics fontMetrics = graphics.getFontMetrics();
        int lineStart = lineX(anchor.getX(), align, width, maxWidth);
        int x = lineStart;
        int column = 0;
        int y = anchor.getY() + yOffset;

        // Full opacity regardless of the marker's own (possibly transparent) color -- the
        // caller's Composite (see drawTileAt) is what applies any fade, not this.
        Color textColor = ColorUtil.colorWithAlpha(color, 0xFF);
        int style = layout.startStyle;
        // The icon just drawn, for a <badge> right after it.
        Rectangle lastIcon = null;

        for (int i = 0; i < layout.segments.size(); i++) {
            Object segment = layout.segments.get(i);
            if (segment instanceof StyleToggle) {
                style = ((StyleToggle) segment).apply(style);
            } else if (segment instanceof Badge) {
                if (lastIcon != null) {
                    drawBadge(graphics, lastIcon, ((Badge) segment).percent);
                }
            } else if (segment instanceof TipToggle) {
                openTip = ((TipToggle) segment).text;
            } else if (segment instanceof CellBreak) {
                column++;
                x = columnOffsets != null && column < columnOffsets.length ? lineStart + columnOffsets[column] : x + ((CellBreak) segment).padding;
            } else if (isIcon(segment) && !(segment instanceof ItemIcon)) {
                BufferedImage flag = layout.images.get(i);
                int flagY = y - fontMetrics.getAscent() + (lineHeight(fontMetrics) - flag.getHeight()) / 2;
                if (segment instanceof SpriteIcon && config.iconBackground()) {
                    drawGlow(graphics, x, flagY, flag.getWidth(), flag.getHeight());
                }
                // A symbol's shadow as text's: a black copy 1px down-right.
                if (segment instanceof SymbolIcon) {
                    graphics.drawImage(silhouette(flag), x + 1, flagY + 1, null);
                }
                graphics.drawImage(flag, x, flagY, null);
                hitTest(x, flagY, flag.getWidth(), flag.getHeight(), -1);
                lastIcon = new Rectangle(x, flagY, flag.getWidth(), flag.getHeight());
                x += flag.getWidth();
            } else if (segment instanceof ItemIcon) {
                BufferedImage image = layout.images.get(i);
                int iconWidth = layout.iconWidths.get(i);
                if (image != null) {
                    int iconHeight = lineHeight(fontMetrics);
                    int imageY = y - fontMetrics.getAscent();
                    if (config.iconBackground()) {
                        drawGlow(graphics, x, imageY, iconWidth, iconHeight);
                    }
                    graphics.drawImage(image, x, imageY, iconWidth, iconHeight, null);
                    hitTest(x, imageY, iconWidth, iconHeight, ((ItemIcon) segment).id);
                    lastIcon = new Rectangle(x, imageY, iconWidth, iconHeight);
                    x += iconWidth;
                }
            } else {
                String text = (String) segment;
                lastIcon = null;
                Font font = styledFont(graphics.getFont(), style);
                if (shadow) {
                    TextComponent textComponent = new TextComponent();
                    textComponent.setText(text);
                    textComponent.setFont(font);
                    textComponent.setColor(textColor);
                    textComponent.setPosition(x, y);
                    textComponent.render(graphics);
                } else {
                    drawRuns(graphics, text, font, textColor, x, y);
                }
                int textWidth = graphics.getFontMetrics(font).stringWidth(COLOR_TAG_PATTERN.matcher(text).replaceAll(""));
                hitTest(x, y - fontMetrics.getAscent(), textWidth, lineHeight(fontMetrics), -1);
                x += textWidth;

                // An open <col=> carries past a toggle into the next run instead of resetting.
                Matcher colorTag = COLOR_TAG_PATTERN.matcher(text);
                while (colorTag.find()) {
                    textColor = Color.decode("#" + colorTag.group(1));
                }
            }
        }
    }

    // text as TextComponent draws it, each <col=> starting a new color, but without its shadow.
    private static void drawRuns(Graphics2D graphics, String text, Font font, Color color, int x, int y) {
        Font previous = graphics.getFont();
        graphics.setFont(font);
        FontMetrics metrics = graphics.getFontMetrics();
        Matcher tag = COLOR_TAG_PATTERN.matcher(text);
        int start = 0;
        while (true) {
            boolean found = tag.find();
            String run = text.substring(start, found ? tag.start() : text.length());
            if (!run.isEmpty()) {
                graphics.setColor(color);
                graphics.drawString(run, x, y);
                x += metrics.stringWidth(run);
            }
            if (!found) {
                break;
            }
            color = Color.decode("#" + tag.group(1));
            start = tag.end();
        }
        graphics.setFont(previous);
    }

    // Under the mouse, an open {tip=} wins over an item's own name tooltip.
    private void hitTest(int x, int y, int width, int height, int itemId) {
        if (mouse == null || !new Rectangle(x, y, width, height).contains(mouse.getX(), mouse.getY())) {
            return;
        }

        if (openTip != null) {
            tipText = openTip;
            tipItemId = -1;
        } else if (itemTips && itemId > 0) {
            tipItemId = itemId;
            tipText = null;
        }
    }

    // A soft round gray glow behind an item/sprite icon (config iconBackground), fading out to its edge.
    private static void drawGlow(Graphics2D graphics, int x, int y, int width, int height) {
        float radius = Math.max(width, height) / 2f * GLOW_SCALE;
        if (radius <= 0) {
            return;
        }

        float centerX = x + width / 2f;
        float centerY = y + height / 2f;
        Paint previous = graphics.getPaint();
        graphics.setPaint(new RadialGradientPaint(new Point2D.Float(centerX, centerY), radius, GLOW_FRACTIONS, GLOW_COLORS));
        graphics.fill(new Ellipse2D.Float(centerX - radius, centerY - radius, radius * 2, radius * 2));
        graphics.setPaint(previous);
    }

    // A dot in icon's top-right corner: full when percent is null, else a pie of percent left, from 12 o'clock.
    // Green, yellow at half or less, red at a fifth or less.
    private static void drawBadge(Graphics2D graphics, Rectangle icon, @Nullable Integer percent) {
        float size = Math.max(4f, Math.min(icon.width, icon.height) * BADGE_SIZE);
        float x = icon.x + icon.width - size;
        float y = icon.y;
        Color color = percent == null || percent > 50 ? BADGE_GREEN : percent > 20 ? BADGE_YELLOW : BADGE_RED;

        Paint previousPaint = graphics.getPaint();
        Object previousAntialias = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        graphics.setPaint(BADGE_BACKGROUND);
        graphics.fill(new Ellipse2D.Float(x, y, size, size));
        graphics.setPaint(color);
        graphics.fill(percent == null ? new Ellipse2D.Float(x, y, size, size)
            : new Arc2D.Float(x, y, size, size, 90, 360 * Math.min(percent, 100) / 100f, Arc2D.PIE));

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, previousAntialias);
        graphics.setPaint(previousPaint);
    }

    private static int lineHeight(FontMetrics fontMetrics) {
        return fontMetrics.getAscent() + fontMetrics.getDescent();
    }

    // Splits a line into text (String), ItemIcons, SpriteIcons, SymbolIcons, flags/emojis (BufferedImage) and StyleToggles.
    private List<Object> splitSegments(String line) {
        List<Object> segments = new ArrayList<>();
        // The icons inside an open {split}, whose text is dropped.
        List<Object> split = null;
        Matcher matcher = INLINE_TAG_PATTERN.matcher(line);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last && split == null) {
                segments.add(line.substring(last, matcher.start()));
            }

            List<Object> icons = split != null ? split : segments;
            if (matcher.group(1) != null) {
                icons.add(new ItemIcon(Integer.parseInt(matcher.group(1)), brightness(matcher.group(2))));
            } else if (matcher.group(5) != null) {
                BufferedImage flag = spriteManager.getSprite(FLAG_SPRITES.get(matcher.group(5).toLowerCase(Locale.ROOT)), 0);
                if (flag != null) {
                    icons.add(flag);
                }
            } else if (matcher.group(6) != null) {
                String codepoint = matcher.group(6).toLowerCase(Locale.ROOT);
                String tint = matcher.group(7) == null ? null : matcher.group(7).toLowerCase(Locale.ROOT);
                BufferedImage emoji = emojis.image(codepoint, tint);
                BufferedImage shadowed = emojis.shadowed(codepoint, tint);
                if (emoji != null) {
                    smoothImages.add(emoji);
                    if (shadowed != null) {
                        smoothImages.add(shadowed);
                    }
                    icons.add(shadowed != null ? new SymbolIcon(emoji, shadowed) : emoji);
                }
            } else if (matcher.group(8) != null) {
                int frame = matcher.group(9) == null ? 0 : Integer.parseInt(matcher.group(9));
                BufferedImage sprite = spriteManager.getSprite(Integer.parseInt(matcher.group(8)), frame);
                if (sprite != null) {
                    icons.add(new SpriteIcon(dimmed(sprite, brightness(matcher.group(10)))));
                }
            } else if (matcher.group(15) != null) {
                int id = Integer.parseInt(matcher.group(15));
                boolean head = matcher.group(16) != null;
                BufferedImage npc = npcImages.image(id, head);
                if (npc != null) {
                    smoothImages.add(npc);
                    icons.add(new NpcIcon(npc, id, head));
                }
            } else if (matcher.group(13) != null) {
                if (split != null) {
                    addSplit(segments, split);
                }
                split = matcher.group(13).isEmpty() ? new ArrayList<>() : null;
            } else if (matcher.group(12) != null) {
                segments.add(new TipToggle(matcher.group(12)));
            } else if ("{/tip}".equalsIgnoreCase(matcher.group(0))) {
                segments.add(new TipToggle(null));
            } else if (matcher.group(0).startsWith("\\")) {
                segments.add(new CellBreak(matcher.group(11) == null ? DEFAULT_CELL_PADDING : Integer.parseInt(matcher.group(11))));
            } else if (matcher.group(0).regionMatches(true, 0, "<badge", 0, 6)) {
                segments.add(new Badge(matcher.group(14) == null ? null : Integer.parseInt(matcher.group(14))));
            } else {
                segments.add(new StyleToggle("b".equalsIgnoreCase(matcher.group(4)) ? Font.BOLD : Font.ITALIC, matcher.group(3).isEmpty()));
            }
            last = matcher.end();
        }
        if (split != null) {
            addSplit(segments, split);
        } else if (last < line.length()) {
            segments.add(line.substring(last));
        }
        return segments;
    }

    // The {split} icons as one, glowing if any is a sprite.
    private void addSplit(List<Object> segments, List<Object> icons) {
        List<BufferedImage> parts = new ArrayList<>();
        boolean sprite = false;
        for (Object icon : icons.subList(0, Math.min(icons.size(), SplitIcons.MAX_PARTS))) {
            parts.add(iconImage(icon, true));
            sprite |= icon instanceof SpriteIcon;
        }

        BufferedImage image = splitIcon(parts);
        if (image != null) {
            segments.add(sprite ? new SpriteIcon(image) : image);
        }
    }

    // See SplitIcons, cached. Null if all parts are.
    @Nullable
    private BufferedImage splitIcon(List<BufferedImage> parts) {
        BufferedImage split = splitIcons.get(parts);
        if (split != null) {
            return split;
        }
        split = SplitIcons.compose(parts);
        if (split == null) {
            return null;
        }
        // An item image still loading is drawn into later.
        for (BufferedImage part : parts) {
            LoadingImages.whenLoaded(part, () -> splitIcons.remove(parts));
        }
        splitIcons.put(parts, split);
        return split;
    }
}
