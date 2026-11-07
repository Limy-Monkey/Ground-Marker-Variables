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
// animated dots, since the two cases can't be told apart from its API. {xp_<level>} is the
// experience needed for <level>, 1-126, e.g. {xp_99}.
public class XpLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{xp_([a-z]+|\\d+)\\}", Pattern.CASE_INSENSITIVE);

	// oldschool.runescape.wiki/w/Experience, indexed by level - 1.
	private static final int[] LEVEL_XP = {
		0, 83, 174, 276, 388, 512, 650, 801, 969, 1154,
		1358, 1584, 1833, 2107, 2411, 2746, 3115, 3523, 3973, 4470,
		5018, 5624, 6291, 7028, 7842, 8740, 9730, 10824, 12031, 13363,
		14833, 16456, 18247, 20224, 22406, 24815, 27473, 30408, 33648, 37224,
		41171, 45529, 50339, 55649, 61512, 67983, 75127, 83014, 91721, 101333,
		111945, 123660, 136594, 150872, 166636, 184040, 203254, 224466, 247886, 273742,
		302288, 333804, 368599, 407015, 449428, 496254, 547953, 605032, 668051, 737627,
		814445, 899257, 992895, 1096278, 1210421, 1336443, 1475581, 1629200, 1798808, 1986068,
		2192818, 2421087, 2673114, 2951373, 3258594, 3597792, 3972294, 4385776, 4842295, 5346332,
		5902831, 6517253, 7195629, 7944614, 8771558, 9684577, 10692629, 11805606, 13034431, 14391160,
		15889109, 17542976, 19368992, 21385073, 23611006, 26068632, 28782069, 31777943, 35085654, 38737661,
		42769801, 47221641, 52136869, 57563718, 63555443, 70170840, 77474828, 85539082, 94442737, 104273167,
		115126838, 127110260, 140341028, 154948977, 171077457, 188884740
	};

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
		if (isLevel(matcher))
		{
			Integer xp = levelXp(matcher);
			return xp == null ? null : String.valueOf(xp);
		}

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
		if (isLevel(matcher))
		{
			Integer xp = levelXp(matcher);
			return xp == null ? null : richText.highlightValue(String.format("%,d", xp));
		}

		Skill skill = SkillFinder.find(matcher.group(1));
		if (skill == null || client.getLocalPlayer() == null)
		{
			return null;
		}

		Long rank = rankLookup.rank(HiscoreSkill.valueOf(skill.name()));
		String rankText = rank == null ? richText.animatedDots(client.getTickCount()) : richText.highlightValue(String.format("%,d", rank));

		return skill.getName() + " Level: " + richText.highlightValue(String.valueOf(client.getRealSkillLevel(skill)))
			+ " | Experience: " + richText.highlightValue(String.format("%,d", client.getSkillExperience(skill)))
			+ " | Rank: " + rankText;
	}

	private static boolean isLevel(Matcher matcher)
	{
		return Character.isDigit(matcher.group(1).charAt(0));
	}

	// Null outside 1-126.
	private static Integer levelXp(Matcher matcher)
	{
		String digits = matcher.group(1);
		if (digits.length() > 3)
		{
			return null;
		}

		int level = Integer.parseInt(digits);
		return level >= 1 && level <= LEVEL_XP.length ? LEVEL_XP[level - 1] : null;
	}
}
