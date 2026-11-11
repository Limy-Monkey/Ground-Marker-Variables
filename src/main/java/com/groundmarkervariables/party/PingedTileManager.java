package com.groundmarkervariables.party;

import com.groundmarkervariables.markers.CachedMarker;
import com.groundmarkervariables.markers.GroundMarkerPointData;
import com.groundmarkervariables.GroundMarkerVariablesConfig;
import com.groundmarkervariables.GroundMarkerVariablesPlugin;
import com.groundmarkervariables.markers.MarkerStorage;
import com.groundmarkervariables.markers.RenderedTiles;
import com.groundmarkervariables.markers.SourceMarker;
import com.groundmarkervariables.markers.TileSource;
import com.groundmarkervariables.variables.LabelResolver;
import com.groundmarkervariables.variables.meta.MetaVariableResolver;
import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;

// Shares a marker with the party from its shift+right-click "Share Marker", and displays markers shared by
// others as a temporary, fading marker (a TileSource) with its own "Save"/"Hide" right-click options.
@Slf4j
@Singleton
public class PingedTileManager implements TileSource
{
	// By stored (region) point. Arrives on WSClient's own thread, not the client/AWT thread render()/menus run on.
	private final Map<WorldPoint, PingedTile> pingedTiles = new ConcurrentHashMap<>();

	// Senders with one hide so far this session -- a second hide blocks them.
	private final Set<Long> hiddenOnceSenderIds = ConcurrentHashMap.newKeySet();
	private final Set<Long> blockedSenderIds = ConcurrentHashMap.newKeySet();

	private final Client client;
	private final ClientThread clientThread;
	private final EventBus eventBus;
	private final ScheduledExecutorService executor;
	private final GroundMarkerVariablesConfig config;
	private final GroundMarkerVariablesPlugin plugin;
	private final MarkerStorage markerStorage;
	private final LabelResolver labelResolver;
	private final MetaVariableResolver metaVariableResolver;
	private final PartyService partyService;
	private final WSClient wsClient;
	private final ChatMessageManager chatMessageManager;
	private final RenderedTiles renderedTiles;

	@Inject
	private PingedTileManager(Client client, ClientThread clientThread, EventBus eventBus, ScheduledExecutorService executor,
		GroundMarkerVariablesConfig config, GroundMarkerVariablesPlugin plugin, MarkerStorage markerStorage, LabelResolver labelResolver,
		MetaVariableResolver metaVariableResolver, PartyService partyService, WSClient wsClient, ChatMessageManager chatMessageManager,
		RenderedTiles renderedTiles)
	{
		this.renderedTiles = renderedTiles;
		this.client = client;
		this.clientThread = clientThread;
		this.eventBus = eventBus;
		this.executor = executor;
		this.config = config;
		this.plugin = plugin;
		this.markerStorage = markerStorage;
		this.labelResolver = labelResolver;
		this.metaVariableResolver = metaVariableResolver;
		this.partyService = partyService;
		this.wsClient = wsClient;
		this.chatMessageManager = chatMessageManager;
	}

	public void startUp()
	{
		eventBus.register(this);

		try
		{
			wsClient.registerMessage(GMVPingedTileShare.class);
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
			wsClient.unregisterMessage(GMVPingedTileShare.class);
		}
		catch (IllegalArgumentException e)
		{
			// Never registered successfully.
		}

		pingedTiles.clear();
		hiddenOnceSenderIds.clear();
		blockedSenderIds.clear();
	}

	// Blocks are scoped to the current party session.
	@Subscribe
	public void onPartyChanged(PartyChanged event)
	{
		if (event.getPartyId() == null)
		{
			hiddenOnceSenderIds.clear();
			blockedSenderIds.clear();
		}
	}

	// The marker at point, from its shift+right-click "Share Marker" (see GroundMarkerVariablesPlugin).
	public void share(WorldPoint point, GroundMarkerPointData marker)
	{
		if (!partyService.isInParty())
		{
			return;
		}

		Integer rgb = marker.getColor() == null ? null : marker.getColor().getRGB();
		partyService.send(new GMVPingedTileShare(point, rgb, marker.getLabel()));
		sendChatMessage("Ground Marker shared with your party.");
	}

	// Displays a tile shared by another party member, unless we already have it marked.
	@Subscribe
	public void onGMVPingedTileShare(GMVPingedTileShare share)
	{
		if (!config.showPingedTiles())
		{
			return;
		}

		if (blockedSenderIds.contains(share.getMemberId()))
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
			CachedMarker marker = new CachedMarker(transientPoint, labelResolver, metaVariableResolver);
			PingedTile pinged = new PingedTile(marker, share.getMemberId(), senderName, Instant.now());
			pingedTiles.put(point, pinged);
			renderedTiles.invalidate();

			// Keyed to this exact instance, so a stale timer can never remove a later ping that
			// landed on the same tile.
			executor.schedule(() -> clientThread.invoke(() ->
			{
				if (pingedTiles.remove(point, pinged))
				{
					renderedTiles.invalidate();
				}
			}), PingedTile.TTL.getSeconds(), TimeUnit.SECONDS);
		});
	}

	// Below saved markers; newer pings win ties.
	@Override
	public Collection<SourceMarker> markers(int regionId)
	{
		List<SourceMarker> markers = new ArrayList<>();
		for (PingedTile tile : pingedTiles.values())
		{
			if (tile.marker.source.getRegionId() == regionId)
			{
				markers.add(new SourceMarker(tile.marker, this, RenderedTiles.PINGED_PRIORITY, tile.receivedAt.toEpochMilli(), tile));
			}
		}
		return markers;
	}

	@Override
	public float opacity(SourceMarker marker, Instant now)
	{
		return ((PingedTile) marker.owner).opacity(now);
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
			.onClick(e -> hide(worldPoint, pinged));
	}

	// A second hide from the same sender blocks them for the rest of this party session.
	private void hide(WorldPoint worldPoint, PingedTile pinged)
	{
		pingedTiles.remove(worldPoint);
		renderedTiles.invalidate();

		long senderId = pinged.senderMemberId;
		if (hiddenOnceSenderIds.remove(senderId))
		{
			blockedSenderIds.add(senderId);
			sendChatMessage("Ground Marker Variables: " + pinged.senderName + " blocked. Turn off Show Pinged Tiles to disable this feature.");
		}
		else
		{
			hiddenOnceSenderIds.add(senderId);
			sendChatMessage("Ground Marker Variables: Marker hidden from " + pinged.senderName + ". Hide one more to block them for this party session.");
		}
	}

	private void save(WorldPoint worldPoint, PingedTile pinged)
	{
		GroundMarkerPointData point = new GroundMarkerPointData(worldPoint.getRegionID(), worldPoint.getRegionX(), worldPoint.getRegionY(),
			worldPoint.getPlane(), pinged.marker.source.getColor(), pinged.marker.source.getLabel());
		markerStorage.addPoints(List.of(point));
		plugin.loadPoints();
		pingedTiles.remove(worldPoint);
		renderedTiles.invalidate();
		sendChatMessage("Ground Marker saved from " + pinged.senderName + ".");
	}

	private void sendChatMessage(String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(message)
			.build());
	}
}
