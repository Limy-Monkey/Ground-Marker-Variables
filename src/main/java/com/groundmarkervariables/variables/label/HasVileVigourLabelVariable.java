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

// {hasVileVigour} — true if the player could cast Vile Vigour right now: Arceuus spellbook (or
// Spellbook Swap) with 1 soul and 3 air runes. Doesn't check Magic level or quest requirement.
public class HasVileVigourLabelVariable extends SpellLabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{hasVileVigour(\\s+icon)?\\}", Pattern.CASE_INSENSITIVE);
	private static final Map<Integer, Integer> VILE_VIGOUR_RUNES = Map.of(ItemID.SOULRUNE, 1, ItemID.AIRRUNE, 3);

	private final Client client;

	@Inject
	private HasVileVigourLabelVariable(Client client, RichText richText, PartyDataSharing partyDataSharing)
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
		return Spellbooks.canCast(client, Spellbooks.ARCEUUS, VILE_VIGOUR_RUNES);
	}

	@Override
	protected Boolean castable(PartyMember member, @Nullable String spellbook)
	{
		return Spellbooks.canCast(member, spellbook, Spellbooks.ARCEUUS, VILE_VIGOUR_RUNES);
	}

	@Override
	protected String text()
	{
		return "Vile Vigour";
	}

	@Override
	protected String icon(@Nullable Boolean castable)
	{
		return Icons.sprite(SpriteID.MagicNecroOn.VILE_VIGOUR, castable);
	}
}
