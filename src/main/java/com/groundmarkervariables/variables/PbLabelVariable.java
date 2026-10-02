package com.groundmarkervariables.variables;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;

// {pb <boss>} -> personal best time for <boss> (same alias resolution as {kc}), formatted like
// !pb, or "None" if none recorded. Reads Chat Commands' own "personalbest" data, read-only.
class PbLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{pb\\s+([^{}?:<>=!]+?)\\}", Pattern.CASE_INSENSITIVE);
	private static final String CHAT_COMMANDS_GROUP = "personalbest";
	private static final String NO_PB_TEXT = "None";

	private final ConfigManager configManager;
	private final RichText richText;

	@Inject
	private PbLabelVariable(ConfigManager configManager, RichText richText)
	{
		this.configManager = configManager;
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
		String boss = matcher.group(1).trim();
		if (boss.isEmpty())
		{
			return null;
		}

		return format(pb(boss));
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		String boss = matcher.group(1).trim();
		if (boss.isEmpty())
		{
			return null;
		}

		String canonicalBoss = BossAliases.resolve(boss);
		return richText.labeled(canonicalBoss + " pb", format(pb(boss)));
	}

	private Double pb(String boss)
	{
		String key = BossAliases.resolve(boss).toLowerCase(Locale.ENGLISH);
		return configManager.getRSProfileConfiguration(CHAT_COMMANDS_GROUP, key, double.class);
	}

	// Mirrors ChatCommandsPlugin#secondsToTimeString so this matches !pb's own format.
	private static String format(Double seconds)
	{
		if (seconds == null)
		{
			return NO_PB_TEXT;
		}

		int hours = (int) (Math.floor(seconds) / 3600);
		int minutes = (int) (Math.floor(seconds / 60) % 60);
		double secs = seconds % 60;

		String prefix = hours > 0 ? String.format("%d:%02d:", hours, minutes) : String.format("%d:", minutes);
		return prefix + (Math.floor(secs) == secs ? String.format("%02d", (int) secs) : String.format("%05.2f", secs));
	}
}
