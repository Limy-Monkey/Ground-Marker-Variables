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

// {hasEntangle} — true if the player could cast Entangle right now, via either path:
//  - Standard spellbook + enough runes for Entangle (5 earth, 5 water, 4 nature —
//    RuneCounter accounts for infinite-source weapons and combo runes; nature has neither), or
//  - a Blighted entangle sack (acts like the runes for Entangle/Snare/Bind while on the
//    standard spellbook) while in the Wilderness.
// Doesn't check Magic level (79) — same convention as hasThralls/hasFreeze, only what was
// explicitly asked for.
public class HasEntangleLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{hasEntangle\\}", Pattern.CASE_INSENSITIVE);
	private static final int STANDARD_SPELLBOOK = 0;

	private final Client client;
	private final RichText richText;

	@Inject
	private HasEntangleLabelVariable(Client client, RichText richText)
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
		return String.valueOf(hasEntangle());
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		return richText.booleanColored(hasEntangle(), "Entangles");
	}

	private boolean hasEntangle()
	{
		return (onStandardSpellbook() && hasEntangleRunes()) || (hasBlightedEntangleSack() && inWilderness());
	}

	private boolean onStandardSpellbook()
	{
		return client.getVarbitValue(VarbitID.SPELLBOOK) == STANDARD_SPELLBOOK;
	}

	private boolean hasEntangleRunes()
	{
		return RuneCounter.hasAtLeast(client, ItemID.EARTHRUNE, 5)
			&& RuneCounter.hasAtLeast(client, ItemID.WATERRUNE, 5)
			&& RuneCounter.hasAtLeast(client, ItemID.NATURERUNE, 4);
	}

	private boolean hasBlightedEntangleSack()
	{
		return RuneCounter.itemCount(client, InventoryID.INVENTORY, ItemID.BLIGHTED_SACK_ENTANGLE) > 0;
	}

	private boolean inWilderness()
	{
		return client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) != 0;
	}
}
