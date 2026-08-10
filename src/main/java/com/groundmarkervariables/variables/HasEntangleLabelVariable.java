package com.groundmarkervariables.variables;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

// {hasEntangle} — true if the player could cast Entangle right now, via either path:
//  - Standard spellbook + enough runes for Entangle (5 earth, 5 water, 4 nature —
//    RuneCounter accounts for infinite-earth/water-rune weapons; nature has no infinite
//    source in the game), or
//  - a Blighted entangle sack (acts like the runes for Entangle/Snare/Bind while on the
//    standard spellbook) while in the Wilderness.
// Doesn't check Magic level (79) — same convention as hasThralls/hasFreeze, only what was
// explicitly asked for.
class HasEntangleLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{hasEntangle\\}", Pattern.CASE_INSENSITIVE);
	private static final int STANDARD_SPELLBOOK = 0;

	private final Client client;

	@Inject
	private HasEntangleLabelVariable(Client client)
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
		boolean hasEntangle = (onStandardSpellbook() && hasEntangleRunes())
			|| (hasBlightedEntangleSack() && inWilderness());

		return String.valueOf(hasEntangle);
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
