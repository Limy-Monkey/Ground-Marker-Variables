package com.groundmarkervariables.variables;

import com.google.gson.Gson;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.QuantityFormatter;

// {loot <monster>} -> gp value of loot Loot Tracker has recorded for <monster>, or 0 if none.
// Matches <monster> case-insensitively against Loot Tracker's own stored "drops_NPC_<name>"
// keys directly — unlike {kc}, Loot Tracker covers far more than named bosses, so BossAliases
// doesn't apply here.
class LootLabelVariable implements LabelVariable
{
	// Excludes ?:<>=! so a trailing BooleanVariable/ConditionalVariable comparator (e.g. "{loot
	// monster > 10000}") isn't swallowed into the monster name here.
	private static final Pattern PATTERN = Pattern.compile("\\{loot\\s+([^{}?:<>=!]+?)\\}", Pattern.CASE_INSENSITIVE);
	static final String GROUP = "loottracker";
	static final String KEY_PREFIX = "drops_NPC_";

	private final ConfigManager configManager;
	private final ItemManager itemManager;
	private final Gson gson;
	private final RichText richText;

	@Inject
	private LootLabelVariable(ConfigManager configManager, ItemManager itemManager, Gson gson, RichText richText)
	{
		this.configManager = configManager;
		this.itemManager = itemManager;
		this.gson = gson;
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
		Loot loot = loot(matcher);
		return loot == null ? null : String.valueOf(loot.gp);
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		Loot loot = loot(matcher);
		if (loot == null)
		{
			return null;
		}

		return loot.monster + " (" + richText.highlightValue(String.valueOf(loot.kills)) + "): "
			+ richText.highlightValue(QuantityFormatter.quantityToStackSize(loot.gp));
	}

	private Loot loot(Matcher matcher)
	{
		String monster = matcher.group(1).trim();
		if (monster.isEmpty())
		{
			return null;
		}

		String profile = configManager.getRSProfileKey();
		String key = null;
		String name = monster;
		for (String candidate : configManager.getRSProfileConfigurationKeys(GROUP, profile, KEY_PREFIX))
		{
			String candidateName = candidate.substring(KEY_PREFIX.length());
			if (candidateName.equalsIgnoreCase(monster))
			{
				key = candidate;
				name = candidateName;
				break;
			}
		}

		if (key == null)
		{
			return new Loot(name, 0, 0);
		}

		LootConfigData data = gson.fromJson(configManager.getConfiguration(GROUP, profile, key), LootConfigData.class);
		long gp = 0;
		if (data.drops != null)
		{
			for (int i = 0; i < data.drops.length - 1; i += 2)
			{
				gp += (long) itemManager.getItemPrice(data.drops[i]) * data.drops[i + 1];
			}
		}

		return new Loot(name, data.kills, gp);
	}

	// Field names match Loot Tracker's own (package-private) ConfigLoot for Gson.
	private static final class LootConfigData
	{
		int kills;
		int[] drops;
	}

	private static final class Loot
	{
		final String monster;
		final int kills;
		final long gp;

		Loot(String monster, int kills, long gp)
		{
			this.monster = monster;
			this.kills = kills;
			this.gp = gp;
		}
	}
}
