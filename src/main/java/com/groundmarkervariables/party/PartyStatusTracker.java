package com.groundmarkervariables.party;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.party.WSClient;
import net.runelite.client.plugins.party.messages.StatusUpdate;

// Listens for RuneLite's own "Party" plugin StatusUpdate message (BSD 2-Clause License):
// Copyright (c) 2022, LlemonDuck <napkinorton@gmail.com>. All rights reserved.
// See https://github.com/runelite/runelite/blob/master/LICENSE
@Slf4j
@Singleton
public class PartyStatusTracker
{
	private final EventBus eventBus;
	private final WSClient wsClient;
	private final Map<Long, StatusUpdate> statusByMember = new ConcurrentHashMap<>();

	@Inject
	private PartyStatusTracker(EventBus eventBus, WSClient wsClient)
	{
		this.eventBus = eventBus;
		this.wsClient = wsClient;
	}

	public void startUp()
	{
		eventBus.register(this);

		try
		{
			wsClient.registerMessage(StatusUpdate.class);
		}
		catch (IllegalArgumentException e)
		{
			log.warn("Party status lookup unavailable due to a message type collision with another plugin", e);
		}
	}

	public void shutDown()
	{
		eventBus.unregister(this);

		try
		{
			wsClient.unregisterMessage(StatusUpdate.class);
		}
		catch (IllegalArgumentException e)
		{
			// Never registered successfully; nothing to unregister.
		}

		statusByMember.clear();
	}

	@Subscribe
	public void onStatusUpdate(StatusUpdate update)
	{
		statusByMember.merge(update.getMemberId(), update, PartyStatusTracker::merge);
	}

	// Only changed fields are sent -- keep whatever was already known for the rest.
	private static StatusUpdate merge(StatusUpdate existing, StatusUpdate update)
	{
		if (update.getHealthCurrent() == null)
		{
			update.setHealthCurrent(existing.getHealthCurrent());
		}
		if (update.getHealthMax() == null)
		{
			update.setHealthMax(existing.getHealthMax());
		}
		if (update.getPrayerCurrent() == null)
		{
			update.setPrayerCurrent(existing.getPrayerCurrent());
		}
		if (update.getPrayerMax() == null)
		{
			update.setPrayerMax(existing.getPrayerMax());
		}
		return update;
	}

	// null if nothing has been received yet for this member.
	public StatusUpdate get(long memberId)
	{
		return statusByMember.get(memberId);
	}
}
