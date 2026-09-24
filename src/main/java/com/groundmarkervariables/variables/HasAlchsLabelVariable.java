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

	@Inject
	private HasAlchsLabelVariable(Client client)
	{
		this.client = client;
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolve(Matcher matcher)
	{
		boolean hasAlchs = client.getVarbitValue(VarbitID.SPELLBOOK) == STANDARD_SPELLBOOK
			&& RuneCounter.hasAtLeast(client, ItemID.NATURERUNE, 1)
			&& RuneCounter.hasAtLeast(client, ItemID.FIRERUNE, 1);

		return String.valueOf(hasAlchs);
	}
}
