package com.groundmarkervariables.variables;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Skill;

// {boost_<skill>} -> the player's current boosted (post potion/prayer/curse) level in
// <skill>, e.g. {boost_attack}, {boost_hitpoints}, {boost_mining}. <skill> matches any
// Skill.getName() case-insensitively, same as {lvl_<skill>}. Uses getBoostedSkillLevel(),
// not getRealSkillLevel() — the opposite of SkillLevelLabelVariable.
class BoostedSkillLevelLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{boost_([a-z]+)\\}", Pattern.CASE_INSENSITIVE);

	private final Client client;

	@Inject
	private BoostedSkillLevelLabelVariable(Client client)
	{
		this.client = client;
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolve(Matcher matcher)
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		Skill skill = SkillFinder.find(matcher.group(1));
		return skill == null ? null : String.valueOf(client.getBoostedSkillLevel(skill));
	}
}
