package com.groundmarkervariables.variables.label;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.BossAliases;
import com.groundmarkervariables.variables.support.CollectionLog;
import com.groundmarkervariables.variables.support.PendingNpcLoot;
import com.groundmarkervariables.variables.support.RecentLootTracker;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.QuantityFormatter;

// {loot <monster>[, items=N][, lines=N]} -> gp value of loot Loot Tracker has recorded for
// <monster> (or the boss it's a BossAliases short name for), or 0 if none.
public class LootLabelVariable implements LabelVariable
{
	// Excludes ?:<>=! so a trailing comparator (e.g. "{loot monster > 10000}") isn't swallowed
	// into the monster name. Group 2 is the options.
	private static final Pattern PATTERN = Pattern.compile(
		"\\{loot\\s+([^{}?:<>=!]+?)((?:\\s*,?\\s*(?:items|lines)=\\d+)*)\\s*,?\\s*\\}", Pattern.CASE_INSENSITIVE);
	private static final Pattern OPTION = Pattern.compile("(items|lines)=(\\d+)", Pattern.CASE_INSENSITIVE);
	static final String GROUP = "loottracker";
	static final String KEY_PREFIX = "drops_";

	// Names that combine several Loot Tracker entries -- shared with AdvancedLabelEditor.
	public static final Map<String, List<String>> COMBINED_MONSTERS = Map.of(
		"Royal Titans", List.of("Eldric the Ice King", "Branda the Fire Queen"));
	public static final List<String> PLAYER_NAMES = List.of("players", "pk", "pker");
	public static final String PLAYER_TYPE = "PLAYER";
	private static final String PLAYERS_DISPLAY_NAME = "PKs";

	// Rich Text lists every pet, then up to items=N collection log or MIN_ITEM_VALUE+ items.
	private static final long MIN_ITEM_VALUE = 300_000;
	private static final int DEFAULT_ITEMS_SHOWN = 5;

	private final ConfigManager configManager;
	private final ItemManager itemManager;
	private final Gson gson;
	private final RecentLootTracker recentLootTracker;
	private final RichText richText;
	private final CollectionLog collectionLog;

	@Inject
	private LootLabelVariable(ConfigManager configManager, ItemManager itemManager, Gson gson, RecentLootTracker recentLootTracker,
		RichText richText, CollectionLog collectionLog)
	{
		this.configManager = configManager;
		this.itemManager = itemManager;
		this.gson = gson;
		this.recentLootTracker = recentLootTracker;
		this.richText = richText;
		this.collectionLog = collectionLog;
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

		String header = loot.monster + " (" + richText.highlightValue(String.valueOf(loot.kills)) + "): "
			+ richText.highlightValue(QuantityFormatter.quantityToStackSize(loot.gp));
		List<NotableItem> items = loot.notableItems;

		int lines = option(matcher, "lines", 1);
		if (lines <= 1)
		{
			return items.isEmpty() ? header : header + " | " + itemsText(items);
		}

		// Like {party}; items split evenly over the remaining lines, earlier lines taking extras.
		StringBuilder sb = new StringBuilder("{align=left}").append(header);
		int itemLines = Math.min(lines - 1, items.size());
		int start = 0;
		for (int line = 0; line < itemLines; line++)
		{
			int linesLeft = itemLines - line;
			int count = (items.size() - start + linesLeft - 1) / linesLeft;
			sb.append("\\n").append(itemsText(items.subList(start, start + count)));
			start += count;
		}
		return sb.append("\\n{/align}").toString();
	}

	// <item=ID> is GroundMarkerVariablesOverlay's own inline-icon tag.
	private static String itemsText(List<NotableItem> items)
	{
		StringBuilder sb = new StringBuilder();
		for (NotableItem item : items)
		{
			if (sb.length() > 0)
			{
				sb.append(' ');
			}
			sb.append(item.quantity).append("x <item=").append(item.itemId).append('>');
		}
		return sb.toString();
	}

	// The last "<name>=N" option, or fallback.
	private static int option(Matcher matcher, String name, int fallback)
	{
		int value = fallback;
		Matcher option = OPTION.matcher(matcher.group(2));
		while (option.find())
		{
			if (option.group(1).equalsIgnoreCase(name))
			{
				try
				{
					value = Integer.parseInt(option.group(2));
				}
				catch (NumberFormatException e)
				{
					value = Integer.MAX_VALUE;
				}
			}
		}
		return value;
	}

