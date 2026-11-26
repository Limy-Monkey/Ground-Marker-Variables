package com.groundmarkervariables.graphics;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPCComposition;

// <npc=ID[,head]>: the NPC's model (or chathead) drawn into a flat-shaded image. Its model is also used on {ground}
// (GroundModelRenderer). Client thread only; cached per NPC.
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class NpcImages {
    private static final int SIZE = 64;
    // Drawn this many times larger, then scaled down, which smooths the edges.
    private static final int SUPERSAMPLE = 4;
    // Turned this far from straight on, and looked down on this much, for a three-quarter view (see ItemModels#turned).
    private static final double BODY_YAW = Math.toRadians(30);
    private static final double BODY_PITCH = Math.toRadians(-15);
    private static final double HEAD_YAW = Math.toRadians(15);
    private static final double HEAD_PITCH = Math.toRadians(-5);

    private final Client client;
    private final ModelColors modelColors;
    private final Map<String, Optional<BufferedImage>> images = new HashMap<>();
    private final Map<String, Optional<Model>> models = new HashMap<>();
    private final Map<Model, float[][]> turnedVertices = new IdentityHashMap<>();

    @Nullable
    public BufferedImage image(int npcId, boolean head) {
        return images.computeIfAbsent(npcId + (head ? ",head" : ""), k -> {
            Model model = model(npcId, head);
            float[][] turned = turned(npcId, head);
            return Optional.ofNullable(model == null || turned == null ? null : draw(model, turned));
        }).orElse(null);
    }

    // Lit and recolored as the NPC is.
    @Nullable
    public Model model(int npcId, boolean head) {
        return models.computeIfAbsent(npcId + (head ? ",head" : ""), k -> Optional.ofNullable(build(npcId, head))).orElse(null);
    }

    @Nullable
    private Model build(int npcId, boolean head) {
        NPCComposition npc = client.getNpcDefinition(npcId);
        if (npc == null) {
            return null;
        }

        int[] modelIds = head ? npc.getChatheadModels() : npc.getModels();
        if (modelIds == null || modelIds.length == 0) {
            return null;
        }

        List<ModelData> parts = new ArrayList<>();
        for (int id : modelIds) {
            ModelData part = client.loadModelData(id);
            if (part == null) {
                return null;
            }
            parts.add(part);
        }

        // A fresh merged copy, so recoloring can't touch the game's cached model data.
        ModelData merged = client.mergeModels(parts.toArray(new ModelData[0])).cloneColors();
        ModelColors.recolor(merged, npc.getColorToReplace(), npc.getColorToReplaceWith());
        return merged.light();
    }

    // Vertices turned to the image's three-quarter view, {x right, y down, z away}; shared with {ground}.
    @Nullable
    public float[][] turned(int npcId, boolean head) {
        Model model = model(npcId, head);
        return model == null ? null : turnedVertices.computeIfAbsent(model, m -> ItemModels.turned(m, head ? HEAD_YAW : BODY_YAW, 0,
            head ? HEAD_PITCH : BODY_PITCH));
    }

    // Orthographic: x right, y down (as the model's own y), viewed from the south looking north.
    @Nullable
    private BufferedImage draw(Model model, float[][] turned) {
        Color[] colors = modelColors.faceColors(model);
        float[] sx = turned[0], sy = turned[1];
        int[] f1 = model.getFaceIndices1(), f2 = model.getFaceIndices2(), f3 = model.getFaceIndices3();

        // The faces drawn, farthest first, and the bounds they cover.
        double[] bounds = new double[6];
        List<Integer> faces = GroundModelRenderer.sortedFaces(model, colors, sx, sy, turned[2], bounds);
        double minX = bounds[0], maxX = bounds[1], minY = bounds[2], maxY = bounds[3];
        if (faces.isEmpty() || maxX <= minX || maxY <= minY) {
            return null;
        }

        int big = SIZE * SUPERSAMPLE;
        double scale = (big - SUPERSAMPLE * 2) / Math.max(maxX - minX, maxY - minY);
        double offsetX = (big - (maxX - minX) * scale) / 2 - minX * scale;
        double offsetY = (big - (maxY - minY) * scale) / 2 - minY * scale;

        BufferedImage canvas = new BufferedImage(big, big, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        for (int f : faces) {
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
