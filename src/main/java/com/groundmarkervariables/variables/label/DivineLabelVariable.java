package com.groundmarkervariables.variables.label;

import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;

// {divine <potion>} -> ticks remaining on <potion>'s buff. Rich is the potion's name colored
// green (>25 ticks), yellow (1-25), or red (0, not active).
public class DivineLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{divine\\s+([^{}?:<>=!]+?)\\}", Pattern.CASE_INSENSITIVE);
	private static final int YELLOW_THRESHOLD = 25;

	private enum Potion
	{
		ATTACK("attack", "Divine Super Attack Potion", VarbitID.DIVINEATTACK_POTION_TIME, VarbitID.DIVINECOMBAT_POTION_TIME),
		STRENGTH("strength", "Divine Super Strength Potion", VarbitID.DIVINESTRENGTH_POTION_TIME, VarbitID.DIVINECOMBAT_POTION_TIME),
		DEFENCE("defence", "Divine Super Defence Potion", VarbitID.DIVINEDEFENCE_POTION_TIME,
			VarbitID.DIVINECOMBAT_POTION_TIME, VarbitID.DIVINEBASTION_POTION_TIME, VarbitID.DIVINEBATTLEMAGE_POTION_TIME),
		RANGE("range", "Divine Ranging Potion", VarbitID.DIVINERANGE_POTION_TIME, VarbitID.DIVINEBASTION_POTION_TIME),
		MAGIC("magic", "Divine Magic Potion", VarbitID.DIVINEMAGIC_POTION_TIME, VarbitID.DIVINEBATTLEMAGE_POTION_TIME),
		COMBAT("combat", "Divine Super Combat Potion", VarbitID.DIVINECOMBAT_POTION_TIME),
		BASTION("bastion", "Divine Bastion Potion", VarbitID.DIVINEBASTION_POTION_TIME),
		BATTLEMAGE("battlemage", "Divine Battlemage Potion", VarbitID.DIVINEBATTLEMAGE_POTION_TIME);

		private final String keyword;
		private final String displayName;
		private final int[] varbits;

		Potion(String keyword, String displayName, int... varbits)
		{
			this.keyword = keyword;
			this.displayName = displayName;
			this.varbits = varbits;
		}

		private static Potion find(String text)
		{
			for (Potion potion : values())
			{
				if (potion.keyword.equalsIgnoreCase(text))
				{
					return potion;
				}
			}
			return null;
		}
	}

	private final Client client;

	@Inject
	private DivineLabelVariable(Client client)
	{
		this.client = client;
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		Potion potion = Potion.find(matcher.group(1).trim());
		if (potion == null || client.getLocalPlayer() == null)
		{
			return null;
		}

		return String.valueOf(ticks(potion));
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		Potion potion = Potion.find(matcher.group(1).trim());
		if (potion == null || client.getLocalPlayer() == null)
		{
			return null;
		}

		int ticks = ticks(potion);
		Color color = ticks == 0 ? Color.RED : ticks <= YELLOW_THRESHOLD ? Color.YELLOW : Color.GREEN;
		return RichText.colored(color, potion.displayName);
	}

	private int ticks(Potion potion)
	{
		int max = 0;
		for (int varbit : potion.varbits)
		{
			max = Math.max(max, client.getVarbitValue(varbit));
		}
		return max;
	}
}
