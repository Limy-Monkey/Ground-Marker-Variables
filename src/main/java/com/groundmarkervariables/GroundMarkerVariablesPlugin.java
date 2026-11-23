package com.groundmarkervariables;

import com.google.common.util.concurrent.Runnables;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.groundmarkervariables.chatbox.AdvancedLabelEditor;
import com.groundmarkervariables.chatbox.GroundMarkerVariablesSharingManager;
import com.groundmarkervariables.chatbox.MarkerManager;
import com.groundmarkervariables.chatbox.SuggestionIconOverlay;
import com.groundmarkervariables.graphics.GroundMarkerVariablesMinimapOverlay;
import com.groundmarkervariables.markers.CachedMarker;
import com.groundmarkervariables.markers.DeletedMarkers;
import com.groundmarkervariables.markers.ExternalMarkerManager;
import com.groundmarkervariables.markers.GroundMarkerPointData;
import com.groundmarkervariables.markers.ImportPreviewManager;
import com.groundmarkervariables.markers.MarkerCulling;
import com.groundmarkervariables.markers.MarkerStorage;
import com.groundmarkervariables.markers.RenderedTile;
import com.groundmarkervariables.markers.RenderedTiles;
import com.groundmarkervariables.markers.SourceMarker;
import com.groundmarkervariables.markers.MarkerSets;
import com.groundmarkervariables.markers.TileSource;
import com.google.inject.Provides;
import com.groundmarkervariables.party.*;
import com.groundmarkervariables.variables.support.BossKillCountTracker;
import com.groundmarkervariables.variables.LabelResolver;
import com.groundmarkervariables.variables.meta.MetaVariableResolver;
import com.groundmarkervariables.variables.meta.MetaState;
import com.groundmarkervariables.variables.label.MetronomeLabelVariable;
import com.groundmarkervariables.variables.support.LatestData;
import com.groundmarkervariables.variables.support.MetronomeResetHotkeyListener;
import com.groundmarkervariables.variables.support.MiscellaniaTracker;
import com.groundmarkervariables.variables.support.NamedColors;
import com.groundmarkervariables.variables.support.ReadyCheckTracker;
import com.groundmarkervariables.variables.support.ActiveEffectTracker;
import com.groundmarkervariables.variables.support.ResolvingTile;
import com.groundmarkervariables.variables.support.LootSessions;
import com.groundmarkervariables.variables.support.RuneProfileCollectionLog;
import com.groundmarkervariables.variables.support.IconNames;
import com.groundmarkervariables.variables.support.WeaponStyleTracker;
import com.groundmarkervariables.variables.support.RecentLootTracker;
import com.groundmarkervariables.variables.label.SixHourTimeRemainingLabelVariable;
import com.groundmarkervariables.variables.support.GroupState;
import com.groundmarkervariables.variables.support.ItemQuantityFinder;
import com.groundmarkervariables.variables.support.RuneCounter;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.KeyCode;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.Tile;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
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
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.slayer.SlayerPlugin;
import net.runelite.client.plugins.xptracker.XpTrackerPlugin;
import net.runelite.client.ui.JagexColors;
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
// XpTrackerPlugin: Used for {xpRate_<skill>} variable, N/A if disabled.
@PluginDependency(XpTrackerPlugin.class)
// SlayerPlugin: Used for {slayerTask} variable, N/A if disabled.
@PluginDependency(SlayerPlugin.class)
public class GroundMarkerVariablesPlugin extends Plugin implements TileSource
{
	private static final String CORE_CONFIG_GROUP = "groundMarker";
	private static final String REGION_PREFIX = "region_";
	private static final String RECENT_LABELS_KEY = "recentLabels";
	private static final int RECENT_LABELS_MAX = 3;
	private static final int NEARBY_LABEL_DISTANCE = 150;
	private static final int REFRESH_DISTANCE = 40;
	private static final String MANAGE_COMMAND = "manageGroundMarkerVariables";
	private static final String MANAGE_COMMAND_SHORT = "manageGMV";
	// {lootSession <monster>...}: its monster, for Tile Variables' Reset Session.
	private static final Pattern LOOT_SESSION_TOKEN = Pattern.compile("\\{lootSession\\s+([^{}?:<>=!,]+)", Pattern.CASE_INSENSITIVE);

