package com.groundmarkervariables.variables.label;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.party.PartyDataSharing;
import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.PartyMemberContext;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.util.Text;

// {party} -> the party size. LabelResolver renders {party <template>} itself via render(), since
// the template may contain nested tokens.
public class PartyLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{party(?:\\s+([^{}?:<>=!]+?))?\\}", Pattern.CASE_INSENSITIVE);
	private static final Pattern NUM_WORD = Pattern.compile("\\bnum\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern NAME_WORD = Pattern.compile("\\bname\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern HP_WORD = Pattern.compile("\\bhp\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern PRAYER_WORD = Pattern.compile("\\bprayer\\b", Pattern.CASE_INSENSITIVE);
	private static final String UNKNOWN = "?";
	private static final String UNKNOWN_NAME = "<unknown>";
	private static final String DEFAULT_TEMPLATE = "num. name | hp | prayer";

	private final Client client;
	private final PartyService partyService;
	private final RichText richText;
	private final PartyMemberContext memberContext;
	private final PartyDataSharing partyDataSharing;

	@Inject
	private PartyLabelVariable(Client client, PartyService partyService, RichText richText, PartyMemberContext memberContext,
							   PartyDataSharing partyDataSharing)
	{
		this.partyDataSharing = partyDataSharing;
		this.client = client;
		this.partyService = partyService;
		this.richText = richText;
		this.memberContext = memberContext;
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		return String.valueOf(size());
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		return richText.highlightValue(String.valueOf(size()));
	}

	public int size()
	{
		return members().size();
	}

	// Skips RuneLite's name for a member until theirs arrives, e.g. just after rejoining. Of members
	// sharing a name, only the newest (last joined) is kept.
	private List<PartyMember> members()
	{
		Map<String, PartyMember> byName = new LinkedHashMap<>();
		for (PartyMember member : partyService.getMembers())
		{
			String name = member.getDisplayName();
			if (name == null || UNKNOWN_NAME.equals(name))
			{
				continue;
			}

			String key = Text.standardize(name.replace('_', ' '));
			byName.remove(key);
			byName.put(key, member);
		}
		return new ArrayList<>(byName.values());
	}

	// The template once per member, one per line: keywords substituted outside nested tokens, then
	// resolveRow run with that member as the context.
	public String render(String template, UnaryOperator<String> resolveRow)
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		template = template.isEmpty() ? DEFAULT_TEMPLATE : template;
		partyDataSharing.addNeeds(template);
		PartyMember local = partyService.getLocalMember();
		List<PartyMember> members = members();
		StringBuilder result = new StringBuilder("{align=left}");

		int num = 1;
		for (PartyMember member : members)
		{
			boolean isLocal = local != null && member.getMemberId() == local.getMemberId();
			int rowNum = num++;
			String row = outsideTokens(template, text -> substituteKeywords(text, rowNum, member, isLocal));

			memberContext.enterRow(isLocal ? null : member);
			try
			{
				row = resolveRow.apply(row);
			}
			finally
			{
				memberContext.exitRow();
			}
			result.append(rowNum > 1 ? "\\n" : "").append(row);
		}

		result.append("\\n{/align}");
		return result.toString();
	}

	private String substituteKeywords(String text, int num, PartyMember member, boolean isLocal)
	{
		String hp = isLocal ? localStat(Skill.HITPOINTS) : memberStat(member, Skill.HITPOINTS);
		String prayer = isLocal ? localStat(Skill.PRAYER) : memberStat(member, Skill.PRAYER);

		text = NUM_WORD.matcher(text).replaceAll(String.valueOf(num));
		text = NAME_WORD.matcher(text).replaceAll(Matcher.quoteReplacement(richText.highlightValue(member.getDisplayName())));
		text = HP_WORD.matcher(text).replaceAll(Matcher.quoteReplacement(RichText.colored(Color.RED, hp)));
		text = PRAYER_WORD.matcher(text).replaceAll(Matcher.quoteReplacement(RichText.colored(Color.CYAN, prayer)));
		return text;
	}

	private String localStat(Skill skill)
	{
		return client.getBoostedSkillLevel(skill) + "/" + client.getRealSkillLevel(skill);
	}

	// A base of 0 means they haven't sent it yet.
	private static String memberStat(PartyMember member, Skill skill)
	{
		return member.getStatBase(skill) > 0 ? member.getStatBoost(skill) + "/" + member.getStatBase(skill) : UNKNOWN;
	}

	// Applies replace to the text outside {...} tokens only.
	private static String outsideTokens(String template, UnaryOperator<String> replace)
	{
		StringBuilder out = new StringBuilder();
		int start = 0;
		int depth = 0;
		for (int i = 0; i < template.length(); i++)
		{
			char c = template.charAt(i);
			if (c == '{' && depth++ == 0)
			{
				out.append(replace.apply(template.substring(start, i)));
				start = i;
			}
			else if (c == '}' && depth > 0 && --depth == 0)
			{
				out.append(template, start, i + 1);
				start = i + 1;
			}
		}

		String rest = template.substring(start);
		return out.append(depth == 0 ? replace.apply(rest) : rest).toString();
	}
}
