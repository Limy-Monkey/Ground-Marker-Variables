package com.groundmarkervariables;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPCComposition;

// <npc=ID[,head]>: the NPC's model (or chathead) drawn into an image from the game's own model data, flat-shaded,
// nearest faces last. Client thread only; cached per NPC.
@Singleton
class NpcImages
{
	private static final int SIZE = 64;
	// Drawn this many times larger, then scaled down, which smooths the edges.
	private static final int SUPERSAMPLE = 4;
	// Turned this far from straight on, and looked down on this much, for a three-quarter view.
	private static final double BODY_YAW = Math.toRadians(-30);
	private static final double BODY_PITCH = Math.toRadians(-15);
	private static final double HEAD_YAW = Math.toRadians(-15);
	private static final double HEAD_PITCH = Math.toRadians(-5);
	private static final double BRIGHTNESS = 0.8;

	private final Client client;
	private final Map<String, Optional<BufferedImage>> images = new HashMap<>();

	@Inject
	private NpcImages(Client client)
	{
		this.client = client;
	}

	@Nullable
	BufferedImage image(int npcId, boolean head)
	{
		String key = npcId + (head ? ",head" : "");
		Optional<BufferedImage> cached = images.get(key);
		if (cached != null)
		{
			return cached.orElse(null);
		}

		BufferedImage image = render(npcId, head);
		images.put(key, Optional.ofNullable(image));
		return image;
	}

	@Nullable
	private BufferedImage render(int npcId, boolean head)
	{
		NPCComposition npc = client.getNpcDefinition(npcId);
		if (npc == null)
		{
			return null;
		}

		int[] modelIds = head ? npc.getChatheadModels() : npc.getModels();
		if (modelIds == null || modelIds.length == 0)
		{
			return null;
		}

		List<ModelData> parts = new ArrayList<>();
		for (int id : modelIds)
		{
			ModelData part = client.loadModelData(id);
			if (part == null)
			{
				return null;
			}
			parts.add(part);
		}

		// A fresh merged copy, so recoloring can't touch the game's cached model data.
		ModelData merged = client.mergeModels(parts.toArray(new ModelData[0])).cloneColors();
		short[] from = npc.getColorToReplace();
		short[] to = npc.getColorToReplaceWith();
		if (from != null && to != null)
		{
			for (int i = 0; i < Math.min(from.length, to.length); i++)
			{
				merged.recolor(from[i], to[i]);
			}
		}

		Model model = merged.light();
		return model == null ? null : draw(model, head ? HEAD_YAW : BODY_YAW, head ? HEAD_PITCH : BODY_PITCH);
	}

