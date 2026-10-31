package com.groundmarkervariables.variables.label;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.PartyMemberVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.SkillFinder;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.party.PartyMember;

// {boost_<skill>} -> the player's current boosted (post potion/prayer/curse) level in
// <skill>, e.g. {boost_attack}, {boost_hitpoints}, {boost_mining}. <skill> matches any
// Skill.getName() case-insensitively, same as {lvl_<skill>}. Uses getBoostedSkillLevel(),
// not getRealSkillLevel() — the opposite of SkillLevelLabelVariable.
public class BoostedSkillLevelLabelVariable implements LabelVariable, PartyMemberVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{boost_([a-z]+)\\}", Pattern.CASE_INSENSITIVE);

	private final Client client;
	private final RichText richText;

	@Inject
	private BoostedSkillLevelLabelVariable(Client client, RichText richText)
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

		Skill skill = SkillFinder.find(matcher.group(1));
		return skill == null ? null : String.valueOf(client.getBoostedSkillLevel(skill));
	}

	// Needs the real level too, so this doesn't delegate to resolvePlain.
	@Override
	public String resolveRich(Matcher matcher)
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		Skill skill = SkillFinder.find(matcher.group(1));
		if (skill == null)
		{
			return null;
		}

		int boosted = client.getBoostedSkillLevel(skill);
		int real = client.getRealSkillLevel(skill);
		return skill.getName() + ": " + richText.highlightValue(String.valueOf(boosted)) + " / " + real;
	}

	// A base level of 0 means the member hasn't sent that skill yet.
	@Override
	public String resolvePlain(Matcher matcher, PartyMember member)
	{
		Skill skill = SkillFinder.find(matcher.group(1));
		return skill == null || member.getStatBase(skill) <= 0 ? null : String.valueOf(member.getStatBoost(skill));
	}

	@Override
	public String resolveRich(Matcher matcher, PartyMember member)
	{
		String level = resolvePlain(matcher, member);
		return level == null ? null : richText.highlightValue(level);
	}
}
