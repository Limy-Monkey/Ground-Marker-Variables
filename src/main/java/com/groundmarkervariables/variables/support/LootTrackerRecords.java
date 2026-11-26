package com.groundmarkervariables.variables.support;

import com.google.gson.Gson;
import java.time.Instant;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;

// Shared by {loot} (LootTracker) and {log} (RuneProfile).
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class LootTrackerRecords {
    private static final String GROUP = "loottracker";
    private static final String KEY_PREFIX = "drops_";

    // Names that combine several Loot Tracker entries -- shared with the editor's suggestions.
    public static final Map<String, List<String>> COMBINED_MONSTERS = Map.of(
        "Royal Titans", List.of("Eldric the Ice King", "Branda the Fire Queen"));
    public static final List<String> PLAYER_NAMES = List.of("players", "pk", "pker");
    private static final String PLAYER_TYPE = "PLAYER";
    private static final String PLAYERS_DISPLAY_NAME = "PKs";

    private final Client client;
    private final ConfigManager configManager;
    private final ItemManager itemManager;
    private final Gson gson;
    private final RecentLootTracker recentLootTracker;

    // The names of the items monster has dropped (unnoted), alphabetically; null off the client thread, where items
    // can't be named.
    @Nullable
    public List<String> itemNames(String monster) {
        if (!client.isClientThread()) {
            return null;
        }
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (int id : find(monster).quantities.keySet()) {
            names.add(itemManager.getItemComposition(itemManager.canonicalize(id)).getName());
        }
        return new ArrayList<>(names);
    }

    // A monster's loot: its name as Loot Tracker has it, and nothing found if it has none.
    public static final class Record {
        public final String name;
        public final boolean found;
        public final int kills;
        public final long gp;
        // Item ID (as dropped, maybe noted) -> quantity.
        public final Map<Integer, Integer> quantities;

        Record(String name, boolean found, int kills, long gp, Map<Integer, Integer> quantities) {
            this.name = name;
            this.found = found;
            this.kills = kills;
            this.gp = gp;
            this.quantities = quantities;
        }

        // Both monsters' loot added up, named "<this> + <other>".
        public Record plus(Record other) {
            Map<Integer, Integer> added = new LinkedHashMap<>(quantities);
            other.quantities.forEach((id, quantity) -> added.merge(id, quantity, Integer::sum));
            return new Record(name + " + " + other.name, found || other.found, kills + other.kills, gp + other.gp, added);
        }
    }

    // monster's loot (any case); found is false if Loot Tracker has none, or it was cleared.
    public Record find(String monster) {
        boolean players = PLAYER_NAMES.contains(monster.toLowerCase(Locale.ENGLISH));
        String name = players ? PLAYERS_DISPLAY_NAME : BossAliases.resolve(monster);
        List<String> parts = List.of(monster, name);
        boolean combined = false;
        for (Map.Entry<String, List<String>> entry : COMBINED_MONSTERS.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(monster)) {
                name = entry.getKey();
                parts = entry.getValue();
                combined = true;
            }
        }

        String profile = configManager.getRSProfileKey();
        boolean found = false;
        int kills = 0;
        long gp = 0;
        Map<Integer, Integer> quantityByItem = new LinkedHashMap<>();

        for (String key : configManager.getRSProfileConfigurationKeys(GROUP, profile, KEY_PREFIX)) {
            String[] typeAndName = typeAndName(key);
            if (typeAndName == null) {
                continue;
            }
            String candidateName = typeAndName[1];
            boolean matches = players
                ? typeAndName[0].equalsIgnoreCase(PLAYER_TYPE)
                : parts.stream().anyMatch(candidateName::equalsIgnoreCase);
            if (!matches) {
                continue;
            }

            if (!found && !players && !combined) {
                name = candidateName;
            }
            found = true;

            LootConfigData data = gson.fromJson(configManager.getConfiguration(GROUP, profile, key), LootConfigData.class);
            kills += data.kills;
            if (data.drops != null) {
                for (int i = 0; i < data.drops.length - 1; i += 2) {
                    int itemId = data.drops[i];
                    int quantity = data.drops[i + 1];
                    gp += (long) itemManager.getItemPrice(itemId) * quantity;
                    quantityByItem.merge(itemId, quantity, Integer::sum);
                }
            }
        }

        // Loot Tracker hasn't written these kills to config yet -- see RecentLootTracker (NPCs only).
        Set<String> pendingChecked = new HashSet<>();
        for (String part : players ? List.<String>of() : parts) {
            PendingNpcLoot recent = pendingChecked.add(part.toLowerCase(Locale.ENGLISH)) ? recentLootTracker.get(part) : null;
            if (recent == null) {
                continue;
            }

            if (!found && !combined) {
                name = recent.displayName;
            }
            found = true;
            kills += recent.kills;
            gp += recent.gpValue;
            for (Map.Entry<Integer, Integer> entry : recent.itemQuantities.entrySet()) {
                quantityByItem.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }

        return new Record(name, found, kills, gp, quantityByItem);
    }

    // Every monster Loot Tracker has loot for, most recently looted first.
    public List<String> monsters() {
        String profile = configManager.getRSProfileKey();
        List<Map.Entry<String, Instant>> byLast = new ArrayList<>();
        for (String key : configManager.getRSProfileConfigurationKeys(GROUP, profile, KEY_PREFIX)) {
            String[] typeAndName = typeAndName(key);
            Instant last = typeAndName == null ? null
                : gson.fromJson(configManager.getConfiguration(GROUP, profile, key), LootConfigData.class).last;
            if (last != null) {
                byLast.add(new SimpleEntry<>(typeAndName[1], last));
            }
        }
        byLast.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        return byLast.stream().map(Map.Entry::getKey).collect(Collectors.toList());
    }

    // Whether Loot Tracker has any loot from players (see PLAYER_NAMES).
    public boolean hasPlayerLoot() {
        return !configManager.getRSProfileConfigurationKeys(GROUP, configManager.getRSProfileKey(), KEY_PREFIX + PLAYER_TYPE + "_").isEmpty();
    }

    // "drops_<TYPE>_<name>" -> {TYPE, name}; TYPE (NPC/EVENT/PLAYER/...) has no underscore of its own, so the first one
    // after the prefix is where the name starts. Null if there's none.
    @Nullable
    private static String[] typeAndName(String key) {
        String rest = key.substring(KEY_PREFIX.length());
        int typeEnd = rest.indexOf('_');
        return typeEnd < 0 ? null : new String[]{rest.substring(0, typeEnd), rest.substring(typeEnd + 1)};
    }

    // Field names match Loot Tracker's own (package-private) ConfigLoot for Gson.
    private static final class LootConfigData {
        int kills;
        int[] drops;
        Instant last;
    }
}
