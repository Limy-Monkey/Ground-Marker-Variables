package com.groundmarkervariables.chatbox;

import com.groundmarkervariables.GroundMarkerVariablesConfig;
import com.groundmarkervariables.chatbox.completion.Completion;
import com.groundmarkervariables.graphics.LoadingImages;
import com.groundmarkervariables.graphics.NpcImages;
import com.groundmarkervariables.graphics.SplitIcons;
import com.groundmarkervariables.variables.support.Emojis;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.TooltipComponent;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

// The Advanced Label Editor's suggestion icons, drawn over their (empty) placeholder widgets: widgets can only scale
// an icon blockily, Java2D smoothly. Empty unless the editor has shown some.
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SuggestionIconOverlay extends Overlay {
    // An icon and the area it fills (aspect kept, never enlarged), relative to container: the chatbox's container
    // outlives each update's widgets, which have no screen bounds until first drawn.
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Placement {
        private final Widget container;
        private final Rectangle area;
        @Nullable
        private final Supplier<Completion.Icon> icon;
        @Nullable
        private final BufferedImage image;

        public Placement(Widget container, Rectangle area, Completion.Icon icon) {
            this(container, area, () -> icon, null);
        }

        // An icon asked for each frame, drawn once it's ready (e.g. LabelCompleter#icon, worked out in the background).
        public Placement(Widget container, Rectangle area, Supplier<Completion.Icon> icon) {
            this(container, area, icon, null);
        }

        // A fixed image instead of an icon.
        public Placement(Widget container, Rectangle area, BufferedImage image) {
            this(container, area, null, image);
        }
    }

    // A suggestion's tooltip, shown while the mouse is over area (relative to container, as a Placement's).
    @RequiredArgsConstructor
    public static final class Tip {
        private final Widget container;
        private final Rectangle area;
        private final String text;
    }

    private final Client client;
    private final TooltipManager tooltipManager;
    private final ItemManager itemManager;
    private final SpriteManager spriteManager;
    private final NpcImages npcImages;
    private final Emojis emojis;
    private final GroundMarkerVariablesConfig config;
    private final ChatboxWidgets widgets;
    // The font size icons were last scaled for: with the larger, they're drawn 2px larger.
    private boolean largeFont;
    private volatile List<Placement> placements = List.of();
    private volatile List<Tip> tips = List.of();
    private static final long NOTICE_MS = 3000;
    @Nullable
    private volatile Tip notice;
    private volatile long noticeUntil;
    // Color swatches by RRGGBB.
    private static final int SWATCH_SIZE = 12;
    private static final Color SWATCH_OUTLINE = new Color(0, 0, 0, 160);
    private final Map<String, BufferedImage> swatches = new HashMap<>();
    // Keyed by the source image, then shrunk to the placeholder's size. Client thread only.
    private final Map<BufferedImage, BufferedImage> scaled = new IdentityHashMap<>();
    // Split icons, by the (per-editor cached) icon. Client thread only.
    private final Map<Completion.Icon, BufferedImage> composed = new IdentityHashMap<>();

    {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(PRIORITY_HIGH);
    }

    // The icons to draw and tooltips to offer now, replacing any before.
    public void show(List<Placement> placements, List<Tip> tips) {
        this.placements = List.copyOf(placements);
        this.tips = List.copyOf(tips);
    }

    // A tooltip under notice's area for NOTICE_MS, wherever the mouse is, e.g. why Show on map can't.
    public void notice(Tip notice) {
        this.notice = notice;
        noticeUntil = System.currentTimeMillis() + NOTICE_MS;
    }

    // The editor closed: nothing to draw, and nothing to keep.
    public void clear() {
        placements = List.of();
        tips = List.of();
        notice = null;
        scaled.clear();
        composed.clear();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        Point mouse = client.getMouseCanvasPosition();
        // With showIconIds, the drawn icon under the mouse, for showTip().
        Completion.Icon hovered = null;
        if (largeFont == widgets.smallFont()) {
            largeFont = !largeFont;
            scaled.clear();
        }
        for (Placement placement : placements) {
            Rectangle container = placement.container.isHidden() ? null : placement.container.getBounds();
            Completion.Icon icon = container == null || container.isEmpty() || placement.icon == null ? null : placement.icon.get();
            BufferedImage source = placement.image != null && container != null && !container.isEmpty() ? placement.image
                : icon != null ? image(icon) : null;
            if (source == null) {
                continue;
            }
            Rectangle bounds = new Rectangle(placement.area);
            bounds.translate(container.x, container.y);
            if (largeFont) {
                bounds.grow(1, 1);
            }
            if (icon != null && config.showIconIds() && container.contains(mouse.getX(), mouse.getY()) && bounds.contains(mouse.getX(), mouse.getY())) {
                hovered = icon;
            }
            BufferedImage image = scaled.get(source);
            if (image == null) {
                image = shrink(source, bounds.width, bounds.height);
                scaled.put(source, image);
                LoadingImages.whenLoaded(source, () -> scaled.remove(source));
            }
            // Cut off where the chatbox cuts off its widgets.
            Graphics2D clipped = (Graphics2D) graphics.create();
            clipped.clip(container);
            clipped.drawImage(image, bounds.x + (bounds.width - image.getWidth()) / 2, bounds.y + (bounds.height - image.getHeight()) / 2, null);
            clipped.dispose();
        }
        showNotice(graphics);
        showTip(mouse, hovered == null ? null : iconId(hovered));
        return null;
    }

    // "Kind: Id <id>", e.g. "Item: Id 4151"; a {split}'s parts a line each.
    private static String iconId(Completion.Icon icon) {
        if (icon.kind == Completion.Icon.Kind.SPLIT) {
            List<String> parts = new ArrayList<>();
            icon.parts.forEach(part -> parts.add(iconId(part)));
            return String.join("</br>", parts);
        }
        return icon.kind.title + ": Id " + icon.id;
    }

    private void showNotice(Graphics2D graphics) {
        Tip tip = notice;
        if (tip == null || System.currentTimeMillis() >= noticeUntil || tip.container.isHidden()) {
            return;
        }
        Rectangle container = tip.container.getBounds();
        TooltipComponent tooltip = new TooltipComponent();
        tooltip.setText(tip.text);
        tooltip.setPosition(new java.awt.Point(container.x + tip.area.x, container.y + tip.area.y + tip.area.height));
        tooltip.render(graphics);
    }

    // The tooltip of the suggestion under the mouse, if it's inside the chatbox, then iconId (the icon under it), if any.
    private void showTip(Point mouse, @Nullable String iconId) {
        String text = null;
        for (Tip tip : tips) {
            Rectangle container = tip.container.isHidden() ? null : tip.container.getBounds();
            if (container == null || !container.contains(mouse.getX(), mouse.getY())) {
                continue;
            }
            Rectangle bounds = new Rectangle(tip.area);
            bounds.translate(container.x, container.y);
            if (bounds.contains(mouse.getX(), mouse.getY())) {
                text = tip.text;
                break;
            }
        }
        text = iconId == null ? text : text == null ? iconId : text + "</br>" + iconId;
        if (text != null) {
            tooltipManager.add(new Tooltip(text));
        }
    }

    // A {split}'s parts drawn as one, cached once every part has loaded.
    @Nullable
    private BufferedImage image(Completion.Icon icon) {
        if (icon.kind != Completion.Icon.Kind.SPLIT) {
            return baseImage(icon);
        }
        BufferedImage cached = composed.get(icon);
        if (cached != null) {
            return cached;
        }

        List<BufferedImage> parts = new ArrayList<>();
        icon.parts.forEach(part -> parts.add(baseImage(part)));
        BufferedImage image = SplitIcons.compose(parts);
        if (image != null && !parts.contains(null)) {
            composed.put(icon, image);
            // An item image still loading is drawn into later.
            parts.forEach(part -> LoadingImages.whenLoaded(part, () -> composed.remove(icon)));
        }
        return image;
    }

    @Nullable
    private BufferedImage baseImage(Completion.Icon icon) {
        switch (icon.kind) {
            case ITEM:
                return itemManager.getImage(Integer.parseInt(icon.id));
            case NPC:
                return npcImages.image(Integer.parseInt(icon.id), false);
            case NPC_HEAD:
                return npcImages.image(Integer.parseInt(icon.id), true);
            case EMOJI:
                // Its codepoint, then "@" and a tint's hex if tinted, e.g. :recycle@white:'s "267b@ffffff".
                String[] emoji = icon.id.split("@", 2);
                String tint = emoji.length > 1 ? emoji[1] : null;
                BufferedImage symbol = emojis.shadowed(emoji[0], tint);
                return symbol != null ? symbol : emojis.image(emoji[0], tint);
            case COLOR:
                return swatches.computeIfAbsent(icon.id, SuggestionIconOverlay::swatch);
            case DIAMOND:
                return swatches.computeIfAbsent("diamond" + icon.id, key -> diamond(new Color(Integer.parseUnsignedInt(icon.id, 16), true)));
            default:
                return spriteManager.getSprite(Integer.parseInt(icon.id), 0);
        }
    }

    // A small circle of color RRGGBB, outlined so light colors show on the chatbox.
    private static BufferedImage swatch(String hex) {
        return shape(new Ellipse2D.Float(0, 0, SWATCH_SIZE - 1, SWATCH_SIZE - 1), new Color(Integer.parseInt(hex, 16)), SWATCH_OUTLINE);
    }

    // A diamond of color (as see-through as it), outlined in black; also the Marker Manager's map pins.
    static BufferedImage diamond(Color color) {
        int half = (SWATCH_SIZE - 1) / 2;
        return shape(new Polygon(new int[]{half, 2 * half, half, 0}, new int[]{0, half, 2 * half, half}, 4), color, Color.BLACK);
    }

    // shape filled with fill and outlined with outline, smoothly, on a SWATCH_SIZE square.
    private static BufferedImage shape(Shape shape, Color fill, Color outline) {
        BufferedImage image = new BufferedImage(SWATCH_SIZE, SWATCH_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(fill);
        g.fill(shape);
        g.setColor(outline);
        g.draw(shape);
        g.dispose();
        return image;
    }

    // Fitted inside width x height, aspect kept and never enlarged, halving step by step for a smooth result.
    private static BufferedImage shrink(BufferedImage image, int width, int height) {
        double scale = Math.min(1, Math.min(width / (double) image.getWidth(), height / (double) image.getHeight()));
        int targetWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int targetHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage current = image;
        while (current.getWidth() != targetWidth || current.getHeight() != targetHeight) {
            int w = Math.max(targetWidth, current.getWidth() / 2);
            int h = Math.max(targetHeight, current.getHeight() / 2);
            BufferedImage next = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = next.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(current, 0, 0, w, h, null);
            g.dispose();
            current = next;
        }
        return current;
    }
}
