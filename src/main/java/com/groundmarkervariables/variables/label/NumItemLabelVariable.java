package com.groundmarkervariables.variables.label;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.PartyMemberVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.ItemQuantityFinder;
import com.groundmarkervariables.variables.support.ItemQuantityFinder.Match;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.util.QuantityFormatter;

// {numItem <name>} -> total quantity of every item whose name contains <name> (same scope as
// {hasItem}), e.g. {numItem brew} counts a Saradomin brew(3) and an Armadyl brew(1) as 2.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class NumItemLabelVariable implements LabelVariable, PartyMemberVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{numItem\\s+([^{}?:<>=!]+?)\\s*\\}", Pattern.CASE_INSENSITIVE);

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
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		return String.valueOf(count(matcher.group(1).trim()));
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		String typed = matcher.group(1).trim();
		return richText.labeled(typed + " count", QuantityFormatter.quantityToStackSize(count(typed)));
	}

	@Override
	public String resolvePlain(Matcher matcher, PartyMember member)
	{
		return String.valueOf(total(ItemQuantityFinder.findAll(member, itemManager, matcher.group(1).trim().toLowerCase(Locale.ROOT))));
	}

	@Override
	public String resolveRich(Matcher matcher, PartyMember member)
	{
		return richText.highlightValue(QuantityFormatter.quantityToStackSize(Long.parseLong(resolvePlain(matcher, member))));
	}

	@Override
	public String resolveRowRich(Matcher matcher, RichText richText)
	{
		String plain = resolvePlain(matcher);
		return plain == null ? null : richText.highlightValue(QuantityFormatter.quantityToStackSize(Long.parseLong(plain)));
	}

	private int count(String typed)
	{
		return total(ItemQuantityFinder.findAll(client, itemManager, typed.toLowerCase(Locale.ROOT)));
	}

	private static int total(List<Match> matches)
	{
		int total = 0;
		for (Match match : matches)
		{
			total += match.quantity;
		}
		return total;
	}
}
