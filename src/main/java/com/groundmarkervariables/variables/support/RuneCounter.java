package com.groundmarkervariables.variables.support;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.party.PartyMember;

// Shared "how many of X does the player actually have available" logic for any variable
// that needs to reason about spell reagents (SpellVariables). The rune pouch is ItemQuantityFinder's.
public final class RuneCounter {
	private RuneCounter() {
	}

	private static final long LOCAL = Long.MIN_VALUE;
	// hasRunes results until clearCache(), by member id (or LOCAL) then rune set.
	private static final Map<Long, Map<Map<Integer, Integer>, Boolean>> HAS_RUNES = new HashMap<>();

	private static final Map<Integer, int[]> INFINITE_RUNE_WEAPONS = Map.of(
		ItemID.FIRERUNE, new int[] {
			ItemID.STAFF_OF_FIRE, ItemID.FIRE_BATTLESTAFF, ItemID.MYSTIC_FIRE_STAFF,
			ItemID.LAVA_BATTLESTAFF, ItemID.MYSTIC_LAVA_STAFF, ItemID.LAVA_BATTLESTAFF_PRETTY, ItemID.MYSTIC_LAVA_STAFF_PRETTY,
			ItemID.STEAM_BATTLESTAFF, ItemID.MYSTIC_STEAM_BATTLESTAFF,
			ItemID.STEAM_BATTLESTAFF_PRETTY, ItemID.MYSTIC_STEAM_BATTLESTAFF_PRETTY,
			ItemID.SMOKE_BATTLESTAFF, ItemID.MYSTIC_SMOKE_BATTLESTAFF,
			ItemID.SHADOWFLAME_QUADRANT, ItemID.TWINFLAME_STAFF,
		},
		ItemID.WATERRUNE, new int[] {
			ItemID.STAFF_OF_WATER, ItemID.WATER_BATTLESTAFF, ItemID.MYSTIC_WATER_STAFF,
			ItemID.MUD_BATTLESTAFF, ItemID.MYSTIC_MUD_STAFF,
			ItemID.STEAM_BATTLESTAFF, ItemID.MYSTIC_STEAM_BATTLESTAFF,
			ItemID.STEAM_BATTLESTAFF_PRETTY, ItemID.MYSTIC_STEAM_BATTLESTAFF_PRETTY,
			ItemID.SHADOWFLAME_QUADRANT, ItemID.TWINFLAME_STAFF,
		},
		ItemID.EARTHRUNE, new int[] {
			ItemID.STAFF_OF_EARTH, ItemID.EARTH_BATTLESTAFF, ItemID.MYSTIC_EARTH_STAFF,
			ItemID.LAVA_BATTLESTAFF, ItemID.MYSTIC_LAVA_STAFF, ItemID.LAVA_BATTLESTAFF_PRETTY, ItemID.MYSTIC_LAVA_STAFF_PRETTY,
			ItemID.MUD_BATTLESTAFF, ItemID.MYSTIC_MUD_STAFF,
			ItemID.SHADOWFLAME_QUADRANT,
		},
		ItemID.AIRRUNE, new int[] {
			ItemID.STAFF_OF_AIR, ItemID.AIR_BATTLESTAFF, ItemID.MYSTIC_AIR_STAFF,
			ItemID.MIST_BATTLESTAFF, ItemID.MYSTIC_MIST_BATTLESTAFF,
			ItemID.DUST_BATTLESTAFF, ItemID.MYSTIC_DUST_BATTLESTAFF,
			ItemID.SMOKE_BATTLESTAFF, ItemID.MYSTIC_SMOKE_BATTLESTAFF,
			ItemID.SHADOWFLAME_QUADRANT,
		});

	// All 7 combination runes (oldschool.runescape.wiki/w/Combination_rune) — usable as either
	// of their two components. We only check whether one exists, not how many.
	private static final Map<Integer, int[]> COMBO_RUNE_COMPONENTS = Map.ofEntries(
		Map.entry(ItemID.MISTRUNE, new int[] { ItemID.AIRRUNE, ItemID.WATERRUNE }),
		Map.entry(ItemID.DUSTRUNE, new int[] { ItemID.AIRRUNE, ItemID.EARTHRUNE }),
		Map.entry(ItemID.MUDRUNE, new int[] { ItemID.WATERRUNE, ItemID.EARTHRUNE }),
		Map.entry(ItemID.SMOKERUNE, new int[] { ItemID.AIRRUNE, ItemID.FIRERUNE }),
		Map.entry(ItemID.STEAMRUNE, new int[] { ItemID.WATERRUNE, ItemID.FIRERUNE }),
		Map.entry(ItemID.LAVARUNE, new int[] { ItemID.EARTHRUNE, ItemID.FIRERUNE }),
		Map.entry(ItemID.AETHERRUNE, new int[] { ItemID.COSMICRUNE, ItemID.SOULRUNE }));

	// runeId -> combo runes that count as it
	private static final Map<Integer, int[]> SUBSTITUTES_BY_RUNE = buildSubstitutesByRune();

	// Worn in the shield slot: charged tomes and Devil's element.
	private static final Map<Integer, int[]> INFINITE_RUNE_OFFHANDS = Map.of(
		ItemID.FIRERUNE, new int[] { ItemID.TOME_OF_FIRE, ItemID.DEVILS_ELEMENT },
		ItemID.WATERRUNE, new int[] { ItemID.TOME_OF_WATER, ItemID.DEVILS_ELEMENT },
		ItemID.EARTHRUNE, new int[] { ItemID.TOME_OF_EARTH, ItemID.DEVILS_ELEMENT },
		ItemID.AIRRUNE, new int[] { ItemID.DEVILS_ELEMENT });

