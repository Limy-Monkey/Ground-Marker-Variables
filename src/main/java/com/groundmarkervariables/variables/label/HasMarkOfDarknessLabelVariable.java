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

// {hasMarkOfDarkness} — true if the player could cast Mark of Darkness right now: Arceuus spellbook (or
// Spellbook Swap) with 1 cosmic and 1 soul rune. Doesn't check Magic level or quest requirement.
public class HasMarkOfDarknessLabelVariable extends SpellLabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{hasMarkOfDarkness(\\s+icon)?\\}", Pattern.CASE_INSENSITIVE);
	private static final Map<Integer, Integer> MARK_OF_DARKNESS_RUNES = Map.of(ItemID.COSMICRUNE, 1, ItemID.SOULRUNE, 1);

	private final Client client;

	@Inject
	private HasMarkOfDarknessLabelVariable(Client client, RichText richText, PartyDataSharing partyDataSharing)
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
		return Spellbooks.canCast(client, Spellbooks.ARCEUUS, MARK_OF_DARKNESS_RUNES);
	}

	@Override
	protected Boolean castable(PartyMember member, @Nullable String spellbook)
	{
		return Spellbooks.canCast(member, spellbook, Spellbooks.ARCEUUS, MARK_OF_DARKNESS_RUNES);
	}

	@Override
	protected String text()
	{
		return "Mark of Darkness";
	}

	@Override
	protected String icon(@Nullable Boolean castable)
	{
		return Icons.sprite(SpriteID.MagicNecroOn.MARK_OF_DARKNESS, castable);
	}
}
