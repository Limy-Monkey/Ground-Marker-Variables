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
//
// {lvl_combat} / {lvl_total} are special-cased here only -- not real Skill entries, and not
// supported by {boost_}/{xpRate_}.
class SkillLevelLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{lvl_([a-z]+)\\}", Pattern.CASE_INSENSITIVE);

	private final Client client;
	private final RichText richText;

	@Inject
	private SkillLevelLabelVariable(Client client, RichText richText)
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
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		Integer level = level(matcher.group(1));
		return level == null ? null : String.valueOf(level);
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		String level = resolvePlain(matcher);
		if (level == null)
		{
			return null;
		}

		return richText.labeled(label(matcher.group(1)), level);
	}

	private Integer level(String name)
	{
		if ("combat".equalsIgnoreCase(name))
		{
			return client.getLocalPlayer().getCombatLevel();
		}

		if ("total".equalsIgnoreCase(name))
		{
			return client.getTotalLevel();
		}

		Skill skill = SkillFinder.find(name);
		return skill == null ? null : client.getRealSkillLevel(skill);
	}

	private static String label(String name)
	{
		if ("combat".equalsIgnoreCase(name))
		{
			return "Combat";
		}

		if ("total".equalsIgnoreCase(name))
		{
			return "Total";
		}

		return SkillFinder.find(name).getName();
	}
}
