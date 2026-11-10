package com.groundmarkervariables;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.util.AsyncBufferedImage;

// {ground}: an image drawn flat on a tile, top to the north. The tile is cut into cells whose corners are
// projected separately (so it follows slopes and perspective), each cell drawn as two affine-mapped triangles.
@Singleton
class GroundImageRenderer
{
	// Space left around the image inside the tile, in local units.
	private static final int MARGIN = 8;
	private static final int CELLS = 4;
	// Each triangle's clip is grown this many px, hiding hairline gaps between neighbours.
	private static final double GROW = 0.7;
	private static final int MAX_UPSCALE = 8;
	// Keyed by the (already cached) source image, then factor.
	private static final Map<BufferedImage, Map<Integer, BufferedImage>> UPSCALED = new IdentityHashMap<>();
	// Keyed by the (already cached) emoji image or icon row.
	private static final Map<BufferedImage, BufferedImage> PADDED = new IdentityHashMap<>();

	private final Client client;
	// Triangles are drawn here with Src, so overlaps overwrite rather than double up, then blended once.
	private BufferedImage buffer;

	@Inject
	private GroundImageRenderer(Client client)
	{
		this.client = client;
	}

	// smooth: sampled straight from image (emoji); otherwise blown up into even pixel blocks first (items, sprites).
	void draw(Graphics2D graphics, LocalPoint center, int plane, BufferedImage image, boolean smooth, float opacity)
	{
		// The image fitted inside the tile, aspect kept, in local units.
		double size = Perspective.LOCAL_TILE_SIZE - MARGIN * 2;
		double scale = size / Math.max(image.getWidth(), image.getHeight());
		// Smooth sampling stops hard at the image's edge, so pixels touching it would end in a straight cut; a
		// transparent border lets them fade out like any other edge.
		int pad = smooth ? 1 : 0;
		double halfW = (image.getWidth() + pad * 2) * scale / 2;
		double halfH = (image.getHeight() + pad * 2) * scale / 2;
		if (smooth)
		{
			image = padded(image);
		}
		double west = center.getX() - halfW;
		double north = center.getY() + halfH;

		Point[][] corners = new Point[CELLS + 1][CELLS + 1];
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
		for (int row = 0; row <= CELLS; row++)
		{
			for (int col = 0; col <= CELLS; col++)
			{
				int x = (int) Math.round(west + halfW * 2 * col / CELLS);
				int y = (int) Math.round(north - halfH * 2 * row / CELLS);
				Point p = Perspective.localToCanvas(client, new LocalPoint(x, y, center.getWorldView()), plane);
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
			return;
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
				double u0 = col * cellW, u1 = u0 + cellW, v0 = row * cellH, v1 = v0 + cellH;
				drawTriangle(bg, image, u0, v0, u1, v0, u0, v1, nw, ne, sw);
				drawTriangle(bg, image, u1, v0, u1, v1, u0, v1, ne, se, sw);
			}
		}
		bg.dispose();

		Composite original = graphics.getComposite();
		if (opacity < 1f)
		{
			graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
		}
		graphics.drawImage(buffer, bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height,
			0, 0, bounds.width, bounds.height, null);
		graphics.setComposite(original);
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
	static BufferedImage upscaled(BufferedImage image, int factor)
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
			if (image instanceof AsyncBufferedImage)
			{
				((AsyncBufferedImage) image).onLoaded(() -> UPSCALED.remove(image));
			}
			UPSCALED.computeIfAbsent(image, i -> new HashMap<>()).put(factor, scaled);
		}
		return scaled;
	}

	// Maps image triangle (u, v) onto canvas triangle (A, B, C).
	private static void drawTriangle(Graphics2D graphics, BufferedImage image, double ua, double va, double ub, double vb, double uc, double vc,
		Point a, Point b, Point c)
	{
		double ux = ub - ua, uy = vb - va, vx = uc - ua, vy = vc - va;
		double det = ux * vy - uy * vx;
		if (det == 0)
		{
			return;
		}
		double i00 = vy / det, i01 = -vx / det, i10 = -uy / det, i11 = ux / det;
		double bx = b.getX() - a.getX(), by = b.getY() - a.getY();
		double cx = c.getX() - a.getX(), cy = c.getY() - a.getY();
		AffineTransform transform = new AffineTransform(bx * i00 + cx * i10, by * i00 + cy * i10, bx * i01 + cx * i11, by * i01 + cy * i11,
			a.getX(), a.getY());
		transform.translate(-ua, -va);

		Graphics2D g = (Graphics2D) graphics.create();
		g.clip(grown(a, b, c));
		g.drawImage(image, transform, null);
		g.dispose();
	}

	// The triangle grown GROW px out from its centroid.
	private static Path2D grown(Point a, Point b, Point c)
	{
		double mx = (a.getX() + b.getX() + c.getX()) / 3.0, my = (a.getY() + b.getY() + c.getY()) / 3.0;
		Path2D.Double path = new Path2D.Double();
		Point[] points = {a, b, c};
		for (int i = 0; i < points.length; i++)
		{
			double dx = points[i].getX() - mx, dy = points[i].getY() - my;
			double length = Math.max(1e-6, Math.hypot(dx, dy));
			double x = points[i].getX() + dx / length * GROW, y = points[i].getY() + dy / length * GROW;
			if (i == 0)
			{
				path.moveTo(x, y);
			}
			else
			{
				path.lineTo(x, y);
			}
		}
		path.closePath();
		return path;
	}
}
