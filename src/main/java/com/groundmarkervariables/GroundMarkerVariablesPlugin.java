package com.groundmarkervariables;

import com.google.common.util.concurrent.Runnables;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import com.groundmarkervariables.party.MetronomeSyncRequest;
import com.groundmarkervariables.party.MetronomeSyncResponse;
import com.groundmarkervariables.variables.LabelResolver;
import com.groundmarkervariables.variables.MetronomeLabelVariable;
import java.awt.Color;
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
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
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
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.components.colorpicker.RuneliteColorPicker;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;

@Slf4j
@PluginDescriptor(
	name = "Ground Marker Variables",
	description = "Ground Markers with variables support, e.g. {spellbook} and {metronome4}",
	tags = {"ground", "markers", "tile", "overlay", "labels", "variables", "metronome"},
	conflicts = {"Ground Markers"}
)
public class GroundMarkerVariablesPlugin extends Plugin
{
	private static final String CORE_CONFIG_GROUP = "groundMarker";
	private static final String REGION_PREFIX = "region_";
	private static final String RECENT_LABELS_KEY = "recentLabels";
	private static final int RECENT_LABELS_MAX = 3;
	private static final int NEARBY_LABEL_DISTANCE = 150;
	private static final int REFRESH_DISTANCE = 40;

	// Config keys shared verbatim between core's config group and our own "Ground Markers"
	// section — see migrateConfigFromCore().
	private static final String[] MIGRATED_CONFIG_KEYS = {
		"markerColor", "drawOnMinimap", "showImportExport", "borderWidth", "fillOpacity"
	};
	private static final String CONFIG_MIGRATED_KEY = "groundMarkerConfigMigrated";

	@Inject
	private ChatboxPanelManager chatboxPanelManager;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ColorPickerManager colorPickerManager;

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
	private GroundMarkerVariablesSharingManager sharingManager;

	@Inject
	private WSClient wsClient;

	@Inject
	private GroundMarkerVariablesOverlay overlay;

	@Inject
	private GroundMarkerVariablesMinimapOverlay minimapOverlay;

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
		if (configManager.getConfiguration(GroundMarkerVariablesConfig.GROUP, CONFIG_MIGRATED_KEY) == null)
		{
			migrateConfigFromCore();
		}

		overlayManager.add(overlay);
		overlayManager.add(minimapOverlay);
		keyManager.registerKeyListener(metronomeResetHotkeyListener);

		if (config.showImportExport())
		{
			sharingManager.addImportExportMenuOptions();
			sharingManager.addClearMenuOption();
		}

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

	// Copies core's current Ground Markers config values into our own "Ground Markers" section.
	private void migrateConfigFromCore()
	{
		for (String key : MIGRATED_CONFIG_KEYS)
		{
			String value = configManager.getConfiguration(CORE_CONFIG_GROUP, key);
			if (value != null)
			{
				configManager.setConfiguration(GroundMarkerVariablesConfig.GROUP, key, value);
			}
		}

		configManager.setConfiguration(GroundMarkerVariablesConfig.GROUP, CONFIG_MIGRATED_KEY, "true");
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(metronomeResetHotkeyListener);
		overlayManager.remove(overlay);
		overlayManager.remove(minimapOverlay);
		sharingManager.removeMenuOptions();
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

	// Mirrors core's GroundMarkerPlugin#onMenuEntryAdded shift-click handling — builds our own
	// "Mark"/"Unmark"/"Label" entries directly, since core's GroundMarkerPlugin is disabled
	// via conflicts (see @PluginDescriptor) rather than run alongside this plugin.
	// Priority < 0 to run after other plugins so our entries show at top of menu entries like core
	@Subscribe(priority = -1)
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		MenuAction menuAction = event.getMenuEntry().getType();
		if (!client.isKeyPressed(KeyCode.KC_SHIFT) || (menuAction != MenuAction.WALK && menuAction != MenuAction.SET_HEADING))
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
		GroundMarkerPointData existing = findStoredPoint(worldPoint);

		client.createMenuEntry(-1)
			.setOption(existing != null ? "Unmark" : "Mark")
			.setTarget("Tile")
			.setType(MenuAction.RUNELITE)
			.onClick(e -> toggleMark(worldPoint));

		if (existing != null)
		{
			client.createMenuEntry(-2)
				.setOption("Label")
				.setTarget("Tile")
				.setType(MenuAction.RUNELITE)
				.onClick(e -> openLabelEditor(worldPoint));

			buildColorMenu(worldPoint, existing);
		}
	}

