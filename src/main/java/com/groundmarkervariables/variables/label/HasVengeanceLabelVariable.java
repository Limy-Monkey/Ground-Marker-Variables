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

// {hasVengeance} — true if the player could cast Vengeance right now: Lunar spellbook with 4 astral,
// 2 death and 10 earth runes. Doesn't check Magic level, quest requirement or the cooldown.
public class HasVengeanceLabelVariable extends SpellLabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{hasVengeance(\\s+icon)?\\}", Pattern.CASE_INSENSITIVE);
	private static final Map<Integer, Integer> VENGEANCE_RUNES = Map.of(ItemID.ASTRALRUNE, 4, ItemID.DEATHRUNE, 2, ItemID.EARTHRUNE, 10);

	private final Client client;

	@Inject
	private HasVengeanceLabelVariable(Client client, RichText richText, PartyDataSharing partyDataSharing)
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
		return Spellbooks.canCast(client, Spellbooks.LUNAR, VENGEANCE_RUNES);
	}

	@Override
	protected Boolean castable(PartyMember member, @Nullable String spellbook)
	{
		return Spellbooks.canCast(member, spellbook, Spellbooks.LUNAR, VENGEANCE_RUNES);
	}

	@Override
	protected String text()
	{
		return "Vengeance";
	}

	@Override
	protected String icon(@Nullable Boolean castable)
	{
		return Icons.sprite(SpriteID.LunarMagicOn.VENGEANCE, castable);
	}
}
