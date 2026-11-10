package com.groundmarkervariables;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
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

// <npc=ID[,head]>: the NPC's model (or chathead) drawn into a flat-shaded image. Its model is also used on {ground}
// (GroundModelRenderer). Client thread only; cached per NPC.
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

	private final Client client;
	private final ModelColors modelColors;
	private final Map<String, Optional<BufferedImage>> images = new HashMap<>();
	private final Map<String, Optional<Model>> models = new HashMap<>();
	private final Map<Model, float[][]> turnedVertices = new IdentityHashMap<>();

	@Inject
	private NpcImages(Client client, ModelColors modelColors)
	{
		this.client = client;
		this.modelColors = modelColors;
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

		Model model = model(npcId, head);
		float[][] turned = turned(npcId, head);
		BufferedImage image = model == null || turned == null ? null : draw(model, turned);
		images.put(key, Optional.ofNullable(image));
		return image;
	}

	// Lit and recolored as the NPC is.
	@Nullable
	Model model(int npcId, boolean head)
	{
		String key = npcId + (head ? ",head" : "");
		Optional<Model> cached = models.get(key);
		if (cached != null)
		{
			return cached.orElse(null);
		}

		Model model = build(npcId, head);
		models.put(key, Optional.ofNullable(model));
		return model;
	}

	@Nullable
	private Model build(int npcId, boolean head)
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

		return merged.light();
	}

	// Vertices turned to the image's three-quarter view, {x right, y down, z away}; shared with {ground}.
	@Nullable
	float[][] turned(int npcId, boolean head)
	{
		Model model = model(npcId, head);
		return model == null ? null : turnedVertices.computeIfAbsent(model, m -> turn(m, head ? HEAD_YAW : BODY_YAW, head ? HEAD_PITCH : BODY_PITCH));
	}

	private static float[][] turn(Model model, double yaw, double pitch)
	{
		int vertexCount = model.getVerticesCount();
		float[] vx = model.getVerticesX(), vy = model.getVerticesY(), vz = model.getVerticesZ();
		float[] x = new float[vertexCount], y = new float[vertexCount], z = new float[vertexCount];
		double cosYaw = Math.cos(yaw), sinYaw = Math.sin(yaw), cosPitch = Math.cos(pitch), sinPitch = Math.sin(pitch);
		for (int i = 0; i < vertexCount; i++)
		{
			double turnedX = vx[i] * cosYaw - vz[i] * sinYaw;
			double turnedZ = vx[i] * sinYaw + vz[i] * cosYaw;
			x[i] = (float) turnedX;
			y[i] = (float) (vy[i] * cosPitch - turnedZ * sinPitch);
			z[i] = (float) (vy[i] * sinPitch + turnedZ * cosPitch);
		}
		return new float[][]{x, y, z};
	}

	// Orthographic: x right, y down (as the model's own y), viewed from the south looking north.
	@Nullable
	private BufferedImage draw(Model model, float[][] turned)
	{
		Color[] colors = modelColors.faceColors(model);
		float[] sx = turned[0], sy = turned[1], depth = turned[2];

		int faceCount = model.getFaceCount();
		int[] f1 = model.getFaceIndices1(), f2 = model.getFaceIndices2(), f3 = model.getFaceIndices3();

		// Visible faces, and the bounds they cover.
		List<Integer> faces = new ArrayList<>();
		double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
		for (int f = 0; f < faceCount; f++)
		{
			if (colors[f] == null)
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

		// Farthest first; each priority level a little nearer (see GroundModelRenderer.PRIORITY_STEP).
		double minDepth = Double.MAX_VALUE, maxDepth = -Double.MAX_VALUE;
		for (float d : depth)
		{
			minDepth = Math.min(minDepth, d);
			maxDepth = Math.max(maxDepth, d);
		}
		byte[] priorities = model.getFaceRenderPriorities();
		double priorityStep = (maxDepth - minDepth) * GroundModelRenderer.PRIORITY_STEP * 3;
		faces.sort(Comparator.comparingDouble((Integer f) -> -(depth[f1[f]] + depth[f2[f]] + depth[f3[f]])
			+ (priorities == null ? 0 : priorities[f] * priorityStep)));

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
			g.setColor(colors[f]);
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
}