	// Mirrors core's "Color" submenu build: Reset all (only when the region has more than one
	// marker), Pick (native color picker), and one quick-select entry per distinct color already
	// used by any currently loaded marker.
	private void buildColorMenu(WorldPoint worldPoint, GroundMarkerPointData existing)
	{
		int regionId = worldPoint.getRegionID();
		List<GroundMarkerPointData> regionPoints = new ArrayList<>(getStoredPoints(regionId));

		Menu submenu = client.createMenuEntry(-3)
			.setOption("Color")
			.setTarget("Tile")
			.setType(MenuAction.RUNELITE)
			.createSubMenu();

		if (regionPoints.size() > 1)
		{
			submenu.createMenuEntry(-1)
				.setOption("Reset all")
				.setType(MenuAction.RUNELITE)
				.onClick(e -> chatboxPanelManager.openTextMenuInput("Are you sure you want to reset the color of " + regionPoints.size() + " tiles?")
					.option("Yes", () -> resetRegionColors(regionId, regionPoints))
					.option("No", Runnables.doNothing())
					.build());
		}

		submenu.createMenuEntry(-1)
			.setOption("Pick")
			.setType(MenuAction.RUNELITE)
			.onClick(e ->
			{
				Color color = existing.getColor() != null ? existing.getColor() : config.markerColor();
				SwingUtilities.invokeLater(() ->
				{
					RuneliteColorPicker colorPicker = colorPickerManager.create(client, color, "Tile marker color", false);
					colorPicker.setOnClose(c -> colorTile(worldPoint, c));
					colorPicker.setVisible(true);
				});
			});

		List<Color> existingColors = existingColors();
		for (Color color : existingColors)
		{
			if (!color.equals(existing.getColor()))
			{
				// Same RGB as another entry, only distinguishable by opacity — disambiguate
				// with the opacity percentage, since the color swatch alone looks identical.
				String option = hasOpacityCollision(existingColors, color)
					? "Color (" + Math.round(color.getAlpha() / 255f * 100) + "%)"
					: "Color";

				submenu.createMenuEntry(-1)
					.setOption(ColorUtil.prependColorTag(option, color))
					.setType(MenuAction.RUNELITE)
					.onClick(e -> colorTile(worldPoint, color));
			}
		}
	}

	// True if some other color in the list shares color's RGB but not its alpha.
	private static boolean hasOpacityCollision(List<Color> colors, Color color)
	{
		for (Color other : colors)
		{
			if (!other.equals(color) && other.getRed() == color.getRed() && other.getGreen() == color.getGreen()
				&& other.getBlue() == color.getBlue())
			{
				return true;
			}
		}

		return false;
	}

	// Distinct, non-null colors across every currently loaded marker, for the Color submenu's
	// quick-select entries — mirrors core's points.values().stream().map(getColor).distinct().
	private List<Color> existingColors()
	{
		List<Color> colors = new ArrayList<>();
		for (WorldView wv : getTrackedWorldViews())
		{
			for (TranslatedMarker translated : getTranslatedMarkers(wv))
			{
				Color color = translated.marker.source.getColor();
				if (color != null && !colors.contains(color))
				{
					colors.add(color);
				}
			}
		}

		return colors;
	}

	// Mirrors core's colorTile() — updates just the color of an existing point.
	private void colorTile(WorldPoint worldPoint, Color newColor)
	{
		int regionId = worldPoint.getRegionID();
		List<GroundMarkerPointData> points = new ArrayList<>(getStoredPoints(regionId));

		for (int i = 0; i < points.size(); i++)
		{
			GroundMarkerPointData point = points.get(i);
			if (point.getRegionX() == worldPoint.getRegionX() && point.getRegionY() == worldPoint.getRegionY()
				&& point.getZ() == worldPoint.getPlane())
			{
				points.set(i, new GroundMarkerPointData(point.getRegionId(), point.getRegionX(), point.getRegionY(), point.getZ(), newColor, point.getLabel()));
				configManager.setConfiguration(CORE_CONFIG_GROUP, REGION_PREFIX + regionId, gson.toJson(points));
				return;
			}
		}
	}

