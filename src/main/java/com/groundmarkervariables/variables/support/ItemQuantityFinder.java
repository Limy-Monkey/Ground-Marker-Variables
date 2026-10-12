package com.groundmarkervariables.variables.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

// Matching items (name + quantity) for {numItem}/{numDoses} -- same scope as {hasItem}.
public final class ItemQuantityFinder
{
	public static final class Match
	{
		public final String name;
		public final int quantity;

		private Match(String name, int quantity)
		{
			this.name = name;
			this.quantity = quantity;
		}
	}

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

	private ItemQuantityFinder()
	{
	}

	public static List<Match> findAll(Client client, ItemManager itemManager, String search)
	{
		List<Match> matches = new ArrayList<>();
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		addFromContainer(matches, inventory, itemManager, search);
		addFromContainer(matches, client.getItemContainer(InventoryID.EQUIPMENT), itemManager, search);
		addFromQuiver(matches, client, itemManager, search);
		addFromRunePouch(matches, client, itemManager, inventory, search);
		return matches;
	}

	private static void addFromContainer(List<Match> matches, ItemContainer container, ItemManager itemManager, String search)
	{
		if (container == null)
		{
			return;
		}

		for (Item item : container.getItems())
		{
			// Empty slots are Items with id <= 0 (typically -1), not null array elements.
			if (item == null || item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}

			String name = itemManager.getItemComposition(item.getId()).getName();
			if (name.toLowerCase(Locale.ROOT).contains(search))
			{
				matches.add(new Match(name, item.getQuantity()));
			}
		}
	}

	private static void addFromQuiver(List<Match> matches, Client client, ItemManager itemManager, String search)
	{
		if (!QuiverAmmo.isAvailable(client, itemManager))
		{
			return;
		}

		int ammoId = client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO);
		int amount = client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT);
		if (ammoId <= 0 || amount <= 0)
		{
			return;
		}

		String name = itemManager.getItemComposition(ammoId).getName();
		if (name.toLowerCase(Locale.ROOT).contains(search))
		{
			matches.add(new Match(name, amount));
		}
	}

	private static void addFromRunePouch(List<Match> matches, Client client, ItemManager itemManager, ItemContainer inventory, String search)
	{
		if (inventory == null || !hasRunePouch(inventory))
		{
			return;
		}

		EnumComposition runepouchEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		for (int i = 0; i < RUNE_POUCH_AMOUNT_VARBITS.length; i++)
		{
			int amount = client.getVarbitValue(RUNE_POUCH_AMOUNT_VARBITS[i]);
			int runeType = client.getVarbitValue(RUNE_POUCH_TYPE_VARBITS[i]);
			if (amount <= 0 || runeType == 0)
			{
				continue;
			}

			String name = itemManager.getItemComposition(runepouchEnum.getIntValue(runeType)).getName();
			if (name.toLowerCase(Locale.ROOT).contains(search))
			{
				matches.add(new Match(name, amount));
			}
		}
	}

	private static boolean hasRunePouch(ItemContainer inventory)
	{
		for (Item item : inventory.getItems())
		{
			if (item == null)
			{
				continue;
			}

			for (int runePouchId : RUNE_POUCH_ITEM_IDS)
			{
				if (item.getId() == runePouchId)
				{
					return true;
				}
			}
		}

		return false;
	}
}
