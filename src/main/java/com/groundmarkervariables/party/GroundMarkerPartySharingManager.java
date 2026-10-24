package com.groundmarkervariables.party;

import com.google.common.util.concurrent.Runnables;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.groundmarkervariables.GroundMarkerPointData;
import com.groundmarkervariables.GroundMarkerVariablesSharingManager;
import com.groundmarkervariables.ImportPreviewManager;
import com.groundmarkervariables.minify.MarkerCompression;
import com.groundmarkervariables.minify.NewerFormatException;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.messages.PartyMemberMessage;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.util.ColorUtil;

// "Party -> Ground Markers" submenu beneath "Export -> Ground Markers": "Share to Party" sends
// every currently loaded marker; recipients get "Import ... from <user>" for PendingPartyShare.TTL.
@Slf4j
@Singleton
public class GroundMarkerPartySharingManager
{
	private static final int WIDGET_FIXED = InterfaceID.Orbs.WORLDMAP;
	private static final int WIDGET_RESIZABLE = InterfaceID.OrbsNomap.WORLDMAP;

	private static final String TARGET = ColorUtil.wrapWithColorTag("Ground Markers", JagexColors.MENU_TARGET);

	private static final Duration BADGE_FULL_OPACITY_DURATION = Duration.ofSeconds(2);
	private static final Duration BADGE_FADE_DURATION = Duration.ofSeconds(3);
	private static final Duration BADGE_VISIBLE_DURATION = BADGE_FULL_OPACITY_DURATION.plus(BADGE_FADE_DURATION);

	// Keyed by sender -- one pending share per sender, replaced (not merged) by their next one.
	private final Map<Long, PendingPartyShare> pendingShares = new ConcurrentHashMap<>();

	private volatile Instant lastReceivedAt;
	private ScheduledFuture<?> badgeClearTask;

	private final Client client;
	private final ChatboxPanelManager chatboxPanelManager;
	private final ChatMessageManager chatMessageManager;
	private final EventBus eventBus;
	private final ScheduledExecutorService executor;
	private final ImportPreviewManager importPreviewManager;
	private final GroundMarkerVariablesSharingManager sharingManager;
	private final PartyService partyService;
	private final WSClient wsClient;
	private final Gson gson;

	@Inject
	private GroundMarkerPartySharingManager(Client client, ChatboxPanelManager chatboxPanelManager, ChatMessageManager chatMessageManager,
		EventBus eventBus, ScheduledExecutorService executor, ImportPreviewManager importPreviewManager,
		GroundMarkerVariablesSharingManager sharingManager, PartyService partyService, WSClient wsClient, Gson gson)
	{
		this.gson = gson;
		this.client = client;
		this.chatboxPanelManager = chatboxPanelManager;
		this.chatMessageManager = chatMessageManager;
		this.eventBus = eventBus;
		this.executor = executor;
		this.importPreviewManager = importPreviewManager;
		this.sharingManager = sharingManager;
		this.partyService = partyService;
		this.wsClient = wsClient;
	}

	public void startUp()
	{
		eventBus.register(this);
		registerMessage(GMVCompressedMarkerShare.class);
		registerMessage(GMVGroundMarkerPartyShare.class);
	}

	private void registerMessage(Class<? extends PartyMemberMessage> message)
	{
		try
		{
			wsClient.registerMessage(message);
		}
		catch (IllegalArgumentException e)
		{
			log.warn("Party marker sharing unavailable due to a message type collision with another plugin", e);
		}
	}

	private void unregisterMessage(Class<? extends PartyMemberMessage> message)
	{
		try
		{
			wsClient.unregisterMessage(message);
		}
		catch (IllegalArgumentException e)
		{
			// Never registered successfully.
		}
	}

	public void shutDown()
	{
		eventBus.unregister(this);
		if (badgeClearTask != null)
		{
			badgeClearTask.cancel(false);
		}

		unregisterMessage(GMVCompressedMarkerShare.class);
		unregisterMessage(GMVGroundMarkerPartyShare.class);

		pendingShares.clear();
		lastReceivedAt = null;
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (client.isWidgetSelected() || event.getType() != MenuAction.CC_OP.getId())
		{
			return;
		}

		int widgetId = event.getActionParam1();
		if (widgetId != WIDGET_FIXED && widgetId != WIDGET_RESIZABLE)
		{
			return;
		}

		if (!partyService.isInParty())
		{
			return;
		}

		for (MenuEntry entry : client.getMenuEntries())
		{
			if ("Party".equals(entry.getOption()) && TARGET.equals(entry.getTarget()))
			{
				return;
			}
		}

		Menu submenu = client.createMenuEntry(-2)
			.setOption("Party")
			.setTarget(TARGET)
			.setType(MenuAction.RUNELITE)
			.createSubMenu();

		submenu.createMenuEntry(-1)
			.setOption("Share to Party")
			.setType(MenuAction.RUNELITE)
			.onClick(e -> promptShare());

		List<PendingPartyShare> pending = new ArrayList<>(pendingShares.values());
		pending.sort(Comparator.comparing((PendingPartyShare p) -> p.receivedAt).reversed());
		for (PendingPartyShare share : pending)
		{
			submenu.createMenuEntry(-1)
				.setOption("Import " + share.markers.size() + " markers from " + share.senderName)
				.setType(MenuAction.RUNELITE)
				.onClick(e -> promptImport(share));
		}
	}

