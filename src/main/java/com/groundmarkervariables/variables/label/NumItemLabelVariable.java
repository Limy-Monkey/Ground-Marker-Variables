package com.groundmarkervariables.variables.label;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.ItemQuantityFinder;
import com.groundmarkervariables.variables.support.ItemQuantityFinder.Match;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.QuantityFormatter;

// {numItem <name>} -> total quantity of every item whose name contains <name> (same scope as
// {hasItem}), e.g. {numItem brew} counts a Saradomin brew(3) and an Armadyl brew(1) as 2.
public class NumItemLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{numItem\\s+([^{}?:<>=!]+?)\\s*\\}", Pattern.CASE_INSENSITIVE);

	private final Client client;
	private final ItemManager itemManager;
	private final RichText richText;

	@Inject
	private NumItemLabelVariable(Client client, ItemManager itemManager, RichText richText)
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

	private int count(String typed)
	{
		List<Match> matches = ItemQuantityFinder.findAll(client, itemManager, typed.toLowerCase(Locale.ROOT));

		int total = 0;
		for (Match match : matches)
		{
			total += match.quantity;
		}
		return total;
	}
}
