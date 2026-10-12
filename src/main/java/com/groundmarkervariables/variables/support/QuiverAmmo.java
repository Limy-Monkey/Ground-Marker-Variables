package com.groundmarkervariables.variables.support;

import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ParamID;
import net.runelite.client.game.ItemManager;

// Dizana's Quiver is a cape param, not a real equipment slot -- its varp doesn't reset just
// because the cape comes off, so callers must check this first (see core's own AmmoPlugin).
public final class QuiverAmmo
{
	private QuiverAmmo()
	{
	}

	public static boolean isAvailable(Client client, ItemManager itemManager)
	{
		ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
		if (equipment != null)
		{
			Item cape = equipment.getItem(EquipmentInventorySlot.CAPE.getSlotIdx());
			if (cape != null && isQuiverCape(itemManager, cape.getId()))
			{
				return true;
			}
		}

		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		if (inventory != null)
		{
			for (Item item : inventory.getItems())
			{
				if (item != null && item.getId() > 0 && isQuiverCape(itemManager, item.getId()))
				{
					return true;
				}
			}
		}

		return false;
	}

	private static boolean isQuiverCape(ItemManager itemManager, int itemId)
	{
		return itemManager.getItemComposition(itemId).getIntValue(ParamID.QUIVER_AMMO_AVAILABLE) == 1;
	}
}
