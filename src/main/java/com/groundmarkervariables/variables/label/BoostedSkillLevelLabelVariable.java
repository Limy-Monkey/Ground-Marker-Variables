package com.groundmarkervariables.variables.label;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;

import com.groundmarkervariables.variables.Args;
import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.PartyMemberVariable;
import com.groundmarkervariables.variables.RichText;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.party.PartyMember;

// {boost_<skill>} -> the player's current boosted (post potion/prayer/curse) level in
// <skill>, e.g. {boost_attack}, {boost_hitpoints}, {boost_mining}. <skill> matches any
// Skill.getName() case-insensitively, same as {lvl_<skill>}. Uses getBoostedSkillLevel(),
// not getRealSkillLevel() — the opposite of SkillLevelLabelVariable.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class BoostedSkillLevelLabelVariable implements LabelVariable, PartyMemberVariable
{
	private static final Args ARGS = Args.of("boost_").joined().main(Args.SKILLS, Args.SKILL_ICON);

	private final Client client;
	private final RichText richText;

	@Override
	public Pattern pattern()
	{
		return ARGS.pattern();
	}

	@Override
	public Args args()
	{
		return ARGS;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		Skill skill = ARGS.skill(matcher);
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

		Skill skill = ARGS.skill(matcher);
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
		Skill skill = ARGS.skill(matcher);
		return skill == null || member.getStatBase(skill) <= 0 ? null : String.valueOf(member.getStatBoost(skill));
	}

	@Override
	public String resolveRich(Matcher matcher, PartyMember member)
	{
		String level = resolvePlain(matcher, member);
		return level == null ? null : richText.highlightValue(level);
	}
}