	// Config keys shared verbatim between core's config group and our own "Ground Markers"
	// section — see migrateConfigFromCore().
	private static final String[] MIGRATED_CONFIG_KEYS = {
		"markerColor", "drawOnMinimap", "showImportExport", "borderWidth", "fillOpacity"
	};
	private static final String CONFIG_MIGRATED_KEY = "groundMarkerConfigMigrated";

	// showCurrent's default changed from true to false — forces existing installs over once.
	private static final String SHOW_CURRENT_MIGRATED_KEY = "showCurrentDefaultMigrated";

	// Bump alongside runelite-plugin.properties/build.gradle's version to show the message
	// again on the next update.
	private static final String NEW_VERSION = "1.4.0";
	private static final String LAST_SEEN_VERSION_KEY = "lastSeenVersion";
	private static final String UPDATE_MESSAGE = "<col=008800>Ground Marker Variables v1.4: New variables and extended conditionals - Try {highlightCorners} or {oct 20 - date} until raids 4";

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
	private MarkerStorage markerStorage;

	@Inject
	private Provider<AdvancedLabelEditor> advancedLabelEditorProvider;

	@Inject
	private Provider<MarkerManager> markerManagerProvider;

	@Inject
	private DeletedMarkers deletedMarkers;

	@Inject
	private LabelResolver labelResolver;

	@Inject
	private MetaVariableResolver metaVariableResolver;

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
	private GroundMarkerVariablesTileOverlay tileOverlay;

	@Inject
	private GroundMarkerVariablesLabelOverlay labelOverlay;

	@Inject
	private GroundMarkerVariablesMinimapOverlay minimapOverlay;

	@Inject
	private PartyNotificationOverlay partyNotificationOverlay;

	@Inject
	private SuggestionIconOverlay suggestionIconOverlay;

	@Inject
	private KeyManager keyManager;

	@Inject
	private MetronomeResetHotkeyListener metronomeResetHotkeyListener;

	@Inject
	private BossKillCountTracker bossKillCountTracker;

	@Inject
	private SixHourTimeRemainingLabelVariable sixHourTimeRemaining;

	@Inject
	private PingedTileManager pingedTileManager;

	@Inject
	private PartyDataSharing partyDataSharing;

	@Inject
	private GroundMarkerPartySharingManager groundMarkerPartySharingManager;

	@Inject
	private RecentLootTracker recentLootTracker;

	@Inject
	private MiscellaniaTracker miscellaniaTracker;

	@Inject
	private ReadyCheckTracker readyCheckTracker;

	@Inject
	private ActiveEffectTracker activeEffectTracker;

	@Inject
	private ResolvingTile resolvingTile;

	@Inject
	private GroupState groupState;

	@Inject
	private MarkerCulling markerCulling;

	@Inject
	private WeaponStyleTracker weaponStyleTracker;

	@Inject
	private IconNames iconNames;

	@Inject
	private LootSessions lootSessions;

	@Inject
	private RuneProfileCollectionLog runeProfileCollectionLog;

	@Inject
	private ImportPreviewManager importPreviewManager;

	@Inject
	private PluginMessageHandler pluginMessageHandler;

	@Inject
	private ExternalMarkerManager externalMarkerManager;

	@Inject
	private MarkerSets markerSets;

	@Inject
	private LatestData latestData;

	@Inject
	private RenderedTiles renderedTiles;

	// Saved markers by region, resolved. Rebuilt when marker data or visible regions change (see onXxx subscribers);
	// drawn ones refresh once per tick (onGameTick). RenderedTiles translates and weighs them with other sources.
	private final Map<Integer, List<SourceMarker>> markersByRegion = new ConcurrentHashMap<>();

	// Guards against replying twice in the same tick if multiple party members target us.
	private boolean hasRespondedThisTick;

	// Captured in startUp() -- true only for a brand new install, so announceUpdateIfNeeded()
	// can skip users who've never seen the old behavior.
	private boolean freshInstall;
	private boolean hasAnnounced = false;

