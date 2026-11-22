package com.groundmarkervariables.variables.label;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;

import com.groundmarkervariables.variables.Args;
import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.PartyMemberVariable;
import com.groundmarkervariables.variables.RichText;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.party.PartyMember;

// {lvl_<skill>} -> the player's real (unboosted) level in <skill>, e.g. {lvl_attack},
// {lvl_hitpoints}, {lvl_mining}. <skill> matches any Skill.getName() case-insensitively, so
// {lvl_Runecraft} and {lvl_RUNECRAFT} both work same as {lvl_runecraft}. Uses
// getRealSkillLevel(), not getBoostedSkillLevel() — potion/prayer boosts don't affect it.
//
// {lvl_combat} / {lvl_total} are special-cased here only -- not real Skill entries, and not
// supported by {boost_}/{xpRate_}.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SkillLevelLabelVariable implements LabelVariable, PartyMemberVariable
{
	// Suggesting every skill, then combat and total.
	private static final Args ARGS = Args.of("lvl_").joined()
		.main(() -> Stream.concat(Args.SKILLS.get().stream(), Stream.of("combat", "total")).collect(Collectors.toList()), Args.SKILL_ICON);

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

		Integer level = level(ARGS.main(matcher));
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

		return richText.labeled(label(ARGS.main(matcher)), level);
	}

	// Real skills only: combat and total aren't sent. A base level of 0 means not sent yet.
	@Override
	public String resolvePlain(Matcher matcher, PartyMember member)
	{
		Skill skill = ARGS.skill(matcher);
		return skill == null || member.getStatBase(skill) <= 0 ? null : String.valueOf(member.getStatBase(skill));
	}

	@Override
	public String resolveRich(Matcher matcher, PartyMember member)
	{
		String level = resolvePlain(matcher, member);
		return level == null ? null : richText.highlightValue(level);
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

		Skill skill = Args.skill(name);
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

		return Args.skill(name).getName();
	}
}
