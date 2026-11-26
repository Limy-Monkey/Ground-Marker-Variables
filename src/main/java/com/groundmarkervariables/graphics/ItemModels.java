package com.groundmarkervariables.graphics;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.Model;
import net.runelite.api.ModelData;

// {ground}<item=ID>: the item's inventory model, lit and recolored as in game. Equippable items lie as when dropped,
// others as their inventory icon. Client thread only; cached per item.
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ItemModels {
    // The game's item lighting, before each item's own ambient/contrast.
    private static final int AMBIENT = 64;
    private static final int CONTRAST = 768;
    private static final int LIGHT_X = -50;
    private static final int LIGHT_Y = -10;
    private static final int LIGHT_Z = -50;
    private static final double ANGLE_UNIT = Math.PI * 2 / 2048;

    private final Client client;
    private final Map<Integer, Optional<Oriented>> models = new HashMap<>();

    // A lit model and its turned vertices: x right, y down, front toward -z.
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Oriented {
        public final Model model;
        public final float[] x;
        public final float[] y;
        public final float[] z;
    }

    // E.g. with Parallax icons turned off.
    public void clear() {
        models.clear();
    }

    @Nullable
    public Oriented model(int itemId) {
        return models.computeIfAbsent(itemId, id -> Optional.ofNullable(build(id))).orElse(null);
    }

    @Nullable
    private Oriented build(int itemId) {
        ItemComposition item = client.getItemDefinition(itemId);
        if (item == null || item.getInventoryModel() <= 0) {
            return null;
        }

        ModelData data = client.loadModelData(item.getInventoryModel());
        if (data == null) {
            return null;
        }

        // A copy, so recoloring can't touch the game's cached model data.
        data = client.mergeModels(data).cloneColors();
        short[] from = item.getColorToReplace();
        short[] to = item.getColorToReplaceWith();
        if (from != null && to != null) {
            for (int i = 0; i < Math.min(from.length, to.length); i++) {
                data.recolor(from[i], to[i]);
            }
        }

        Model model = data.light(AMBIENT + item.getAmbient(), CONTRAST + item.getContrast(), LIGHT_X, LIGHT_Y, LIGHT_Z);
        if (model == null) {
            return null;
        }

        // As dropped: the model as built (y down, z north), pitched a quarter turn so laying it flat keeps east, north and up.
        // Else as the inventory icon.
        float[][] turned = equippable(item) ? turned(model, 0, 0, Math.PI / 2)
            : turned(model, item.getYan2d() * ANGLE_UNIT, item.getZan2d() * ANGLE_UNIT, item.getXan2d() * ANGLE_UNIT);
        return new Oriented(model, turned[0], turned[1], turned[2]);
    }

    // model's vertices turned by yaw (around y), roll (around z), then pitch (around x): {x, y, z}. Also NpcImages'.
    static float[][] turned(Model model, double yaw, double roll, double pitch) {
        float[] vx = model.getVerticesX(), vy = model.getVerticesY(), vz = model.getVerticesZ();
        int count = model.getVerticesCount();
        float[] x = new float[count], y = new float[count], z = new float[count];
        double sinYaw = Math.sin(yaw), cosYaw = Math.cos(yaw);
        double sinRoll = Math.sin(roll), cosRoll = Math.cos(roll);
        double sinPitch = Math.sin(pitch), cosPitch = Math.cos(pitch);
        for (int i = 0; i < count; i++) {
            double px = vx[i], py = vy[i], pz = vz[i];
            double t = pz * sinYaw + px * cosYaw;
            pz = pz * cosYaw - px * sinYaw;
            px = t;
            t = py * sinRoll + px * cosRoll;
            py = py * cosRoll - px * sinRoll;
            px = t;
            t = py * cosPitch - pz * sinPitch;
            pz = py * sinPitch + pz * cosPitch;
            py = t;
            x[i] = (float) px;
            y[i] = (float) py;
            z[i] = (float) pz;
        }
        return new float[][]{x, y, z};
    }

    // Wear, Wield or Equip in its inventory menu.
    private static boolean equippable(ItemComposition item) {
        String[] actions = item.getInventoryActions();
        if (actions == null) {
            return false;
        }
        for (String action : actions) {
            if ("Wear".equalsIgnoreCase(action) || "Wield".equalsIgnoreCase(action) || "Equip".equalsIgnoreCase(action)) {
                return true;
            }
        }
        return false;
    }
}
