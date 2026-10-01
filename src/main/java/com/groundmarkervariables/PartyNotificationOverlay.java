package com.groundmarkervariables;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.time.Instant;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

// Badge over the World Map orb's top-left corner, shown when GroundMarkerPartySharingManager
// receives a party-shared marker set. Opacity comes from its badgeOpacity(now).
class PartyNotificationOverlay extends Overlay
{
	private static final int SPRITE_ID = 937;
	private static final int WIDTH = 5;
	private static final int HEIGHT = 16;

	private final Client client;
	private final GroundMarkerPartySharingManager partySharingManager;
	private final SpriteManager spriteManager;

	@Inject
	private PartyNotificationOverlay(Client client, GroundMarkerPartySharingManager partySharingManager, SpriteManager spriteManager)
	{
		this.client = client;
		this.partySharingManager = partySharingManager;
		this.spriteManager = spriteManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		float opacity = partySharingManager.badgeOpacity(Instant.now());
		if (opacity <= 0f)
		{
			return null;
		}

		Widget worldMapOrb = client.getWidget(InterfaceID.Orbs.WORLDMAP);
		if (worldMapOrb == null || worldMapOrb.isHidden())
		{
			worldMapOrb = client.getWidget(InterfaceID.OrbsNomap.WORLDMAP);
		}
		if (worldMapOrb == null || worldMapOrb.isHidden())
		{
			return null;
		}

		Rectangle bounds = worldMapOrb.getBounds();
		if (bounds.getX() <= 0)
		{
			return null;
		}

		BufferedImage sprite = spriteManager.getSprite(SPRITE_ID, 0);
		if (sprite == null)
		{
			return null;
		}

		// Centered on the corner, not flush inside/outside it.
		int x = bounds.x - WIDTH / 2;
		int y = bounds.y - HEIGHT / 2;

		Composite originalComposite = graphics.getComposite();
		if (opacity < 1f)
		{
			graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
		}
		graphics.drawImage(sprite, x, y, WIDTH, HEIGHT, null);
		graphics.setComposite(originalComposite);

		return null;
	}
}
