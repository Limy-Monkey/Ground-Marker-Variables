package com.groundmarkervariables.variables.label;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.RuneCounter;
import com.groundmarkervariables.variables.support.Spellbooks;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

// {hasFreeze} — true if on Ancient Magicks (or Spellbook Swap) and able to cast Ice Barrage: either enough runes
// (6 water, 2 blood, 4 death — RuneCounter covers combo runes) or a Blighted ancient ice sack
// while in the Wilderness. Doesn't check Magic level — same convention as hasThralls.
public class HasFreezeLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{hasFreezes?\\}", Pattern.CASE_INSENSITIVE);
	private static final Map<Integer, Integer> ICE_BARRAGE_RUNES = Map.of(ItemID.WATERRUNE, 6, ItemID.BLOODRUNE, 2, ItemID.DEATHRUNE, 4);

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
		return Spellbooks.canCast(client, Spellbooks.ANCIENT, ICE_BARRAGE_RUNES)
			|| (hasBlightedIceSack() && inWilderness() && Spellbooks.canCast(client, Spellbooks.ANCIENT, Map.of()));
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
