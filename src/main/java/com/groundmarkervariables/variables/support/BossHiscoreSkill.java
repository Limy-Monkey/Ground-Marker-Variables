package com.groundmarkervariables.variables.support;

import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;

// Matches a canonical boss name (as BossAliases.resolve() returns) to its HiscoreSkill entry --
// same "no 'The ' prefix, no colon" transform AdvancedLabelEditor's own autocomplete uses.
public final class BossHiscoreSkill
{
	private BossHiscoreSkill()
	{
	}

	public static HiscoreSkill find(String canonicalName)
	{
		for (HiscoreSkill skill : HiscoreSkill.values())
		{
			if (skill.getType() != HiscoreSkillType.BOSS)
			{
				continue;
			}

			String name = skill.getName();
			if (name.regionMatches(true, 0, "The ", 0, 4))
			{
				name = name.substring(4);
			}
			name = name.replace(":", "");

			if (name.equalsIgnoreCase(canonicalName))
			{
				return skill;
			}
		}

		return null;
	}
}
