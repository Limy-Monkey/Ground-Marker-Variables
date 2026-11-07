package com.groundmarkervariables.variables.label;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.PartyMemberVariable;
import com.groundmarkervariables.variables.RichText;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.party.PartyMember;

// {invSpace} -> empty inventory slots, 0-28.
public class InvSpaceLabelVariable implements LabelVariable, PartyMemberVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{invSpace\\}", Pattern.CASE_INSENSITIVE);
	private static final int INVENTORY_SIZE = 28;

	private final Client client;
	private final RichText richText;

	@Inject
	private InvSpaceLabelVariable(Client client, RichText richText)
	{
		this.client = client;
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
		return String.valueOf(freeSlots());
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		String spaces = resolvePlain(matcher);
		return spaces == null ? null : richText.highlightValue(spaces) + (spaces.equals("1") ? " inventory space" : " inventory spaces");
	}

	// Null until their inventory has been shared.
	@Override
	public String resolvePlain(Matcher matcher, PartyMember member)
	{
		Item[] inventory = member.getInv();
		return inventory == null ? null : String.valueOf(freeSlots(inventory));
	}

	@Override
	public String resolveRich(Matcher matcher, PartyMember member)
	{
		String spaces = resolvePlain(matcher, member);
		return spaces == null ? null : richText.highlightValue(spaces);
	}

	private int freeSlots()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		return inventory == null ? INVENTORY_SIZE : freeSlots(inventory.getItems());
	}

	private static int freeSlots(Item[] items)
	{
		int used = 0;
		for (Item item : items)
		{
			if (item.getId() != -1)
			{
				used++;
			}
		}
		return Math.max(0, INVENTORY_SIZE - used);
	}
}
