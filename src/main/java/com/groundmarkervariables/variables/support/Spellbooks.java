package com.groundmarkervariables.variables.support;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

// Spellbook + rune check shared by the {has<Spell>} variables. Lunar's Spellbook Swap stands in
// for any other spellbook, as long as its runes are there on top of the spell's own.
public final class Spellbooks
{
	public static final int STANDARD = 0;
	public static final int ANCIENT = 1;
	public static final int LUNAR = 2;
	public static final int ARCEUUS = 3;

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

		Map<Integer, Integer> withSwap = new HashMap<>(runes);
		SPELLBOOK_SWAP_RUNES.forEach((rune, amount) -> withSwap.merge(rune, amount, Integer::sum));
		return hasRunes(client, withSwap);
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
}
