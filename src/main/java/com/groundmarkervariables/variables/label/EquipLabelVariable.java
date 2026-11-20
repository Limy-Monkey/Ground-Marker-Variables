package com.groundmarkervariables.variables.label;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.PartyMemberVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.Icons;
import com.groundmarkervariables.variables.support.QuiverAmmo;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.party.PartyMember;

// {equip_<slot>} -> the item name equipped in <slot> (helm, cape, amulet, body, shield, legs,
// gloves, boots, ring, ammo), or "Empty" if nothing's there. {equip_quiver} is special: Dizana's
// Quiver isn't a real equipment slot, so its live ammo type comes from a varp instead.
// {equip_<slot> icon} is the item's icon, or nothing if empty.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class EquipLabelVariable implements LabelVariable, PartyMemberVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{equip_(\\w+?)(\\s+icon)?\\}", Pattern.CASE_INSENSITIVE);
	private static final String EMPTY = "Empty";
	private static final String QUIVER = "quiver";
	// An unknown slot.
	private static final int UNKNOWN = Integer.MIN_VALUE;

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

	private static final Map<String, String> SLOT_LABELS = Map.ofEntries(
		Map.entry("helm", "Helm"), Map.entry("cape", "Cape"), Map.entry("amulet", "Amulet"),
		Map.entry("body", "Body"), Map.entry("shield", "Shield"), Map.entry("legs", "Legs"),
		Map.entry("gloves", "Gloves"), Map.entry("boots", "Boots"), Map.entry("ring", "Ring"),
		Map.entry("ammo", "Ammo"), Map.entry("quiver", "Quiver"));

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
		return client.getLocalPlayer() == null ? null : name(localItemId(slot(matcher)));
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		String slot = slot(matcher);
		int itemId = localItemId(slot);
		if (matcher.group(2) != null)
		{
			return icon(itemId);
		}

		String itemName = name(itemId);
		return itemName == null ? null : richText.labeled(SLOT_LABELS.getOrDefault(slot, slot), itemName);
	}

	@Override
	public String resolvePlain(Matcher matcher, PartyMember member)
	{
		return name(memberItemId(slot(matcher), member));
	}

	@Override
	public String resolveRich(Matcher matcher, PartyMember member)
	{
		int itemId = memberItemId(slot(matcher), member);
		if (matcher.group(2) != null)
		{
			return icon(itemId);
		}

		String itemName = name(itemId);
		return itemName == null ? null : richText.highlightValue(itemName);
	}

	@Override
	public String resolveRowRich(Matcher matcher, RichText richText)
	{
		return matcher.group(2) != null ? resolveRich(matcher) : PartyMemberVariable.super.resolveRowRich(matcher, richText);
	}

	private static String slot(Matcher matcher)
	{
		return matcher.group(1).toLowerCase(Locale.ENGLISH);
	}

	// Null for an unknown slot.
	private String name(int itemId)
	{
		if (itemId == UNKNOWN)
		{
			return null;
		}
		return itemId <= 0 ? EMPTY : itemManager.getItemComposition(itemId).getName();
	}

	private static String icon(int itemId)
	{
		if (itemId == UNKNOWN)
		{
			return null;
		}
		return itemId <= 0 ? "" : Icons.item(itemId);
	}

	// <= 0 when empty, UNKNOWN for an unknown slot.
	private int localItemId(String slot)
	{
		if (QUIVER.equals(slot))
		{
			return QuiverAmmo.isAvailable(client, itemManager) ? client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO) : -1;
		}

		EquipmentInventorySlot equipmentSlot = SLOTS.get(slot);
		if (equipmentSlot == null)
		{
			return UNKNOWN;
		}

		ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
		Item item = equipment == null ? null : equipment.getItem(equipmentSlot.getSlotIdx());
		return item == null ? -1 : item.getId();
	}

	private static int memberItemId(String slot, PartyMember member)
	{
		if (QUIVER.equals(slot))
		{
			return member.getQuiver() == null ? -1 : member.getQuiver().getId();
		}

		EquipmentInventorySlot equipmentSlot = SLOTS.get(slot);
		if (equipmentSlot == null)
		{
			return UNKNOWN;
		}

		Item[] worn = member.getWorn();
		Item item = worn == null || equipmentSlot.getSlotIdx() >= worn.length ? null : worn[equipmentSlot.getSlotIdx()];
		return item == null ? -1 : item.getId();
	}
}
