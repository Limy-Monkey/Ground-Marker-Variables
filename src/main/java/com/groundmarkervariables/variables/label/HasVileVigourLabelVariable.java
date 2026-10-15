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

// {hasVileVigour} — true if the player could cast Vile Vigour right now: Arceuus spellbook (or
// Spellbook Swap) with 1 soul and 3 air runes. Doesn't check Magic level or quest requirement.
public class HasVileVigourLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{hasVileVigour\\}", Pattern.CASE_INSENSITIVE);
	private static final Map<Integer, Integer> VILE_VIGOUR_RUNES = Map.of(ItemID.SOULRUNE, 1, ItemID.AIRRUNE, 3);

	private final Client client;
	private final RichText richText;

	@Inject
	private HasVileVigourLabelVariable(Client client, RichText richText)
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
		return String.valueOf(hasVileVigour());
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		return richText.booleanColored(hasVileVigour(), "Vile Vigour");
	}

	private boolean hasVileVigour()
	{
		return Spellbooks.canCast(client, Spellbooks.ARCEUUS, VILE_VIGOUR_RUNES);
	}
}