	// Orthographic: x right, y down (as the model's own y), viewed from the south looking north.
	@Nullable
	private static BufferedImage draw(Model model, double yaw, double pitch)
	{
		int vertexCount = model.getVerticesCount();
		float[] vx = model.getVerticesX();
		float[] vy = model.getVerticesY();
		float[] vz = model.getVerticesZ();
		double[] sx = new double[vertexCount];
		double[] sy = new double[vertexCount];
		double[] depth = new double[vertexCount];
		double cosYaw = Math.cos(yaw), sinYaw = Math.sin(yaw), cosPitch = Math.cos(pitch), sinPitch = Math.sin(pitch);
		for (int i = 0; i < vertexCount; i++)
		{
			double x = vx[i] * cosYaw - vz[i] * sinYaw;
			double z = vx[i] * sinYaw + vz[i] * cosYaw;
			double y = vy[i] * cosPitch - z * sinPitch;
			sx[i] = x;
			sy[i] = y;
			depth[i] = vy[i] * sinPitch + z * cosPitch;
		}

		int faceCount = model.getFaceCount();
		int[] f1 = model.getFaceIndices1(), f2 = model.getFaceIndices2(), f3 = model.getFaceIndices3();
		int[] c1 = model.getFaceColors1(), c2 = model.getFaceColors2(), c3 = model.getFaceColors3();
		byte[] alphas = model.getFaceTransparencies();

		// Visible faces, and the bounds they cover.
		List<Integer> faces = new ArrayList<>();
		double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
		for (int f = 0; f < faceCount; f++)
		{
			if (c3[f] == -2 || (alphas != null && (alphas[f] & 0xFF) >= 254))
			{
				continue;
			}
			faces.add(f);
			for (int v : new int[]{f1[f], f2[f], f3[f]})
			{
				minX = Math.min(minX, sx[v]);
				maxX = Math.max(maxX, sx[v]);
				minY = Math.min(minY, sy[v]);
				maxY = Math.max(maxY, sy[v]);
			}
		}
		if (faces.isEmpty() || maxX <= minX || maxY <= minY)
		{
			return null;
		}

		// Farthest first.
		faces.sort((a, b) -> Double.compare(depth[f1[b]] + depth[f2[b]] + depth[f3[b]], depth[f1[a]] + depth[f2[a]] + depth[f3[a]]));

		int big = SIZE * SUPERSAMPLE;
		double scale = (big - SUPERSAMPLE * 2) / Math.max(maxX - minX, maxY - minY);
		double offsetX = (big - (maxX - minX) * scale) / 2 - minX * scale;
		double offsetY = (big - (maxY - minY) * scale) / 2 - minY * scale;

		BufferedImage canvas = new BufferedImage(big, big, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = canvas.createGraphics();
		for (int f : faces)
		{
			int a = f1[f], b = f2[f], c = f3[f];
			Polygon triangle = new Polygon(
				new int[]{(int) Math.round(sx[a] * scale + offsetX), (int) Math.round(sx[b] * scale + offsetX), (int) Math.round(sx[c] * scale + offsetX)},
				new int[]{(int) Math.round(sy[a] * scale + offsetY), (int) Math.round(sy[b] * scale + offsetY), (int) Math.round(sy[c] * scale + offsetY)},
				3);
			int alpha = alphas == null ? 255 : 255 - (alphas[f] & 0xFF);
			g.setColor(faceColor(c1[f], c2[f], c3[f], alpha));
			g.fill(triangle);
			// The outline too, closing hairline gaps between neighbouring faces.
			g.draw(triangle);
		}
		g.dispose();

		BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D small = out.createGraphics();
		small.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		small.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		small.drawImage(canvas.getScaledInstance(SIZE, SIZE, java.awt.Image.SCALE_AREA_AVERAGING), 0, 0, null);
		small.dispose();
		return out;
	}

	// The face's lit colors averaged (flat if c3 is -1).
	private static Color faceColor(int c1, int c2, int c3, int alpha)
	{
		int rgb1 = hslToRgb(c1);
		if (c3 == -1)
		{
			return new Color(rgb1 >> 16 & 0xFF, rgb1 >> 8 & 0xFF, rgb1 & 0xFF, alpha);
		}
		int rgb2 = hslToRgb(c2), rgb3 = hslToRgb(c3);
		return new Color(((rgb1 >> 16 & 0xFF) + (rgb2 >> 16 & 0xFF) + (rgb3 >> 16 & 0xFF)) / 3,
			((rgb1 >> 8 & 0xFF) + (rgb2 >> 8 & 0xFF) + (rgb3 >> 8 & 0xFF)) / 3,
			((rgb1 & 0xFF) + (rgb2 & 0xFF) + (rgb3 & 0xFF)) / 3, alpha);
	}

	// The game's packed 16-bit HSL (6 hue, 3 saturation, 7 lightness bits) to RGB, as its own palette builds it.
	private static int hslToRgb(int hsl)
	{
		double hue = (hsl >> 10 & 63) / 64.0 + 0.0078125;
		double saturation = (hsl >> 7 & 7) / 8.0 + 0.0625;
		double lightness = (hsl & 127) / 128.0;
		double r = lightness, g = lightness, b = lightness;
		if (saturation != 0)
		{
			double q = lightness < 0.5 ? lightness * (1 + saturation) : lightness + saturation - lightness * saturation;
			double p = 2 * lightness - q;
			r = hueToChannel(p, q, hue + 1 / 3.0);
			g = hueToChannel(p, q, hue);
			b = hueToChannel(p, q, hue - 1 / 3.0);
		}
		int ri = (int) (Math.pow(r, BRIGHTNESS) * 256), gi = (int) (Math.pow(g, BRIGHTNESS) * 256), bi = (int) (Math.pow(b, BRIGHTNESS) * 256);
		return Math.min(255, ri) << 16 | Math.min(255, gi) << 8 | Math.min(255, bi);
	}

	private static double hueToChannel(double p, double q, double t)
	{
		t = t < 0 ? t + 1 : t > 1 ? t - 1 : t;
		if (t < 1 / 6.0)
		{
			return p + (q - p) * 6 * t;
		}
		if (t < 0.5)
		{
			return q;
		}
		if (t < 2 / 3.0)
		{
			return p + (q - p) * (2 / 3.0 - t) * 6;
		}
		return p;
	}
}
