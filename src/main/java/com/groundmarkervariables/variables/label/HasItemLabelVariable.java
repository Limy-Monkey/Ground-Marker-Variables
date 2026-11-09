package com.groundmarkervariables.variables.label;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.PartyMemberVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.ItemQuantityFinder;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import net.runelite.client.party.PartyMember;

// {hasItem <name>} -> "true" if the player currently holds an item whose ItemComposition name
// contains <name> (case-insensitive substring, not exact match — e.g. {hasItem rune} matches
// "Rune pouch", "Air rune", "Runite ore", ...) in their inventory or equipment, "false"
// otherwise. <name> is everything after "hasItem " up to the closing brace.
//
// Also checks a rune pouch's contents and Dizana's Quiver's active ammo, via ItemQuantityFinder.
//
// [^{}?:<>=!]+? deliberately excludes ?:<>=! from <name> — otherwise this pattern could swallow a
// trailing "? A : B" or "> 5" as part of the item name. No real item name uses those characters.
public class HasItemLabelVariable implements LabelVariable, PartyMemberVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{hasItem\\s+([^{}?:<>=!]+?)\\s*\\}", Pattern.CASE_INSENSITIVE);

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

		return String.valueOf(itemName(matcher, null) != null);
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

		String foundName = itemName(matcher, null);
		return richText.booleanColored(foundName != null, foundName != null ? foundName : matcher.group(1).trim());
	}

	@Override
	public String resolvePlain(Matcher matcher, PartyMember member)
	{
		return String.valueOf(itemName(matcher, member) != null);
	}

	@Override
	public String resolveRich(Matcher matcher, PartyMember member)
	{
		String foundName = itemName(matcher, member);
		return richText.booleanColored(foundName != null, foundName != null ? foundName : matcher.group(1).trim());
	}

	@Override
	public String resolveRowRich(Matcher matcher, RichText richText)
	{
		return resolveRich(matcher);
	}

	// The first match's name, for the local player when member is null.
	private String itemName(Matcher matcher, PartyMember member)
	{
		String search = matcher.group(1).trim().toLowerCase(Locale.ROOT);
		List<ItemQuantityFinder.Match> matches = member == null
			? ItemQuantityFinder.findAll(client, itemManager, search)
			: ItemQuantityFinder.findAll(member, itemManager, search);
		return matches.isEmpty() ? null : matches.get(0).name;
	}
}
