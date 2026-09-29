package com.groundmarkervariables.variables;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

// {hasFreeze} — true if on Ancient Magicks and able to cast Ice Barrage: either enough runes
// (6 water, 2 blood, 4 death — RuneCounter covers combo runes) or a Blighted ancient ice sack
// while in the Wilderness. Doesn't check Magic level — same convention as hasThralls.
class HasFreezeLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{hasFreezes?\\}", Pattern.CASE_INSENSITIVE);
	private static final int ANCIENT_SPELLBOOK = 1;

	private final Client client;
	private final RichText richText;

	@Inject
	private HasFreezeLabelVariable(Client client, RichText richText)
	{
		this.client = client;
		this.richText = richText;
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		return String.valueOf(hasFreeze());
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		return richText.booleanColored(hasFreeze(), "Freezes");
	}

	private boolean hasFreeze()
	{
		return onAncientSpellbook() && (hasIceBarrageRunes() || (hasBlightedIceSack() && inWilderness()));
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
