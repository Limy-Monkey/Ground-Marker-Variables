package com.groundmarkervariables.variables;

import net.runelite.api.Skill;

// Shared "<skill> text -> Skill enum" lookup for any variable keyed by skill name (e.g.
// {lvl_<skill>}, {boost_<skill>}). Matches Skill.getName() case-insensitively.
final class SkillFinder
{
	private SkillFinder()
	{
	}

	static Skill find(String name)
	{
		for (Skill skill : Skill.values())
		{
			if (skill.getName().equalsIgnoreCase(name))
			{
				return skill;
			}
		}

		return null;
	}
}