	private Loot loot(Matcher matcher)
	{
		String monster = matcher.group(1).trim();
		if (monster.isEmpty())
		{
			return null;
		}

		boolean players = PLAYER_NAMES.contains(monster.toLowerCase(Locale.ENGLISH));
		String name = players ? PLAYERS_DISPLAY_NAME : BossAliases.resolve(monster);
		List<String> parts = List.of(monster, name);
		boolean combined = false;
		for (Map.Entry<String, List<String>> entry : COMBINED_MONSTERS.entrySet())
		{
			if (entry.getKey().equalsIgnoreCase(monster))
			{
				name = entry.getKey();
				parts = entry.getValue();
				combined = true;
			}
		}

		String profile = configManager.getRSProfileKey();
		boolean found = false;
		int kills = 0;
		long gp = 0;
		Map<Integer, Integer> quantityByItem = new LinkedHashMap<>();

		for (String key : configManager.getRSProfileConfigurationKeys(GROUP, profile, KEY_PREFIX))
		{
			// "drops_<TYPE>_<name>" -- TYPE (NPC/EVENT/PLAYER/...) has no underscore of its own,
			// so the first one after the prefix is exactly where the name starts.
			String rest = key.substring(KEY_PREFIX.length());
			int typeEnd = rest.indexOf('_');
			if (typeEnd < 0)
			{
				continue;
			}
			String candidateName = rest.substring(typeEnd + 1);
			boolean matches = players
				? rest.substring(0, typeEnd).equalsIgnoreCase(PLAYER_TYPE)
				: parts.stream().anyMatch(candidateName::equalsIgnoreCase);
			if (!matches)
			{
				continue;
			}

			if (!found && !players && !combined)
			{
				name = candidateName;
			}
			found = true;

			LootConfigData data = gson.fromJson(configManager.getConfiguration(GROUP, profile, key), LootConfigData.class);
			kills += data.kills;
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

		// Loot Tracker hasn't written these kills to config yet -- see RecentLootTracker (NPCs only).
		Set<String> pendingChecked = new HashSet<>();
		for (String part : players ? List.<String>of() : parts)
		{
			PendingNpcLoot recent = pendingChecked.add(part.toLowerCase(Locale.ENGLISH)) ? recentLootTracker.get(part) : null;
			if (recent == null)
			{
				continue;
			}

			if (!found && !combined)
			{
				name = recent.displayName;
			}
			found = true;
			kills += recent.kills;
			gp += recent.gpValue;
			for (Map.Entry<Integer, Integer> entry : recent.itemQuantities.entrySet())
			{
				quantityByItem.merge(entry.getKey(), entry.getValue(), Integer::sum);
			}
		}

		if (!found)
		{
			return new Loot(name, 0, 0, List.of());
		}

		List<NotableItem> pets = new ArrayList<>();
		List<NotableItem> others = new ArrayList<>();
		for (Map.Entry<Integer, Integer> entry : quantityByItem.entrySet())
		{
			// Collection log ids are unnoted.
			int canonicalId = itemManager.canonicalize(entry.getKey());
			long price = itemManager.getItemPrice(entry.getKey());
			NotableItem item = new NotableItem(entry.getKey(), entry.getValue(), price);
			if (collectionLog.isPet(canonicalId))
			{
				pets.add(item);
			}
			else if (price >= MIN_ITEM_VALUE || collectionLog.contains(canonicalId))
			{
				others.add(item);
			}
		}
		others.sort(Comparator.comparingLong((NotableItem item) -> item.price).reversed());
		int maxItems = option(matcher, "items", DEFAULT_ITEMS_SHOWN);
		if (others.size() > maxItems)
		{
			others = others.subList(0, maxItems);
		}

		List<NotableItem> notableItems = new ArrayList<>(pets);
		notableItems.addAll(others);
		return new Loot(name, kills, gp, notableItems);
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
		final List<NotableItem> notableItems;

		Loot(String monster, int kills, long gp, List<NotableItem> notableItems)
		{
			this.monster = monster;
			this.kills = kills;
			this.gp = gp;
			this.notableItems = notableItems;
		}
	}

	private static final class NotableItem
	{
		final int itemId;
		final int quantity;
		final long price;

		NotableItem(int itemId, int quantity, long price)
		{
			this.itemId = itemId;
			this.quantity = quantity;
			this.price = price;
		}
	}
}
