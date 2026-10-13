package com.groundmarkervariables.variables.label;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.HiscoreRankLookup;
import com.groundmarkervariables.variables.support.SkillFinder;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.hiscore.HiscoreSkill;

// {xp_<skill>} -> the player's exact experience in <skill>, e.g. {xp_agility}. Rich Text adds
// the skill's level and hiscore rank, same data as the "!lvl" chat command. Rank comes from
// HiscoreRankLookup -- while it's unavailable (still loading, or genuinely unranked), shown as
// animated dots, since the two cases can't be told apart from its API.
public class XpLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{xp_([a-z]+)\\}", Pattern.CASE_INSENSITIVE);
	private static final int DOT_COUNT = 3;

	private final Client client;
	private final RichText richText;
	private final HiscoreRankLookup rankLookup;

	@Inject
	private XpLabelVariable(Client client, RichText richText, HiscoreRankLookup rankLookup)
	{
		this.client = client;
		this.richText = richText;
		this.rankLookup = rankLookup;
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		Skill skill = SkillFinder.find(matcher.group(1));
		if (skill == null || client.getLocalPlayer() == null)
		{
			return null;
		}

		return String.valueOf(client.getSkillExperience(skill));
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		Skill skill = SkillFinder.find(matcher.group(1));
		if (skill == null || client.getLocalPlayer() == null)
		{
			return null;
		}

		Long rank = rankLookup.rank(HiscoreSkill.valueOf(skill.name()));
		String rankText = rank == null ? animatedDots() : richText.highlightValue(String.format("%,d", rank));

		return skill.getName() + " Level: " + richText.highlightValue(String.valueOf(client.getRealSkillLevel(skill)))
			+ " | Experience: " + richText.highlightValue(String.format("%,d", client.getSkillExperience(skill)))
			+ " | Rank: " + rankText;
	}

	// One dot highlighted, cycling each game tick: "<col>.</col>..", ".<col>.</col>.", ...
	private String animatedDots()
	{
		int active = client.getTickCount() % DOT_COUNT;
		StringBuilder dots = new StringBuilder();
		for (int i = 0; i < DOT_COUNT; i++)
		{
			dots.append(i == active ? richText.highlightValue(".") : ".");
		}
		return dots.toString();
	}
}
