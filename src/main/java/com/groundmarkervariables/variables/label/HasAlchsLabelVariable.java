package com.groundmarkervariables.variables.label;

import java.util.Map;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;

import com.groundmarkervariables.party.PartyDataSharing;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.Icons;
import com.groundmarkervariables.variables.support.Spellbooks;
import net.runelite.api.Client;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.party.PartyMember;

// {hasAlchs} — true only while the player could cast High Level Alchemy right now: Standard
// spellbook (or Spellbook Swap) active, and at least one nature and fire rune available (RuneCounter —
// inventory + rune pouch, plus an infinite source for fire). Doesn't check Magic level.
public class HasAlchsLabelVariable extends SpellLabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{hasAlchs(\\s+icon)?\\}", Pattern.CASE_INSENSITIVE);
	private static final Map<Integer, Integer> ALCH_RUNES = Map.of(ItemID.NATURERUNE, 1, ItemID.FIRERUNE, 1);

	private final Client client;

	@Inject
	private HasAlchsLabelVariable(Client client, RichText richText, PartyDataSharing partyDataSharing)
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
		return Spellbooks.canCast(client, Spellbooks.STANDARD, ALCH_RUNES);
	}

	@Override
	protected Boolean castable(PartyMember member, @Nullable String spellbook)
	{
		return Spellbooks.canCast(member, spellbook, Spellbooks.STANDARD, ALCH_RUNES);
	}

	@Override
	protected String text()
	{
		return "Alchs";
	}

	@Override
	protected String icon(@Nullable Boolean castable)
	{
		return Icons.sprite(SpriteID.Magicon.HIGH_LEVEL_ALCHEMY, castable);
	}
}
