package com.groundmarkervariables.variables.support;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.party.PartyService;
import net.runelite.client.util.Text;

// {readyCheck}: each party member's latest ready status, from what they typed in chat. Only recorded
// while a {readyCheck} tile is nearby, and reset once none is, e.g. on leaving a raid lobby.
@Singleton
public class ReadyCheckTracker
{
	public enum Status
	{
		READY,
		POTTING,
		NOT_READY,
		AWAY
	}

	private static final Pattern READY = Pattern.compile("r|ready", Pattern.CASE_INSENSITIVE);
	private static final Pattern POTTING = Pattern.compile("p|pot|potting", Pattern.CASE_INSENSITIVE);
	private static final Pattern NOT_READY = Pattern.compile("(?:not|no|un) r", Pattern.CASE_INSENSITIVE);
	private static final Pattern AWAY = Pattern.compile("\\bbrb\\b|\\b\\d+ mins\\b", Pattern.CASE_INSENSITIVE);

	private final Client client;
	private final EventBus eventBus;
	private final PartyService partyService;

	// Set by the plugin while it refreshes nearby tiles.
	public boolean collecting;

	private boolean seenThisTick;
	private boolean active;

	// By standardized name.
	private final Map<String, Status> statuses = new HashMap<>();

	@Inject
	private ReadyCheckTracker(Client client, EventBus eventBus, PartyService partyService)
	{
		this.client = client;
		this.eventBus = eventBus;
		this.partyService = partyService;
	}

	public void startUp()
	{
		eventBus.register(this);
	}

	public void shutDown()
	{
		eventBus.unregister(this);
		statuses.clear();
		active = false;
		seenThisTick = false;
	}

	@Nullable
	public Status status(@Nullable String name)
	{
		seenThisTick |= collecting;
		return name == null ? null : statuses.get(standardize(name));
	}

	@Nullable
	public Status localStatus()
	{
		return client.getLocalPlayer() == null ? null : status(client.getLocalPlayer().getName());
	}

	// After the plugin's own onGameTick, which resolves the nearby tiles.
	@Subscribe(priority = -1)
	public void onGameTick(GameTick event)
	{
		active = seenThisTick;
		seenThisTick = false;
		if (!active)
		{
			statuses.clear();
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!active || !isPlayerChat(event.getType()) || event.getName() == null)
		{
			return;
		}

		String name = standardize(event.getName());
		if (!isPartyMember(name))
		{
			return;
		}

		String message = Text.removeTags(event.getMessage()).replace(' ', ' ').trim();
		Status status = READY.matcher(message).matches() ? Status.READY
			: POTTING.matcher(message).matches() ? Status.POTTING
			: NOT_READY.matcher(message).matches() ? Status.NOT_READY
			: AWAY.matcher(message).find() ? Status.AWAY
			: null;
		if (status != null)
		{
			statuses.put(name, status);
		}
	}

	@Subscribe
	public void onPartyChanged(PartyChanged event)
	{
		statuses.clear();
	}

	private boolean isPartyMember(String name)
	{
		return partyService.getMembers().stream().anyMatch(member -> name.equals(standardize(member.getDisplayName())));
	}

	private static boolean isPlayerChat(ChatMessageType type)
	{
		switch (type)
		{
			case PUBLICCHAT:
			case MODCHAT:
			case PRIVATECHAT:
			case MODPRIVATECHAT:
			case FRIENDSCHAT:
			case CLAN_CHAT:
			case CLAN_GUEST_CHAT:
			case CLAN_GIM_CHAT:
				return true;
			default:
				return false;
		}
	}

	// Party names can arrive with underscores in place of spaces.
	private static String standardize(String name)
	{
		return Text.standardize(Text.removeTags(name).replace('_', ' '));
	}
}
