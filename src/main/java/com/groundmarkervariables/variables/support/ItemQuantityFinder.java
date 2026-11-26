package com.groundmarkervariables.variables.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.party.PartyMember;

// Matching items (id, name, quantity) for {hasItem}/{numItem}/{numDoses}/{showItems}, in inventory, equipment,
// quiver, then rune pouch order. Each holder's items, and its matches by search, are cached until clearCache().
public final class ItemQuantityFinder {
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Match {
        public final int id;
        public final String name;
        public final int quantity;
    }

    // A holder's items with their lowercase names, and its matches by search.
    private static final class ItemSearchCache {
        private final List<Match> items = new ArrayList<>();
        private final List<String> lowerNames = new ArrayList<>();
        private final Map<String, List<Match>> searches = new HashMap<>();

        private void add(int id, int quantity, ItemManager itemManager) {
            String name = itemManager.getItemComposition(id).getName();
            items.add(new Match(id, name, quantity));
            lowerNames.add(name.toLowerCase(Locale.ROOT));
        }

        private List<Match> find(String search) {
            return searches.computeIfAbsent(search, s -> {
                List<Match> matches = new ArrayList<>();
                for (int i = 0; i < items.size(); i++) {
                    if (lowerNames.get(i).contains(s)) {
                        matches.add(items.get(i));
                    }
                }
                return Collections.unmodifiableList(matches);
            });
        }
    }

    private static final long LOCAL = Long.MIN_VALUE;
    // By member id, or LOCAL.
    private static final Map<Long, ItemSearchCache> CACHES = new HashMap<>();

    private static final int[] RUNE_POUCH_ITEM_IDS = {
        ItemID.BH_RUNE_POUCH, ItemID.BH_RUNE_POUCH_TROUVER, ItemID.DIVINE_RUNE_POUCH, ItemID.DIVINE_RUNE_POUCH_TROUVER
    };
    private static final int[] RUNE_POUCH_AMOUNT_VARBITS = {
        VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2, VarbitID.RUNE_POUCH_QUANTITY_3,
        VarbitID.RUNE_POUCH_QUANTITY_4, VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6
    };
    private static final int[] RUNE_POUCH_TYPE_VARBITS = {
        VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3,
        VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6
    };

    private ItemQuantityFinder() {
    }

    // Once per tick, so held items are read again.
    public static void clearCache() {
        CACHES.clear();
    }

    public static List<Match> findAll(Client client, ItemManager itemManager, String search) {
        return cache(LOCAL, () -> local(client, itemManager)).find(search);
    }

    // The same search over a party member's PartyDataService data.
    public static List<Match> findAll(PartyMember member, ItemManager itemManager, String search) {
        return cache(member.getMemberId(), () -> member(member, itemManager)).find(search);
    }

    private static ItemSearchCache cache(long holder, Supplier<ItemSearchCache> build) {
        ItemSearchCache cache = CACHES.get(holder);
        if (cache == null) {
            cache = build.get();
            CACHES.put(holder, cache);
        }
        return cache;
    }

    private static ItemSearchCache local(Client client, ItemManager itemManager) {
        ItemSearchCache cache = new ItemSearchCache();
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        addItems(cache, inventory == null ? null : inventory.getItems(), itemManager);
        ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
        addItems(cache, equipment == null ? null : equipment.getItems(), itemManager);

        if (QuiverAmmo.isAvailable(client, itemManager)) {
            int ammoId = client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO);
            int amount = client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT);
            if (ammoId > 0 && amount > 0) {
                cache.add(ammoId, amount, itemManager);
            }
        }

        addItems(cache, runePouch(client, inventory == null ? null : inventory.getItems()), itemManager);
        return cache;
    }

    private static ItemSearchCache member(PartyMember member, ItemManager itemManager) {
        ItemSearchCache cache = new ItemSearchCache();
        addItems(cache, member.getInv(), itemManager);
        addItems(cache, member.getWorn(), itemManager);
        addItems(cache, member.getQuiver() == null ? null : new Item[] {member.getQuiver()}, itemManager);
        addItems(cache, member.getRunepouch(), itemManager);
        return cache;
    }

    private static void addItems(ItemSearchCache cache, Item[] items, ItemManager itemManager) {
        if (items == null) {
            return;
        }

        for (Item item : items) {
            // Empty slots are Items with id <= 0 (typically -1), not null array elements.
            if (item != null && item.getId() > 0 && item.getQuantity() > 0) {
                cache.add(item.getId(), item.getQuantity(), itemManager);
            }
        }
    }

    // The rune pouch's runes as items, if inventory holds one (any kind); else none. Also RuneCounter's.
    public static Item[] runePouch(Client client, @Nullable Item[] inventory) {
        List<Item> runes = new ArrayList<>();
        if (hasRunePouch(inventory)) {
            EnumComposition runepouchEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
            for (int i = 0; i < RUNE_POUCH_AMOUNT_VARBITS.length; i++) {
                int amount = client.getVarbitValue(RUNE_POUCH_AMOUNT_VARBITS[i]);
                int runeType = client.getVarbitValue(RUNE_POUCH_TYPE_VARBITS[i]);
                if (amount > 0 && runeType != 0) {
                    runes.add(new Item(runepouchEnum.getIntValue(runeType), amount));
                }
            }
        }
        return runes.toArray(new Item[0]);
    }

    public static boolean hasRunePouch(@Nullable Item[] inventory) {
        for (int runePouchId : RUNE_POUCH_ITEM_IDS) {
            if (itemCount(inventory, runePouchId) > 0) {
                return true;
            }
        }
        return false;
    }

    // The total quantity of itemId in items.
    public static int itemCount(@Nullable Item[] items, int itemId) {
        int total = 0;
        if (items != null) {
            for (Item item : items) {
                if (item != null && item.getId() == itemId) {
                    total += item.getQuantity();
                }
            }
        }
        return total;
    }
}
