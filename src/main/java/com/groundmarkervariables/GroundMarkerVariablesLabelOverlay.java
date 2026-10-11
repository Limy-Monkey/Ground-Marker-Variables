package com.groundmarkervariables;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

// Draws marker labels (text, shadows, color runs and inline item icons). Tiles are drawn by
// GroundMarkerVariablesOverlay at a lower priority, so labels always end up above every tile.
public class GroundMarkerVariablesLabelOverlay extends Overlay
{
	private final GroundMarkerVariablesOverlay markerOverlay;

	@Inject
	private GroundMarkerVariablesLabelOverlay(GroundMarkerVariablesOverlay markerOverlay)
	{
		this.markerOverlay = markerOverlay;
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(PRIORITY_HIGH);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		markerOverlay.renderMarkers(graphics, true);
		return null;
	}
}
