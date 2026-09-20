package com.groundmarkervariables.variables;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;

// {hasItem <name>} -> "true" if the player currently holds an item whose ItemComposition name
// contains <name> (case-insensitive substring, not exact match — e.g. {hasItem rune} matches
// "Rune pouch", "Air rune", "Runite ore", ...) in their inventory or equipment, "false"
// otherwise. <name> is everything after "hasItem " up to the closing brace.
//
// Also checks a rune pouch's contents (any variant: regular/looted, divine/looted), but only
// when the pouch itself is actually in the inventory — same rune/quantity varbits and
// EnumID.RUNEPOUCH_RUNE lookup core's own RunepouchOverlay uses.
//
// [^{}?:]+? (not [^{}]+?) deliberately excludes '?' and ':' from <name>, the same fail-safe
// philosophy ConditionalVariable uses excluding '{'/'}' from its own groups: this variable is
// itself a registered base variable, so LabelResolver's own pass over it runs before
// ConditionalVariable's does. Without the exclusion, {hasItem staff of the dead ? A : B} used
// as a bare conditional <expr> would match THIS pattern directly first and swallow "? A : B"
// as part of the item name, resolving to false before the conditional ever got a turn. No
// real item name contains '?' or ':', so this costs nothing in practice.
class HasItemLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{hasItem\\s+([^{}?:]+?)\\s*\\}", Pattern.CASE_INSENSITIVE);

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

	private final Client client;
	private final ItemManager itemManager;

	@Inject
	private HasItemLabelVariable(Client client, ItemManager itemManager)
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

		String search = matcher.group(1).toLowerCase(Locale.ROOT);
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		boolean hasItem = containerHasItemContaining(inventory, search)
			|| containerHasItemContaining(client.getItemContainer(InventoryID.EQUIPMENT), search)
			|| (inventoryHasRunePouch(inventory) && runePouchContains(search));
		return String.valueOf(hasItem);
	}

	private boolean containerHasItemContaining(ItemContainer container, String search)
	{
		if (container == null)
		{
			return false;
		}

		for (Item item : container.getItems())
		{
			// Empty slots are Items with id <= 0 (typically -1), not null array elements.
			if (item == null || item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}

			String name = itemManager.getItemComposition(item.getId()).getName().toLowerCase(Locale.ROOT);
			if (name.contains(search))
			{
				return true;
			}
		}

		return false;
	}

	private boolean inventoryHasRunePouch(ItemContainer inventory)
	{
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

	private boolean runePouchContains(String search)
	{
		EnumComposition runepouchEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		for (int i = 0; i < RUNE_POUCH_AMOUNT_VARBITS.length; i++)
		{
			int amount = client.getVarbitValue(RUNE_POUCH_AMOUNT_VARBITS[i]);
			int runeType = client.getVarbitValue(RUNE_POUCH_TYPE_VARBITS[i]);
			if (amount <= 0 || runeType == 0)
			{
				continue;
			}

			String name = itemManager.getItemComposition(runepouchEnum.getIntValue(runeType)).getName().toLowerCase(Locale.ROOT);
			if (name.contains(search))
			{
				return true;
			}
		}

		return false;
	}
}
