package com.groundmarkervariables.variables;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

// {hasThralls} — true only while the player could cast an Arceuus Conjure spell right now:
// Arceuus spellbook active, Book of the Dead owned (equipped or carried), and at least one
// fire, blood, and cosmic rune available (RuneCounter — inventory + rune pouch, plus an
// infinite source for fire). Doesn't check Magic level.
class HasThrallsLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{hasThralls\\}", Pattern.CASE_INSENSITIVE);
	private static final int ARCEUUS_SPELLBOOK = 3;

	private final Client client;

	@Inject
	private HasThrallsLabelVariable(Client client)
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
		boolean hasThralls = client.getVarbitValue(VarbitID.SPELLBOOK) == ARCEUUS_SPELLBOOK
			&& hasBookOfTheDead()
			&& RuneCounter.hasAtLeast(client, ItemID.FIRERUNE, 1)
			&& RuneCounter.hasAtLeast(client, ItemID.BLOODRUNE, 1)
			&& RuneCounter.hasAtLeast(client, ItemID.COSMICRUNE, 1);

		return String.valueOf(hasThralls);
	}

	private boolean hasBookOfTheDead()
	{
		return RuneCounter.itemCount(client, InventoryID.INVENTORY, ItemID.BOOK_OF_THE_DEAD) > 0
			|| RuneCounter.itemCount(client, InventoryID.EQUIPMENT, ItemID.BOOK_OF_THE_DEAD) > 0;
	}
}
