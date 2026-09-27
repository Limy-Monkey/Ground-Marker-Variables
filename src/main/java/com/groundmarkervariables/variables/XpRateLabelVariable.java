package com.groundmarkervariables.variables;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.xptracker.XpTrackerPlugin;
import net.runelite.client.plugins.xptracker.XpTrackerService;

// {xpRate_<skill>} -> the player's current xp/hour in <skill> from the XP Tracker plugin, e.g.
// {xpRate_agility}, {xpRate_fishing}. <skill> matches any Skill.getName() case-insensitively,
// same as {lvl_<skill>}/{boost_<skill>}. Resolves to "N/A" if XP Tracker isn't running, since
// its numbers are otherwise meaninglessly 0.
class XpRateLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{xpRate_([a-z]+)\\}", Pattern.CASE_INSENSITIVE);
	private static final String XP_TRACKER_DISABLED_TEXT = "N/A";
	private static final String XP_TRACKER_DISABLED_RICH_TEXT = "Enable XP Tracker Plugin";
	private static final int ABBREVIATE_ABOVE = 10_000;

	private final Client client;
	private final XpTrackerService xpTrackerService;
	private final PluginManager pluginManager;
	private final RichText richText;

	@Inject
	private XpRateLabelVariable(Client client, XpTrackerService xpTrackerService, PluginManager pluginManager,
		RichText richText)
	{
		this.client = client;
		this.xpTrackerService = xpTrackerService;
		this.pluginManager = pluginManager;
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
		if (skill == null)
		{
			return null;
		}

		return isXpTrackerRunning() ? String.valueOf(xpTrackerService.getXpHr(skill)) : XP_TRACKER_DISABLED_TEXT;
	}

	// Needs the raw int for k-formatting, and diverges from Plain when disabled, so this
	// doesn't delegate to resolvePlain.
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

		if (!isXpTrackerRunning())
		{
			return XP_TRACKER_DISABLED_RICH_TEXT;
		}

		return richText.labeled(skill.getName() + " XP/hr", formatXpHr(xpTrackerService.getXpHr(skill)));
	}

	// > 10,000 abbreviates to the nearest thousand ("48k"); at or below, shows the exact number.
	private static String formatXpHr(int xpHr)
	{
		return xpHr > ABBREVIATE_ABOVE ? (xpHr / 1000) + "k" : String.valueOf(xpHr);
	}

	private boolean isXpTrackerRunning()
	{
		return pluginManager.getPlugins().stream()
			.anyMatch(plugin -> plugin.getClass() == XpTrackerPlugin.class && pluginManager.isPluginActive(plugin));
	}
}
