package com.groundmarkervariables;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import com.groundmarkervariables.party.MetronomeSyncRequest;
import com.groundmarkervariables.party.MetronomeSyncResponse;
import com.groundmarkervariables.variables.LabelResolver;
import com.groundmarkervariables.variables.MetronomeLabelVariable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Tile;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.WorldViewLoaded;
import net.runelite.api.events.WorldViewUnloaded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.groundmarkers.GroundMarkerOverlay;
import net.runelite.client.plugins.groundmarkers.GroundMarkerPlugin;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
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
	private static final String RECENT_LABELS_KEY = "recentLabels";
	private static final int RECENT_LABELS_MAX = 3;
	private static final int NEARBY_LABEL_DISTANCE = 150;
	private static final int REFRESH_DISTANCE = 40;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private GroundMarkerVariablesConfig config;

	@Inject
	private Gson gson;

	@Inject
	private Provider<AdvancedLabelEditor> advancedLabelEditorProvider;

	@Inject
	private LabelResolver labelResolver;

	@Inject
	private MetronomeLabelVariable metronomeLabelVariable;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private PartyService partyService;

	@Inject
	private WSClient wsClient;

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

	// Instance-translated positions keyed by WorldView, mirroring core's
	// ListMultimap<WorldView, ColorTileMarker> — avoids WorldPoint.toLocalInstance() in render().
	private final Map<WorldView, List<TranslatedMarker>> markersByWorldView = new HashMap<>();

	// Guards against replying twice in the same tick if multiple party members target us.
	private boolean hasRespondedThisTick;

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

		// startUp() itself runs on the AWT thread (PluginManager starts plugins via
		// SwingUtilities.invokeAndWait), but resolving a variable requires the client thread —
		// same reasoning as onConfigChanged's rebuild below.
		clientThread.invoke(() -> loadPoints());

		// A collision (another plugin already claiming these message names) throws here;
		// party sync is simply unavailable in that case rather than failing the whole plugin.
		try
		{
			wsClient.registerMessage(MetronomeSyncRequest.class);
			wsClient.registerMessage(MetronomeSyncResponse.class);
		}
		catch (IllegalArgumentException e)
		{
			log.warn("Party sync unavailable due to a message type collision with another plugin", e);
		}
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(metronomeResetHotkeyListener);
		overlayManager.remove(overlay);
		overlayManager.add(coreOverlay);
		markersByRegion.clear();
		markersByWorldView.clear();

		try
		{
			wsClient.unregisterMessage(MetronomeSyncRequest.class);
			wsClient.unregisterMessage(MetronomeSyncResponse.class);
		}
		catch (IllegalArgumentException e)
		{
			// Never registered successfully; nothing to unregister.
		}
	}

	// Priority -1 so this runs after core's GroundMarkerPlugin#onMenuEntryAdded, which is what
	// actually creates the "Label" entry we're looking for — EventBus otherwise orders equal-
	// priority subscribers alphabetically by class name, which would run ours first.
	@Subscribe(priority = -1)
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.advancedLabelEditor())
		{
			return;
		}

		MenuEntry labelEntry = null;
		for (MenuEntry entry : client.getMenuEntries())
		{
			if ("Label".equals(entry.getOption()) && "Tile".equals(entry.getTarget()) && entry.getType() == MenuAction.RUNELITE)
			{
				labelEntry = entry;
				break;
			}
		}

		if (labelEntry == null)
		{
			return;
		}

		WorldView wv = client.getWorldView(event.getMenuEntry().getWorldViewId());
		if (wv == null)
		{
			return;
		}

		Tile selectedSceneTile = wv.getSelectedSceneTile();
		if (selectedSceneTile == null)
		{
			return;
		}

		WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, selectedSceneTile.getLocalLocation());
		labelEntry.onClick(e -> openLabelEditor(worldPoint));
	}

	private void openLabelEditor(WorldPoint worldPoint)
	{
		GroundMarkerPointData existing = findStoredPoint(worldPoint);
		String currentLabel = existing != null && existing.getLabel() != null ? existing.getLabel() : "";

		advancedLabelEditorProvider.get()
			.recommendations(currentLabel, getRecentLabels(), findNearbyLabels(worldPoint), this::removeRecentLabel)
			.prompt("Tile label")
			.value(currentLabel)
			.onDone((Consumer<String>) newLabel -> saveLabel(worldPoint, newLabel))
			.build();
	}

	// Every other marker's own (raw, unresolved) label within NEARBY_LABEL_DISTANCE tiles —
	// much further than the overlay's own 32-tile render distance — closest first, excluding
	// worldPoint itself and any duplicate label text.
	private List<String> findNearbyLabels(WorldPoint worldPoint)
	{
		List<TranslatedMarker> candidates = new ArrayList<>();
		for (WorldView wv : getTrackedWorldViews())
		{
			candidates.addAll(getTranslatedMarkers(wv));
		}

		candidates.sort(Comparator.comparingInt(m -> m.worldPoint.distanceTo(worldPoint)));

		List<String> labels = new ArrayList<>();
		for (TranslatedMarker marker : candidates)
		{
			String label = marker.marker.source.getLabel();
			if (label == null || label.isEmpty() || marker.worldPoint.equals(worldPoint)
				|| marker.worldPoint.distanceTo(worldPoint) > NEARBY_LABEL_DISTANCE)
			{
				continue;
			}

			if (!labels.contains(label))
			{
				labels.add(label);
			}
		}

		return labels;
	}

	private List<String> getRecentLabels()
	{
		String json = configManager.getConfiguration(GroundMarkerVariablesConfig.GROUP, RECENT_LABELS_KEY);
		if (json == null || json.isEmpty())
		{
			return Collections.emptyList();
		}

		List<String> recent = gson.fromJson(json, new TypeToken<List<String>>()
		{
		}.getType());
		return recent == null ? Collections.emptyList() : recent;
	}

	// Moves label to the front if it's already there, rather than storing a duplicate.
	private void recordRecentLabel(String label)
	{
		List<String> recent = new ArrayList<>(getRecentLabels());
		recent.remove(label);
		recent.add(0, label);
		if (recent.size() > RECENT_LABELS_MAX)
		{
			recent = recent.subList(0, RECENT_LABELS_MAX);
		}

		configManager.setConfiguration(GroundMarkerVariablesConfig.GROUP, RECENT_LABELS_KEY, gson.toJson(recent));
	}

	private void removeRecentLabel(String label)
	{
		List<String> recent = new ArrayList<>(getRecentLabels());
		recent.remove(label);
		configManager.setConfiguration(GroundMarkerVariablesConfig.GROUP, RECENT_LABELS_KEY, gson.toJson(recent));
	}

	private GroundMarkerPointData findStoredPoint(WorldPoint worldPoint)
	{
		for (GroundMarkerPointData point : parseStoredPoints(worldPoint.getRegionID()))
		{
			if (point.getRegionX() == worldPoint.getRegionX() && point.getRegionY() == worldPoint.getRegionY()
				&& point.getZ() == worldPoint.getPlane())
			{
				return point;
			}
		}

		return null;
	}

	// Mirrors core's labelTile()/savePoints() persistence — we can't call those directly since
	// they're private on core's plugin instance, but writing to the same config key means our
	// own onConfigChanged picks this up and refreshes the overlay for free.
	private void saveLabel(WorldPoint worldPoint, String newLabel)
	{
		int regionId = worldPoint.getRegionID();
		List<GroundMarkerPointData> points = new ArrayList<>(parseStoredPoints(regionId));
		String label = newLabel.isEmpty() ? null : newLabel;

		for (int i = 0; i < points.size(); i++)
		{
			GroundMarkerPointData point = points.get(i);
			if (point.getRegionX() == worldPoint.getRegionX() && point.getRegionY() == worldPoint.getRegionY()
				&& point.getZ() == worldPoint.getPlane())
			{
				points.set(i, new GroundMarkerPointData(point.getRegionId(), point.getRegionX(), point.getRegionY(), point.getZ(), point.getColor(), label));
				configManager.setConfiguration(CORE_CONFIG_GROUP, REGION_PREFIX + regionId, gson.toJson(points));
				if (label != null)
				{
					recordRecentLabel(label);
				}
				return;
			}
		}
	}

	// Fired from ConfigManager's profile-switch methods, called from the config panel's AWT
	// thread — same reasoning as startUp() and onConfigChanged() below.
	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		clientThread.invoke(() -> loadPoints());
	}

	@Subscribe
	public void onWorldViewLoaded(WorldViewLoaded event)
	{
		loadPoints(event.getWorldView());
	}

	// Evicts the translation cache so we don't keep a dead WorldView referenced as a map key.
	@Subscribe
	public void onWorldViewUnloaded(WorldViewUnloaded event)
	{
		markersByWorldView.remove(event.getWorldView());
	}

	// Core's own plugin writes mark/label/color/import/clear edits to config, not us — this
	// is how we notice those edits happened. Some of those edits (e.g. labelTile's chatbox
	// input) fire this from the AWT thread, but resolving a variable requires the client
	// thread, so the rebuild is marshalled there via clientThread.invoke().
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
			clientThread.invoke(() ->
			{
				rebuildRegion(regionId);
				retranslateWorldViewsShowing(regionId);
			});
		}
		catch (NumberFormatException e)
		{
			// Not actually a region key; ignore.
		}
	}

	// Refreshes cached labels once per tick instead of once per render() call, distance-culled
	// the same way as the overlay's own render distance.
	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (client.getLocalPlayer() != null)
		{
			boolean cullByDistance = client.getLocalPlayer().getWorldView().isTopLevel();
			WorldPoint playerLocation = client.getLocalPlayer().getWorldLocation();

			for (WorldView wv : getTrackedWorldViews())
			{
				for (TranslatedMarker translated : getTranslatedMarkers(wv))
				{
					if (cullByDistance && translated.worldPoint.distanceTo(playerLocation) >= REFRESH_DISTANCE)
					{
						continue;
					}

					translated.marker.refresh(labelResolver);
				}
			}
		}

		hasRespondedThisTick = false;
		if (config.enablePartySync() && !config.syncTarget().isEmpty()
			&& partyService.getMembers().stream().anyMatch(m -> matchesTarget(m.getDisplayName(), config.syncTarget())))
		{
			partyService.send(new MetronomeSyncRequest(config.syncTarget()));
		}
	}

	// Answers a request if we're the named target, regardless of our own Party Sync setting —
	// being a sync source doesn't require opting into following anyone yourself.
	@Subscribe
	public void onMetronomeSyncRequest(MetronomeSyncRequest request)
	{
		PartyMember localMember = partyService.getLocalMember();
		if (localMember == null || !matchesTarget(localMember.getDisplayName(), request.getTarget()))
		{
			return;
		}

		if (hasRespondedThisTick)
		{
			return;
		}
		hasRespondedThisTick = true;

		partyService.send(new MetronomeSyncResponse(metronomeLabelVariable.elapsedTicks()));
	}

	// Applies a response only if it's actually from our configured sync target.
	@Subscribe
	public void onMetronomeSyncResponse(MetronomeSyncResponse response)
	{
		if (!config.enablePartySync())
		{
			return;
		}

		PartyMember sender = partyService.getMemberById(response.getMemberId());
		if (sender == null || !matchesTarget(sender.getDisplayName(), config.syncTarget()))
		{
			return;
		}

		metronomeLabelVariable.syncTo(response.getElapsedTicks());
	}

	// Case-insensitive, and underscore/space-interchangeable, since a display name can arrive
	// over the party websocket with underscores in place of spaces.
	private static boolean matchesTarget(String displayName, String target)
	{
		return displayName.replace('_', ' ').equalsIgnoreCase(target.replace('_', ' '));
	}

	Collection<CachedMarker> getMarkers(int regionId)
	{
		return markersByRegion.getOrDefault(regionId, Collections.emptyList());
	}

	Collection<WorldView> getTrackedWorldViews()
	{
		return markersByWorldView.keySet();
	}

	Collection<TranslatedMarker> getTranslatedMarkers(WorldView wv)
	{
		return markersByWorldView.getOrDefault(wv, Collections.emptyList());
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

		translateWorldView(wv);
	}

	// Instance-translates every marker in wv's currently loaded regions, replacing (not
	// mutating) the cached list — same reasoning as rebuildRegion().
	private void translateWorldView(WorldView wv)
	{
		int[] regions = wv.getMapRegions();
		if (regions == null)
		{
			markersByWorldView.remove(wv);
			return;
		}

		List<TranslatedMarker> translated = new ArrayList<>();
		for (int regionId : regions)
		{
			for (CachedMarker marker : getMarkers(regionId))
			{
				GroundMarkerPointData point = marker.source;
				WorldPoint storedPoint = WorldPoint.fromRegion(point.getRegionId(), point.getRegionX(), point.getRegionY(), point.getZ());
				for (WorldPoint worldPoint : WorldPoint.toLocalInstance(wv, storedPoint))
				{
					translated.add(new TranslatedMarker(worldPoint, marker));
				}
			}
		}

		markersByWorldView.put(wv, translated);
	}

	// Retranslates only the currently tracked worldviews whose loaded regions include
	// regionId — core just retranslates everything after every edit, but it can do that
	// synchronously from its own mutation methods; we only learn about edits via
	// ConfigChanged, so it's worth narrowing this to the worldviews actually affected.
	private void retranslateWorldViewsShowing(int regionId)
	{
		for (WorldView wv : new ArrayList<>(markersByWorldView.keySet()))
		{
			int[] regions = wv.getMapRegions();
			if (regions == null)
			{
				continue;
			}

			for (int candidate : regions)
			{
				if (candidate == regionId)
				{
					translateWorldView(wv);
					break;
				}
			}
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
