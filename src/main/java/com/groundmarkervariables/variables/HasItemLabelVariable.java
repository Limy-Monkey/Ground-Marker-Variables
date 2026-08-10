package com.groundmarkervariables.variables;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.client.game.ItemManager;

// {hasItem <name>} -> "true" if the player currently holds an item whose ItemComposition name
// contains <name> (case-insensitive substring, not exact match — e.g. {hasItem rune} matches
// "Rune pouch", "Air rune", "Runite ore", ...) in their inventory or equipment, "false"
// otherwise. <name> is everything after "hasItem " up to the closing brace.
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
		boolean hasItem = containerHasItemContaining(InventoryID.INVENTORY, search)
			|| containerHasItemContaining(InventoryID.EQUIPMENT, search);
		return String.valueOf(hasItem);
	}

	private boolean containerHasItemContaining(InventoryID inventoryId, String search)
	{
		ItemContainer container = client.getItemContainer(inventoryId);
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
}
