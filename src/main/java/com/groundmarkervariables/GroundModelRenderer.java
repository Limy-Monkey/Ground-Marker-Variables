package com.groundmarkervariables;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;

// {ground}<npc=ID> / <item=ID>: the model lying face up across the tile, top to the north. Its depth is squashed to
// ABOVE over and BELOW under the ground; the part below is clipped to the tile, as if seen through it. Projected through
// the game camera every frame, Gouraud shaded, textures scrolling as in game.
@Singleton
class GroundModelRenderer
{
	// Space around the model inside the tile, in local units.
	private static final int MARGIN = 8;
	// Shares of the model's depth over and under the ground.
	private static final double ABOVE = 0.1;
	private static final double BELOW = 0.15;
	// Share of the model's depth each render priority level moves a face forward when sorting.
	static final double PRIORITY_STEP = 0.01;
	// Faces are grown this many px, closing hairline gaps between neighbours.
	private static final double GROW = 0.7;

	private final Client client;
	private final ModelColors modelColors;
	// Keyed by the (cached) model.
	private final Map<Model, Flat> flats = new IdentityHashMap<>();

	@Inject
	private GroundModelRenderer(Client client, ModelColors modelColors)
	{
		this.client = client;
		this.modelColors = modelColors;
	}

	// The model laid flat: each vertex's offset from the tile center and height, in local units, and per face to draw
	// (back to front) its color, three corner colors, and texture (-1 none) with corner u/v (0..1) and darkening.
	private static final class Flat
	{
		private final int[] east;
		private final int[] north;
		private final int[] height;
		private final int[] faces;
		private final Color[] colors;
		private final int[] corners;
		private final int[] textures;
		private final float[] u;
		private final float[] v;
		private final float[] shade;
		private final int[] f1;
		private final int[] f2;
		private final int[] f3;

		private Flat(int[] east, int[] north, int[] height, int[] faces, Color[] colors, int[] corners, int[] textures, float[] u,
			float[] v, float[] shade, int[] f1, int[] f2, int[] f3)
		{
			this.east = east;
			this.north = north;
			this.height = height;
			this.faces = faces;
			this.colors = colors;
			this.corners = corners;
			this.textures = textures;
			this.u = u;
			this.v = v;
			this.shade = shade;
			this.f1 = f1;
			this.f2 = f2;
			this.f3 = f3;
		}
	}

	// E.g. with Parallax icons turned off.
	void clear()
	{
		flats.clear();
	}

	void draw(Graphics2D graphics, LocalPoint center, int plane, Model model, float opacity)
	{
		draw(graphics, center, plane, model, model.getVerticesX(), model.getVerticesY(), model.getVerticesZ(), opacity);
	}

