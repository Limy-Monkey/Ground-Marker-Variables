package com.groundmarkervariables.variables.label;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.PendingNpcLoot;
import com.groundmarkervariables.variables.support.RecentLootTracker;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.QuantityFormatter;

// {loot <monster>} -> gp value of loot Loot Tracker has recorded for <monster>, or 0 if none.
// Matches <monster> against Loot Tracker's own "drops_<TYPE>_<name>" keys (TYPE = NPC, EVENT,
// etc.) — unlike {kc}, Loot Tracker covers far more than named bosses, so BossAliases doesn't apply.
public class LootLabelVariable implements LabelVariable
{
	// Excludes ?:<>=! so a trailing BooleanVariable/ConditionalVariable comparator (e.g. "{loot
	// monster > 10000}") isn't swallowed into the monster name here.
	private static final Pattern PATTERN = Pattern.compile("\\{loot\\s+([^{}?:<>=!]+?)\\}", Pattern.CASE_INSENSITIVE);
	static final String GROUP = "loottracker";
	static final String KEY_PREFIX = "drops_";

	// Rich Text lists any single item worth at least this much on its own, most expensive first
	// -- a stack only qualifies if EVERY unit in it clears the bar, not the stack's total value.
	private static final long MIN_ITEM_VALUE = 300_000;
	private static final int MAX_ITEMS_SHOWN = 5;

	private final ConfigManager configManager;
	private final ItemManager itemManager;
	private final Gson gson;
	private final RecentLootTracker recentLootTracker;
	private final RichText richText;

	@Inject
	private LootLabelVariable(ConfigManager configManager, ItemManager itemManager, Gson gson, RecentLootTracker recentLootTracker,
		RichText richText)
	{
		this.configManager = configManager;
		this.itemManager = itemManager;
		this.gson = gson;
		this.recentLootTracker = recentLootTracker;
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

		StringBuilder sb = new StringBuilder()
			.append(loot.monster).append(" (").append(richText.highlightValue(String.valueOf(loot.kills))).append("): ")
			.append(richText.highlightValue(QuantityFormatter.quantityToStackSize(loot.gp)));

		// <item=ID> is GroundMarkerVariablesOverlay's own inline-icon tag.
		if (!loot.expensiveItems.isEmpty())
		{
			sb.append(" |");
			for (ExpensiveItem item : loot.expensiveItems)
			{
				sb.append(" ").append(item.quantity).append("x <item=").append(item.itemId).append('>');
			}
		}

		return sb.toString();
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
			// "drops_<TYPE>_<name>" -- TYPE (NPC/EVENT/PLAYER/...) has no underscore of its own,
			// so the first one after the prefix is exactly where the name starts.
			String rest = candidate.substring(KEY_PREFIX.length());
			int typeEnd = rest.indexOf('_');
			if (typeEnd < 0)
			{
				continue;
			}
			String candidateName = rest.substring(typeEnd + 1);
			if (candidateName.equalsIgnoreCase(monster))
			{
				key = candidate;
				name = candidateName;
				break;
			}
		}

		int kills = 0;
		long gp = 0;
		Map<Integer, Integer> quantityByItem = new LinkedHashMap<>();

		if (key != null)
		{
			LootConfigData data = gson.fromJson(configManager.getConfiguration(GROUP, profile, key), LootConfigData.class);
			kills = data.kills;
			if (data.drops != null)
			{
				for (int i = 0; i < data.drops.length - 1; i += 2)
				{
					int itemId = data.drops[i];
					int quantity = data.drops[i + 1];
					gp += (long) itemManager.getItemPrice(itemId) * quantity;
					quantityByItem.merge(itemId, quantity, Integer::sum);
				}
			}
		}

		// Loot Tracker hasn't written this kill to config yet -- see RecentLootTracker.
		PendingNpcLoot recent = recentLootTracker.get(name);
		if (recent != null)
		{
			if (key == null)
			{
				name = recent.displayName;
			}
			kills += recent.kills;
			gp += recent.gpValue;
			for (Map.Entry<Integer, Integer> entry : recent.itemQuantities.entrySet())
			{
				quantityByItem.merge(entry.getKey(), entry.getValue(), Integer::sum);
			}
		}

		if (key == null && recent == null)
		{
			return new Loot(name, 0, 0, List.of());
		}

		List<ExpensiveItem> expensiveItems = new ArrayList<>();
		for (Map.Entry<Integer, Integer> entry : quantityByItem.entrySet())
		{
			long price = itemManager.getItemPrice(entry.getKey());
			if (price >= MIN_ITEM_VALUE)
			{
				expensiveItems.add(new ExpensiveItem(entry.getKey(), entry.getValue(), price));
			}
		}
		expensiveItems.sort(Comparator.comparingLong((ExpensiveItem item) -> item.price).reversed());
		if (expensiveItems.size() > MAX_ITEMS_SHOWN)
		{
			expensiveItems = expensiveItems.subList(0, MAX_ITEMS_SHOWN);
		}

		return new Loot(name, kills, gp, expensiveItems);
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
		final List<ExpensiveItem> expensiveItems;

		Loot(String monster, int kills, long gp, List<ExpensiveItem> expensiveItems)
		{
			this.monster = monster;
			this.kills = kills;
			this.gp = gp;
			this.expensiveItems = expensiveItems;
		}
	}

	private static final class ExpensiveItem
	{
		final int itemId;
		final int quantity;
		final long price;

		ExpensiveItem(int itemId, int quantity, long price)
		{
			this.itemId = itemId;
			this.quantity = quantity;
			this.price = price;
		}
	}
}
