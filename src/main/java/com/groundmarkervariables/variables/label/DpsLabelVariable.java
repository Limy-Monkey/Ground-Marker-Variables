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
		"(?<![\\w:])(avgDps|shareDps|totalDamage|takenDamage|acc|thrallDps|recoilDps|theoryDps|numAttacks|numMisses|numHits|(\\d+)s)(?![\\w:])", Pattern.CASE_INSENSITIVE);
	private static final String DEFAULT_TEMPLATE = "avgDps shareDps";
	private static final String DEFAULT_WEAPON_TEMPLATE = "theoryDps (shareDps | acc)";
	private static final Pattern REPORT_WORD = Pattern.compile("(?<![\\w:])report(?![\\w:])", Pattern.CASE_INSENSITIVE);
	private static final String PAUSED = " <col=" + NamedColors.HEX_BY_NAME.get("gray") + ">(paused)</col>";

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
		Matcher report = REPORT_WORD.matcher(template);
		if (!report.find())
		{
			String text = renderTemplate(template, keyword -> value(keyword, stats), format, true);
			return rich ? "DPS: " + text + paused : text;
		}

		String topLine = template.substring(0, report.start()).trim();
		String weaponLine = template.substring(report.end()).trim();
		return report(topLine.isEmpty() ? DEFAULT_TEMPLATE : topLine, weaponLine.isEmpty() ? DEFAULT_WEAPON_TEMPLATE : weaponLine,
			stats, format, paused);
	}

	// {dps <top> report <weapon>}: the top line, then <weapon> for each weapon used (by share), then
	// thralls, recoil and vengeance once they've done damage.
	private String report(String topLine, String weaponLine, DpsTracker.Stats stats, Function<String, String> format, String paused)
	{
		StringBuilder report = new StringBuilder("{align=left}DPS: ")
			.append(renderTemplate(topLine, keyword -> value(keyword, stats), format, true)).append(paused);
		List<Map.Entry<Integer, DpsTracker.Stats.WeaponStats>> weapons = new ArrayList<>(stats.weaponStats().entrySet());
		weapons.sort(Comparator.comparingDouble((Map.Entry<Integer, DpsTracker.Stats.WeaponStats> entry) -> entry.getValue().share()).reversed());
		for (Map.Entry<Integer, DpsTracker.Stats.WeaponStats> entry : weapons)
		{
			int weapon = entry.getKey();
			report.append("\\n").append(weapon < 0 ? "Unarmed" : client.getItemDefinition(weapon).getName())
				.append(": ").append(renderTemplate(weaponLine, keyword -> weaponValue(keyword, entry.getValue()), format, false));
		}
		if (stats.damage(DpsTracker.Source.THRALL) > 0)
		{
			report.append("\\nThralls: ").append(format.apply(decimal(stats.thrallTheoryDps())));
		}
		if (stats.damage(DpsTracker.Source.RECOIL) > 0)
		{
			report.append("\\nRecoil: ").append(format.apply(decimal(stats.dps(DpsTracker.Source.RECOIL))));
		}
		if (stats.damage(DpsTracker.Source.VENGEANCE) > 0)
		{
			report.append("\\nVengeance: ").append(format.apply(decimal(stats.dps(DpsTracker.Source.VENGEANCE))));
		}
		return report.append("\\n{/align}").toString();
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
			case "numattacks":
				return String.valueOf(stats.numAttacks());
			case "nummisses":
				return String.valueOf(stats.numMisses());
			case "numhits":
				return String.valueOf(stats.numHits());
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
			case "totaldamage":
				return QuantityFormatter.formatNumber(weapon.damage());
			case "acc":
				return weapon.accuracy() < 0 ? "-" : percent(weapon.accuracy());
			case "theorydps":
				return decimal(weapon.theoryDps());
			case "numattacks":
				return String.valueOf(weapon.numAttacks());
			case "nummisses":
				return String.valueOf(weapon.numMisses());
			case "numhits":
				return String.valueOf(weapon.numHits());
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

	private static String percent(double fraction)
	{
		return Math.round(fraction * 100) + "%";
	}
}
