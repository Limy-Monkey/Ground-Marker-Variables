package com.groundmarkervariables.party;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.groundmarkervariables.CachedMarker;
import com.groundmarkervariables.GroundMarkerPointData;
import com.groundmarkervariables.GroundMarkerVariablesConfig;
import com.groundmarkervariables.GroundMarkerVariablesPlugin;
import com.groundmarkervariables.MarkerStorage;
import com.groundmarkervariables.variables.LabelResolver;
import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.WorldViewLoaded;
import net.runelite.api.events.WorldViewUnloaded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.plugins.party.messages.TilePing;

// Shares a marked tile with the party when you ping it, and displays tiles shared by others as
// a temporary, fading marker with its own "Save"/"Hide" right-click options. See PINGEDTILE.md.
@Slf4j
@Singleton
public class PingedTileManager
{
	// Arrives on WSClient's own thread, not the client/AWT thread render()/menus run on.
	private final Map<WorldPoint, PingedTile> pingedTiles = new ConcurrentHashMap<>();

	// Mirrors GroundMarkerVariablesPlugin's markersByWorldView, rebuilt by retranslate().
	private final Map<WorldView, List<TranslatedPingedTile>> translatedByWorldView = new ConcurrentHashMap<>();

	// Send-side debounce, presence-only, self-evicting after 2s.
	private final Cache<WorldPoint, Boolean> recentlySent = CacheBuilder.newBuilder()
		.expireAfterWrite(2, TimeUnit.SECONDS)
		.build();

	private final Client client;
	private final ClientThread clientThread;
	private final EventBus eventBus;
	private final ScheduledExecutorService executor;
	private final GroundMarkerVariablesConfig config;
	private final GroundMarkerVariablesPlugin plugin;
	private final MarkerStorage markerStorage;
	private final LabelResolver labelResolver;
	private final PartyService partyService;
	private final WSClient wsClient;

