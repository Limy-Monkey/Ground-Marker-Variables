package com.groundmarkervariables.variables.support;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.NPCComposition;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigSync;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.util.Text;

// Fills the gap between an NPC kill and Loot Tracker writing it to config, which only happens
// on ConfigSync (up to every 5 min) -- see LootLabelVariable, which merges this in each tick.
@Singleton
public class RecentLootTracker
{
	private final Map<String, PendingNpcLoot> pending = new ConcurrentHashMap<>();

	private final EventBus eventBus;
	private final ItemManager itemManager;

	@Inject
	private RecentLootTracker(EventBus eventBus, ItemManager itemManager)
	{
		this.eventBus = eventBus;
		this.itemManager = itemManager;
	}

	public void startUp()
	{
		eventBus.register(this);
	}

	public void shutDown()
	{
		eventBus.unregister(this);
		pending.clear();
	}

	@Subscribe
	public void onServerNpcLoot(ServerNpcLoot event)
	{
		NPCComposition npc = event.getComposition();
		String name = Text.removeTags(npc.getName());
		if (name.isEmpty())
		{
			return;
		}

		PendingNpcLoot loot = pending.computeIfAbsent(name.toLowerCase(Locale.ENGLISH), key -> new PendingNpcLoot(name));
		loot.kills++;
		for (ItemStack item : event.getItems())
		{
			loot.gpValue += (long) itemManager.getItemPrice(item.getId()) * item.getQuantity();
			loot.itemQuantities.merge(item.getId(), item.getQuantity(), Integer::sum);
		}
	}

	// Same trigger as Loot Tracker's ConfigSync flush
	@Subscribe
	public void onConfigSync(ConfigSync event)
	{
		pending.clear();
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		pending.clear();
	}

	public PendingNpcLoot get(String monster)
	{
		return pending.get(monster.toLowerCase(Locale.ENGLISH));
	}
}
