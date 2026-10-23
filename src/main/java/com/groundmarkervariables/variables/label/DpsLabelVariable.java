package com.groundmarkervariables.variables.label;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.DpsTracker;
import com.groundmarkervariables.variables.support.NamedColors;
import net.runelite.api.Client;
import net.runelite.client.util.QuantityFormatter;

// {dps <template>} -> template keywords replaced by values, like {party}. {dps} is {dps avgDps shareDps}.
public class DpsLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{dps(?:\\s+([^{}?<>=!]+?))?\\}", Pattern.CASE_INSENSITIVE);
	// Whole words only, where ':' counts as part of a word, so "acc:" and "accuracy" stay text.
	private static final Pattern KEYWORD = Pattern.compile(
		"(?<![\\w:])(avgDps|shareDps|splitDps|totalDamage|takenDamage|acc|thrallDps|recoilDps|theoryDps|pureTheoryDps|numAttacks|numMisses|numHits|timeSpent|(\\d+)s)(?![\\w:])", Pattern.CASE_INSENSITIVE);
	private static final String DEFAULT_TEMPLATE = "avgDps shareDps";
	private static final String DEFAULT_WEAPON_TEMPLATE = "theoryDps (splitDps | acc)";
	private static final Pattern REPORT_WORD = Pattern.compile("(?<![\\w:])report(?![\\w:])", Pattern.CASE_INSENSITIVE);
	private static final String DEFAULT_GROUP_TEMPLATE = "totalDamage (shareDps | theoryDps)";
	private static final String DEFAULT_BOSS_TEMPLATE = "timeSpent (theoryDps | acc)";
	private static final Pattern GROUP_REPORT_WORD = Pattern.compile("(?<![\\w:])groupReport(?![\\w:])", Pattern.CASE_INSENSITIVE);
	private static final Pattern BOSS_REPORT_WORD = Pattern.compile("(?<![\\w:])bossReport(?![\\w:])", Pattern.CASE_INSENSITIVE);
	// Anywhere after a report word: adds a line naming the row template's values.
	private static final Pattern LEGEND_WORD = Pattern.compile("(?<![\\w:])legend(?![\\w:])", Pattern.CASE_INSENSITIVE);
	private static final String GRAY = NamedColors.HEX_BY_NAME.get("gray");
	private static final String PAUSED = " <col=" + GRAY + ">(paused)</col>";

	private final Client client;
	private final RichText richText;
	private final DpsTracker tracker;

	@Inject
	private DpsLabelVariable(Client client, RichText richText, DpsTracker tracker)
	{
		this.client = client;
		this.richText = richText;
		this.tracker = tracker;
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		return render(matcher, value -> value, false);
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		return render(matcher, richText::highlightValue, true);
	}

	// Rich adds the "DPS: " label, and "(paused)" at the end of the first line while paused.
	private String render(Matcher matcher, Function<String, String> format, boolean rich)
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		DpsTracker.Stats stats = tracker.markActive();
		String template = matcher.group(1) == null ? DEFAULT_TEMPLATE : matcher.group(1).trim();
		String paused = rich && stats.isPaused() ? PAUSED : "";
		Matcher bossReport = BOSS_REPORT_WORD.matcher(template);
		Matcher groupReport = GROUP_REPORT_WORD.matcher(template);
		Matcher npcWord = bossReport.find() ? bossReport : groupReport.find() ? groupReport : null;
		if (npcWord != null)
		{
			String topLine = template.substring(0, npcWord.start()).trim();
			String npcLine = template.substring(npcWord.end());
			boolean boss = npcWord == bossReport;
			String rowLine = withoutLegend(npcLine).isEmpty() ? (boss ? DEFAULT_BOSS_TEMPLATE : DEFAULT_GROUP_TEMPLATE) : withoutLegend(npcLine);
			String legend = LEGEND_WORD.matcher(npcLine).find() ? legendLine(boss ? "Phase" : "NPC", rowLine, format, rich) : null;
			return groupReport(topLine.isEmpty() ? DEFAULT_TEMPLATE : topLine, rowLine, legend, stats.bossStats(), stats, format, paused);
		}

		Matcher report = REPORT_WORD.matcher(template);
		if (!report.find())
		{
			String text = renderTemplate(template, keyword -> value(keyword, stats), format, true);
			return rich ? "DPS: " + text + paused : text;
		}

		String topLine = template.substring(0, report.start()).trim();
		String afterReport = template.substring(report.end());
		String weaponLine = withoutLegend(afterReport).isEmpty() ? DEFAULT_WEAPON_TEMPLATE : withoutLegend(afterReport);
		String legend = LEGEND_WORD.matcher(afterReport).find() ? legendLine("Weapon", weaponLine, format, rich) : null;
		return report(topLine.isEmpty() ? DEFAULT_TEMPLATE : topLine, weaponLine, legend, stats, format, paused);
	}

	private static String withoutLegend(String line)
	{
		return LEGEND_WORD.matcher(line).replaceAll("").trim();
	}

	// e.g. "weapon: Dps (SplitOfYourDamage | acc)". Rich: gray, with the names colored like the rows' values.
	private static String legendLine(String rows, String rowLine, Function<String, String> format, boolean rich)
	{
		String gray = "<col=" + GRAY + ">";
		Function<String, String> name = rich ? value -> format.apply(value) + gray : value -> value;
		String text = rows + ": " + renderTemplate(rowLine, DpsLabelVariable::shortName, name, false);
		return rich ? gray + text + "</col>" : text;
	}

	private static String shortName(Matcher keyword)
	{
		if (keyword.group(2) != null)
		{
			return keyword.group();
		}

		switch (keyword.group(1).toLowerCase(Locale.ROOT))
		{
			case "avgdps":
				return "AvgDps";
			case "sharedps":
				return "%OfGroup";
			case "splitdps":
				return "%OfDmg";
			case "totaldamage":
				return "Dmg";
			case "theorydps":
				return "DPS";
			case "puretheorydps":
				return "PureDps";
			case "numattacks":
				return "#Attacks";
			case "nummisses":
				return "#Misses";
			case "numhits":
				return "#Hits";
			case "timespent":
				return "Time";
			case "acc":
				return "Accuracy";
			default:
				return keyword.group();
		}
	}

	// {dps <top> report <weapon>}: the top line, then <weapon> for each weapon used (by split), then
	// thralls, recoil and vengeance once they've done damage. legend is a line under the top one, or null.
	private String report(String topLine, String weaponLine, String legend, DpsTracker.Stats stats, Function<String, String> format, String paused)
	{
		StringBuilder report = new StringBuilder("{align=left}DPS: ")
			.append(renderTemplate(topLine, keyword -> value(keyword, stats), format, true)).append(paused);
		if (legend != null)
		{
			report.append("\\n").append(legend);
		}
		List<Map.Entry<Integer, DpsTracker.Stats.WeaponStats>> weapons = new ArrayList<>(stats.weaponStats().entrySet());
		weapons.sort(Comparator.comparingDouble((Map.Entry<Integer, DpsTracker.Stats.WeaponStats> entry) -> entry.getValue().split()).reversed());
		for (Map.Entry<Integer, DpsTracker.Stats.WeaponStats> entry : weapons)
		{
			report.append("\\n").append(weaponName(entry.getKey()))
				.append(": ").append(renderTemplate(weaponLine, keyword -> weaponValue(keyword, entry.getValue()), format, false));
		}
		// Nothing yet: the current weapon with every value "-".
		if (weapons.isEmpty())
		{
			report.append("\\n").append(weaponName(tracker.equippedWeaponId()))
				.append(": ").append(renderTemplate(weaponLine, keyword -> "-", format, false));
		}
		appendSourceLine(report, "Thralls", DpsTracker.Source.THRALL, weaponLine, stats, format);
		appendSourceLine(report, "Recoil", DpsTracker.Source.RECOIL, weaponLine, stats, format);
		appendSourceLine(report, "Vengeance", DpsTracker.Source.VENGEANCE, weaponLine, stats, format);
		return report.append("\\n{/align}").toString();
	}

	// {dps <top> groupReport <npc>}: the top line, then <npc> for each boss stage or NPC name you've damaged,
	// bosses first, then by damage. {dps bossReport} is the same with another default <npc>.
	private static String groupReport(String topLine, String npcLine, String legend, List<DpsTracker.Stats.NpcStats> npcs,
		DpsTracker.Stats stats, Function<String, String> format, String paused)
	{
		StringBuilder report = new StringBuilder("{align=left}DPS: ")
			.append(renderTemplate(topLine, keyword -> value(keyword, stats), format, true)).append(paused);
		if (legend != null)
		{
			report.append("\\n").append(legend);
		}
		npcs.sort(Comparator.comparing((DpsTracker.Stats.NpcStats npc) -> !npc.isBoss())
			.thenComparing(Comparator.comparingInt(DpsTracker.Stats.NpcStats::damage).reversed()));
		for (DpsTracker.Stats.NpcStats npc : npcs)
		{
			report.append("\\n").append(npc.name()).append(npc.count() > 1 ? " x" + npc.count() : "")
				.append(": ").append(renderTemplate(npcLine, keyword -> npcValue(keyword, npc), format, false));
		}
		return report.append("\\n{/align}").toString();
	}

	// One NPC name's values.
	private static String npcValue(Matcher keyword, DpsTracker.Stats.NpcStats npc)
	{
		if (keyword.group(2) != null)
		{
			return "-";
		}

		switch (keyword.group(1).toLowerCase(Locale.ROOT))
		{
			case "sharedps":
				return percent(npc.share());
			case "splitdps":
				return percent(npc.split());
			case "totaldamage":
				return QuantityFormatter.formatNumber(npc.damage());
			case "avgdps":
				return decimal(npc.avgDps());
			case "theorydps":
				return npc.theoryDps() < 0 ? "-" : decimal(npc.theoryDps());
			case "puretheorydps":
				return npc.pureTheoryDps() < 0 ? "-" : decimal(npc.pureTheoryDps());
			case "timespent":
				return duration(npc.timeSpentTicks());
			case "acc":
				return npc.accuracy() < 0 ? "-" : percent(npc.accuracy());
			default:
				return "-";
		}
	}

	private String weaponName(int weapon)
	{
		return weapon < 0 ? "Unarmed" : client.getItemDefinition(weapon).getName();
	}

	// The per-weapon template for a non-weapon source, once it's done damage.
	private static void appendSourceLine(StringBuilder report, String name, DpsTracker.Source source, String weaponLine,
		DpsTracker.Stats stats, Function<String, String> format)
	{
		if (stats.damage(source) > 0)
		{
			report.append("\\n").append(name).append(": ")
				.append(renderTemplate(weaponLine, keyword -> sourceValue(keyword, source, stats), format, false));
		}
	}

	// theoryDps is the same as avgDps here, except for thralls.
	private static String sourceValue(Matcher keyword, DpsTracker.Source source, DpsTracker.Stats stats)
	{
		if (keyword.group(2) != null)
		{
			return "-";
		}

		switch (keyword.group(1).toLowerCase(Locale.ROOT))
		{
			case "theorydps":
			case "puretheorydps":
				return decimal(source == DpsTracker.Source.THRALL ? stats.thrallTheoryDps() : stats.dps(source));
			case "avgdps":
				return decimal(stats.dps(source));
			case "sharedps":
				return percent(stats.groupTotal() == 0 ? 0 : stats.damage(source) / (double) stats.groupTotal());
			case "splitdps":
				return percent(stats.total() == 0 ? 0 : stats.damage(source) / (double) stats.total());
			case "totaldamage":
				return QuantityFormatter.formatNumber(stats.damage(source));
			default:
				return "-";
		}
	}

	// value gives each keyword's text, "" to drop it. shareInParens wraps shareDps in "( )".
	private static String renderTemplate(String template, Function<Matcher, String> value, Function<String, String> format, boolean shareInParens)
	{
		Matcher keyword = KEYWORD.matcher(template);
		StringBuilder result = new StringBuilder();
		while (keyword.find())
		{
			String text = value.apply(keyword);
			String formatted = text.isEmpty() ? "" : format.apply(text);
			if (!text.isEmpty() && shareInParens && "shareDps".equalsIgnoreCase(keyword.group(1)))
			{
				formatted = "(" + formatted + ")";
			}
			keyword.appendReplacement(result, Matcher.quoteReplacement(formatted));
		}
		keyword.appendTail(result);
		return result.toString().replaceAll(" {2,}", " ").trim();
	}

	private static String value(Matcher keyword, DpsTracker.Stats stats)
	{
		if (keyword.group(2) != null)
		{
			return decimal(stats.recentDps(Integer.parseInt(keyword.group(2))));
		}

		switch (keyword.group(1).toLowerCase(Locale.ROOT))
		{
			case "sharedps":
				double share = stats.share();
				return share < 0 || Math.round(share * 100) == 100 ? "" : percent(share);
			case "splitdps":
				return "-";
			case "totaldamage":
				return QuantityFormatter.formatNumber(stats.total());
			case "takendamage":
				return QuantityFormatter.formatNumber(stats.taken());
			case "acc":
				double accuracy = stats.accuracy();
				return accuracy < 0 ? "-" : percent(accuracy);
			case "thralldps":
				return decimal(stats.dps(DpsTracker.Source.THRALL));
			case "recoildps":
				return decimal(stats.dps(DpsTracker.Source.RECOIL));
			case "theorydps":
				return decimal(stats.theoryDps());
			case "puretheorydps":
				return decimal(stats.pureTheoryDps());
			case "numattacks":
				return String.valueOf(stats.numAttacks());
			case "nummisses":
				return String.valueOf(stats.numMisses());
			case "numhits":
				return String.valueOf(stats.numHits());
			case "timespent":
				return duration(stats.timeSpentTicks());
			default:
				return decimal(stats.dps());
		}
	}

	// One weapon's values. Damage taken, thrall and recoil aren't per weapon, so they show "-".
	private static String weaponValue(Matcher keyword, DpsTracker.Stats.WeaponStats weapon)
	{
		if (keyword.group(2) != null)
		{
			return decimal(weapon.recentDps(Integer.parseInt(keyword.group(2))));
		}

		switch (keyword.group(1).toLowerCase(Locale.ROOT))
		{
			case "sharedps":
				return percent(weapon.share());
			case "splitdps":
				return percent(weapon.split());
			case "totaldamage":
				return QuantityFormatter.formatNumber(weapon.damage());
			case "acc":
				return weapon.accuracy() < 0 ? "-" : percent(weapon.accuracy());
			case "theorydps":
				return decimal(weapon.theoryDps());
			case "puretheorydps":
				return decimal(weapon.pureTheoryDps());
			case "numattacks":
				return String.valueOf(weapon.numAttacks());
			case "nummisses":
				return String.valueOf(weapon.numMisses());
			case "numhits":
				return String.valueOf(weapon.numHits());
			case "timespent":
				return duration(weapon.timeSpentTicks());
			case "avgdps":
				return decimal(weapon.avgDps());
			default:
				return "-";
		}
	}

	private static String decimal(double value)
	{
		return String.format(Locale.ROOT, "%.2f", value);
	}

	// m:ss, or h:mm:ss from an hour.
	private static String duration(int ticks)
	{
		long seconds = Math.round(ticks * 0.6);
		return seconds >= 3600
			? String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
			: String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
	}

	private static String percent(double fraction)
	{
		return Math.round(fraction * 100) + "%";
	}
}
