package com.groundmarkervariables.markers;

import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

// Distance culling from the nearer of the player and the camera's focal point, which differ when the
// camera is detached, e.g. spectating a teammate after dying in a raid. Only on the top-level
// worldview, since the player's position isn't meaningful for a world entity's (e.g. a ship's) own.
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class MarkerCulling {
    private final Client client;

    // True if tile is within distance of the player or the camera's focal point, or culling doesn't apply.
    public boolean inRange(WorldPoint tile, int distance) {
        Player player = client.getLocalPlayer();
        if (player == null || !player.getWorldView().isTopLevel() || tile.distanceTo(player.getWorldLocation()) < distance) {
            return true;
        }

        WorldPoint focus = cameraFocus();
        return focus != null && tile.distanceTo(focus) < distance;
    }

    // Null if it's outside the scene.
    @Nullable
    private WorldPoint cameraFocus() {
        WorldView wv = client.getTopLevelWorldView();
        if (wv == null) {
            return null;
        }

        // fromLocal, not fromLocalInstance: markers are culled by their instanced tile.
        LocalPoint focus = new LocalPoint((int) client.getCameraFocalPointX(), (int) client.getCameraFocalPointZ(), wv);
        return focus.isInScene() ? WorldPoint.fromLocal(wv, focus.getX(), focus.getY(), wv.getPlane()) : null;
    }
}
