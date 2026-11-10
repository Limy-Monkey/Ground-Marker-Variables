package com.groundmarkervariables.variables.label;

import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;

import com.groundmarkervariables.party.PartyDataSharing;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.Icons;
import com.groundmarkervariables.variables.support.Spellbooks;
import net.runelite.api.Client;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.party.PartyMember;

// {hasSpellbookSwap} — true if the player could cast Spellbook Swap right now: Lunar spellbook with 3 astral,
// 2 cosmic and 1 law rune. Doesn't check Magic level or quest requirement.
public class HasSpellbookSwapLabelVariable extends SpellLabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{hasSpellbookSwap(\\s+icon)?\\}", Pattern.CASE_INSENSITIVE);

	private final Client client;

	@Inject
	private HasSpellbookSwapLabelVariable(Client client, RichText richText, PartyDataSharing partyDataSharing)
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
		return Spellbooks.canCast(client, Spellbooks.LUNAR, Spellbooks.SPELLBOOK_SWAP_RUNES);
	}

	@Override
	protected Boolean castable(PartyMember member, @Nullable String spellbook)
	{
		return Spellbooks.canCast(member, spellbook, Spellbooks.LUNAR, Spellbooks.SPELLBOOK_SWAP_RUNES);
	}

	@Override
	protected String text()
	{
		return "Spellbook Swap";
	}

	@Override
	protected String icon(@Nullable Boolean castable)
	{
		return Icons.sprite(SpriteID.LunarMagicOn.SPELLBOOK_SWAP, castable);
	}
}
