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

// {numDoses <name>} -> total doses (quantity * the "(N)" in the item's own name) of every
// matching item with a dose count, e.g. {numDoses brew} counts a Saradomin brew(3) and an
// Armadyl brew(1) as 4. Items with no "(N)" in their name aren't counted at all.
public class NumDosesLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{numDoses\\s+([^{}?:<>=!]+?)\\s*\\}", Pattern.CASE_INSENSITIVE);
	private static final Pattern DOSE_COUNT = Pattern.compile("\\((\\d+)\\)");

	private final Client client;
	private final ItemManager itemManager;
	private final RichText richText;

	@Inject
	private NumDosesLabelVariable(Client client, ItemManager itemManager, RichText richText)
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
		return richText.labeled(typed + " doses", QuantityFormatter.quantityToStackSize(count(typed)));
	}

	private int count(String typed)
	{
		List<Match> matches = ItemQuantityFinder.findAll(client, itemManager, typed.toLowerCase(Locale.ROOT));

		int total = 0;
		for (Match match : matches)
		{
			Matcher doseMatcher = DOSE_COUNT.matcher(match.name);
			if (doseMatcher.find())
			{
				total += match.quantity * Integer.parseInt(doseMatcher.group(1));
			}
		}
		return total;
	}
}
