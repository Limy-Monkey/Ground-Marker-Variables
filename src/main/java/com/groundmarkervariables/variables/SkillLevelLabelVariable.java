package com.groundmarkervariables.variables;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Skill;

// {lvl_<skill>} -> the player's real (unboosted) level in <skill>, e.g. {lvl_attack},
// {lvl_hitpoints}, {lvl_mining}. <skill> matches any Skill.getName() case-insensitively, so
// {lvl_Runecraft} and {lvl_RUNECRAFT} both work same as {lvl_runecraft}. Uses
// getRealSkillLevel(), not getBoostedSkillLevel() — potion/prayer boosts don't affect it.
class SkillLevelLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{lvl_([a-z]+)\\}", Pattern.CASE_INSENSITIVE);

	private final Client client;

	@Inject
	private SkillLevelLabelVariable(Client client)
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
		return skill == null ? null : String.valueOf(client.getRealSkillLevel(skill));
	}
}
