package com.groundmarkervariables.variables.label;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.Spellbooks;
import net.runelite.api.Client;
import net.runelite.api.gameval.ItemID;

// {hasVengeance} — true if the player could cast Vengeance right now: Lunar spellbook with 4 astral,
// 2 death and 10 earth runes. Doesn't check Magic level, quest requirement or the cooldown.
public class HasVengeanceLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{hasVengeance\\}", Pattern.CASE_INSENSITIVE);
	private static final Map<Integer, Integer> VENGEANCE_RUNES = Map.of(ItemID.ASTRALRUNE, 4, ItemID.DEATHRUNE, 2, ItemID.EARTHRUNE, 10);

	private final Client client;
	private final RichText richText;

	@Inject
	private HasVengeanceLabelVariable(Client client, RichText richText)
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
		return String.valueOf(hasVengeance());
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		return richText.booleanColored(hasVengeance(), "Vengeance");
	}

	private boolean hasVengeance()
	{
		return Spellbooks.canCast(client, Spellbooks.LUNAR, VENGEANCE_RUNES);
	}
}
