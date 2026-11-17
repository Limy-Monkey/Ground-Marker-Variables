package com.groundmarkervariables.graphics;

import com.google.common.cache.CacheBuilder;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;

// {ground}: an image drawn flat on a tile, top to the north. The tile is cut into cells whose corners are
// projected separately (so it follows slopes and perspective), each cell drawn with one affine transform.
@Singleton
public class GroundImageRenderer
{
	// Space left around the image inside the tile, in local units, and the size left inside it.
	private static final int MARGIN = 8;
	public static final int SIZE = Perspective.LOCAL_TILE_SIZE - MARGIN * 2;
	private static final int CELLS = 4;
	private static final int MAX_UPSCALE = 8;
	// Pixels per local unit along a shadow's shift is measured across this many local units.
	private static final int SHIFT_PROBE = 64;
	// Keyed by the (already cached) source image, then factor. Weak keys, by identity.
	private static final Map<BufferedImage, Map<Integer, BufferedImage>> UPSCALED =
		CacheBuilder.newBuilder().weakKeys().<BufferedImage, Map<Integer, BufferedImage>>build().asMap();
	// Keyed by the (already cached) emoji image, icon row or text image; weak, as text images come and go.
	private static final Map<BufferedImage, BufferedImage> PADDED = new WeakHashMap<>();

	private final Client client;
	// Cells are drawn here with Src, so overlaps overwrite rather than double up, then blended once.
	private BufferedImage buffer;
	// A black copy of buffer, for a shadow.
	private BufferedImage shadowBuffer;

	@Inject
	private GroundImageRenderer(Client client)
	{
		this.client = client;
	}

	// smooth: sampled straight from image (emoji); otherwise blown up into even pixel blocks first (items, sprites).
	public void draw(Graphics2D graphics, LocalPoint center, int plane, BufferedImage image, boolean smooth, float opacity)
	{
		draw(graphics, center, plane, image, smooth, opacity, 0, 0, SIZE, SIZE);
	}

	// center moved east and north local units in a frame turned facing degrees clockwise (its north toward facing).
	public static LocalPoint turned(LocalPoint center, double east, double north, int facing)
	{
		double angle = Math.toRadians(facing);
		double sin = Math.sin(angle), cos = Math.cos(angle);
		return new LocalPoint((int) Math.round(center.getX() + east * cos + north * sin),
			(int) Math.round(center.getY() - east * sin + north * cos), center.getWorldView());
	}

	// Screen px per local unit across a width x height box centered offsetNorth north of the tile's center (in a frame
	// turned to facing), from its widest projected extent; 0 if a corner isn't on screen.
	public double pixelsPerUnit(LocalPoint center, int plane, int facing, double offsetNorth, double width, double height)
	{
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
		for (int corner = 0; corner < 4; corner++)
		{
			Point p = Perspective.localToCanvas(client,
				turned(center, (corner % 2 == 0 ? -width : width) / 2, offsetNorth + (corner < 2 ? -height : height) / 2, facing), plane);
			if (p == null)
			{
				return 0;
			}
			minX = Math.min(minX, p.getX());
			minY = Math.min(minY, p.getY());
			maxX = Math.max(maxX, p.getX());
			maxY = Math.max(maxY, p.getY());
		}
		return Math.max(maxX - minX, maxY - minY) / Math.max(width, height);
	}

	// Fitted inside a width x height box (local units) centered offsetNorth north of the tile's center, aspect kept, in a
	// frame turned facing degrees clockwise so the image's top faces that way.
	public void draw(Graphics2D graphics, LocalPoint center, int plane, BufferedImage image, boolean smooth, float opacity,
		int facing, double offsetNorth, double width, double height)
	{
		draw(graphics, center, plane, image, smooth, opacity, facing, offsetNorth, width, height, 0);
	}

	// As above, lift local units over the ground (under it if negative).
	public void draw(Graphics2D graphics, LocalPoint center, int plane, BufferedImage image, boolean smooth, float opacity,
		int facing, double offsetNorth, double width, double height, int lift)
	{
		Rectangle bounds = render(center, plane, image, smooth, facing, offsetNorth, width, height, lift);
		if (bounds != null)
		{
			blit(graphics, buffer, bounds.x, bounds.y, bounds, opacity);
		}
	}