	@Provides
	GroundMarkerVariablesConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GroundMarkerVariablesConfig.class);
	}

	@Override
	protected void startUp()
	{
		NamedColors.setHighlight(config.highlightColor());
		freshInstall = configManager.getConfiguration(GroundMarkerVariablesConfig.GROUP, CONFIG_MIGRATED_KEY) == null;
		if (freshInstall)
		{
			migrateConfigFromCore();
		}
		else
		{
			migrateShowCurrentDefault();
		}

		overlayManager.add(tileOverlay);
		overlayManager.add(labelOverlay);
		overlayManager.add(minimapOverlay);
		overlayManager.add(partyNotificationOverlay);
		overlayManager.add(suggestionIconOverlay);
		keyManager.registerKeyListener(metronomeResetHotkeyListener);
		pingedTileManager.startUp();
		partyDataSharing.startUp(this);
		groundMarkerPartySharingManager.startUp();
		recentLootTracker.startUp();
		miscellaniaTracker.startUp();
		readyCheckTracker.startUp();
		activeEffectTracker.startUp();
		weaponStyleTracker.startUp();
		runeProfileCollectionLog.startUp();
		renderedTiles.setSources(List.of(this, externalMarkerManager, markerSets, pingedTileManager, importPreviewManager));
		markerSets.startUp();
		latestData.startUp();
		pluginMessageHandler.startUp();

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
			wsClient.registerMessage(GMVMetronomeSyncRequest.class);
			wsClient.registerMessage(GMVMetronomeSyncResponse.class);
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

	// Runs once for existing installs only — fresh installs already get showCurrent's new
	// (false) default with nothing stored yet.
	private void migrateShowCurrentDefault()
	{
		if (configManager.getConfiguration(GroundMarkerVariablesConfig.GROUP, SHOW_CURRENT_MIGRATED_KEY) != null)
		{
			return;
		}

		configManager.setConfiguration(GroundMarkerVariablesConfig.GROUP, "showCurrent", false);
		configManager.setConfiguration(GroundMarkerVariablesConfig.GROUP, SHOW_CURRENT_MIGRATED_KEY, "true");
	}

	@Override
	protected void shutDown()
	{
		pingedTileManager.shutDown();
		partyDataSharing.shutDown();
		groundMarkerPartySharingManager.shutDown();
		recentLootTracker.shutDown();
		miscellaniaTracker.shutDown();
		readyCheckTracker.shutDown();
		activeEffectTracker.shutDown();
		groupState.clear();
		ItemQuantityFinder.clearCache();
		RuneCounter.clearCache();
		weaponStyleTracker.shutDown();
		runeProfileCollectionLog.shutDown();
		pluginMessageHandler.shutDown();
		externalMarkerManager.shutDown();
		markerSets.shutDown();
		keyManager.unregisterKeyListener(metronomeResetHotkeyListener);
		overlayManager.remove(tileOverlay);
		overlayManager.remove(labelOverlay);
		overlayManager.remove(minimapOverlay);
		overlayManager.remove(partyNotificationOverlay);
		overlayManager.remove(suggestionIconOverlay);
		suggestionIconOverlay.clear();
		sharingManager.removeMenuOptions();
		markersByRegion.clear();
		renderedTiles.clear();

		try
		{
			wsClient.unregisterMessage(GMVMetronomeSyncRequest.class);
			wsClient.unregisterMessage(GMVMetronomeSyncResponse.class);
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
		GroundMarkerPointData existing = markerStorage.findStoredPoint(worldPoint);

		// Not over another source's marker (a group's, a ping, a preview), which Mark/Unmark/Label/Color wouldn't affect.
		RenderedTile drawn = renderedTiles.winner(wv, selectedSceneTile.getWorldLocation());
		boolean savedDrawn = drawn == null || drawn.marker.source == this;
		if (savedDrawn)
		{
			client.createMenuEntry(-1)
				.setOption(existing != null ? "Unmark" : "Mark")
				.setTarget("Tile")
				.setType(MenuAction.RUNELITE)
				.onClick(e -> toggleMark(worldPoint));
		}

		if (savedDrawn && (existing != null || config.labelUnmarkedTiles()))
		{
			client.createMenuEntry(-2)
				.setOption("Label")
				.setTarget("Tile")
				.setType(MenuAction.RUNELITE)
				.onClick(e -> openLabelEditor(worldPoint));
		}

		buildConfigureMenu(existing, wv, selectedSceneTile.getWorldLocation());
		if (existing != null)
		{
			if (savedDrawn)
			{
				buildColorMenu(worldPoint, existing);
			}

			if (config.shareMarkerOption() && partyService.isInParty())
			{
				client.createMenuEntry(-5)
					.setOption("Party Share")
					.setTarget("Marker")
					.setType(MenuAction.RUNELITE)
					.onClick(e -> pingedTileManager.share(worldPoint, existing));
			}
		}
	}

	// Tile Variables: per-variable options for the saved marker's label, and other plugins' markers' Hide/Show, if any.
	private void buildConfigureMenu(@Nullable GroundMarkerPointData existing, WorldView wv, WorldPoint tile)
	{
		Menu submenu = null;
		List<ExternalMarkerManager.MenuOption> sourceOptions = new ArrayList<>(externalMarkerManager.menuOptions(wv, tile));
		sourceOptions.addAll(markerSets.menuOptions(wv, tile));
		for (ExternalMarkerManager.MenuOption option : sourceOptions)
		{
			submenu = addVariableOption(submenu, option.option, option.target, option.action);
		}

		if (existing == null)
		{
			return;
		}

		String label = existing.getLabel() == null ? "" : existing.getLabel();

		// "Reset Session" for each {lootSession <monster>} in the label, once per monster.
		Set<String> sessions = new HashSet<>();
		Matcher session = LOOT_SESSION_TOKEN.matcher(label);
		while (session.find())
		{
			String monster = session.group(1).trim();
			if (!monster.isEmpty() && sessions.add(monster.toLowerCase(Locale.ROOT)))
			{
				submenu = addVariableOption(submenu, "Reset Session", monster, () ->
				{
					lootSessions.reset(monster);
					refreshNearbyMarkers();
				});
			}
		}

		CachedMarker marker = markerAt(tile);
		if (marker == null)
		{
			return;
		}

		// Each declared group with a {setGroup} in the resolved label (so not a losing branch's), once, in its own option order.
		Set<String> groups = new HashSet<>();
		for (MetaState.GroupMenu menu : marker.getMetaState().getGroupMenus())
		{
			GroupState.Group group = groupState.group(menu.name);
			if (group == null || !groups.add(group.name.toLowerCase(Locale.ROOT)))
			{
				continue;
			}

			List<String> options = menu.options == null ? group.options : menu.options;
			String current = groupState.value(group.name);
			for (String option : options)
			{
				submenu = addVariableOption(submenu, group.name + ":", (option.equalsIgnoreCase(current) ? "*" : "") + option, () ->
				{
					groupState.pick(group.name, option);
					refreshNearbyMarkers();
				});
			}
		}
	}

	// The saved marker at tile (an instanced point), as last resolved.
	@Nullable
	private CachedMarker markerAt(WorldPoint tile)
	{
		for (WorldView wv : getTrackedWorldViews())
		{
			for (RenderedTile rendered : renderedTiles.all(wv, tile))
			{
				if (rendered.marker.source == this)
				{
					return rendered.marker.marker;
				}
			}
		}
		return null;
	}

	// Every loaded saved marker, at its instanced points.
	private List<RenderedTile> savedTiles()
	{
		List<RenderedTile> saved = new ArrayList<>();
		for (WorldView wv : getTrackedWorldViews())
		{
			for (RenderedTile rendered : renderedTiles.all(wv))
			{
				if (rendered.marker.source == this)
				{
					saved.add(rendered);
				}
			}
		}
		return saved;
	}

	// e.g. "Reset Session" with an orange monster name. Creates the "Variables" submenu on first use.
	private Menu addVariableOption(Menu submenu, String option, String variable, Runnable action)
	{
		if (submenu == null)
		{
			submenu = client.createMenuEntry(-4)
				.setOption("Tile")
				.setTarget("Variables")
				.setType(MenuAction.RUNELITE)
				.createSubMenu();
		}

		submenu.createMenuEntry(0)
			.setOption(option)
			.setTarget(JagexColors.MENU_TARGET_TAG + variable)
			.setType(MenuAction.RUNELITE)
			.onClick(e -> action.run());
		return submenu;
	}

	// Mirrors core's "Color" submenu build: Reset all (only when the region has more than one
	// marker), Pick (native color picker), and one quick-select entry per distinct color already
	// used by any currently loaded marker.
	private void buildColorMenu(WorldPoint worldPoint, GroundMarkerPointData existing)
	{
		int regionId = worldPoint.getRegionID();
		List<GroundMarkerPointData> regionPoints = new ArrayList<>(markerStorage.getStoredPoints(regionId));

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
		for (RenderedTile saved : savedTiles())
		{
			Color color = saved.marker.marker.source.getColor();
			if (color != null && !colors.contains(color))
			{
				colors.add(color);
			}
		}

		return colors;
	}

	// Mirrors core's colorTile() — updates just the color of an existing point.
	private void colorTile(WorldPoint worldPoint, Color newColor)
	{
		int regionId = worldPoint.getRegionID();
		List<GroundMarkerPointData> points = new ArrayList<>(markerStorage.getStoredPoints(regionId));

		for (int i = 0; i < points.size(); i++)
		{
			GroundMarkerPointData point = points.get(i);
			if (point.getRegionX() == worldPoint.getRegionX() && point.getRegionY() == worldPoint.getRegionY()
				&& point.getZ() == worldPoint.getPlane())
			{
				points.set(i, new GroundMarkerPointData(point.getRegionId(), point.getRegionX(), point.getRegionY(), point.getZ(), newColor, point.getLabel()));
				markerStorage.savePoints(regionId, points);
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

		markerStorage.savePoints(regionId, newPoints);
	}

	// Mirrors core's markTile().
	private void toggleMark(WorldPoint worldPoint)
	{
		GroundMarkerPointData existing = markerStorage.findStoredPoint(worldPoint);

		if (existing != null)
		{
			markerStorage.deletePoints(List.of(existing));
		}
		else
		{
			int regionId = worldPoint.getRegionID();
			GroundMarkerPointData point = new GroundMarkerPointData(regionId, worldPoint.getRegionX(), worldPoint.getRegionY(), worldPoint.getPlane(), config.markerColor(), null);
			markerStorage.addPoints(List.of(point));
		}
	}

	private void openLabelEditor(WorldPoint worldPoint)
	{
		// Unmarked tile (labelUnmarkedTiles) -- show it faded until the editor closes.
		if (markerStorage.findStoredPoint(worldPoint) == null)
		{
			importPreviewManager.show(List.of(new GroundMarkerPointData(worldPoint.getRegionID(), worldPoint.getRegionX(),
				worldPoint.getRegionY(), worldPoint.getPlane(), config.markerColor(), null)));
		}

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
		GroundMarkerPointData existing = markerStorage.findStoredPoint(worldPoint);
		editLabel(worldPoint, existing != null ? existing.getLabel() : null, existing != null ? existing.getColor() : null,
			label -> saveExpandedLabel(worldPoint, label), importPreviewManager::clear);
	}

	// The Advanced Label Editor on label (recommending labels near worldPoint), its icon IDs shown by name as typed; save
	// gets the new label with them turned back into IDs, on the client thread. Also Marker Sets' Edit.
	public void editLabel(WorldPoint worldPoint, @Nullable String label, @Nullable Color color, Consumer<String> save, Runnable onClose)
	{
		String currentLabel = iconNames.collapse(label != null ? label : "");
		advancedLabelEditorProvider.get()
			.recommendations(currentLabel, getRecentLabels(), findNearbyLabels(worldPoint), this::removeRecentLabel,
				color != null ? color : config.markerColor())
			.prompt("Tile label")
			.value(currentLabel)
			.onDone((Consumer<String>) newLabel -> clientThread.invoke(() -> save.accept(iconNames.expand(newLabel))))
			.onClose(onClose)
			.build();
	}

	// Mirrors core's own labelTile() — used when the Advanced Label Editor is turned off.
	private void openPlainLabelEditor(WorldPoint worldPoint)
	{
		GroundMarkerPointData existing = markerStorage.findStoredPoint(worldPoint);
		String currentLabel = iconNames.collapse(existing != null && existing.getLabel() != null ? existing.getLabel() : "");

		chatboxPanelManager.openTextInput("Tile label")
			.value(currentLabel)
			.onDone((Consumer<String>) newLabel -> saveLabel(worldPoint, newLabel))
			.onClose(importPreviewManager::clear)
			.build();
	}

	// Every other marker's own (raw, unresolved) label within NEARBY_LABEL_DISTANCE tiles —
	// much further than the overlay's own 32-tile render distance — closest first, excluding
	// worldPoint itself and any duplicate label text.
	private List<String> findNearbyLabels(WorldPoint worldPoint)
	{
		List<RenderedTile> candidates = savedTiles();
		candidates.sort(Comparator.comparingInt(m -> m.worldPoint.distanceTo(worldPoint)));

		List<String> labels = new ArrayList<>();
		for (RenderedTile marker : candidates)
		{
			String label = marker.marker.marker.source.getLabel();
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

	// Mirrors core's labelTile()/savePoints() persistence — we can't call those directly since
	// they're private on core's plugin instance, but writing to the same config key means our
	// own onConfigChanged picks this up and refreshes the overlay for free.
	// <npc=Name> / <item=Name> / <sprite=Name> become IDs here, on the client thread (IconNames reads game data).
	private void saveLabel(WorldPoint worldPoint, String newLabel)
	{
		clientThread.invoke(() -> saveExpandedLabel(worldPoint, iconNames.expand(newLabel)));
	}

	// A saved marker's label (icon names already IDs); also the Marker Manager's Edit.
	public void saveExpandedLabel(WorldPoint worldPoint, String newLabel)
	{
		int regionId = worldPoint.getRegionID();
		List<GroundMarkerPointData> points = new ArrayList<>(markerStorage.getStoredPoints(regionId));
		String label = newLabel.isEmpty() ? null : newLabel;

		for (int i = 0; i < points.size(); i++)
		{
			GroundMarkerPointData point = points.get(i);
			if (point.getRegionX() == worldPoint.getRegionX() && point.getRegionY() == worldPoint.getRegionY()
				&& point.getZ() == worldPoint.getPlane())
			{
				points.set(i, new GroundMarkerPointData(point.getRegionId(), point.getRegionX(), point.getRegionY(), point.getZ(), point.getColor(), label));
				markerStorage.savePoints(regionId, points);
				if (label != null)
				{
					recordRecentLabel(label);
				}
				return;
			}
		}

		// Unmarked tile, labeled via labelUnmarkedTiles -- an empty label leaves it unmarked.
		if (label != null)
		{
			markerStorage.addPoints(List.of(new GroundMarkerPointData(regionId, worldPoint.getRegionX(), worldPoint.getRegionY(), worldPoint.getPlane(), config.markerColor(), label)));
			recordRecentLabel(label);
		}
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted event)
	{
		if (MANAGE_COMMAND.equalsIgnoreCase(event.getCommand()) || MANAGE_COMMAND_SHORT.equalsIgnoreCase(event.getCommand()))
		{
			MarkerManager manager = markerManagerProvider.get();
			manager.load();
			chatboxPanelManager.openInput(manager);
		}
	}

	// Fired from ConfigManager's profile-switch methods, called from the config panel's AWT
	// thread — same reasoning as startUp() and onConfigChanged() below.
	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		// Undo would otherwise restore the old profile's markers into this one.
		deletedMarkers.clear();
		clientThread.invoke(() ->
		{
			loadPoints();
			markerSets.load();
		});
	}

	@Subscribe
	public void onWorldViewLoaded(WorldViewLoaded event)
	{
		loadPoints(event.getWorldView());
	}

	// So we don't keep a dead WorldView referenced.
	@Subscribe
	public void onWorldViewUnloaded(WorldViewUnloaded event)
	{
		renderedTiles.untrack(event.getWorldView());
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

		// The "highlight" named color follows it; markers keep their labels' colors expanded, so rebuild them.
		if (GroundMarkerVariablesConfig.GROUP.equals(event.getGroup()) && "highlightColor".equals(event.getKey()))
		{
			NamedColors.setHighlight(config.highlightColor());
			clientThread.invoke(() -> loadPoints());
			return;
		}

		if (!CORE_CONFIG_GROUP.equals(event.getGroup()) && !GroundMarkerVariablesConfig.GROUP.equals(event.getGroup()))
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
				renderedTiles.invalidate();
			});
		}
		catch (NumberFormatException e)
		{
			// Not actually a region key; ignore.
		}
	}

	// Refreshes drawn markers' labels once per tick instead of once per render() call, at their own tile and
	// distance-culled the same way as the overlay's own render distance.
	private void refreshNearbyMarkers()
	{
		if (client.getLocalPlayer() == null)
		{
			return;
		}

		readyCheckTracker.collecting = true;
		try
		{
			for (WorldView wv : getTrackedWorldViews())
			{
				for (RenderedTile rendered : renderedTiles.winners(wv))
				{
					if (markerCulling.inRange(rendered.worldPoint, REFRESH_DISTANCE))
					{
						resolvingTile.tile = rendered.worldPoint;
						rendered.marker.marker.refresh(labelResolver, metaVariableResolver);
					}
				}
			}
		}
		finally
		{
			resolvingTile.tile = null;
			readyCheckTracker.collecting = false;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		ItemQuantityFinder.clearCache();
		RuneCounter.clearCache();
		refreshNearbyMarkers();
		groupState.tick();

		hasRespondedThisTick = false;
		if (config.enablePartySync() && !config.syncTarget().isEmpty()
			&& partyService.getMembers().stream().anyMatch(m -> matchesTarget(m.getDisplayName(), config.syncTarget())))
		{
			partyService.send(new GMVMetronomeSyncRequest(config.syncTarget()));
		}
	}

	// Answers a request if we're the named target, regardless of our own Party Sync setting —
	// being a sync source doesn't require opting into following anyone yourself.
	@Subscribe
	public void onGMVMetronomeSyncRequest(GMVMetronomeSyncRequest request)
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

		partyService.send(new GMVMetronomeSyncResponse(metronomeLabelVariable.elapsedTicks(), config.countDown()));
	}

	// Applies a response only if it's actually from our configured sync target.
	@Subscribe
	public void onGMVMetronomeSyncResponse(GMVMetronomeSyncResponse response)
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

		metronomeLabelVariable.syncTo(response.getElapsedTicks(), response.isCountDown());
	}

	// Feeds {6HourTimeRemaining} — see SixHourTimeRemainingLabelVariable.
	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		sixHourTimeRemaining.onGameStateChanged(event.getGameState());

		if (event.getGameState() == GameState.LOGGED_IN)
		{
			announceUpdateIfNeeded();
		}
	}

	// Shows UPDATE_MESSAGE once per version, skipping a fresh install. Safe against LOGGED_IN
	// re-firing on loading-line crossings -- the config write makes later checks see it's shown.
	private void announceUpdateIfNeeded()
	{
		if (hasAnnounced)
		{
			return;
		}
		hasAnnounced = true;
		if (NEW_VERSION.equals(configManager.getConfiguration(GroundMarkerVariablesConfig.GROUP, LAST_SEEN_VERSION_KEY))) {
			return;
		}
		if (freshInstall)
		{
			configManager.setConfiguration(GroundMarkerVariablesConfig.GROUP, LAST_SEEN_VERSION_KEY, NEW_VERSION);
			return;
		}

		configManager.setConfiguration(GroundMarkerVariablesConfig.GROUP, LAST_SEEN_VERSION_KEY, NEW_VERSION);
		client.addChatMessage(ChatMessageType.CONSOLE, "", UPDATE_MESSAGE, null);
	}

	// Feeds {kc <boss>} — see BossKillCountTracker. Kill count messages are always GAMEMESSAGE.
	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		if (chatMessage.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		bossKillCountTracker.onChatMessage(chatMessage.getMessage());
	}

	// Case-insensitive, and underscore/space-interchangeable, since a display name can arrive
	// over the party websocket with underscores in place of spaces.
	private static boolean matchesTarget(String displayName, String target)
	{
		return displayName.replace('_', ' ').equalsIgnoreCase(target.replace('_', ' '));
	}

	public Collection<WorldView> getTrackedWorldViews()
	{
		return renderedTiles.worldViews();
	}

	@Override
	public Collection<SourceMarker> markers(int regionId)
	{
		return markersByRegion.getOrDefault(regionId, Collections.emptyList());
	}

	public void loadPoints()
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

		renderedTiles.track(wv);
	}

	// Replaces (not mutates) the cached list, so a concurrent render() never sees a partially-built list. Resolved
	// without a tile; the tick refresh then resolves each drawn one at its own tile.
	private void rebuildRegion(int regionId)
	{
		List<SourceMarker> markers = markerStorage.getStoredPoints(regionId).stream()
			.map(point -> new SourceMarker(new CachedMarker(point, labelResolver, metaVariableResolver), this,
				RenderedTiles.SAVED_PRIORITY, Long.MAX_VALUE, null))
			.collect(Collectors.toList());
		markersByRegion.put(regionId, markers);
	}
}
