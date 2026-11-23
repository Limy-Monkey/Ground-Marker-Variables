package com.groundmarkervariables.variables.label;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.PartyMemberVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.Icons;
import com.groundmarkervariables.variables.support.ItemQuantityFinder;
import com.groundmarkervariables.variables.support.ItemQuantityFinder.Match;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import net.runelite.client.party.PartyMember;

// {runePouch} -> the icon of every rune held, in the inventory or rune pouch: items named "... rune", so not
// rune pouches or Rune gear. Plain: "true" if any.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class RunePouchLabelVariable implements LabelVariable, PartyMemberVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{runePouch\\}", Pattern.CASE_INSENSITIVE);
	private static final String RUNE = " rune";

	private final Client client;
	private final ItemManager itemManager;

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		return client.getLocalPlayer() == null ? null : String.valueOf(!runes(ItemQuantityFinder.findAll(client, itemManager, RUNE)).isEmpty());
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		return client.getLocalPlayer() == null ? null : Icons.items(runes(ItemQuantityFinder.findAll(client, itemManager, RUNE)));
	}

	@Override
	public String resolvePlain(Matcher matcher, PartyMember member)
	{
		return String.valueOf(!runes(ItemQuantityFinder.findAll(member, itemManager, RUNE)).isEmpty());
	}

	@Override
	public String resolveRich(Matcher matcher, PartyMember member)
	{
		return Icons.items(runes(ItemQuantityFinder.findAll(member, itemManager, RUNE)));
	}

	@Override
	public String resolveRowRich(Matcher matcher, RichText richText)
	{
		return resolveRich(matcher);
	}

	// Each rune's id once, in the order found.
	private static Set<Integer> runes(List<Match> matches)
	{
		Set<Integer> ids = new LinkedHashSet<>();
		for (Match match : matches)
		{
			if (match.name.toLowerCase(Locale.ROOT).endsWith(RUNE))
			{
				ids.add(match.id);
			}
		}
		return ids;
	}

}
