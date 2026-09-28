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
// [^{}?:<>=!]+? deliberately excludes ?:<>=! from <name> — otherwise, as a base variable,
// this pattern runs before ConditionalVariable/BooleanVariable and would swallow a trailing
// "? A : B" or "> 5" as part of the item name. No real item name uses those characters.
class HasItemLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{hasItem\\s+([^{}?:<>=!]+?)\\s*\\}", Pattern.CASE_INSENSITIVE);

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
	private final RichText richText;

	@Inject
	private HasItemLabelVariable(Client client, ItemManager itemManager, RichText richText)
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
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		return String.valueOf(findItemName(matcher.group(1).trim().toLowerCase(Locale.ROOT)) != null);
	}

	// Colors the found item's own name, not the search term -- falls back to the search term
	// if nothing matched.
	@Override
	public String resolveRich(Matcher matcher)
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		String typed = matcher.group(1).trim();
		String foundName = findItemName(typed.toLowerCase(Locale.ROOT));
		return richText.booleanColored(foundName != null, foundName != null ? foundName : typed);
	}

	private String findItemName(String search)
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		String name = findInContainer(inventory, search);
		if (name != null)
		{
			return name;
		}

		name = findInContainer(client.getItemContainer(InventoryID.EQUIPMENT), search);
		if (name != null)
		{
			return name;
		}

		return inventoryHasRunePouch(inventory) ? findInRunePouch(search) : null;
	}

	private String findInContainer(ItemContainer container, String search)
	{
		if (container == null)
		{
			return null;
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
				return name;
			}
		}

		return null;
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

	private String findInRunePouch(String search)
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

			String name = itemManager.getItemComposition(runepouchEnum.getIntValue(runeType)).getName();
			if (name.toLowerCase(Locale.ROOT).contains(search))
			{
				return name;
			}
		}

		return null;
	}
}
