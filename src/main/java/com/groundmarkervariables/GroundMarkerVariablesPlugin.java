package com.groundmarkervariables;

import javax.inject.Inject;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.groundmarkers.GroundMarkerOverlay;
import net.runelite.client.plugins.groundmarkers.GroundMarkerPlugin;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Ground Marker Variables",
	description = "Ground Markers with variables support, e.g. {spellbook} and {metronome4}",
	tags = {"ground", "markers", "tile", "overlay", "labels", "variables", "metronome"}
)
@PluginDependency(GroundMarkerPlugin.class)
public class GroundMarkerVariablesPlugin extends Plugin
{
	@Inject
	private OverlayManager overlayManager;

	// GroundMarkerOverlay isn't @Singleton, so injecting it here gives us a fresh
	// instance, not the one GroundMarkerPlugin itself registered — removeIf() below
	// finds the real registered overlay by type instead of by (wrong) reference. We
	// still keep this injected instance around to hand back to the manager on
	// shutDown(); a fresh instance renders identically since it holds no unique state.
	@Inject
	private GroundMarkerOverlay coreOverlay;

	@Inject
	private GroundMarkerVariablesOverlay overlay;

	@Override
	protected void startUp()
	{
		// @PluginDependency guarantees GroundMarkerPlugin has already started and
		// added its overlay by now.
		overlayManager.removeIf(o -> o instanceof GroundMarkerOverlay);
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		overlayManager.add(coreOverlay);
	}
}
