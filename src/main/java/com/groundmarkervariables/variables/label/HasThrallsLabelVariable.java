package com.groundmarkervariables.variables.label;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.RuneCounter;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

// {hasThralls} — true only while the player could cast an Arceuus Conjure spell right now:
// Arceuus spellbook active, Book of the Dead owned (equipped or carried), and at least one
// fire, blood, and cosmic rune available (RuneCounter — inventory + rune pouch, infinite
// sources, and combo runes). Doesn't check Magic level.
public class HasThrallsLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{hasThralls\\}", Pattern.CASE_INSENSITIVE);
	private static final int ARCEUUS_SPELLBOOK = 3;

	private final Client client;
	private final RichText richText;

	@Inject
	private HasThrallsLabelVariable(Client client, RichText richText)
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
		return String.valueOf(hasThralls());
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		return richText.booleanColored(hasThralls(), "Thralls");
	}

	private boolean hasThralls()
	{
		return client.getVarbitValue(VarbitID.SPELLBOOK) == ARCEUUS_SPELLBOOK
			&& hasBookOfTheDead()
			&& RuneCounter.hasAtLeast(client, ItemID.FIRERUNE, 1)
			&& RuneCounter.hasAtLeast(client, ItemID.BLOODRUNE, 1)
			&& RuneCounter.hasAtLeast(client, ItemID.COSMICRUNE, 1);
	}

	private boolean hasBookOfTheDead()
	{
		return RuneCounter.itemCount(client, InventoryID.INVENTORY, ItemID.BOOK_OF_THE_DEAD) > 0
			|| RuneCounter.itemCount(client, InventoryID.EQUIPMENT, ItemID.BOOK_OF_THE_DEAD) > 0;
	}
}
