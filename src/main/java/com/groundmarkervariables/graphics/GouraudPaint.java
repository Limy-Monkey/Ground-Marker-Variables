package com.groundmarkervariables.graphics;

import java.awt.Paint;
import java.awt.PaintContext;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

// A triangle's corner colors blended across it, as the game shades models. Outside it (a grown fill) takes the nearest.
final class GouraudPaint implements Paint
{
	private final double[] x;
	private final double[] y;
	private final int[] rgb;
	private final int alpha;

	// Corners in user space, their colors, and the face's alpha.
	GouraudPaint(double[] x, double[] y, int[] rgb, int alpha)
	{
		this.x = x;
		this.y = y;
		this.rgb = rgb;
		this.alpha = alpha;
	}

	@Override
	public int getTransparency()
	{
		return alpha == 255 ? Transparency.OPAQUE : Transparency.TRANSLUCENT;
	}

	@Override
	public PaintContext createContext(ColorModel cm, Rectangle deviceBounds, Rectangle2D userBounds, AffineTransform xform, RenderingHints hints)
	{
		double[] dx = new double[3], dy = new double[3];
		for (int i = 0; i < 3; i++)
		{
			Point2D p = xform.transform(new Point2D.Double(x[i], y[i]), null);
			dx[i] = p.getX();
			dy[i] = p.getY();
		}
		return new Context(dx, dy, rgb, alpha);
	}

	private static final class Context implements PaintContext
	{
		private final double x0, y0;
		private final double i00, i01, i10, i11;
		private final boolean degenerate;
		private final int[] rgb;
		private final int alpha;

		private Context(double[] x, double[] y, int[] rgb, int alpha)
		{
			this.rgb = rgb;
			this.alpha = alpha;
			x0 = x[0];
			y0 = y[0];
			double ux = x[1] - x[0], uy = y[1] - y[0], vx = x[2] - x[0], vy = y[2] - y[0];
			double det = ux * vy - uy * vx;
			degenerate = Math.abs(det) < 1e-9;
			// Offset from corner 0 -> weights of corners 1 and 2.
			i00 = degenerate ? 0 : vy / det;
			i01 = degenerate ? 0 : -vx / det;
			i10 = degenerate ? 0 : -uy / det;
			i11 = degenerate ? 0 : ux / det;
		}

		@Override
		public void dispose()
		{
		}

		@Override
		public ColorModel getColorModel()
		{
			return ColorModel.getRGBdefault();
		}

		@Override
		public Raster getRaster(int x, int y, int w, int h)
		{
			WritableRaster raster = getColorModel().createCompatibleWritableRaster(w, h);
			int[] pixels = new int[w * h];
			for (int row = 0; row < h; row++)
			{
				double py = y + row + 0.5 - y0;
				for (int col = 0; col < w; col++)
				{
					double px = x + col + 0.5 - x0;
					double b1 = degenerate ? 0 : Math.max(0, i00 * px + i01 * py);
					double b2 = degenerate ? 0 : Math.max(0, i10 * px + i11 * py);
					double b0 = Math.max(0, 1 - b1 - b2);
					double sum = b0 + b1 + b2;
					pixels[row * w + col] = alpha << 24 | blend(16, b0, b1, b2, sum) << 16 | blend(8, b0, b1, b2, sum) << 8 | blend(0, b0, b1, b2, sum);
				}
			}
			raster.setDataElements(0, 0, w, h, pixels);
			return raster;
		}

		private int blend(int shift, double b0, double b1, double b2, double sum)
		{
			double value = ((rgb[0] >> shift & 0xFF) * b0 + (rgb[1] >> shift & 0xFF) * b1 + (rgb[2] >> shift & 0xFF) * b2) / sum;
			return (int) Math.max(0, Math.min(255, Math.round(value)));
		}
	}
}
