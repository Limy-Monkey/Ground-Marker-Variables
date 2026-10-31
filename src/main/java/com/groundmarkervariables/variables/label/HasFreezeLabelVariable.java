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

// {hasFreeze} — true if on Ancient Magicks (or Spellbook Swap) and able to cast Ice Barrage: either enough runes
// (6 water, 2 blood, 4 death — RuneCounter covers combo runes) or a Blighted ancient ice sack
// while in the Wilderness. Doesn't check Magic level — same convention as hasThralls. A party
// member's Wilderness status isn't known, so for them only the runes count.
public class HasFreezeLabelVariable extends SpellLabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{hasFreezes?(\\s+icon)?\\}", Pattern.CASE_INSENSITIVE);
	private static final Map<Integer, Integer> ICE_BARRAGE_RUNES = Map.of(ItemID.WATERRUNE, 6, ItemID.BLOODRUNE, 2, ItemID.DEATHRUNE, 4);

	private final Client client;

	@Inject
	private HasFreezeLabelVariable(Client client, RichText richText, PartyDataSharing partyDataSharing)
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
		return Spellbooks.canCast(client, Spellbooks.ANCIENT, ICE_BARRAGE_RUNES)
			|| (hasBlightedIceSack() && inWilderness() && Spellbooks.canCast(client, Spellbooks.ANCIENT, Map.of()));
	}

	@Override
	protected Boolean castable(PartyMember member, @Nullable String spellbook)
	{
		return Spellbooks.canCast(member, spellbook, Spellbooks.ANCIENT, ICE_BARRAGE_RUNES);
	}

	@Override
	protected String text()
	{
		return "Freezes";
	}

	@Override
	protected String icon(@Nullable Boolean castable)
	{
		return Icons.sprite(SpriteID.Magicon2.ICE_BARRAGE, castable);
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
