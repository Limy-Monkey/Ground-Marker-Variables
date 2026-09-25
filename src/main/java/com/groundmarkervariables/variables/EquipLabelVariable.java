package com.groundmarkervariables.variables;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.game.ItemManager;

// {equip_<slot>} -> the item name equipped in <slot> (helm, cape, amulet, body, shield, legs,
// gloves, boots, ring, ammo), or "Empty" if nothing's there. {equip_quiver} is special: Dizana's
// Quiver isn't a real equipment slot, so its live ammo type comes from a varp instead.
class EquipLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{equip_(\\w+)\\}", Pattern.CASE_INSENSITIVE);
	private static final String EMPTY = "Empty";
	private static final String QUIVER = "quiver";

	private static final Map<String, EquipmentInventorySlot> SLOTS = Map.ofEntries(
		Map.entry("helm", EquipmentInventorySlot.HEAD),
		Map.entry("cape", EquipmentInventorySlot.CAPE),
		Map.entry("amulet", EquipmentInventorySlot.AMULET),
		Map.entry("body", EquipmentInventorySlot.BODY),
		Map.entry("shield", EquipmentInventorySlot.SHIELD),
		Map.entry("legs", EquipmentInventorySlot.LEGS),
		Map.entry("gloves", EquipmentInventorySlot.GLOVES),
		Map.entry("boots", EquipmentInventorySlot.BOOTS),
		Map.entry("ring", EquipmentInventorySlot.RING),
		Map.entry("ammo", EquipmentInventorySlot.AMMO));

	private final Client client;
	private final ItemManager itemManager;

	@Inject
	private EquipLabelVariable(Client client, ItemManager itemManager)
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

		String slot = matcher.group(1).toLowerCase(Locale.ENGLISH);
		if (QUIVER.equals(slot))
		{
			return resolveQuiver();
		}

		EquipmentInventorySlot equipmentSlot = SLOTS.get(slot);
		if (equipmentSlot == null)
		{
			return null;
		}

		ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
		if (equipment == null)
		{
			return EMPTY;
		}

		Item item = equipment.getItem(equipmentSlot.getSlotIdx());
		if (item == null || item.getId() <= 0)
		{
			return EMPTY;
		}

		return itemManager.getItemComposition(item.getId()).getName();
	}

	// Dizana's Quiver isn't in the equipment ItemContainer — its live ammo type is tracked
	// as a varp instead (there's no equivalent equipment slot).
	private String resolveQuiver()
	{
		int ammoId = client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO);
		return ammoId <= 0 ? EMPTY : itemManager.getItemComposition(ammoId).getName();
	}
}