	// model's faces and colors, with its vertices turned to x, y, z (x right, y down, front toward -z).
	void draw(Graphics2D graphics, LocalPoint center, int plane, Model model, float[] x, float[] y, float[] z, float opacity)
	{
		Flat flat = flats.computeIfAbsent(model, m -> layFlat(m, x, y, z));
		if (flat == null)
		{
			return;
		}

		int vertexCount = flat.east.length;
		int[] sx = new int[vertexCount];
		int[] sy = new int[vertexCount];
		boolean[] visible = new boolean[vertexCount];
		for (int i = 0; i < vertexCount; i++)
		{
			LocalPoint point = new LocalPoint(center.getX() + flat.east[i], center.getY() + flat.north[i], center.getWorldView());
			Point p = Perspective.localToCanvas(client, point, plane, flat.height[i]);
			if (p != null)
			{
				visible[i] = true;
				sx[i] = p.getX();
				sy[i] = p.getY();
			}
		}

		Polygon tile = Perspective.getCanvasTilePoly(client, center);
		if (tile == null)
		{
			return;
		}
		Graphics2D above = (Graphics2D) graphics.create();
		Graphics2D below = (Graphics2D) graphics.create();
		below.clip(tile);
		if (opacity < 1f)
		{
			AlphaComposite faded = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity);
			above.setComposite(faded);
			below.setComposite(faded);
		}
		drawFaces(above, below, flat, sx, sy, visible);
		above.dispose();
		below.dispose();
	}

	// Faces with a corner under the ground go to below (clipped to the tile), the rest to above.
	private void drawFaces(Graphics2D above, Graphics2D below, Flat flat, int[] sx, int[] sy, boolean[] visible)
	{
		int cycle = client.getGameCycle();
		Polygon triangle = new Polygon(new int[3], new int[3], 3);
		for (int i = 0; i < flat.faces.length; i++)
		{
			int f = flat.faces[i];
			int a = flat.f1[f], b = flat.f2[f], c = flat.f3[f];
			if (!visible[a] || !visible[b] || !visible[c])
			{
				continue;
			}
			// Back faces are culled, as in game.
			if ((sx[a] - sx[b]) * (sy[c] - sy[b]) - (sy[a] - sy[b]) * (sx[c] - sx[b]) <= 0)
			{
				continue;
			}
			triangle.xpoints[0] = sx[a];
			triangle.xpoints[1] = sx[b];
			triangle.xpoints[2] = sx[c];
			triangle.ypoints[0] = sy[a];
			triangle.ypoints[1] = sy[b];
			triangle.ypoints[2] = sy[c];
			triangle.invalidate();

			Graphics2D graphics = flat.height[a] < 0 || flat.height[b] < 0 || flat.height[c] < 0 ? below : above;
			if (flat.textures[i] >= 0)
			{
				// The texture's average color if the texture can't be drawn.
				if (!drawTextured(graphics, flat, i, triangle, cycle))
				{
					graphics.setColor(flat.colors[i]);
					graphics.fill(grown(triangle));
				}
				continue;
			}
			// No outline: it would show as lines along every edge.
			graphics.setPaint(new GouraudPaint(
				new double[]{sx[a], sx[b], sx[c]}, new double[]{sy[a], sy[b], sy[c]},
				new int[]{flat.corners[i * 3], flat.corners[i * 3 + 1], flat.corners[i * 3 + 2]}, flat.colors[i].getAlpha()));
			graphics.fill(grown(triangle));
		}
	}

	// The face's texture, scrolled to this game cycle and darkened by its lighting. False if it can't be drawn.
	private boolean drawTextured(Graphics2D graphics, Flat flat, int i, Polygon triangle, int cycle)
	{
		ModelColors.TextureFill fill = modelColors.textureFill(flat.textures[i]);
		if (fill == null)
		{
			return false;
		}

		// Corners in texture px.
		double offsetU = fill.offsetU(cycle), offsetV = fill.offsetV(cycle);
		double ua = flat.u[i * 3] * fill.size + offsetU, va = flat.v[i * 3] * fill.size + offsetV;
		double ub = flat.u[i * 3 + 1] * fill.size + offsetU, vb = flat.v[i * 3 + 1] * fill.size + offsetV;
		double uc = flat.u[i * 3 + 2] * fill.size + offsetU, vc = flat.v[i * 3 + 2] * fill.size + offsetV;
		AffineTransform toScreen = triangleTransform(ua, va, ub, vb, uc, vc, triangle);
		if (toScreen == null)
		{
			return false;
		}

		// Texture and lighting fill the same grown shape, so lighting never covers a neighbour.
		Shape screen = grown(triangle);
		Shape texture;
		try
		{
			texture = toScreen.createInverse().createTransformedShape(screen);
		}
		catch (NoninvertibleTransformException e)
		{
			return false;
		}

		Graphics2D g = (Graphics2D) graphics.create();
		g.transform(toScreen);
		g.setPaint(fill.paint);
		g.fill(texture);
		g.dispose();

		if (flat.shade[i] > 0)
		{
			graphics.setColor(new Color(0, 0, 0, Math.round(flat.shade[i] * 255)));
			graphics.fill(screen);
		}
		return true;
	}

	// Grown GROW px out from its centroid.
	private static Shape grown(Polygon triangle)
	{
		double mx = (triangle.xpoints[0] + triangle.xpoints[1] + triangle.xpoints[2]) / 3.0;
		double my = (triangle.ypoints[0] + triangle.ypoints[1] + triangle.ypoints[2]) / 3.0;
		Path2D.Double path = new Path2D.Double();
		for (int i = 0; i < 3; i++)
		{
			double dx = triangle.xpoints[i] - mx, dy = triangle.ypoints[i] - my;
			double length = Math.max(1e-6, Math.hypot(dx, dy));
			double x = triangle.xpoints[i] + dx / length * GROW, y = triangle.ypoints[i] + dy / length * GROW;
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

	// Maps the texture triangle onto the screen triangle, or null if degenerate.
	@Nullable
	private static AffineTransform triangleTransform(double ua, double va, double ub, double vb, double uc, double vc, Polygon screen)
	{
		double ux = ub - ua, uy = vb - va, vx = uc - ua, vy = vc - va;
		double det = ux * vy - uy * vx;
		if (Math.abs(det) < 1e-9)
		{
			return null;
		}
		double i00 = vy / det, i01 = -vx / det, i10 = -uy / det, i11 = ux / det;
		double ax = screen.xpoints[0], ay = screen.ypoints[0];
		double bx = screen.xpoints[1] - ax, by = screen.ypoints[1] - ay;
		double cx = screen.xpoints[2] - ax, cy = screen.ypoints[2] - ay;
		AffineTransform transform = new AffineTransform(bx * i00 + cx * i10, by * i00 + cy * i10, bx * i01 + cx * i11, by * i01 + cy * i11,
			ax, ay);
		transform.translate(-ua, -va);
		return transform;
	}

	// Laid face up: x stays east, up (-y) becomes north, the front (-z) faces the sky.
	@Nullable
	private Flat layFlat(Model model, float[] vx, float[] vy, float[] vz)
	{
		int vertexCount = model.getVerticesCount();
		int faceCount = model.getFaceCount();
		int[] f1 = model.getFaceIndices1(), f2 = model.getFaceIndices2(), f3 = model.getFaceIndices3();
		int[] c1 = model.getFaceColors1(), c2 = model.getFaceColors2(), c3 = model.getFaceColors3();
		short[] faceTextures = model.getFaceTextures();
		Color[] faceColors = modelColors.faceColors(model);
		int[] faceCorners = modelColors.cornerColors(model);

		float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minUp = Float.MAX_VALUE, maxUp = -Float.MAX_VALUE;
		float minFront = Float.MAX_VALUE, maxFront = -Float.MAX_VALUE;
		List<Integer> visible = new ArrayList<>();
		for (int f = 0; f < faceCount; f++)
		{
			if (faceColors[f] == null)
			{
				continue;
			}
			visible.add(f);
			for (int v : new int[]{f1[f], f2[f], f3[f]})
			{
				minX = Math.min(minX, vx[v]);
				maxX = Math.max(maxX, vx[v]);
				minUp = Math.min(minUp, -vy[v]);
				maxUp = Math.max(maxUp, -vy[v]);
				minFront = Math.min(minFront, -vz[v]);
				maxFront = Math.max(maxFront, -vz[v]);
			}
		}
		if (maxX <= minX || maxUp <= minUp)
		{
			return null;
		}

		// Back to front by the model's own depth (bottom to top lying face up), right from any camera angle. Each render
		// priority level moves a face forward, so details the game layers by priority (a glory's gem) land on top.
		byte[] priorities = model.getFaceRenderPriorities();
		double priorityStep = (maxFront - minFront) * PRIORITY_STEP * 3;
		visible.sort(Comparator.comparingDouble(f -> -(vz[f1[f]] + vz[f2[f]] + vz[f3[f]])
			+ (priorities == null ? 0 : priorities[f] * priorityStep)));
		int count = visible.size();
		int[] faces = new int[count];
		Color[] colors = new Color[count];
		int[] corners = new int[count * 3];
		int[] textures = new int[count];
		float[] u = new float[count * 3], v = new float[count * 3], shade = new float[count];
		float[] uv = new float[6];
		for (int i = 0; i < count; i++)
		{
			int f = visible.get(i);
			faces[i] = f;
			colors[i] = faceColors[f];
			System.arraycopy(faceCorners, f * 3, corners, i * 3, 3);
			textures[i] = -1;
			if (faceTextures != null && faceTextures[f] != -1 && textureUvs(model, f, uv))
			{
				textures[i] = faceTextures[f];
				for (int corner = 0; corner < 3; corner++)
				{
					u[i * 3 + corner] = uv[corner * 2];
					v[i * 3 + corner] = uv[corner * 2 + 1];
				}
				shade[i] = (float) Math.max(0, Math.min(1, 1 - ModelColors.textureBrightness(c1[f], c2[f], c3[f])));
			}
		}

		double scale = (Perspective.LOCAL_TILE_SIZE - MARGIN * 2) / Math.max(maxX - minX, maxUp - minUp);
		double centerX = (minX + maxX) / 2, centerUp = (minUp + maxUp) / 2;
		double depthScale = scale * (ABOVE + BELOW);
		double ground = maxFront - (maxFront - minFront) * ABOVE / (ABOVE + BELOW);
		int[] east = new int[vertexCount], north = new int[vertexCount], height = new int[vertexCount];
		for (int i = 0; i < vertexCount; i++)
		{
			east[i] = (int) Math.round((vx[i] - centerX) * scale);
			north[i] = (int) Math.round((-vy[i] - centerUp) * scale);
			height[i] = (int) Math.round((-vz[i] - ground) * depthScale);
		}
		return new Flat(east, north, height, faces, colors, corners, textures, u, v, shade, f1, f2, f3);
	}

	// The face's corner u/v into uv, mapped as the game does from its texture triangle P, M, N (its own corners, or one
	// from the model's texture faces). From the unturned vertices. False if degenerate.
	private static boolean textureUvs(Model model, int face, float[] uv)
	{
		float[] x = model.getVerticesX(), y = model.getVerticesY(), z = model.getVerticesZ();
		int a = model.getFaceIndices1()[face], b = model.getFaceIndices2()[face], c = model.getFaceIndices3()[face];
		byte[] textureFaces = model.getTextureFaces();
		int p = a, m = b, n = c;
		if (textureFaces != null && textureFaces[face] != -1)
		{
			int t = textureFaces[face] & 0xFF;
			p = model.getTexIndices1()[t];
			m = model.getTexIndices2()[t];
			n = model.getTexIndices3()[t];
		}

		float pmx = x[m] - x[p], pmy = y[m] - y[p], pmz = z[m] - z[p];
		float pnx = x[n] - x[p], pny = y[n] - y[p], pnz = z[n] - z[p];
		float nx = pmy * pnz - pmz * pny, ny = pmz * pnx - pmx * pnz, nz = pmx * pny - pmy * pnx;

		// u along PM, v along PN.
		float ux = pny * nz - pnz * ny, uy = pnz * nx - pnx * nz, uz = pnx * ny - pny * nx;
		float uScale = ux * pmx + uy * pmy + uz * pmz;
		float wx = pmy * nz - pmz * ny, wy = pmz * nx - pmx * nz, wz = pmx * ny - pmy * nx;
		float vScale = wx * pnx + wy * pny + wz * pnz;
		if (uScale == 0 || vScale == 0)
		{
			return false;
		}

		int[] corners = {a, b, c};
		for (int i = 0; i < 3; i++)
		{
			float dx = x[corners[i]] - x[p], dy = y[corners[i]] - y[p], dz = z[corners[i]] - z[p];
			uv[i * 2] = (ux * dx + uy * dy + uz * dz) / uScale;
			uv[i * 2 + 1] = (wx * dx + wy * dy + wz * dz) / vScale;
		}
		return true;
	}
}
