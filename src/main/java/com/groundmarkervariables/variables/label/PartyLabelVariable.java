package com.groundmarkervariables.variables.label;

import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;

// {party <template>} -> Plain is always the party size. Rich is always "Party (N)" followed
// by one row per member, each the template with num/name/hp/prayer substituted for that
// member -- e.g. {party num. name} renders "1. Alice\n2. Bob".
public class PartyLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{party(?:\\s+([^{}?:<>=!]+?))?\\}", Pattern.CASE_INSENSITIVE);
	private static final Pattern NUM_WORD = Pattern.compile("\\bnum\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern NAME_WORD = Pattern.compile("\\bname\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern HP_WORD = Pattern.compile("\\bhp\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern PRAYER_WORD = Pattern.compile("\\bprayer\\b", Pattern.CASE_INSENSITIVE);
	private static final String UNKNOWN = "?";
	private static final String DEFAULT_TEMPLATE = "num. name | hp | prayer";

	private final Client client;
	private final PartyService partyService;
	private final RichText richText;

	@Inject
	private PartyLabelVariable(Client client, PartyService partyService, RichText richText)
	{
		this.client = client;
		this.partyService = partyService;
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
		return String.valueOf(partyService.getMembers().size());
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		String template = matcher.group(1) == null ? DEFAULT_TEMPLATE : matcher.group(1).trim();
		if (template.isEmpty() || client.getLocalPlayer() == null)
		{
			return null;
		}

		PartyMember local = partyService.getLocalMember();
		StringBuilder result = new StringBuilder("{align=left}Party (").append(partyService.getMembers().size()).append(")");

		int row = 1;
		for (PartyMember member : partyService.getMembers())
		{
			boolean isLocal = local != null && member.getMemberId() == local.getMemberId();
			result.append("\\n").append(renderRow(template, row++, member, isLocal));
		}

		result.append("\\n{/align}");
		return result.toString();
	}

	private String renderRow(String template, int num, PartyMember member, boolean isLocal)
	{
		String hp = UNKNOWN;
		String prayer = UNKNOWN;

		if (isLocal)
		{
			hp = client.getBoostedSkillLevel(Skill.HITPOINTS) + "/" + client.getRealSkillLevel(Skill.HITPOINTS);
			prayer = client.getBoostedSkillLevel(Skill.PRAYER) + "/" + client.getRealSkillLevel(Skill.PRAYER);
		}
		else
		{
			// A max of 0 means they haven't sent it yet.
			if (member.getStatBase(Skill.HITPOINTS) > 0)
			{
				hp = member.getStatBoost(Skill.HITPOINTS) + "/" + member.getStatBase(Skill.HITPOINTS);
			}
			if (member.getStatBase(Skill.PRAYER) > 0)
			{
				prayer = member.getStatBoost(Skill.PRAYER) + "/" + member.getStatBase(Skill.PRAYER);
			}
		}

		String row = template;
		row = NUM_WORD.matcher(row).replaceAll(String.valueOf(num));
		row = NAME_WORD.matcher(row).replaceAll(Matcher.quoteReplacement(richText.highlightValue(member.getDisplayName())));
		row = HP_WORD.matcher(row).replaceAll(Matcher.quoteReplacement(RichText.colored(Color.RED, hp)));
		row = PRAYER_WORD.matcher(row).replaceAll(Matcher.quoteReplacement(RichText.colored(Color.CYAN, prayer)));
		return row;
	}
}
