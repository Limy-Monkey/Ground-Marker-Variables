package com.groundmarkervariables.variables;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

// {hasFreeze} — true if the player could freeze someone right now, via either path:
//  - Ancient Magicks spellbook + enough runes for Ice Barrage (6 water, 2 blood, 4 death —
//    the strongest/most commonly used ice spell, not the cheapest; RuneCounter accounts for
//    an infinite-water-rune weapon, but blood/death have no infinite source in the game), or
//  - a Blighted ancient ice sack (acts like the runes for any ice spell) while in the
//    Wilderness.
// Doesn't check Magic level (94 for Ice Barrage) — same convention as hasThralls, only what
// was explicitly asked for.
class HasFreezeLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{hasFreeze\\}", Pattern.CASE_INSENSITIVE);
	private static final int ANCIENT_SPELLBOOK = 1;

	private final Client client;

	@Inject
	private HasFreezeLabelVariable(Client client)
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
		boolean hasFreeze = (onAncientSpellbook() && hasIceBarrageRunes())
			|| (hasBlightedIceSack() && inWilderness());

		return String.valueOf(hasFreeze);
	}

	private boolean onAncientSpellbook()
	{
		return client.getVarbitValue(VarbitID.SPELLBOOK) == ANCIENT_SPELLBOOK;
	}

	private boolean hasIceBarrageRunes()
	{
		return RuneCounter.hasAtLeast(client, ItemID.WATERRUNE, 6)
			&& RuneCounter.hasAtLeast(client, ItemID.BLOODRUNE, 2)
			&& RuneCounter.hasAtLeast(client, ItemID.DEATHRUNE, 4);
	}

	private boolean hasBlightedIceSack()
	{
		return RuneCounter.itemCount(client, InventoryID.INVENTORY, ItemID.BLIGHTED_SACK_ICEBARRAGE) > 0;
	}

	private boolean inWilderness()
	{
		return client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) != 0;
	}
}
