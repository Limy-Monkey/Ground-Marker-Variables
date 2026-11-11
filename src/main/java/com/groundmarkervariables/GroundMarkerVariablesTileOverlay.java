package com.groundmarkervariables;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

// Draws tile fills/outlines. Labels are drawn by GroundMarkerVariablesLabelOverlay at a higher
// priority, so every tile ends up below every label.
public class GroundMarkerVariablesTileOverlay extends Overlay
{
	private final GroundMarkerVariablesOverlay markerOverlay;

	@Inject
	private GroundMarkerVariablesTileOverlay(GroundMarkerVariablesOverlay markerOverlay)
	{
		this.markerOverlay = markerOverlay;
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(PRIORITY_LOW);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		markerOverlay.renderMarkers(graphics, false);
		return null;
	}
}
