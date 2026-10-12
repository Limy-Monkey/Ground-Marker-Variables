package com.groundmarkervariables.variables.label;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;

// {combatAchievement} (or its {combatAchievements} alias) -> current CA reward tier, 0-6.
public class CombatAchievementsLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{combatAchievements?\\}", Pattern.CASE_INSENSITIVE);

	private static final String[] TIER_NAMES = {"Easy", "Medium", "Hard", "Elite", "Master", "Grandmaster"};
	private static final int[] TIER_THRESHOLDS = {
		VarbitID.CA_THRESHOLD_EASY, VarbitID.CA_THRESHOLD_MEDIUM, VarbitID.CA_THRESHOLD_HARD,
		VarbitID.CA_THRESHOLD_ELITE, VarbitID.CA_THRESHOLD_MASTER, VarbitID.CA_THRESHOLD_GRANDMASTER
	};

	private final Client client;
	private final RichText richText;

	@Inject
	private CombatAchievementsLabelVariable(Client client, RichText richText)
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
		return String.valueOf(tier());
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		int tier = tier();
		return richText.labeled("Combat Achievement tier", tier == 0 ? "None" : TIER_NAMES[tier - 1]);
	}

	// Count of tier thresholds the player's points have reached.
	private int tier()
	{
		int points = client.getVarbitValue(VarbitID.CA_POINTS);
		int tier = 0;
		for (int threshold : TIER_THRESHOLDS)
		{
			if (points < client.getVarbitValue(threshold))
			{
				break;
			}
			tier++;
		}
		return tier;
	}
}
