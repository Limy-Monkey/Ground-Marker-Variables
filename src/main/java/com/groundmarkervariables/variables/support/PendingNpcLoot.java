package com.groundmarkervariables.variables.support;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

// One monster's NPC loot the game has told us about but Loot Tracker hasn't written to config
// yet -- already merged across however many kills, not a raw per-kill list. See RecentLootTracker.
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class PendingNpcLoot {
    public final String displayName;
    public int kills;
    public long gpValue;
    public final Map<Integer, Integer> itemQuantities = new LinkedHashMap<>();
}
