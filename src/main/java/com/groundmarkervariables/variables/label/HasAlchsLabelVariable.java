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

// {hasAlchs} — true only while the player could cast High Level Alchemy right now: Standard
// spellbook (or Spellbook Swap) active, and at least one nature and fire rune available (RuneCounter —
// inventory + rune pouch, plus an infinite source for fire). Doesn't check Magic level.
public class HasAlchsLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{hasAlchs\\}", Pattern.CASE_INSENSITIVE);
	private static final Map<Integer, Integer> ALCH_RUNES = Map.of(ItemID.NATURERUNE, 1, ItemID.FIRERUNE, 1);

	private final Client client;
	private final RichText richText;

	@Inject
	private HasAlchsLabelVariable(Client client, RichText richText)
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
		return String.valueOf(hasAlchs());
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		return richText.booleanColored(hasAlchs(), "Alchs");
	}

	private boolean hasAlchs()
	{
		return Spellbooks.canCast(client, Spellbooks.STANDARD, ALCH_RUNES);
	}
}
