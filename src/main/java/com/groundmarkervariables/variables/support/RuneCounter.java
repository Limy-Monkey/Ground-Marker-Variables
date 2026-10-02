package com.groundmarkervariables.variables.support;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

// Shared "how many of X does the player actually have available" logic for any variable
// that needs to reason about spell reagents (HasThrallsLabelVariable, HasFreezeLabelVariable,
// HasEntangleLabelVariable, and future ones). Rune pouch decoding mirrors gearscape's own
// RunePouchItem/buildRunePouch.
public final class RuneCounter
{
	private RuneCounter()
	{
	}

	// Regular/Trouver x normal/Divine — mirrors gearscape's RelevantItems.RUNE_POUCH_VARIANTS.
	private static final int[] RUNE_POUCH_VARIANTS = {
		ItemID.BH_RUNE_POUCH, ItemID.BH_RUNE_POUCH_TROUVER, ItemID.DIVINE_RUNE_POUCH, ItemID.DIVINE_RUNE_POUCH_TROUVER,
	};

	private static final int[] RUNE_POUCH_QUANTITY_VARBITS = {
		VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2, VarbitID.RUNE_POUCH_QUANTITY_3,
		VarbitID.RUNE_POUCH_QUANTITY_4, VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6,
	};
	private static final int[] RUNE_POUCH_TYPE_VARBITS = {
		VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3,
		VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6,
	};

	private static final Map<Integer, int[]> INFINITE_RUNE_WEAPONS = Map.of(
		ItemID.FIRERUNE, new int[] {
			ItemID.STAFF_OF_FIRE, ItemID.FIRE_BATTLESTAFF, ItemID.MYSTIC_FIRE_STAFF,
			ItemID.LAVA_BATTLESTAFF, ItemID.MYSTIC_LAVA_STAFF, ItemID.LAVA_BATTLESTAFF_PRETTY, ItemID.MYSTIC_LAVA_STAFF_PRETTY,
			ItemID.STEAM_BATTLESTAFF, ItemID.MYSTIC_STEAM_BATTLESTAFF,
			ItemID.STEAM_BATTLESTAFF_PRETTY, ItemID.MYSTIC_STEAM_BATTLESTAFF_PRETTY,
			ItemID.SMOKE_BATTLESTAFF, ItemID.MYSTIC_SMOKE_BATTLESTAFF,
		},
		ItemID.WATERRUNE, new int[] {
			ItemID.STAFF_OF_WATER, ItemID.WATER_BATTLESTAFF, ItemID.MYSTIC_WATER_STAFF,
			ItemID.MUD_BATTLESTAFF, ItemID.MYSTIC_MUD_STAFF,
			ItemID.STEAM_BATTLESTAFF, ItemID.MYSTIC_STEAM_BATTLESTAFF,
			ItemID.STEAM_BATTLESTAFF_PRETTY, ItemID.MYSTIC_STEAM_BATTLESTAFF_PRETTY,
		},
		ItemID.EARTHRUNE, new int[] {
			ItemID.STAFF_OF_EARTH, ItemID.EARTH_BATTLESTAFF, ItemID.MYSTIC_EARTH_STAFF,
			ItemID.LAVA_BATTLESTAFF, ItemID.MYSTIC_LAVA_STAFF, ItemID.LAVA_BATTLESTAFF_PRETTY, ItemID.MYSTIC_LAVA_STAFF_PRETTY,
			ItemID.MUD_BATTLESTAFF, ItemID.MYSTIC_MUD_STAFF,
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

	private static Map<Integer, int[]> buildSubstitutesByRune()
	{
		Map<Integer, List<Integer>> byRune = new HashMap<>();
		COMBO_RUNE_COMPONENTS.forEach((combo, components) ->
		{
			for (int component : components)
			{
				byRune.computeIfAbsent(component, k -> new ArrayList<>()).add(combo);
			}
		});

		Map<Integer, int[]> result = new HashMap<>();
		byRune.forEach((runeId, substitutes) -> result.put(runeId, substitutes.stream().mapToInt(Integer::intValue).toArray()));
		return result;
	}

	public static boolean hasAtLeast(Client client, int runeId, int required)
	{
		return hasInfiniteSource(client, runeId) || count(client, runeId) >= required || hasSubstitute(client, runeId);
	}

	// Any quantity of a combo rune covering runeId counts as satisfying it.
	private static boolean hasSubstitute(Client client, int runeId)
	{
		int[] substitutes = SUBSTITUTES_BY_RUNE.get(runeId);
		if (substitutes == null)
		{
			return false;
		}

		for (int substitute : substitutes)
		{
			if (count(client, substitute) > 0)
			{
				return true;
			}
		}

		return false;
	}

	static boolean hasInfiniteSource(Client client, int runeId)
	{
		int[] weapons = INFINITE_RUNE_WEAPONS.get(runeId);
		if (weapons == null)
		{
			return false;
		}

		int weaponId = equippedWeaponId(client);
		for (int candidate : weapons)
		{
			if (weaponId == candidate)
			{
				return true;
			}
		}

		return false;
	}

	// Total quantity of a rune available: inventory + rune pouch contents
	static int count(Client client, int runeId)
	{
		return itemCount(client, InventoryID.INVENTORY, runeId) + runePouchCount(client, runeId);
	}

	public static int itemCount(Client client, InventoryID inventoryId, int itemId)
	{
		ItemContainer container = client.getItemContainer(inventoryId);
		if (container == null)
		{
			return 0;
		}

		int total = 0;
		for (Item item : container.getItems())
		{
			if (item != null && item.getId() == itemId)
			{
				total += item.getQuantity();
			}
		}

		return total;
	}

	// The equipped weapon's item id, or -1 if there's no equipment or nothing in that slot
	static int equippedWeaponId(Client client)
	{
		ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
		if (equipment == null)
		{
			return -1;
		}

		Item weapon = equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		return weapon == null || weapon.getId() <= 0 ? -1 : weapon.getId();
	}

	private static int runePouchCount(Client client, int runeId)
	{
		if (!containsRunePouch(client))
		{
			return 0;
		}

		EnumComposition runepouchEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		int total = 0;
		for (int i = 0; i < RUNE_POUCH_TYPE_VARBITS.length; i++)
		{
			int quantity = client.getVarbitValue(RUNE_POUCH_QUANTITY_VARBITS[i]);
			int runeIndex = client.getVarbitValue(RUNE_POUCH_TYPE_VARBITS[i]);
			if (runeIndex == 0 || quantity <= 0)
			{
				continue;
			}

			if (runepouchEnum.getIntValue(runeIndex) == runeId)
			{
				total += quantity;
			}
		}

		return total;
	}

	private static boolean containsRunePouch(Client client)
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		if (inventory == null)
		{
			return false;
		}

		for (Item item : inventory.getItems())
		{
			if (item == null)
			{
				continue;
			}

			for (int variant : RUNE_POUCH_VARIANTS)
			{
				if (item.getId() == variant)
				{
					return true;
				}
			}
		}

		return false;
	}
}
