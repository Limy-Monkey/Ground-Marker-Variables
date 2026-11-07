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

// {hasUndeadGrasp} — true if the player could cast Undead Grasp right now: Arceuus spellbook (or
// Spellbook Swap) with 1 blood and 12 fire runes. Doesn't check Magic level.
public class HasUndeadGraspLabelVariable extends SpellLabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{hasUndeadGrasp(\\s+icon)?\\}", Pattern.CASE_INSENSITIVE);
	private static final Map<Integer, Integer> UNDEAD_GRASP_RUNES = Map.of(ItemID.BLOODRUNE, 1, ItemID.FIRERUNE, 12);

	private final Client client;

	@Inject
	private HasUndeadGraspLabelVariable(Client client, RichText richText, PartyDataSharing partyDataSharing)
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
		return Spellbooks.canCast(client, Spellbooks.ARCEUUS, UNDEAD_GRASP_RUNES);
	}

	@Override
	protected Boolean castable(PartyMember member, @Nullable String spellbook)
	{
		return Spellbooks.canCast(member, spellbook, Spellbooks.ARCEUUS, UNDEAD_GRASP_RUNES);
	}

	@Override
	protected String text()
	{
		return "Undead Grasp";
	}

	@Override
	protected String icon(@Nullable Boolean castable)
	{
		return Icons.sprite(SpriteID.MagicNecroOn.UNDEAD_GRASP, castable);
	}
}
