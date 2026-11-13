package com.groundmarkervariables.chatbox;

import com.groundmarkervariables.chatbox.completion.Completion;
import com.groundmarkervariables.graphics.NpcImages;
import com.groundmarkervariables.variables.support.Emojis;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.AsyncBufferedImage;

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

	private final ItemManager itemManager;
	private final SpriteManager spriteManager;
	private final NpcImages npcImages;
	private final Emojis emojis;
	private volatile List<Placement> placements = List.of();
	// Keyed by the source image, then shrunk to the placeholder's size. Client thread only.
	private final Map<BufferedImage, BufferedImage> scaled = new IdentityHashMap<>();
	private final Set<BufferedImage> watched = Collections.newSetFromMap(new IdentityHashMap<>());

	@Inject
	private SuggestionIconOverlay(ItemManager itemManager, SpriteManager spriteManager, NpcImages npcImages, Emojis emojis)
	{
		this.itemManager = itemManager;
		this.spriteManager = spriteManager;
		this.npcImages = npcImages;
		this.emojis = emojis;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGH);
	}

	// The icons to draw now, replacing any before.
	public void show(List<Placement> placements)
	{
		this.placements = List.copyOf(placements);
	}

	// The editor closed: nothing to draw, and nothing to keep.
	public void clear()
	{
		placements = List.of();
		scaled.clear();
		watched.clear();
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
			BufferedImage image = scaled.computeIfAbsent(source, s -> shrink(s, bounds.width, bounds.height));
			if (source instanceof AsyncBufferedImage && watched.add(source))
			{
				((AsyncBufferedImage) source).onLoaded(() -> scaled.remove(source));
			}
			// Cut off where the chatbox cuts off its widgets.
			Graphics2D clipped = (Graphics2D) graphics.create();
			clipped.clip(container);
			clipped.drawImage(image, bounds.x + (bounds.width - image.getWidth()) / 2, bounds.y + (bounds.height - image.getHeight()) / 2, null);
			clipped.dispose();
		}
		return null;
	}

	@Nullable
	private BufferedImage image(Completion.Icon icon)
	{
		switch (icon.kind)
		{
			case ITEM:
				return itemManager.getImage(Integer.parseInt(icon.id));
			case NPC:
				return npcImages.image(Integer.parseInt(icon.id), false);
			case EMOJI:
				BufferedImage symbol = emojis.shadowed(icon.id, null);
				return symbol != null ? symbol : emojis.image(icon.id, null);
			default:
				return spriteManager.getSprite(Integer.parseInt(icon.id), 0);
		}
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
