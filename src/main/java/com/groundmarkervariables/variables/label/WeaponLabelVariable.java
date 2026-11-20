package com.groundmarkervariables.variables.label;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.PartyMemberVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.Icons;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.InventoryID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.party.PartyMember;

// {weapon} -> the equipped weapon's item name (e.g. "Abyssal whip"), or "Unarmed" if the
// weapon slot is empty. {weapon icon} is its item icon, or nothing if unarmed.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class WeaponLabelVariable implements LabelVariable, PartyMemberVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{weapon(\\s+icon)?\\}", Pattern.CASE_INSENSITIVE);
	private static final String UNARMED = "Unarmed";

	private final Client client;
	private final ItemManager itemManager;
	private final RichText richText;

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		return client.getLocalPlayer() == null ? null : name(localWeaponId());
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		int weaponId = localWeaponId();
		return matcher.group(1) != null ? icon(weaponId) : richText.labeled("Weapon", name(weaponId));
	}

	@Override
	public String resolvePlain(Matcher matcher, PartyMember member)
	{
		return name(memberWeaponId(member));
	}

	@Override
	public String resolveRich(Matcher matcher, PartyMember member)
	{
		int weaponId = memberWeaponId(member);
		return matcher.group(1) != null ? icon(weaponId) : richText.highlightValue(name(weaponId));
	}

	@Override
	public String resolveRowRich(Matcher matcher, RichText richText)
	{
		return matcher.group(1) != null ? resolveRich(matcher) : PartyMemberVariable.super.resolveRowRich(matcher, richText);
	}

	private String name(int itemId)
	{
		return itemId <= 0 ? UNARMED : itemManager.getItemComposition(itemId).getName();
	}

	private static String icon(int itemId)
	{
		return itemId <= 0 ? "" : Icons.item(itemId);
	}

	// <= 0 when unarmed.
	private int localWeaponId()
	{
		ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
		Item weapon = equipment == null ? null : equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		return weapon == null ? -1 : weapon.getId();
	}

	private static int memberWeaponId(PartyMember member)
	{
		Item[] worn = member.getWorn();
		int slot = EquipmentInventorySlot.WEAPON.getSlotIdx();
		Item weapon = worn == null || slot >= worn.length ? null : worn[slot];
		return weapon == null ? -1 : weapon.getId();
	}
}