	private void promptShare()
	{
		List<GroundMarkerPointData> activePoints = sharingManager.activePoints();
		if (activePoints.isEmpty())
		{
			sendChatMessage("You have no ground markers to share.");
			return;
		}

		chatboxPanelManager.openTextMenuInput("Are you sure you want to share " + activePoints.size() + " ground markers with your party?")
			.option("Yes", () -> share(activePoints))
			.option("No", Runnables.doNothing())
			.build();
	}

	private void share(List<GroundMarkerPointData> points)
	{
		byte[] compressed = MarkerCompression.compress(gson.toJson(points));
		partyService.send(new GMVCompressedMarkerShare(MarkerCompression.VERSION, Base64.getEncoder().withoutPadding().encodeToString(compressed)));
		sendChatMessage(points.size() + " ground markers were shared with your party.");
	}

	@Subscribe
	public void onGMVCompressedMarkerShare(GMVCompressedMarkerShare share)
	{
		if (isOwn(share))
		{
			return;
		}

		List<GroundMarkerPointData> points;
		try
		{
			String json = MarkerCompression.decompress(Base64.getDecoder().decode(share.getData()), share.getVersion());
			points = gson.fromJson(json, new TypeToken<List<GroundMarkerPointData>>(){}.getType());
		}
		catch (NewerFormatException e)
		{
			sendChatMessage(senderName(share) + " shared ground markers from a newer version of Ground Marker Variables. Update the plugin to import them.");
			return;
		}
		catch (IllegalArgumentException | JsonSyntaxException e)
		{
			log.debug("Damaged party marker share", e);
			return;
		}
		addPending(share, points);
	}

	@Subscribe
	public void onGMVGroundMarkerPartyShare(GMVGroundMarkerPartyShare share)
	{
		if (isOwn(share))
		{
			return;
		}

		List<GroundMarkerPointData> points = new ArrayList<>();
		for (GMVGroundMarkerPartyShare.SharedMarker marker : share.getMarkers())
		{
			WorldPoint point = marker.getPoint();
			Color color = marker.getColorRgb() == null ? null : new Color(marker.getColorRgb(), true);
			points.add(new GroundMarkerPointData(point.getRegionID(), point.getRegionX(), point.getRegionY(), point.getPlane(), color, marker.getLabel()));
		}
		addPending(share, points);
	}

	private boolean isOwn(PartyMemberMessage message)
	{
		PartyMember local = partyService.getLocalMember();
		return local != null && message.getMemberId() == local.getMemberId();
	}

	private String senderName(PartyMemberMessage message)
	{
		PartyMember sender = partyService.getMemberById(message.getMemberId());
		return sender != null ? sender.getDisplayName() : "Unknown";
	}

	// Replaces any pending share from the same sender. The scheduled removal is keyed to this exact
	// instance, so a stale timer from a replaced share can never remove the one that replaced it.
	private void addPending(PartyMemberMessage share, List<GroundMarkerPointData> points)
	{
		if (points == null || points.isEmpty())
		{
			return;
		}

		PendingPartyShare pending = new PendingPartyShare(share.getMemberId(), senderName(share), points, Instant.now());
		pendingShares.put(share.getMemberId(), pending);
		executor.schedule(() -> pendingShares.remove(pending.senderMemberId, pending), PendingPartyShare.TTL.getSeconds(), TimeUnit.SECONDS);

		showBadge();
	}

	private void promptImport(PendingPartyShare pending)
	{
		List<GroundMarkerPointData> points = pending.markers;
		List<GroundMarkerPointData> nonOverlapping = sharingManager.nonOverlapping(points);
		importPreviewManager.show(nonOverlapping);
		String count = GroundMarkerVariablesSharingManager.countWithOverlap(points.size(), nonOverlapping.size());
		chatboxPanelManager.openTextMenuInput(
				"Are you sure you want to import " + count + " ground markers<br>from " + pending.senderName + "?")
			.option("Yes", () -> doImport(pending, nonOverlapping))
			.option("No", Runnables.doNothing())
			.onClose(importPreviewManager::clear)
			.build();
	}

	private void doImport(PendingPartyShare pending, List<GroundMarkerPointData> points)
	{
		sharingManager.importGroundMarkers(points);
		sendChatMessage(points.size() + " ground markers were imported from " + pending.senderName + ".");
		pendingShares.remove(pending.senderMemberId, pending);
	}

	// Cancels any still-pending clear from an earlier badge before scheduling this one, so an
	// old timer can never null out a newer receipt.
	private void showBadge()
	{
		lastReceivedAt = Instant.now();
		if (badgeClearTask != null)
		{
			badgeClearTask.cancel(false);
		}
		badgeClearTask = executor.schedule(() -> lastReceivedAt = null, BADGE_VISIBLE_DURATION.toMillis(), TimeUnit.MILLISECONDS);
	}

	// 100% for the first 2s, linearly down to 0% over the next 3s, null (hidden) after that.
	float badgeOpacity(Instant now)
	{
		Instant receivedAt = lastReceivedAt;
		if (receivedAt == null)
		{
			return 0f;
		}

		Duration age = Duration.between(receivedAt, now);
		if (age.compareTo(BADGE_FULL_OPACITY_DURATION) <= 0)
		{
			return 1f;
		}

		Duration intoFade = age.minus(BADGE_FULL_OPACITY_DURATION);
		return 1f - (float) intoFade.toMillis() / BADGE_FADE_DURATION.toMillis();
	}

	private void sendChatMessage(String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(message)
			.build());
	}
}
