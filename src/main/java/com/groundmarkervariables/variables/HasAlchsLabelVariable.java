package com.groundmarkervariables.variables;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

// {hasAlchs} — true only while the player could cast High Level Alchemy right now: Standard
// spellbook active, and at least one nature and fire rune available (RuneCounter —
// inventory + rune pouch, plus an infinite source for fire). Doesn't check Magic level.
class HasAlchsLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{hasAlchs\\}", Pattern.CASE_INSENSITIVE);
	private static final int STANDARD_SPELLBOOK = 0;

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
		return client.getVarbitValue(VarbitID.SPELLBOOK) == STANDARD_SPELLBOOK
			&& RuneCounter.hasAtLeast(client, ItemID.NATURERUNE, 1)
			&& RuneCounter.hasAtLeast(client, ItemID.FIRERUNE, 1);
	}
}
