package com.groundmarkervariables.chatbox;

import com.groundmarkervariables.chatbox.completion.Completion;
import com.groundmarkervariables.graphics.LoadingImages;
import com.groundmarkervariables.graphics.NpcImages;
import com.groundmarkervariables.graphics.SplitIcons;
import com.groundmarkervariables.variables.support.Emojis;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

// The Advanced Label Editor's suggestion icons, drawn over their (empty) placeholder widgets: widgets can only scale
// an icon blockily, Java2D smoothly. Empty unless the editor has shown some.
@Singleton
public class SuggestionIconOverlay extends Overlay
{
	// An icon and the area it fills (aspect kept, never enlarged), relative to container: the chatbox's container
	// outlives each update's widgets, which have no screen bounds until first drawn.
	public static final class Placement
	{
		private final Widget container;
		private final Rectangle area;
		private final Completion.Icon icon;

		public Placement(Widget container, Rectangle area, Completion.Icon icon)
		{
			this.container = container;
			this.area = area;
			this.icon = icon;
		}
	}

	// A suggestion's tooltip, shown while the mouse is over area (relative to container, as a Placement's).
	public static final class Tip
	{
		private final Widget container;
		private final Rectangle area;
		private final String text;

		public Tip(Widget container, Rectangle area, String text)
		{
			this.container = container;
			this.area = area;
			this.text = text;
		}
	}

	private final Client client;
	private final TooltipManager tooltipManager;
	private final ItemManager itemManager;
	private final SpriteManager spriteManager;
	private final NpcImages npcImages;
	private final Emojis emojis;
	private volatile List<Placement> placements = List.of();
	private volatile List<Tip> tips = List.of();
	// Color swatches by RRGGBB.
	private static final int SWATCH_SIZE = 12;
	private static final Color SWATCH_OUTLINE = new Color(0, 0, 0, 160);
	private final Map<String, BufferedImage> swatches = new HashMap<>();
	// Keyed by the source image, then shrunk to the placeholder's size. Client thread only.
	private final Map<BufferedImage, BufferedImage> scaled = new IdentityHashMap<>();
	// Split icons, by the (per-editor cached) icon. Client thread only.
	private final Map<Completion.Icon, BufferedImage> composed = new IdentityHashMap<>();

	@Inject
	private SuggestionIconOverlay(Client client, TooltipManager tooltipManager, ItemManager itemManager, SpriteManager spriteManager,
		NpcImages npcImages, Emojis emojis)
	{
		this.client = client;
		this.tooltipManager = tooltipManager;
		this.itemManager = itemManager;
		this.spriteManager = spriteManager;
		this.npcImages = npcImages;
		this.emojis = emojis;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGH);
	}

	// The icons to draw and tooltips to offer now, replacing any before.
	public void show(List<Placement> placements, List<Tip> tips)
	{
		this.placements = List.copyOf(placements);
		this.tips = List.copyOf(tips);
	}

	// The editor closed: nothing to draw, and nothing to keep.
	public void clear()
	{
		placements = List.of();
		tips = List.of();
		scaled.clear();
		composed.clear();
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		for (Placement placement : placements)
		{
			Rectangle container = placement.container.isHidden() ? null : placement.container.getBounds();
			BufferedImage source = container == null || container.isEmpty() ? null : image(placement.icon);
			if (source == null)
			{
				continue;
			}
			Rectangle bounds = new Rectangle(placement.area);
			bounds.translate(container.x, container.y);
			BufferedImage image = scaled.get(source);
			if (image == null)
			{
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
		showTip();
		return null;
	}

	// The tooltip of the suggestion under the mouse, if it's inside the chatbox.
	private void showTip()
	{
		Point mouse = client.getMouseCanvasPosition();
		for (Tip tip : tips)
		{
			Rectangle container = tip.container.isHidden() ? null : tip.container.getBounds();
			if (container == null || !container.contains(mouse.getX(), mouse.getY()))
			{
				continue;
			}
			Rectangle bounds = new Rectangle(tip.area);
			bounds.translate(container.x, container.y);
			if (bounds.contains(mouse.getX(), mouse.getY()))
			{
				tooltipManager.add(new Tooltip(tip.text));
				return;
			}
		}
	}

	// A {split}'s parts drawn as one, cached once every part has loaded.
	@Nullable
	private BufferedImage image(Completion.Icon icon)
	{
		if (icon.kind != Completion.Icon.Kind.SPLIT)
		{
			return baseImage(icon);
		}
		BufferedImage cached = composed.get(icon);
		if (cached != null)
		{
			return cached;
		}

		List<BufferedImage> parts = new ArrayList<>();
		icon.parts.forEach(part -> parts.add(baseImage(part)));
		BufferedImage image = SplitIcons.compose(parts);
		if (image != null && !parts.contains(null))
		{
			composed.put(icon, image);
			// An item image still loading is drawn into later.
			parts.forEach(part -> LoadingImages.whenLoaded(part, () -> composed.remove(icon)));
		}
		return image;
	}

	@Nullable
	private BufferedImage baseImage(Completion.Icon icon)
	{
		switch (icon.kind)
		{
			case ITEM:
				return itemManager.getImage(Integer.parseInt(icon.id));
			case NPC:
				return npcImages.image(Integer.parseInt(icon.id), false);
			case NPC_HEAD:
				return npcImages.image(Integer.parseInt(icon.id), true);
			case EMOJI:
				BufferedImage symbol = emojis.shadowed(icon.id, null);
				return symbol != null ? symbol : emojis.image(icon.id, null);
			case COLOR:
				return swatches.computeIfAbsent(icon.id, SuggestionIconOverlay::swatch);
			default:
				return spriteManager.getSprite(Integer.parseInt(icon.id), 0);
		}
	}

	// A small circle of color RRGGBB, outlined so light colors show on the chatbox.
	private static BufferedImage swatch(String hex)
	{
		BufferedImage image = new BufferedImage(SWATCH_SIZE, SWATCH_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(Integer.parseInt(hex, 16)));
		g.fillOval(0, 0, SWATCH_SIZE - 1, SWATCH_SIZE - 1);
		g.setColor(SWATCH_OUTLINE);
		g.drawOval(0, 0, SWATCH_SIZE - 1, SWATCH_SIZE - 1);
		g.dispose();
		return image;
	}

	// Fitted inside width x height, aspect kept and never enlarged, halving step by step for a smooth result.
	private static BufferedImage shrink(BufferedImage image, int width, int height)
	{
		double scale = Math.min(1, Math.min(width / (double) image.getWidth(), height / (double) image.getHeight()));
		int targetWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
		int targetHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
		BufferedImage current = image;
		while (current.getWidth() != targetWidth || current.getHeight() != targetHeight)
		{
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
