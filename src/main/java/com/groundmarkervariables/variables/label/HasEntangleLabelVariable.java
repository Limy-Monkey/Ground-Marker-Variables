package com.groundmarkervariables.variables.label;

import java.util.Map;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;

import com.groundmarkervariables.party.PartyDataSharing;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.Icons;
import com.groundmarkervariables.variables.support.RuneCounter;
import com.groundmarkervariables.variables.support.Spellbooks;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.party.PartyMember;

// {hasEntangle} — true if the player could cast Entangle right now, via either path:
//  - Standard spellbook (or Spellbook Swap) + enough runes for Entangle (5 earth, 5 water, 4 nature —
//    RuneCounter accounts for infinite-source weapons and combo runes; nature has neither), or
//  - a Blighted entangle sack (acts like the runes for Entangle/Snare/Bind while on the
//    standard spellbook) while in the Wilderness.
// Doesn't check Magic level (79) — same convention as hasThralls/hasFreeze, only what was
// explicitly asked for. A party member's Wilderness status isn't known, so for them only the runes count.
public class HasEntangleLabelVariable extends SpellLabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{hasEntangle(\\s+icon)?\\}", Pattern.CASE_INSENSITIVE);
	private static final Map<Integer, Integer> ENTANGLE_RUNES = Map.of(ItemID.EARTHRUNE, 5, ItemID.WATERRUNE, 5, ItemID.NATURERUNE, 4);

	private final Client client;

	@Inject
	private HasEntangleLabelVariable(Client client, RichText richText, PartyDataSharing partyDataSharing)
	{
		super(richText, partyDataSharing);
		this.client = client;
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	protected boolean castable()
	{
		return Spellbooks.canCast(client, Spellbooks.STANDARD, ENTANGLE_RUNES) || (hasBlightedEntangleSack() && inWilderness());
	}

	@Override
	protected Boolean castable(PartyMember member, @Nullable String spellbook)
	{
		return Spellbooks.canCast(member, spellbook, Spellbooks.STANDARD, ENTANGLE_RUNES);
	}

	@Override
	protected String text()
	{
		return "Entangles";
	}

	@Override
	protected String icon(@Nullable Boolean castable)
	{
		return Icons.sprite(SpriteID.Magicon2.ENTANGLE, castable);
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