	// As above, over a black copy of it: at shadowLift instead, moved shift image px right and down (turned with the
	// image), and drawn only inside shadowClip (null: anywhere). Rendered once, the shadow made from it.
	public void drawWithShadow(Graphics2D graphics, LocalPoint center, int plane, BufferedImage image, boolean smooth, float opacity,
		int facing, double offsetNorth, double width, double height, int lift, int shadowLift, int shift, @Nullable Shape shadowClip)
	{
		Rectangle bounds = render(center, plane, image, smooth, facing, offsetNorth, width, height, lift);
		if (bounds == null)
		{
			return;
		}

		// The image's center at each height; the shift (local units) as screen px, measured across SHIFT_PROBE units.
		double shiftUnits = shift * Math.min(width / image.getWidth(), height / image.getHeight());
		LocalPoint at = turned(center, 0, offsetNorth, facing);
		Point from = Perspective.localToCanvas(client, at, plane, lift);
		Point to = Perspective.localToCanvas(client, at, plane, shadowLift);
		Point probe = Perspective.localToCanvas(client, turned(center, SHIFT_PROBE, offsetNorth - SHIFT_PROBE, facing), plane, shadowLift);
		if (from != null && to != null && probe != null)
		{
			double perUnit = shiftUnits / SHIFT_PROBE;
			int dx = to.getX() - from.getX() + (int) Math.round((probe.getX() - to.getX()) * perUnit);
			int dy = to.getY() - from.getY() + (int) Math.round((probe.getY() - to.getY()) * perUnit);
			Graphics2D shadowGraphics = (Graphics2D) graphics.create();
			if (shadowClip != null)
			{
				shadowGraphics.clip(shadowClip);
			}
			blit(shadowGraphics, blackened(bounds), bounds.x + dx, bounds.y + dy, bounds, opacity);
			shadowGraphics.dispose();
		}
		blit(graphics, buffer, bounds.x, bounds.y, bounds, opacity);
	}

	// source's top-left bounds-sized area drawn at x, y.
	private static void blit(Graphics2D graphics, BufferedImage source, int x, int y, Rectangle bounds, float opacity)
	{
		Composite original = graphics.getComposite();
		if (opacity < 1f)
		{
			graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
		}
		graphics.drawImage(source, x, y, x + bounds.width, y + bounds.height, 0, 0, bounds.width, bounds.height, null);
		graphics.setComposite(original);
	}

	// A black copy (alpha kept) of buffer's bounds-sized area, in shadowBuffer.
	private BufferedImage blackened(Rectangle bounds)
	{
		if (shadowBuffer == null || shadowBuffer.getWidth() < bounds.width || shadowBuffer.getHeight() < bounds.height)
		{
			shadowBuffer = new BufferedImage(Math.max(bounds.width, buffer.getWidth()), Math.max(bounds.height, buffer.getHeight()),
				BufferedImage.TYPE_INT_ARGB);
		}
		Graphics2D g = shadowBuffer.createGraphics();
		g.setComposite(AlphaComposite.Src);
		g.drawImage(buffer, 0, 0, bounds.width, bounds.height, 0, 0, bounds.width, bounds.height, null);
		g.setComposite(AlphaComposite.SrcIn);
		g.setColor(Color.BLACK);
		g.fillRect(0, 0, bounds.width, bounds.height);
		g.dispose();
		return shadowBuffer;
	}

	// image projected into buffer (its top-left at the returned screen bounds); null if off screen.
	@Nullable
	private Rectangle render(LocalPoint center, int plane, BufferedImage image, boolean smooth, int facing, double offsetNorth,
		double width, double height, int lift)
	{
		double scale = Math.min(width / image.getWidth(), height / image.getHeight());
		// Smooth sampling stops hard at the image's edge, so pixels touching it would end in a straight cut; a
		// transparent border lets them fade out like any other edge.
		int pad = smooth ? 1 : 0;
		double halfW = (image.getWidth() + pad * 2) * scale / 2;
		double halfH = (image.getHeight() + pad * 2) * scale / 2;
		if (smooth)
		{
			image = padded(image);
		}
		Point[][] corners = new Point[CELLS + 1][CELLS + 1];
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
		for (int row = 0; row <= CELLS; row++)
		{
			for (int col = 0; col <= CELLS; col++)
			{
				LocalPoint point = turned(center, -halfW + halfW * 2 * col / CELLS, offsetNorth + halfH - halfH * 2 * row / CELLS, facing);
				Point p = Perspective.localToCanvas(client, point, plane, lift);
				corners[row][col] = p;
				if (p != null)
				{
					minX = Math.min(minX, p.getX());
					minY = Math.min(minY, p.getY());
					maxX = Math.max(maxX, p.getX());
					maxY = Math.max(maxY, p.getY());
				}
			}
		}

		Rectangle bounds = new Rectangle(minX - 2, minY - 2, maxX - minX + 4, maxY - minY + 4)
			.intersection(new Rectangle(0, 0, client.getCanvasWidth(), client.getCanvasHeight()));
		if (minX > maxX || bounds.isEmpty())
		{
			return null;
		}

		// Blown up so each source pixel spans a few screen pixels, then sampled smoothly: crisp, even pixels.
		int onScreen = Math.max(maxX - minX, maxY - minY);
		if (!smooth)
		{
			int factor = Math.max(1, Math.min(MAX_UPSCALE, (int) Math.ceil(onScreen / (double) Math.max(image.getWidth(), image.getHeight()))));
			image = upscaled(image, factor);
		}

		Graphics2D bg = buffer(bounds.width, bounds.height);
		bg.translate(-bounds.x, -bounds.y);
		double cellW = image.getWidth() / (double) CELLS;
		double cellH = image.getHeight() / (double) CELLS;
		for (int row = 0; row < CELLS; row++)
		{
			for (int col = 0; col < CELLS; col++)
			{
				Point nw = corners[row][col], ne = corners[row][col + 1], sw = corners[row + 1][col], se = corners[row + 1][col + 1];
				if (nw == null || ne == null || sw == null || se == null)
				{
					continue;
				}
				drawCell(bg, image, col * cellW, row * cellH, cellW, cellH, nw, ne, sw);
			}
		}
		bg.dispose();
		return bounds;
	}

