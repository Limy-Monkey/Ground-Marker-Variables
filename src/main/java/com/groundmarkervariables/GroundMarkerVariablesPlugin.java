package com.groundmarkervariables;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import com.groundmarkervariables.variables.LabelResolver;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WorldViewLoaded;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
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
	private static final String CORE_CONFIG_GROUP = "groundMarker";
	private static final String REGION_PREFIX = "region_";

	@Inject
	private Client client;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Gson gson;

	@Inject
	private LabelResolver labelResolver;

	@Inject
	private OverlayManager overlayManager;

	// GroundMarkerOverlay isn't @Singleton; removeIf() below finds the real registered
	// overlay by type, and this fresh instance is handed back to the manager on shutDown().
	@Inject
	private GroundMarkerOverlay coreOverlay;

	@Inject
	private GroundMarkerVariablesOverlay overlay;

	@Inject
	private KeyManager keyManager;

	@Inject
	private MetronomeResetHotkeyListener metronomeResetHotkeyListener;

	// Parsed + label-resolved markers keyed by region ID. Rebuilt when marker data or visible
	// regions change (see onXxx subscribers); resolved labels refresh once per tick (onGameTick).
	private final Map<Integer, List<CachedMarker>> markersByRegion = new ConcurrentHashMap<>();

	@Provides
	GroundMarkerVariablesConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GroundMarkerVariablesConfig.class);
	}

	@Override
	protected void startUp()
	{
		// @PluginDependency guarantees GroundMarkerPlugin has already started and
		// added its overlay by now.
		overlayManager.removeIf(o -> o instanceof GroundMarkerOverlay);
		overlayManager.add(overlay);
		keyManager.registerKeyListener(metronomeResetHotkeyListener);
		loadPoints();
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(metronomeResetHotkeyListener);
		overlayManager.remove(overlay);
		overlayManager.add(coreOverlay);
		markersByRegion.clear();
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		loadPoints();
	}

	@Subscribe
	public void onWorldViewLoaded(WorldViewLoaded event)
	{
		loadPoints(event.getWorldView());
	}

	// Core's own plugin writes mark/label/color/import/clear edits to config, not us —
	// this is how we notice those edits happened.
	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!CORE_CONFIG_GROUP.equals(event.getGroup()))
		{
			return;
		}

		String key = event.getKey();
		if (key == null || !key.startsWith(REGION_PREFIX))
		{
			return;
		}

		try
		{
			int regionId = Integer.parseInt(key.substring(REGION_PREFIX.length()));
			rebuildRegion(regionId);
		}
		catch (NumberFormatException e)
		{
			// Not actually a region key; ignore.
		}
	}

	// Refreshes every cached label once per tick instead of once per render() call.
	@Subscribe
	public void onGameTick(GameTick event)
	{
		for (List<CachedMarker> markers : markersByRegion.values())
		{
			for (CachedMarker marker : markers)
			{
				marker.refresh(labelResolver);
			}
		}
	}

	Collection<CachedMarker> getMarkers(int regionId)
	{
		return markersByRegion.getOrDefault(regionId, Collections.emptyList());
	}

	private void loadPoints()
	{
		WorldView wv = client.getTopLevelWorldView();
		if (wv == null)
		{
			return;
		}

		loadPoints(wv);
		for (WorldEntity worldEntity : wv.worldEntities())
		{
			loadPoints(worldEntity.getWorldView());
		}
	}

	private void loadPoints(WorldView wv)
	{
		int[] regions = wv.getMapRegions();
		if (regions == null)
		{
			return;
		}

		for (int regionId : regions)
		{
			rebuildRegion(regionId);
		}
	}

	// Replaces (not mutates) the cached list, so a concurrent render() never sees a
	// partially-built list.
	private void rebuildRegion(int regionId)
	{
		List<CachedMarker> markers = parseStoredPoints(regionId).stream()
			.map(point -> new CachedMarker(point, labelResolver))
			.collect(Collectors.toList());
		markersByRegion.put(regionId, markers);
	}

	private Collection<GroundMarkerPointData> parseStoredPoints(int regionId)
	{
		String json = configManager.getConfiguration(CORE_CONFIG_GROUP, REGION_PREFIX + regionId);
		if (json == null || json.isEmpty())
		{
			return Collections.emptyList();
		}

		List<GroundMarkerPointData> points = gson.fromJson(json, new TypeToken<List<GroundMarkerPointData>>()
		{
		}.getType());
		return points == null ? Collections.emptyList() : points;
	}
}
