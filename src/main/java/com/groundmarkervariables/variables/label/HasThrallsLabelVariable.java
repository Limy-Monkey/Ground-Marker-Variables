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
import net.runelite.client.party.PartyMember;

// {hasThralls} — true only while the player could cast an Arceuus Conjure spell right now:
// Arceuus spellbook (or Spellbook Swap) active, Book of the Dead owned (equipped or carried), and at least one
// fire, blood, and cosmic rune available (RuneCounter — inventory + rune pouch, infinite
// sources, and combo runes). Doesn't check Magic level.
public class HasThrallsLabelVariable extends SpellLabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{hasThralls(\\s+icon)?\\}", Pattern.CASE_INSENSITIVE);
	private static final Map<Integer, Integer> THRALL_RUNES = Map.of(ItemID.FIRERUNE, 1, ItemID.BLOODRUNE, 1, ItemID.COSMICRUNE, 1);

	private final Client client;

	@Inject
	private HasThrallsLabelVariable(Client client, RichText richText, PartyDataSharing partyDataSharing)
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
		return hasBookOfTheDead() && Spellbooks.canCast(client, Spellbooks.ARCEUUS, THRALL_RUNES);
	}

	@Override
	protected Boolean castable(PartyMember member, @Nullable String spellbook)
	{
		boolean hasBook = RuneCounter.itemCount(member.getInv(), ItemID.BOOK_OF_THE_DEAD) > 0
			|| RuneCounter.itemCount(member.getWorn(), ItemID.BOOK_OF_THE_DEAD) > 0;
		return hasBook ? Spellbooks.canCast(member, spellbook, Spellbooks.ARCEUUS, THRALL_RUNES) : Boolean.FALSE;
	}

	@Override
	protected String text()
	{
		return "Thralls";
	}

	@Override
	protected String icon(@Nullable Boolean castable)
	{
		return Icons.item(ItemID.BOOK_OF_THE_DEAD, castable);
	}

	private boolean hasBookOfTheDead()
	{
		return RuneCounter.itemCount(client, InventoryID.INVENTORY, ItemID.BOOK_OF_THE_DEAD) > 0
			|| RuneCounter.itemCount(client, InventoryID.EQUIPMENT, ItemID.BOOK_OF_THE_DEAD) > 0;
	}
}
