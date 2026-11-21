package com.groundmarkervariables.variables.label;

import com.google.gson.Gson;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.CollectionLog;
import com.groundmarkervariables.variables.support.LootSessions;
import com.groundmarkervariables.variables.support.LootTrackerRecords;
import com.groundmarkervariables.variables.support.RuneProfileCollectionLog;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;

// {lootSession <monster>, ...}: {loot}, counting only from its first use (see LootSessions). Reset by Shift
// right-click > Tile Variables > Reset Session.
public class LootSessionLabelVariable extends LootLabelVariable
{
	@Inject
	private LootSessionLabelVariable(ItemManager itemManager, LootTrackerRecords lootTrackerRecords, RichText richText,
		CollectionLog collectionLog, RuneProfileCollectionLog runeProfile, LootSessions lootSessions, ConfigManager configManager, Gson gson)
	{
		super(itemManager, lootTrackerRecords, richText, collectionLog, runeProfile, lootSessions, configManager, gson, true);
	}
}