	// The shared buffer, grown if needed and cleared over the area used.
	private Graphics2D buffer(int width, int height)
	{
		if (buffer == null || buffer.getWidth() < width || buffer.getHeight() < height)
		{
			buffer = new BufferedImage(Math.max(width, buffer == null ? 0 : buffer.getWidth()),
				Math.max(height, buffer == null ? 0 : buffer.getHeight()), BufferedImage.TYPE_INT_ARGB);
		}
		Graphics2D bg = buffer.createGraphics();
		bg.setComposite(AlphaComposite.Clear);
		bg.fillRect(0, 0, width, height);
		bg.setComposite(AlphaComposite.Src);
		bg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		return bg;
	}

	// image with a 1px transparent border, cached per image.
	private static BufferedImage padded(BufferedImage image)
	{
		return PADDED.computeIfAbsent(image, i ->
		{
			BufferedImage out = new BufferedImage(i.getWidth() + 2, i.getHeight() + 2, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = out.createGraphics();
			g.drawImage(i, 1, 1, null);
			g.dispose();
			return out;
		});
	}

	// image blown up factor times with nearest-neighbour, cached per image and factor.
	public static BufferedImage upscaled(BufferedImage image, int factor)
	{
		if (factor <= 1)
		{
			return image;
		}

		Map<Integer, BufferedImage> byFactor = UPSCALED.get(image);
		BufferedImage scaled = byFactor == null ? null : byFactor.get(factor);
		if (scaled == null)
		{
			scaled = new BufferedImage(image.getWidth() * factor, image.getHeight() * factor, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = scaled.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g.drawImage(image, 0, 0, scaled.getWidth(), scaled.getHeight(), null);
			g.dispose();
			// An item image still loading is drawn into later.
			LoadingImages.whenLoaded(image, () -> UPSCALED.remove(image));
			UPSCALED.computeIfAbsent(image, i -> new HashMap<>()).put(factor, scaled);
		}
		return scaled;
	}

	// The image's u, v, w x h cell onto the parallelogram nw, ne, sw (its fourth corner where those three put it), no clip:
	// with a source px of margin, so neighbouring cells overlap rather than leave hairline gaps.
	private static void drawCell(Graphics2D graphics, BufferedImage image, double u, double v, double w, double h, Point nw, Point ne, Point sw)
	{
		int x0 = Math.max(0, (int) Math.floor(u) - 1), y0 = Math.max(0, (int) Math.floor(v) - 1);
		int x1 = Math.min(image.getWidth(), (int) Math.ceil(u + w) + 1), y1 = Math.min(image.getHeight(), (int) Math.ceil(v + h) + 1);
		if (x1 <= x0 || y1 <= y0)
		{
			return;
		}
		// Image (u, v) to nw, (u + w, v) to ne, (u, v + h) to sw.
		double ax = (ne.getX() - nw.getX()) / w, ay = (ne.getY() - nw.getY()) / w;
		double bx = (sw.getX() - nw.getX()) / h, by = (sw.getY() - nw.getY()) / h;
		AffineTransform transform = new AffineTransform(ax, ay, bx, by, nw.getX() - ax * u - bx * v, nw.getY() - ay * u - by * v);
		transform.translate(x0, y0);
		graphics.drawImage(image.getSubimage(x0, y0, x1 - x0, y1 - y0), transform, null);
	}
}
