package com.groundmarkervariables.variables.support;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.client.config.ConfigManager;

// {lootSession}: each monster's Loot Tracker record when first asked for (per account, until reset or the client
// closes), and what's been added since.
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class LootSessions {
    private final ConfigManager configManager;
    // Account + monster (BossAliases-resolved, lower case) -> its record at the session's start.
    private final Map<String, LootTrackerRecords.Record> starts = new ConcurrentHashMap<>();

    // current minus monster's session start, saved now if it has none. A cleared Loot Tracker starts it again.
    public LootTrackerRecords.Record since(String monster, LootTrackerRecords.Record current) {
        String key = key(monster);
        LootTrackerRecords.Record saved = starts.get(key);
        if (saved == null || current.kills < saved.kills) {
            starts.put(key, current);
            saved = current;
        }
        LootTrackerRecords.Record start = saved;

        Map<Integer, Integer> added = new LinkedHashMap<>();
        current.quantities.forEach((id, quantity) -> {
            int more = quantity - start.quantities.getOrDefault(id, 0);
            if (more > 0) {
                added.put(id, more);
            }
        });
        return new LootTrackerRecords.Record(current.name, current.found, current.kills - start.kills,
            Math.max(0, current.gp - start.gp), added);
    }

    // Starts monster's session again from now (on its next use).
    public void reset(String monster) {
        starts.remove(key(monster));
    }

    private String key(String monster) {
        return configManager.getRSProfileKey() + ":" + BossAliases.resolve(monster.trim()).toLowerCase(Locale.ROOT);
    }
}
