package com.groundmarkervariables.variables;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.InventoryID;
import net.runelite.client.game.ItemManager;

// {weapon} -> the equipped weapon's item name (e.g. "Abyssal whip"), or "Unarmed" if the
// weapon slot is empty. Unresolvable (not "Unarmed") while not logged in, same convention
// every other variable here uses for "don't know yet" vs. a real answer.
class WeaponLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{weapon\\}", Pattern.CASE_INSENSITIVE);
	private static final String UNARMED = "Unarmed";

	private final Client client;
	private final ItemManager itemManager;

	@Inject
	private WeaponLabelVariable(Client client, ItemManager itemManager)
	{
		this.client = client;
		this.itemManager = itemManager;
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolve(Matcher matcher)
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
		if (equipment == null)
		{
			return UNARMED;
		}

		Item[] items = equipment.getItems();
		int weaponSlot = EquipmentInventorySlot.WEAPON.getSlotIdx();
		if (weaponSlot >= items.length || items[weaponSlot] == null)
		{
			return UNARMED;
		}

		return itemManager.getItemComposition(items[weaponSlot].getId()).getName();
	}
}
