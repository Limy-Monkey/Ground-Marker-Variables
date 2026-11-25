package com.groundmarkervariables.variables.label;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.ConditionEvaluator;
import com.groundmarkervariables.variables.support.PartyMemberContext;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.util.Text;

// {party} -> the party size. LabelResolver renders {party <template>} itself via render(), since
// the template may contain nested tokens.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PartyLabelVariable implements LabelVariable {
	private static final Pattern PATTERN = Pattern.compile("\\{party(?:\\s+([^{}?:<>=!]+?))?\\}", Pattern.CASE_INSENSITIVE);
	private static final Pattern COUNT_START = Pattern.compile("\\{partyCount\\s+", Pattern.CASE_INSENSITIVE);
	// A keyword may follow a "\t" table cell break, whose t would otherwise hide the word boundary.
	private static final String WORD_START = "(?:(?<=\\\\t)|\\b)";
	private static final Pattern NUM_WORD = Pattern.compile(WORD_START + "num\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern NAME_WORD = Pattern.compile(WORD_START + "name\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern HP_WORD = Pattern.compile(WORD_START + "hp\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern PRAYER_WORD = Pattern.compile(WORD_START + "prayer\\b", Pattern.CASE_INSENSITIVE);
	private static final String UNKNOWN = "?";
	private static final String UNKNOWN_NAME = "<unknown>";
	private static final String DEFAULT_TEMPLATE = "num. name | hp | prayer";

	// Used to sort names by raid party order
	private static final class RaidParty {
		private final int statusVarbit;
		private final int lobbyNames;
		private final int[] nameVarcs;

		private RaidParty(int statusVarbit, int lobbyNames, int... nameVarcs) {
			this.statusVarbit = statusVarbit;
			this.lobbyNames = lobbyNames;
			this.nameVarcs = nameVarcs;
		}
	}

	private static final List<RaidParty> RAID_PARTIES = List.of(
		new RaidParty(VarbitID.TOB_CLIENT_PARTYSTATUS, InterfaceID.TobHud.NAMES,
			VarClientID.TOB_CLIENT_NAME0, VarClientID.TOB_CLIENT_NAME1, VarClientID.TOB_CLIENT_NAME2,
			VarClientID.TOB_CLIENT_NAME3, VarClientID.TOB_CLIENT_NAME4),
		new RaidParty(VarbitID.TOA_CLIENT_PARTYSTATUS, InterfaceID.ToaLobby.NAMES,
			VarClientID.TOA_CLIENT_NAME0, VarClientID.TOA_CLIENT_NAME1, VarClientID.TOA_CLIENT_NAME2, VarClientID.TOA_CLIENT_NAME3,
			VarClientID.TOA_CLIENT_NAME4, VarClientID.TOA_CLIENT_NAME5, VarClientID.TOA_CLIENT_NAME6, VarClientID.TOA_CLIENT_NAME7),
		new RaidParty(VarbitID.TFA_CLIENT_PARTYSTATUS, InterfaceID.TfaLobby.NAMES,
			VarClientID.TFA_CLIENT_NAME0, VarClientID.TFA_CLIENT_NAME1, VarClientID.TFA_CLIENT_NAME2, VarClientID.TFA_CLIENT_NAME3,
			VarClientID.TFA_CLIENT_NAME4, VarClientID.TFA_CLIENT_NAME5, VarClientID.TFA_CLIENT_NAME6, VarClientID.TFA_CLIENT_NAME7));

	private final Client client;
	private final PartyService partyService;
	private final RichText richText;
	private final PartyMemberContext memberContext;

	@Override
	public Pattern pattern() {
		return PATTERN;
	}

	@Override
	public String resolvePlain(Matcher matcher) {
		return String.valueOf(size());
	}

	@Override
	public String resolveRich(Matcher matcher) {
		return richText.highlightValue(String.valueOf(size()));
	}

	public int size() {
		return members().size();
	}

	// Skips RuneLite's name for a member until theirs arrives, e.g. just after rejoining. Of members
	// sharing a name, only the newest (last joined) is kept.
	private List<PartyMember> members() {
		Map<String, PartyMember> byName = new LinkedHashMap<>();
		for (PartyMember member : partyService.getMembers()) {
			String name = member.getDisplayName();
			if (name == null || UNKNOWN_NAME.equals(name)) {
				continue;
			}

			String key = standardize(name);
			byName.remove(key);
			byName.put(key, member);
		}
		return new ArrayList<>(byName.values());
	}

	private static String standardize(String name) {
		return Text.standardize(name.replace('_', ' '));
	}

	// Sort party members by raid party order (ToB / ToA / TFA)
	private void sortByRaidSlot(List<PartyMember> members) {
		List<String> raidNames = raidPartyNames();
		if (raidNames.isEmpty()) {
			return;
		}

		members.sort(Comparator.comparingInt(member -> {
			int slot = raidNames.indexOf(standardize(member.getDisplayName()));
			return slot < 0 ? Integer.MAX_VALUE : slot;
		}));
	}

	// Names by slot, or empty when not in a raid party.
	private List<String> raidPartyNames() {
		for (RaidParty raid : RAID_PARTIES) {
			int status = client.getVarbitValue(raid.statusVarbit);
			if (status == 0) {
				continue;
			}

			// 1: in the lobby, so the raid's own name varcs aren't set yet.
			if (status == 1) {
				return lobbyNames(raid.lobbyNames);
			}

			List<String> names = new ArrayList<>();
			for (int varc : raid.nameVarcs) {
				String name = client.getVarcStrValue(varc);
				names.add(name == null ? "" : standardize(Text.removeTags(name)));
			}
			return names;
		}
		return List.of();
	}

	// One name per <br> line of the lobby's party list.
	private List<String> lobbyNames(int widgetId) {
		Widget widget = client.getWidget(widgetId);
		if (widget == null || widget.getText() == null) {
			return List.of();
		}

		List<String> names = new ArrayList<>();
		for (String line : widget.getText().split("<br>")) {
			names.add(standardize(Text.removeTags(line)));
		}
		return names;
	}

	// The template once per member, one per line: keywords substituted outside nested tokens, then
	// resolveRow run with that member as the context.
	public String render(String template, UnaryOperator<String> resolveRow) {
		if (client.getLocalPlayer() == null) {
			return null;
		}

		template = template.isEmpty() ? DEFAULT_TEMPLATE : template;
		PartyMember local = partyService.getLocalMember();
		List<PartyMember> members = members();
		sortByRaidSlot(members);
		StringBuilder result = new StringBuilder("{align=left}");

		int num = 1;
		boolean anyRow = false;
		for (PartyMember member : members) {
			boolean isLocal = local != null && member.getMemberId() == local.getMemberId();
			int rowNum = num++;
			String row = outsideTokens(template, text -> substituteKeywords(text, rowNum, member, isLocal));

			memberContext.enterRow(isLocal ? null : member, rowNum);
			try {
				row = resolveRow.apply(row);
			} finally {
				memberContext.exitRow();
			}
			// Rows that resolve to nothing are skipped, e.g. {hasFreeze ? name} for members without.
			if (row == null || row.trim().isEmpty()) {
				continue;
			}
			result.append(anyRow ? "\\n" : "").append(row);
			anyRow = true;
		}

		if (!anyRow) {
			return "";
		}
		result.append("\\n{/align}");
		return result.toString();
	}

	// {partyCount <template>}: each {<expr>} in the template summed over the party.
	public String resolveCounts(String text, UnaryOperator<String> resolvePlain) {
		Matcher start = COUNT_START.matcher(text);
		StringBuilder out = new StringBuilder();
		int i = 0;
		while (start.find(i)) {
			int close = ConditionalVariable.matchingBrace(text, start.start());
			int firstToken = text.indexOf('{', start.end());
			if (close < 0 || firstToken < 0 || firstToken > close) {
				out.append(text, i, start.end());
				i = start.end();
				continue;
			}

			String value;
			try {
				value = renderCount(text.substring(start.end(), close), resolvePlain);
			} catch (RuntimeException e) {
				value = "ERROR";
			}

			out.append(text, i, start.start()).append(value);
			i = close + 1;
		}
		return out.append(text.substring(i)).toString();
	}

	private String renderCount(String template, UnaryOperator<String> resolvePlain) {
		StringBuilder out = new StringBuilder();
		int i = 0;
		int open;
		while ((open = template.indexOf('{', i)) >= 0) {
			int close = ConditionalVariable.matchingBrace(template, open);
			if (close < 0) {
				break;
			}

			out.append(template, i, open).append(count(template.substring(open, close + 1), resolvePlain));
			i = close + 1;
		}
		return out.append(template.substring(i)).toString();
	}

	// A number adds itself, any other true value 1. Unavailable inside a {party} row or {partyCount}.
	private String count(String expression, UnaryOperator<String> resolvePlain) {
		if (memberContext.rowNumber() > 0) {
			return memberContext.unavailable(false);
		}

		double total = 0;
		for (String value : resolveEach(expression, resolvePlain)) {
			String plain = value == null ? "" : Text.removeTags(value).trim();
			Double number = ConditionEvaluator.tryParseNumber(plain);
			if (number != null) {
				total += Double.isFinite(number) ? number : 0;
			} else if (BooleanVariable.truthy(plain) && !plain.equals(memberContext.unavailable(false))) {
				total++;
			}
		}
		return MathVariable.formatPlain(total);
	}

	// text resolved once per member, in row order, with that member as the context.
	private List<String> resolveEach(String text, UnaryOperator<String> resolve) {
		PartyMember local = partyService.getLocalMember();
		List<PartyMember> members = members();
		sortByRaidSlot(members);
		List<String> values = new ArrayList<>();

		int num = 1;
		for (PartyMember member : members) {
			boolean isLocal = local != null && member.getMemberId() == local.getMemberId();
			memberContext.enterRow(isLocal ? null : member, num++);
			try {
				values.add(resolve.apply(text));
			} finally {
				memberContext.exitRow();
			}
		}
		return values;
	}

	private String substituteKeywords(String text, int num, PartyMember member, boolean isLocal) {
		String hp = isLocal ? localStat(Skill.HITPOINTS) : memberStat(member, Skill.HITPOINTS);
		String prayer = isLocal ? localStat(Skill.PRAYER) : memberStat(member, Skill.PRAYER);

		text = NUM_WORD.matcher(text).replaceAll(String.valueOf(num));
		text = NAME_WORD.matcher(text).replaceAll(Matcher.quoteReplacement(richText.highlightValue(member.getDisplayName())));
		text = HP_WORD.matcher(text).replaceAll(Matcher.quoteReplacement(RichText.colored(Color.RED, hp)));
		text = PRAYER_WORD.matcher(text).replaceAll(Matcher.quoteReplacement(RichText.colored(Color.CYAN, prayer)));
		return text;
	}

	private String localStat(Skill skill) {
		return client.getBoostedSkillLevel(skill) + "/" + client.getRealSkillLevel(skill);
	}

	// A base of 0 means they haven't sent it yet.
	private static String memberStat(PartyMember member, Skill skill) {
		return member.getStatBase(skill) > 0 ? member.getStatBoost(skill) + "/" + member.getStatBase(skill) : UNKNOWN;
	}

	// Applies replace to the text outside {...} tokens only.
	private static String outsideTokens(String template, UnaryOperator<String> replace) {
		StringBuilder out = new StringBuilder();
		int start = 0;
		int depth = 0;
		for (int i = 0; i < template.length(); i++) {
			char c = template.charAt(i);
			if (c == '{' && depth++ == 0) {
				out.append(replace.apply(template.substring(start, i)));
				start = i;
			} else if (c == '}' && depth > 0 && --depth == 0) {
				out.append(template, start, i + 1);
				start = i + 1;
			}
		}

		String rest = template.substring(start);
		return out.append(depth == 0 ? replace.apply(rest) : rest).toString();
	}
}