	// Mirrors core's "Reset all" — resets every marker's color in the region to the configured
	// default marker color.
	private void resetRegionColors(int regionId, List<GroundMarkerPointData> regionPoints)
	{
		Color defaultColor = config.markerColor();
		List<GroundMarkerPointData> newPoints = new ArrayList<>();
		for (GroundMarkerPointData point : regionPoints)
		{
			newPoints.add(new GroundMarkerPointData(point.getRegionId(), point.getRegionX(), point.getRegionY(), point.getZ(), defaultColor, point.getLabel()));
		}

		configManager.setConfiguration(CORE_CONFIG_GROUP, REGION_PREFIX + regionId, gson.toJson(newPoints));
	}

	// Mirrors core's markTile().
	private void toggleMark(WorldPoint worldPoint)
	{
		int regionId = worldPoint.getRegionID();
		List<GroundMarkerPointData> points = new ArrayList<>(getStoredPoints(regionId));
		GroundMarkerPointData existing = findStoredPoint(worldPoint);

		if (existing != null)
		{
			points.remove(existing);
		}
		else
		{
			points.add(new GroundMarkerPointData(regionId, worldPoint.getRegionX(), worldPoint.getRegionY(), worldPoint.getPlane(), config.markerColor(), null));
		}

		savePoints(regionId, points);
	}

	private void openLabelEditor(WorldPoint worldPoint)
	{
		if (config.useAdvancedLabelEditor())
		{
			openAdvancedLabelEditor(worldPoint);
		}
		else
		{
			openPlainLabelEditor(worldPoint);
		}
	}

	private void openAdvancedLabelEditor(WorldPoint worldPoint)
	{
		GroundMarkerPointData existing = findStoredPoint(worldPoint);
		String currentLabel = existing != null && existing.getLabel() != null ? existing.getLabel() : "";
		Color tileColor = existing != null && existing.getColor() != null ? existing.getColor() : config.markerColor();

		advancedLabelEditorProvider.get()
			.recommendations(currentLabel, getRecentLabels(), findNearbyLabels(worldPoint), this::removeRecentLabel, tileColor)
			.prompt("Tile label")
			.value(currentLabel)
			.onDone((Consumer<String>) newLabel -> saveLabel(worldPoint, newLabel))
			.build();
	}

	// Mirrors core's own labelTile() — used when the Advanced Label Editor is turned off.
	private void openPlainLabelEditor(WorldPoint worldPoint)
	{
		GroundMarkerPointData existing = findStoredPoint(worldPoint);
		String currentLabel = existing != null && existing.getLabel() != null ? existing.getLabel() : "";

		chatboxPanelManager.openTextInput("Tile label")
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
		for (GroundMarkerPointData point : getStoredPoints(worldPoint.getRegionID()))
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
		List<GroundMarkerPointData> points = new ArrayList<>(getStoredPoints(regionId));
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
		if (GroundMarkerVariablesConfig.GROUP.equals(event.getGroup()) && "showImportExport".equals(event.getKey()))
		{
			sharingManager.removeMenuOptions();
			if (config.showImportExport())
			{
				sharingManager.addImportExportMenuOptions();
				sharingManager.addClearMenuOption();
			}
			return;
		}

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

	void loadPoints()
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
		List<CachedMarker> markers = getStoredPoints(regionId).stream()
			.map(point -> new CachedMarker(point, labelResolver))
			.collect(Collectors.toList());
		markersByRegion.put(regionId, markers);
	}

	Collection<GroundMarkerPointData> getStoredPoints(int regionId)
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

	// Mirrors core's savePoints() — used by the sharing manager's import/clear, which need to
	// replace a whole region's point list at once rather than one point at a time.
	void savePoints(int regionId, Collection<GroundMarkerPointData> points)
	{
		if (points == null || points.isEmpty())
		{
			configManager.unsetConfiguration(CORE_CONFIG_GROUP, REGION_PREFIX + regionId);
			return;
		}

		configManager.setConfiguration(CORE_CONFIG_GROUP, REGION_PREFIX + regionId, gson.toJson(points));
	}
}
