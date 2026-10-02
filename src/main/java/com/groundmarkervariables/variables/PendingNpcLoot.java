package com.groundmarkervariables.variables;

import java.util.LinkedHashMap;
import java.util.Map;

// One monster's NPC loot the game has told us about but Loot Tracker hasn't written to config
// yet -- already merged across however many kills, not a raw per-kill list. See RecentLootTracker.
class PendingNpcLoot
{
	final String displayName;
	int kills;
	long gpValue;
	final Map<Integer, Integer> itemQuantities = new LinkedHashMap<>();

	PendingNpcLoot(String displayName)
	{
		this.displayName = displayName;
	}
}
