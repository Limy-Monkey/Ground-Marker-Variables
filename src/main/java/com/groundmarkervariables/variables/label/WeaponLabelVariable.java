package com.groundmarkervariables.variables.label;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.InventoryID;
import net.runelite.client.game.ItemManager;

// {weapon} -> the equipped weapon's item name (e.g. "Abyssal whip"), or "Unarmed" if the
// weapon slot is empty.
public class WeaponLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{weapon\\}", Pattern.CASE_INSENSITIVE);
	private static final String UNARMED = "Unarmed";

	private final Client client;
	private final ItemManager itemManager;
	private final RichText richText;

	@Inject
	private WeaponLabelVariable(Client client, ItemManager itemManager, RichText richText)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.richText = richText;
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		return client.getLocalPlayer() == null ? null : resolveWeaponName();
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		String name = resolvePlain(matcher);
		return name == null ? null : richText.labeled("Weapon", name);
	}

	private String resolveWeaponName()
	{
		ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
		if (equipment == null)
		{
			return UNARMED;
		}

		Item weapon = equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		if (weapon == null || weapon.getId() <= 0)
		{
			return UNARMED;
		}

		return itemManager.getItemComposition(weapon.getId()).getName();
	}
}
