package com.groundmarkervariables.graphics;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class GroundModelRenderer {
    // Space around the model inside the tile, in local units.
    private static final int MARGIN = 8;
    // Shares of the model's depth over and under the ground.
    private static final double ABOVE = 0.05;
    private static final double BELOW = 0.1;
    // Share of the model's depth each render priority level moves a face forward when sorting.
    private static final double PRIORITY_STEP = 0.01;
    // Textured faces are grown this many px, closing hairline gaps between neighbours.
    private static final double GROW = 0.7;
    // How far outside a triangle a pixel center may sit and still count, so shared edges leave no gaps.
    private static final double EDGE = 1e-9;
    // Models over SUPERSAMPLE_MIN_SIZE px across on screen are shaded at SUPERSAMPLE times the size, then scaled down, for
    // smooth edges; smaller ones at screen size.
    private static final int SUPERSAMPLE = 2;
    private static final int SUPERSAMPLE_MIN_SIZE = 80;

    private final Client client;
    private final ModelColors modelColors;
    // Keyed by the (cached) model.
    private final Map<Model, Flat> flats = new IdentityHashMap<>();
    // Shared by both passes of every model; client thread only.
    private BufferedImage buffer;
    private int[] bufferPixels;

    // The model laid flat: each vertex's offset from the tile center and height, in local units, and per face to draw
    // (back to front) its color, three corner colors, and texture (-1 none) with corner u/v (0..1) and darkening.
    @RequiredArgsConstructor
    private static final class Flat {
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
    }

    // E.g. with Parallax icons turned off.
    public void clear() {
        flats.clear();
        buffer = null;
        bufferPixels = null;
    }

    // model's faces and colors, with its vertices turned to x, y, z (x right, y down, front toward -z). Shrunk by scale
    // and moved offsetNorth local units north, in a frame turned facing degrees clockwise so the model's top faces that
    // way. onGround runs once, between the faces under the ground and those over it.
    public void draw(Graphics2D graphics, LocalPoint center, int plane, Model model, float[] x, float[] y, float[] z, float opacity,
        int facing, double scale, double offsetNorth, Runnable onGround) {
        Flat flat = flats.computeIfAbsent(model, m -> layFlat(m, x, y, z));
        if (flat == null) {
            onGround.run();
            return;
        }

        int vertexCount = flat.east.length;
        int[] sx = new int[vertexCount];
        int[] sy = new int[vertexCount];
        boolean[] visible = new boolean[vertexCount];
        Polygon area = new Polygon();
        double angle = Math.toRadians(facing);
        double sin = Math.sin(angle), cos = Math.cos(angle);
        for (int i = 0; i < vertexCount; i++) {
            double east = flat.east[i] * scale, north = flat.north[i] * scale + offsetNorth;
            LocalPoint point = new LocalPoint(center.getX() + (int) Math.round(east * cos + north * sin),
                center.getY() + (int) Math.round(-east * sin + north * cos), center.getWorldView());
            Point p = Perspective.localToCanvas(client, point, plane, (int) Math.round(flat.height[i] * scale));
            if (p != null) {
                visible[i] = true;
                sx[i] = p.getX();
                sy[i] = p.getY();
                area.addPoint(p.getX(), p.getY());
            }
        }

        Polygon tile = Perspective.getCanvasTilePoly(client, center);
        Rectangle bounds = tile == null ? null : GroundImageRenderer.onCanvas(client, area, 1);
        if (bounds == null) {
            onGround.run();
            return;
        }
        int samples = Math.max(bounds.width, bounds.height) > SUPERSAMPLE_MIN_SIZE ? SUPERSAMPLE : 1;
        drawPass(graphics, flat, sx, sy, visible, bounds, samples, true, tile, opacity);
        onGround.run();
        drawPass(graphics, flat, sx, sy, visible, bounds, samples, false, null, opacity);
    }

    // The faces with a corner under the ground, or the rest, shaded into the buffer at samples times the size, then drawn
    // once (clipped to clip if any), scaled down so edges come out smooth.
    private void drawPass(Graphics2D graphics, Flat flat, int[] sx, int[] sy, boolean[] visible, Rectangle bounds, int samples,
        boolean underground, @Nullable Shape clip, float opacity) {
        int width = bounds.width * samples, height = bounds.height * samples;
        int[] pixels = buffer(width, height);
        int stride = buffer.getWidth();
        int cycle = client.getGameCycle();
        Polygon triangle = new Polygon(new int[3], new int[3], 3);
        Graphics2D textured = null;
        boolean drawn = false;
        for (int i = 0; i < flat.faces.length; i++) {
            int f = flat.faces[i];
            int a = flat.f1[f], b = flat.f2[f], c = flat.f3[f];
            if (!visible[a] || !visible[b] || !visible[c]
                || (flat.height[a] < 0 || flat.height[b] < 0 || flat.height[c] < 0) != underground) {
                continue;
            }
            // Back faces are culled, as in game.
            if ((sx[a] - sx[b]) * (sy[c] - sy[b]) - (sy[a] - sy[b]) * (sx[c] - sx[b]) <= 0) {
                continue;
            }
            drawn = true;

            if (flat.textures[i] >= 0) {
                // Through Java2D, into the same buffer in screen coordinates, so faces stay in order.
                if (textured == null) {
                    textured = buffer.createGraphics();
                    textured.scale(samples, samples);
                    textured.translate(-bounds.x, -bounds.y);
                }
                triangle.xpoints[0] = sx[a];
                triangle.xpoints[1] = sx[b];
                triangle.xpoints[2] = sx[c];
                triangle.ypoints[0] = sy[a];
                triangle.ypoints[1] = sy[b];
                triangle.ypoints[2] = sy[c];
                triangle.invalidate();
                // The texture's average color if the texture can't be drawn.
                if (!drawTextured(textured, flat, i, triangle, cycle)) {
                    textured.setColor(flat.colors[i]);
                    textured.fill(grown(triangle));
                }
                continue;
            }
            fillGouraud(pixels, stride, width, height,
                (sx[a] - bounds.x) * samples, (sy[a] - bounds.y) * samples,
                (sx[b] - bounds.x) * samples, (sy[b] - bounds.y) * samples,
                (sx[c] - bounds.x) * samples, (sy[c] - bounds.y) * samples,
                flat.corners[i * 3], flat.corners[i * 3 + 1], flat.corners[i * 3 + 2], flat.colors[i].getAlpha());
        }
        if (textured != null) {
            textured.dispose();
        }
        if (!drawn) {
            return;
        }

        Graphics2D g = (Graphics2D) graphics.create();
        if (clip != null) {
            g.clip(clip);
        }
        if (opacity < 1f) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
        }
        if (samples > 1) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        g.drawImage(buffer, bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, 0, 0, width, height, null);
        g.dispose();
    }

    // The shared buffer's pixels, grown if needed and cleared over width x height.
    private int[] buffer(int width, int height) {
        if (buffer == null || buffer.getWidth() < width || buffer.getHeight() < height) {
            buffer = new BufferedImage(Math.max(width, buffer == null ? 0 : buffer.getWidth()),
                Math.max(height, buffer == null ? 0 : buffer.getHeight()), BufferedImage.TYPE_INT_ARGB_PRE);
            bufferPixels = ((DataBufferInt) buffer.getRaster().getDataBuffer()).getData();
        }
        int stride = buffer.getWidth();
        for (int row = 0; row < height; row++) {
            Arrays.fill(bufferPixels, row * stride, row * stride + width, 0);
        }
        return bufferPixels;
    }

    // A triangle's corner colors (0xRRGGBB) blended across it, as the game shades models, over the pixels whose centers
    // it covers; alpha over what's there. Premultiplied pixels.
    private static void fillGouraud(int[] pixels, int stride, int width, int height, double ax, double ay, double bx, double by,
        double cx, double cy, int ca, int cb, int cc, int alpha) {
        double area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
        if (Math.abs(area) < 1e-9) {
            return;
        }
        double inv = 1 / area;
        int minX = Math.max(0, (int) Math.floor(Math.min(ax, Math.min(bx, cx))));
        int maxX = Math.min(width - 1, (int) Math.ceil(Math.max(ax, Math.max(bx, cx))));
        int minY = Math.max(0, (int) Math.floor(Math.min(ay, Math.min(by, cy))));
        int maxY = Math.min(height - 1, (int) Math.ceil(Math.max(ay, Math.max(by, cy))));

        // Each corner's weight is the area opposite it as a share of the whole, linear across a row: its step per pixel,
        // and the color's.
        double dwa = (by - cy) * inv, dwb = (cy - ay) * inv, dwc = -dwa - dwb;
        int ra = ca >> 16 & 0xFF, ga = ca >> 8 & 0xFF, ba = ca & 0xFF;
        int rb = cb >> 16 & 0xFF, gb = cb >> 8 & 0xFF, bb = cb & 0xFF;
        int rc = cc >> 16 & 0xFF, gc = cc >> 8 & 0xFF, bc = cc & 0xFF;
        double dr = ra * dwa + rb * dwb + rc * dwc;
        double dg = ga * dwa + gb * dwb + gc * dwc;
        double db = ba * dwa + bb * dwb + bc * dwc;
        int inverse = 255 - alpha;
        double[] starts = new double[3], steps = {dwa, dwb, dwc};
        for (int y = minY; y <= maxY; y++) {
            // Weights at this row's first pixel center (x = 0).
            double py = y + 0.5;
            double wa = ((bx - 0.5) * (cy - py) - (by - py) * (cx - 0.5)) * inv;
            double wb = ((cx - 0.5) * (ay - py) - (cy - py) * (ax - 0.5)) * inv;
            double wc = 1 - wa - wb;

            // The run of pixels whose centers every weight keeps inside.
            double lo = minX, hi = maxX;
            starts[0] = wa;
            starts[1] = wb;
            starts[2] = wc;
            for (int k = 0; k < 3; k++) {
                double edge = (-EDGE - starts[k]) / steps[k];
                if (steps[k] > 0) {
                    lo = Math.max(lo, edge);
                } else if (steps[k] < 0) {
                    hi = Math.min(hi, edge);
                } else if (starts[k] < -EDGE) {
                    hi = lo - 1;
                }
            }
            int start = (int) Math.ceil(lo), end = (int) Math.floor(hi);
            if (start > end) {
                continue;
            }

            double r = ra * (wa + dwa * start) + rb * (wb + dwb * start) + rc * (wc + dwc * start);
            double g = ga * (wa + dwa * start) + gb * (wb + dwb * start) + gc * (wc + dwc * start);
            double b = ba * (wa + dwa * start) + bb * (wb + dwb * start) + bc * (wc + dwc * start);
            int index = y * stride + start;
            for (int x = start; x <= end; x++, index++, r += dr, g += dg, b += db) {
                int red = clamp(r), green = clamp(g), blue = clamp(b);
                if (alpha == 255) {
                    pixels[index] = 0xFF000000 | red << 16 | green << 8 | blue;
                    continue;
                }
                int dst = pixels[index];
                pixels[index] = (alpha + (dst >>> 24) * inverse / 255) << 24
                    | (red * alpha + (dst >> 16 & 0xFF) * inverse) / 255 << 16
                    | (green * alpha + (dst >> 8 & 0xFF) * inverse) / 255 << 8
                    | (blue * alpha + (dst & 0xFF) * inverse) / 255;
            }
        }
    }

    // Rounded into 0..255.
    private static int clamp(double value) {
        int rounded = (int) (value + 0.5);
        return rounded < 0 ? 0 : Math.min(rounded, 255);
    }

    // The face's texture, scrolled to this game cycle and darkened by its lighting. False if it can't be drawn.
    private boolean drawTextured(Graphics2D graphics, Flat flat, int i, Polygon triangle, int cycle) {
        ModelColors.TextureFill fill = modelColors.textureFill(flat.textures[i]);
        if (fill == null) {
            return false;
        }

        // Corners in texture px.
        double offsetU = fill.offsetU(cycle), offsetV = fill.offsetV(cycle);
        double ua = flat.u[i * 3] * fill.size + offsetU, va = flat.v[i * 3] * fill.size + offsetV;
        double ub = flat.u[i * 3 + 1] * fill.size + offsetU, vb = flat.v[i * 3 + 1] * fill.size + offsetV;
        double uc = flat.u[i * 3 + 2] * fill.size + offsetU, vc = flat.v[i * 3 + 2] * fill.size + offsetV;
        AffineTransform toScreen = triangleTransform(ua, va, ub, vb, uc, vc, triangle);
        if (toScreen == null) {
            return false;
        }

        // Texture and lighting fill the same grown shape, so lighting never covers a neighbour.
        Shape screen = grown(triangle);
        Shape texture;
        try {
            texture = toScreen.createInverse().createTransformedShape(screen);
        } catch (NoninvertibleTransformException e) {
            return false;
        }

        Graphics2D g = (Graphics2D) graphics.create();
        g.transform(toScreen);
        g.setPaint(fill.paint);
        g.fill(texture);
        g.dispose();

        if (flat.shade[i] > 0) {
            graphics.setColor(new Color(0, 0, 0, Math.round(flat.shade[i] * 255)));
            graphics.fill(screen);
        }
        return true;
    }

    // Grown GROW px out from its centroid.
    private static Shape grown(Polygon triangle) {
        double mx = (triangle.xpoints[0] + triangle.xpoints[1] + triangle.xpoints[2]) / 3.0;
        double my = (triangle.ypoints[0] + triangle.ypoints[1] + triangle.ypoints[2]) / 3.0;
        Path2D.Double path = new Path2D.Double();
        for (int i = 0; i < 3; i++) {
            double dx = triangle.xpoints[i] - mx, dy = triangle.ypoints[i] - my;
            double length = Math.max(1e-6, Math.hypot(dx, dy));
            double x = triangle.xpoints[i] + dx / length * GROW, y = triangle.ypoints[i] + dy / length * GROW;
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.closePath();
        return path;
    }

    // Maps the texture triangle onto the screen triangle, or null if degenerate.
    @Nullable
    private static AffineTransform triangleTransform(double ua, double va, double ub, double vb, double uc, double vc, Polygon screen) {
        double ux = ub - ua, uy = vb - va, vx = uc - ua, vy = vc - va;
        double det = ux * vy - uy * vx;
        if (Math.abs(det) < 1e-9) {
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

    // The faces drawn (colors not null), farthest first by z (the front toward -z), each render priority level moving a face
    // PRIORITY_STEP of their depth nearer, so details the game layers by priority (a glory's gem) land on top. Their x, y
    // and z bounds into bounds, {min x, max x, min y, max y, min z, max z}. Also NpcImages'.
    static List<Integer> sortedFaces(Model model, Color[] colors, float[] x, float[] y, float[] z, double[] bounds) {
        int[] f1 = model.getFaceIndices1(), f2 = model.getFaceIndices2(), f3 = model.getFaceIndices3();
        Arrays.fill(bounds, Double.MAX_VALUE);
        bounds[1] = bounds[3] = bounds[5] = -Double.MAX_VALUE;
        List<Integer> faces = new ArrayList<>();
        for (int f = 0; f < model.getFaceCount(); f++) {
            if (colors[f] == null) {
                continue;
            }
            faces.add(f);
            for (int v : new int[]{f1[f], f2[f], f3[f]}) {
                float[] axes = {x[v], y[v], z[v]};
                for (int axis = 0; axis < 3; axis++) {
                    bounds[axis * 2] = Math.min(bounds[axis * 2], axes[axis]);
                    bounds[axis * 2 + 1] = Math.max(bounds[axis * 2 + 1], axes[axis]);
                }
            }
        }
        byte[] priorities = model.getFaceRenderPriorities();
        double step = (bounds[5] - bounds[4]) * PRIORITY_STEP * 3;
        faces.sort(Comparator.comparingDouble(f -> -(z[f1[f]] + z[f2[f]] + z[f3[f]]) + (priorities == null ? 0 : priorities[f] * step)));
        return faces;
    }

    // Laid face up: x stays east, up (-y) becomes north, the front (-z) faces the sky.
    @Nullable
    private Flat layFlat(Model model, float[] vx, float[] vy, float[] vz) {
        int vertexCount = model.getVerticesCount();
        int faceCount = model.getFaceCount();
        int[] f1 = model.getFaceIndices1(), f2 = model.getFaceIndices2(), f3 = model.getFaceIndices3();
        int[] c1 = model.getFaceColors1(), c2 = model.getFaceColors2(), c3 = model.getFaceColors3();
        short[] faceTextures = model.getFaceTextures();
        Color[] faceColors = modelColors.faceColors(model);
        int[] faceCorners = modelColors.cornerColors(model);

        // Back to front by the model's own depth: bottom to top lying face up, right from any camera angle.
        double[] bounds = new double[6];
        List<Integer> visible = sortedFaces(model, faceColors, vx, vy, vz, bounds);
        double minX = bounds[0], maxX = bounds[1], minUp = -bounds[3], maxUp = -bounds[2], minFront = -bounds[5], maxFront = -bounds[4];
        if (maxX <= minX || maxUp <= minUp) {
            return null;
        }
        int count = visible.size();
        int[] faces = new int[count];
        Color[] colors = new Color[count];
        int[] corners = new int[count * 3];
        int[] textures = new int[count];
        float[] u = new float[count * 3], v = new float[count * 3], shade = new float[count];
        float[] uv = new float[6];
        for (int i = 0; i < count; i++) {
            int f = visible.get(i);
            faces[i] = f;
            colors[i] = faceColors[f];
            System.arraycopy(faceCorners, f * 3, corners, i * 3, 3);
            textures[i] = -1;
            if (faceTextures != null && faceTextures[f] != -1 && textureUvs(model, f, uv)) {
                textures[i] = faceTextures[f];
                for (int corner = 0; corner < 3; corner++) {
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
        for (int i = 0; i < vertexCount; i++) {
            east[i] = (int) Math.round((vx[i] - centerX) * scale);
            north[i] = (int) Math.round((-vy[i] - centerUp) * scale);
            height[i] = (int) Math.round((-vz[i] - ground) * depthScale);
        }
        return new Flat(east, north, height, faces, colors, corners, textures, u, v, shade, f1, f2, f3);
    }

    // The face's corner u/v into uv, mapped as the game does from its texture triangle P, M, N (its own corners, or one
    // from the model's texture faces). From the unturned vertices. False if degenerate.
    private static boolean textureUvs(Model model, int face, float[] uv) {
        float[] x = model.getVerticesX(), y = model.getVerticesY(), z = model.getVerticesZ();
        int a = model.getFaceIndices1()[face], b = model.getFaceIndices2()[face], c = model.getFaceIndices3()[face];
        byte[] textureFaces = model.getTextureFaces();
        int p = a, m = b, n = c;
        if (textureFaces != null && textureFaces[face] != -1) {
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
        if (uScale == 0 || vScale == 0) {
            return false;
        }

        int[] corners = {a, b, c};
        for (int i = 0; i < 3; i++) {
            float dx = x[corners[i]] - x[p], dy = y[corners[i]] - y[p], dz = z[corners[i]] - z[p];
            uv[i * 2] = (ux * dx + uy * dy + uz * dz) / uScale;
            uv[i * 2 + 1] = (wx * dx + wy * dy + wz * dz) / vScale;
        }
        return true;
    }
}
