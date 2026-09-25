package com.groundmarkervariables.variables;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

// Tracks per-boss kill counts for {kc <boss>}, learned from the same game-message text and
// regex Chat Commands' own KILLCOUNT_PATTERN uses (ChatCommandsPlugin#onChatMessage) — kept
// independent of that plugin (it might be disabled) rather than depending on it, but getKc()
// still falls back to reading its stored "killcount" RS-profile data (read-only) so {kc}
// already has a value on a fresh install if Chat Commands has ever seen the kill.
//
// @Singleton for the same reason as MetronomeLabelVariable: injected both here (as a
// LabelVariable, via VariableRegistry) and in GroundMarkerVariablesPlugin#onChatMessage, which
// must share the one tracker instance. Public for the same cross-package-access reason.
@Singleton
public class BossKillCountTracker
{
	private static final String GROUP = "groundMarkerVariablesKc";
	private static final String CHAT_COMMANDS_GROUP = "killcount";

	// Ported from ChatCommandsPlugin.KILLCOUNT_PATTERN — matches messages like "Your Zulrah
	// kill count is: 50.", "Your Chambers of Xeric count is: 5.", "Your subdued Wintertodt
	// count is: 12.", "Your Theatre of Blood completion count is: 3." etc.
	private static final Pattern KILLCOUNT_PATTERN = Pattern.compile(
		"Your (?<pre>completion count for |subdued |completed )?(?:<col=[0-9a-f]{6}>)?(?<boss>.+?)(?:</col>)? "
			+ "(?<post>(?:(?:kill|harvest|lap|completion|success|Total Ticket) )?(?:count )?)is: ?"
			+ "(?:<col=[0-9a-f]{6}>|@.+?@)(?<kc>[0-9,]+)</col>");

	private static final Map<String, String> KILLCOUNT_RENAMES = Map.of("Barrows chest", "Barrows Chests");

	private final ConfigManager configManager;

	@Inject
	private BossKillCountTracker(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	public void onChatMessage(String message)
	{
		Matcher matcher = KILLCOUNT_PATTERN.matcher(message);
		if (!matcher.find())
		{
			return;
		}

		String boss = matcher.group("boss");
		int kc;
		try
		{
			kc = Integer.parseInt(matcher.group("kc").replace(",", ""));
		}
		catch (NumberFormatException e)
		{
			return;
		}

		// Neither qualifying word present means this isn't really a kill-count line (e.g. some
		// unrelated "Your ... is: <number>" message) — same false-positive guard as the source.
		if (isEmpty(matcher.group("pre")) && isEmpty(matcher.group("post")))
		{
			return;
		}

		String renamedBoss = KILLCOUNT_RENAMES.getOrDefault(boss, boss).replace(":", "");
		configManager.setRSProfileConfiguration(GROUP, renamedBoss.toLowerCase(Locale.ENGLISH), kc);
	}

	public Integer getKc(String boss)
	{
		String key = boss.toLowerCase(Locale.ENGLISH);
		Integer own = configManager.getRSProfileConfiguration(GROUP, key, int.class);
		if (own != null)
		{
			return own;
		}

		return configManager.getRSProfileConfiguration(CHAT_COMMANDS_GROUP, key, int.class);
	}

	private static boolean isEmpty(String value)
	{
		return value == null || value.isEmpty();
	}
}
