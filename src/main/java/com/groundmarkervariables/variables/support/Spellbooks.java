package com.groundmarkervariables.variables.support;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.party.PartyMember;

// Spellbook + rune check shared by the {has<Spell>} variables. Lunar's Spellbook Swap stands in
// for any other spellbook, as long as its runes are there on top of the spell's own.
public final class Spellbooks
{
	public static final int STANDARD = 0;
	public static final int ANCIENT = 1;
	public static final int LUNAR = 2;
	public static final int ARCEUUS = 3;
	// By VarbitID.SPELLBOOK value; also {spellbook}'s plain text.
	public static final List<String> NAMES = List.of("Standard", "Ancient", "Lunar", "Arceuus");

	private static final Map<Integer, Integer> SPELLBOOK_SWAP_RUNES = Map.of(
		ItemID.ASTRALRUNE, 3,
		ItemID.COSMICRUNE, 2,
		ItemID.LAWRUNE, 1);

	private Spellbooks()
	{
	}

	// runes: rune id -> amount the spell itself costs.
	public static boolean canCast(Client client, int spellbook, Map<Integer, Integer> runes)
	{
		int current = client.getVarbitValue(VarbitID.SPELLBOOK);
		if (current == spellbook)
		{
			return hasRunes(client, runes);
		}

		if (current != LUNAR)
		{
			return false;
		}

		return hasRunes(client, withSwap(runes));
	}

	// Our spellbook's name, or null if it isn't a known one.
	@Nullable
	public static String name(Client client)
	{
		int spellbook = client.getVarbitValue(VarbitID.SPELLBOOK);
		return spellbook >= 0 && spellbook < NAMES.size() ? NAMES.get(spellbook) : null;
	}

	// For a party member, whose spellbook is spellbookName (null if they haven't shared it). Null
	// means they have the runes, but their spellbook isn't known.
	@Nullable
	public static Boolean canCast(PartyMember member, @Nullable String spellbookName, int spellbook, Map<Integer, Integer> runes)
	{
		int current = spellbookName == null ? -1 : NAMES.indexOf(spellbookName);
		if (current == -1)
		{
			return hasRunes(member, runes) ? null : false;
		}

		if (current == spellbook)
		{
			return hasRunes(member, runes);
		}

		return current == LUNAR && hasRunes(member, withSwap(runes));
	}

	private static Map<Integer, Integer> withSwap(Map<Integer, Integer> runes)
	{
		Map<Integer, Integer> withSwap = new HashMap<>(runes);
		SPELLBOOK_SWAP_RUNES.forEach((rune, amount) -> withSwap.merge(rune, amount, Integer::sum));
		return withSwap;
	}

	private static boolean hasRunes(Client client, Map<Integer, Integer> runes)
	{
		for (Map.Entry<Integer, Integer> rune : runes.entrySet())
		{
			if (!RuneCounter.hasAtLeast(client, rune.getKey(), rune.getValue()))
			{
				return false;
			}
		}
		return true;
	}

	private static boolean hasRunes(PartyMember member, Map<Integer, Integer> runes)
	{
		for (Map.Entry<Integer, Integer> rune : runes.entrySet())
		{
			if (!RuneCounter.hasAtLeast(member, rune.getKey(), rune.getValue()))
			{
				return false;
			}
		}
		return true;
	}
}