	@Inject
	private PingedTileManager(Client client, ClientThread clientThread, EventBus eventBus, ScheduledExecutorService executor,
		GroundMarkerVariablesConfig config, GroundMarkerVariablesPlugin plugin, MarkerStorage markerStorage, LabelResolver labelResolver,
		PartyService partyService, WSClient wsClient)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.eventBus = eventBus;
		this.executor = executor;
		this.config = config;
		this.plugin = plugin;
		this.markerStorage = markerStorage;
		this.labelResolver = labelResolver;
		this.partyService = partyService;
		this.wsClient = wsClient;
	}

	public void startUp()
	{
		eventBus.register(this);

		try
		{
			wsClient.registerMessage(TilePing.class);
			wsClient.registerMessage(PingedTileShare.class);
		}
		catch (IllegalArgumentException e)
		{
			log.warn("Pinged tile sharing unavailable due to a message type collision with another plugin", e);
		}
	}

	public void shutDown()
	{
		eventBus.unregister(this);

		try
		{
			wsClient.unregisterMessage(PingedTileShare.class);
		}
		catch (IllegalArgumentException e)
		{
			// Never registered successfully.
		}

		pingedTiles.clear();
		translatedByWorldView.clear();
	}

	// Reacts only to our own ping (the relay echoes TilePing back to the sender too) -- shares
	// the tile there if we have one marked.
	@Subscribe
	public void onTilePing(TilePing event)
	{
		if (!config.sendPingedTiles())
		{
			return;
		}

		PartyMember local = partyService.getLocalMember();
		if (local == null || event.getMemberId() != local.getMemberId())
		{
			return;
		}

		WorldPoint point = event.getPoint();
		if (recentlySent.getIfPresent(point) != null)
		{
			return;
		}

		GroundMarkerPointData existing = markerStorage.findStoredPoint(point);
		if (existing == null)
		{
			return;
		}

		recentlySent.put(point, Boolean.TRUE);
		Integer rgb = existing.getColor() == null ? null : existing.getColor().getRGB();
		partyService.send(new PingedTileShare(point, rgb, existing.getLabel()));
	}

	// Displays a tile shared by another party member, unless we already have it marked.
	@Subscribe
	public void onPingedTileShare(PingedTileShare share)
	{
		if (!config.showPingedTiles())
		{
			return;
		}

		WorldPoint point = share.getPoint();
		if (markerStorage.findStoredPoint(point) != null)
		{
			return;
		}

		PartyMember sender = partyService.getMemberById(share.getMemberId());
		String senderName = sender != null ? sender.getDisplayName() : "Unknown";
		Color color = share.getColorRgb() == null ? null : new Color(share.getColorRgb(), true);

		// Resolving the label needs the client thread; this handler runs on WSClient's own.
		clientThread.invoke(() ->
		{
			GroundMarkerPointData transientPoint = new GroundMarkerPointData(
				point.getRegionID(), point.getRegionX(), point.getRegionY(), point.getPlane(), color, share.getLabel());
			CachedMarker marker = new CachedMarker(transientPoint, labelResolver);
			PingedTile pinged = new PingedTile(marker, share.getMemberId(), senderName, Instant.now());
			pingedTiles.put(point, pinged);
			retranslate();

			// Keyed to this exact instance, so a stale timer can never remove a later ping that
			// landed on the same tile.
			executor.schedule(() -> clientThread.invoke(() ->
			{
				if (pingedTiles.remove(point, pinged))
				{
					retranslate();
				}
			}), PingedTile.TTL.getSeconds(), TimeUnit.SECONDS);
		});
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		pingedTiles.values().forEach(tile -> tile.marker.refresh(labelResolver));
	}

	// Same two hooks GroundMarkerVariablesPlugin uses for its own markersByWorldView.
	@Subscribe
	public void onWorldViewLoaded(WorldViewLoaded event)
	{
		retranslate();
	}

	@Subscribe
	public void onWorldViewUnloaded(WorldViewUnloaded event)
	{
		retranslate();
	}

	// Mirrors GroundMarkerVariablesPlugin#translateWorldView. Drops any tile that no longer
	// resolves anywhere, instead of just skipping it.
	private void retranslate()
	{
		Map<WorldView, List<TranslatedPingedTile>> rebuilt = new ConcurrentHashMap<>();
		Set<WorldPoint> resolved = new HashSet<>();

		for (WorldView wv : plugin.getTrackedWorldViews())
		{
			List<TranslatedPingedTile> translated = new ArrayList<>();
			for (PingedTile tile : pingedTiles.values())
			{
				GroundMarkerPointData source = tile.marker.source;
				WorldPoint point = WorldPoint.fromRegion(source.getRegionId(), source.getRegionX(), source.getRegionY(), source.getZ());
				for (WorldPoint wp : WorldPoint.toLocalInstance(wv, point))
				{
					translated.add(new TranslatedPingedTile(wp, tile));
					resolved.add(point);
				}
			}
			rebuilt.put(wv, translated);
		}

		translatedByWorldView.clear();
		translatedByWorldView.putAll(rebuilt);
		pingedTiles.keySet().removeIf(point -> !resolved.contains(point));
	}

	// Independent of GroundMarkerVariablesPlugin's own shift-gated Mark/Unmark/Label/Color --
	// Save/Hide apply to a temp-cached ping, not a persisted marker, and aren't shift-gated.
	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		MenuAction menuAction = event.getMenuEntry().getType();
		if (menuAction != MenuAction.WALK && menuAction != MenuAction.SET_HEADING)
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
		PingedTile pinged = pingedTiles.get(worldPoint);
		if (pinged == null)
		{
			return;
		}

		client.createMenuEntry(-1)
			.setOption("Save " + pinged.senderName + "'s")
			.setTarget("Tile")
			.setType(MenuAction.RUNELITE)
			.onClick(e -> save(worldPoint, pinged));

		client.createMenuEntry(-2)
			.setOption("Hide " + pinged.senderName + "'s")
			.setTarget("Tile")
			.setType(MenuAction.RUNELITE)
			.onClick(e ->
			{
				pingedTiles.remove(worldPoint);
				retranslate();
			});
	}

	private void save(WorldPoint worldPoint, PingedTile pinged)
	{
		GroundMarkerPointData point = new GroundMarkerPointData(worldPoint.getRegionID(), worldPoint.getRegionX(), worldPoint.getRegionY(),
			worldPoint.getPlane(), pinged.marker.source.getColor(), pinged.marker.source.getLabel());
		markerStorage.addPoints(List.of(point));
		plugin.loadPoints();
		pingedTiles.remove(worldPoint);
		retranslate();
	}

	public Collection<TranslatedPingedTile> getTranslatedPingedTiles(WorldView wv)
	{
		return translatedByWorldView.getOrDefault(wv, Collections.emptyList());
	}
}