	private static final Map<Integer, Set<Integer>> WEAPON_SOURCES = bySource(INFINITE_RUNE_WEAPONS);
	private static final Map<Integer, Set<Integer>> OFFHAND_SOURCES = bySource(INFINITE_RUNE_OFFHANDS);

	private static Map<Integer, int[]> buildSubstitutesByRune() {
		Map<Integer, List<Integer>> byRune = new HashMap<>();
		COMBO_RUNE_COMPONENTS.forEach((combo, components) -> {
			for (int component : components) {
				byRune.computeIfAbsent(component, k -> new ArrayList<>()).add(combo);
			}
		});

		Map<Integer, int[]> result = new HashMap<>();
		byRune.forEach((runeId, substitutes) -> result.put(runeId, substitutes.stream().mapToInt(Integer::intValue).toArray()));
		return result;
	}

	// Once per tick, so held runes are counted again.
	public static void clearCache() {
		HAS_RUNES.clear();
	}

	// runes: rune id -> amount.
	public static boolean hasRunes(Client client, Map<Integer, Integer> runes) {
		return HAS_RUNES.computeIfAbsent(LOCAL, k -> new HashMap<>()).computeIfAbsent(runes, r -> {
			Item[] inventory = items(client, InventoryID.INVENTORY);
			return hasRunes(r, inventory, items(client, InventoryID.EQUIPMENT), counter(inventory, ItemQuantityFinder.runePouch(client, inventory)));
		});
	}

	// The same, from a party member's PartyDataService inventory, gear and rune pouch.
	public static boolean hasRunes(PartyMember member, Map<Integer, Integer> runes) {
		return HAS_RUNES.computeIfAbsent(member.getMemberId(), k -> new HashMap<>()).computeIfAbsent(runes, r -> {
			Item[] inventory = member.getInv();
			return hasRunes(r, inventory, member.getWorn(), counter(inventory, ItemQuantityFinder.hasRunePouch(inventory) ? member.getRunepouch() : null));
		});
	}

	// A rune's count: in inventory, plus in the rune pouch (null if none).
	private static ToIntFunction<Integer> counter(@Nullable Item[] inventory, @Nullable Item[] pouch) {
		return rune -> ItemQuantityFinder.itemCount(inventory, rune) + ItemQuantityFinder.itemCount(pouch, rune);
	}

	// With some choice of weapon and offhand, worn or in the inventory to equip instead. Each one
	// worn can be an infinite source.
	private static boolean hasRunes(Map<Integer, Integer> runes, @Nullable Item[] inventory, @Nullable Item[] worn, ToIntFunction<Integer> count) {
		for (int weapon : choices(worn, EquipmentInventorySlot.WEAPON, inventory, WEAPON_SOURCES)) {
			for (int offhand : choices(worn, EquipmentInventorySlot.SHIELD, inventory, OFFHAND_SOURCES)) {
				if (hasRunes(runes, count, WEAPON_SOURCES.get(weapon), OFFHAND_SOURCES.get(offhand))) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean hasRunes(Map<Integer, Integer> runes, ToIntFunction<Integer> count, @Nullable Set<Integer> weaponRunes,
		@Nullable Set<Integer> offhandRunes) {
		for (Map.Entry<Integer, Integer> rune : runes.entrySet()) {
			int runeId = rune.getKey();
			boolean infinite = weaponRunes != null && weaponRunes.contains(runeId) || offhandRunes != null && offhandRunes.contains(runeId);
			if (!infinite && count.applyAsInt(runeId) < rune.getValue() && !hasSubstitute(count, runeId)) {
				return false;
			}
		}
		return true;
	}

	// The worn item in slot (-1 if none), plus each source in the inventory.
	private static Set<Integer> choices(@Nullable Item[] worn, EquipmentInventorySlot slot, @Nullable Item[] inventory,
		Map<Integer, Set<Integer>> sources) {
		Set<Integer> choices = new LinkedHashSet<>();
		Item wornItem = worn == null || slot.getSlotIdx() >= worn.length ? null : worn[slot.getSlotIdx()];
		choices.add(wornItem == null ? -1 : wornItem.getId());
		if (inventory != null) {
			for (Item item : inventory) {
				if (item != null && sources.containsKey(item.getId())) {
					choices.add(item.getId());
				}
			}
		}
		return choices;
	}

	@Nullable
	private static Item[] items(Client client, InventoryID inventoryId) {
		ItemContainer container = client.getItemContainer(inventoryId);
		return container == null ? null : container.getItems();
	}

	// Item id -> the runes it's an infinite source of.
	private static Map<Integer, Set<Integer>> bySource(Map<Integer, int[]> sourcesByRune) {
		Map<Integer, Set<Integer>> bySource = new HashMap<>();
		sourcesByRune.forEach((rune, items) -> {
			for (int item : items) {
				bySource.computeIfAbsent(item, k -> new HashSet<>()).add(rune);
			}
		});
		return bySource;
	}

	// Any quantity of a combo rune covering runeId counts as satisfying it.
	private static boolean hasSubstitute(ToIntFunction<Integer> count, int runeId) {
		int[] substitutes = SUBSTITUTES_BY_RUNE.get(runeId);
		if (substitutes == null) {
			return false;
		}

		for (int substitute : substitutes) {
			if (count.applyAsInt(substitute) > 0) {
				return true;
			}
		}

		return false;
	}

	public static int itemCount(Client client, InventoryID inventoryId, int itemId) {
		return ItemQuantityFinder.itemCount(items(client, inventoryId), itemId);
	}
}
