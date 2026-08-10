package com.groundmarkervariables.variables;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;

class SpellbookLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{spellbook\\}", Pattern.CASE_INSENSITIVE);
	// VarbitID.SPELLBOOK values: 0 = Standard, 1 = Ancient Magicks, 2 = Lunar, 3 = Arceuus.
	private static final String[] SPELLBOOKS = {"Standard", "Ancient", "Lunar", "Arceuus"};

	private final Client client;

	@Inject
	private SpellbookLabelVariable(Client client)
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
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		int spellbook = client.getVarbitValue(VarbitID.SPELLBOOK);
		return spellbook >= 0 && spellbook < SPELLBOOKS.length ? SPELLBOOKS[spellbook] : null;
	}
}
