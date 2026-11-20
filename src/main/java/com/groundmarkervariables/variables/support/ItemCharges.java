package com.groundmarkervariables.variables.support;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.PluginManager;

// Item charges saved by Item Charges Improved (hub) and core Item Charges, each only while that plugin is on.
// Names are their config keys made readable: "toxic_blowpipe" / "dodgyNecklace" -> "Toxic blowpipe" / "Dodgy necklace".
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ItemCharges
{
	public static final int UNLIMITED_CHARGES = -2;

	// Improved: global (not per account), one integer per item; -1 unknown, -2 unlimited.
	private static final String IMPROVED_GROUP = "tictac7x-charges";
	private static final String IMPROVED_PLUGIN = "tictac7x.charges.TicTac7xChargesImprovedPlugin";
	// Items holding others (blowpipe darts and scales, herb sack, ...): "<key>_storage", a JSON list of
	// {itemId, quantity}. Their charges are the quantities' total, or the largest if "<key>_display" is "individual".
	private static final String IMPROVED_STORAGE = "_storage";
	private static final String IMPROVED_DISPLAY = "_display";
	private static final String IMPROVED_DISPLAY_INDIVIDUAL = "individual";
	// Overrides
	private static final Set<String> IMPROVED_SMALLEST = Set.of("toxic_blowpipe", "blazing_blowpipe", "chugging_barrel");
	private static final Set<String> IMPROVED_BLOCKED = Set.of("escape_crystal");
	// Set pieces' keys start with their set; their names don't, to match the items' ("Ahrims hood").
	private static final List<String> IMPROVED_SET_PREFIXES = List.of("barrows_gear_", "moons_gear_");
	// Its other keys: per-item settings and state, and its own settings that also save as integers (colors too).
	private static final List<String> IMPROVED_SUFFIXES = List.of("_infobox", "_overlay", IMPROVED_STORAGE, IMPROVED_DISPLAY, "_status");
	private static final List<String> IMPROVED_SETTING_PREFIXES = List.of("colors_", "dose_");
	private static final Set<String> IMPROVED_SETTINGS = Set.of("storage_bank", "escape_crystal_time_remaining_warning",
		"escape_crystal_inactivity_period", "colossal_pouch_decay_count");

	// Core: per account.
	private static final String CORE_GROUP = "itemCharge";
	private static final String CORE_PLUGIN = "net.runelite.client.plugins.itemcharges.ItemChargePlugin";

	private static final int UNKNOWN_CHARGES = -1;

	private final Client client;
	private final ConfigManager configManager;
	private final PluginManager pluginManager;

	// Every item either plugin has known charges saved for (not -1), alphabetically; core's where both have one.
	public List<String> names()
	{
		Map<String, String> byNormalized = new LinkedHashMap<>();
		if (active(CORE_PLUGIN))
		{
			String profile = configManager.getRSProfileKey();
			for (String coreKey : profile == null ? List.<String>of() : configManager.getRSProfileConfigurationKeys(CORE_GROUP, profile, ""))
			{
				if (parse(configManager.getConfiguration(CORE_GROUP, profile, coreKey)) == null)
				{
					continue;
				}
				String name = readable(coreKey.replaceAll("([a-z0-9])([A-Z])", "$1 $2"));
				byNormalized.putIfAbsent(normalized(name), name);
			}
		}
		if (active(IMPROVED_PLUGIN))
		{
			String prefix = IMPROVED_GROUP + ".";
			for (String key : configManager.getConfigurationKeys(prefix))
			{
				String improvedKey = key.substring(prefix.length());
				if (improvedKey.endsWith(IMPROVED_STORAGE))
				{
					improvedKey = improvedKey.substring(0, improvedKey.length() - IMPROVED_STORAGE.length());
				}
				if (isImprovedCharges(improvedKey) && improvedCharges(improvedKey) != null)
				{
					String shown = improvedKey;
					for (String setPrefix : IMPROVED_SET_PREFIXES)
					{
						if (shown.startsWith(setPrefix))
						{
							shown = shown.substring(setPrefix.length());
						}
					}
					String name = readable(shown.replace('_', ' '));
					byNormalized.putIfAbsent(normalized(name), name);
				}
			}
		}
		List<String> names = new ArrayList<>(byNormalized.values());
		names.sort(Comparator.comparing(name -> name, String.CASE_INSENSITIVE_ORDER));
		return names;
	}

	// name's charges (any case, spacing and apostrophes), UNLIMITED_CHARGES for unlimited; null if unknown or neither plugin is on.
	@Nullable
	public Integer charges(String name)
	{
		String[] words = words(name);
		if (words[0].isEmpty())
		{
			return null;
		}
		// Core first: it's per account.
		Integer charges = null;
		if (active(CORE_PLUGIN))
		{
			StringBuilder coreKey = new StringBuilder(words[0]);
			for (int i = 1; i < words.length; i++)
			{
				coreKey.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
			}
			charges = parse(configManager.getRSProfileConfiguration(CORE_GROUP, coreKey.toString()));
		}
		if (charges == null && active(IMPROVED_PLUGIN))
		{
			String improvedKey = String.join("_", words);
			if (isImprovedCharges(improvedKey))
			{
				charges = improvedCharges(improvedKey);
				for (int i = 0; charges == null && i < IMPROVED_SET_PREFIXES.size(); i++)
				{
					charges = improvedCharges(IMPROVED_SET_PREFIXES.get(i) + improvedKey);
				}
			}
		}
		return charges;
	}

	// Client thread. The charges of only the named items (any case and apostrophes) inside Improved's container
	// name, by its usual rule; null if it isn't one.
	@Nullable
	public Integer charges(String name, List<String> only)
	{
		String key = storageKey(name);
		Map<Integer, Integer> items = key == null ? null : storedItems(name, only);
		return items == null ? null : combine(key, items.values());
	}

	// Client thread (to name them). What Improved's container name holds, item ID -> quantity: everything in its
	// order, or only the named items in the order named. Null if it isn't one.
	@Nullable
	public Map<Integer, Integer> storedItems(String name, List<String> only)
	{
		String key = storageKey(name);
		Map<Integer, Integer> stored = key == null ? null : stored(key);
		if (stored == null || only.isEmpty())
		{
			return stored;
		}
		Map<Integer, Integer> named = new LinkedHashMap<>();
		for (String wanted : only)
		{
			stored.forEach((id, quantity) ->
			{
				String item = itemName(id);
				if (item != null && loose(item).equals(loose(wanted)))
				{
					named.put(id, quantity);
				}
			});
		}
		return named;
	}

	// The names of what Improved's container name holds; empty if it isn't one. Null off the client thread, where
	// items can't be named.
	@Nullable
	public List<String> storedNames(String name)
	{
		if (!client.isClientThread())
		{
			return null;
		}
		List<String> names = new ArrayList<>();
		Map<Integer, Integer> stored = storedItems(name, List.of());
		if (stored != null)
		{
			for (int id : stored.keySet())
			{
				String item = itemName(id);
				if (item != null)
				{
					names.add(item);
				}
			}
		}
		return names;
	}

	// 8643 -> "8.6k", 15000 -> "15k", 2000000 -> "2m", as Improved shortens them.
	public static String shortened(int quantity)
	{
		if (quantity >= 1_000_000)
		{
			return quantity / 1_000_000 + "m";
		}
		if (quantity >= 10_000)
		{
			return Math.round(quantity / 1000f) + "k";
		}
		if (quantity >= 1000)
		{
			int hundreds = Math.min((quantity % 1000 + 50) / 100, 9);
			return quantity / 1000 + (hundreds > 0 ? "." + hundreds : "") + "k";
		}
		return String.valueOf(quantity);
	}

	// An Improved item's own charges, else those of what it holds; null if neither is known.
	@Nullable
	private Integer improvedCharges(String key)
	{
		Integer charges = parse(configManager.getConfiguration(IMPROVED_GROUP, key));
		if (charges != null)
		{
			return charges;
		}
		Map<Integer, Integer> stored = stored(key);
		return stored == null ? null : combine(key, stored.values());
	}

	// key's quantities as one number: the smallest (IMPROVED_SMALLEST), largest ("individual") or total; 0 if none.
	private int combine(String key, Collection<Integer> quantities)
	{
		boolean individual = IMPROVED_DISPLAY_INDIVIDUAL.equals(configManager.getConfiguration(IMPROVED_GROUP, key + IMPROVED_DISPLAY));
		boolean smallest = IMPROVED_SMALLEST.contains(key);
		Integer result = null;
		for (int quantity : quantities)
		{
			result = result == null ? quantity
				: smallest ? Math.min(result, quantity) : individual ? Math.max(result, quantity) : result + quantity;
		}
		return result == null ? 0 : result;
	}

	// Improved's key for name with a container saved (with or without a set's prefix), or null.
	@Nullable
	private String storageKey(String name)
	{
		String[] words = words(name);
		if (words[0].isEmpty() || !active(IMPROVED_PLUGIN))
		{
			return null;
		}
		String key = String.join("_", words);
		if (!isImprovedCharges(key))
		{
			return null;
		}
		List<String> candidates = new ArrayList<>();
		candidates.add(key);
		IMPROVED_SET_PREFIXES.forEach(prefix -> candidates.add(prefix + key));
		for (String candidate : candidates)
		{
			if (configManager.getConfiguration(IMPROVED_GROUP, candidate + IMPROVED_STORAGE) != null)
			{
				return candidate;
			}
		}
		return null;
	}

	// key's container, item ID -> quantity (none below 0), in Improved's order; null if none saved.
	@Nullable
	private Map<Integer, Integer> stored(String key)
	{
		String storage = configManager.getConfiguration(IMPROVED_GROUP, key + IMPROVED_STORAGE);
		if (storage == null)
		{
			return null;
		}
		try
		{
			Map<Integer, Integer> items = new LinkedHashMap<>();
			for (JsonElement element : new JsonParser().parse(storage).getAsJsonArray())
			{
				JsonObject item = element.getAsJsonObject();
				items.merge(item.get("itemId").getAsInt(), Math.max(0, item.get("quantity").getAsInt()), Integer::sum);
			}
			return items;
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	// An item's name; null off the client thread.
	@Nullable
	private String itemName(int id)
	{
		if (!client.isClientThread())
		{
			return null;
		}
		ItemComposition item = client.getItemDefinition(id);
		return item == null ? null : item.getName();
	}

	// name as shown: words single-spaced, only the first capitalized.
	public static String readable(String name)
	{
		String words = name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
		return words.isEmpty() ? words : Character.toUpperCase(words.charAt(0)) + words.substring(1);
	}

	// The keys have no apostrophes: "Alchemist's amulet" -> alchemists, amulet.
	private static String[] words(String name)
	{
		return name.trim().toLowerCase(Locale.ROOT).replace("'", "").split("\\s+");
	}

	private static String loose(String name)
	{
		return name.trim().toLowerCase(Locale.ROOT).replace("'", "");
	}

	private static boolean isImprovedCharges(String key)
	{
		return !IMPROVED_SETTINGS.contains(key) && !IMPROVED_BLOCKED.contains(key) && IMPROVED_SUFFIXES.stream().noneMatch(key::endsWith)
			&& IMPROVED_SETTING_PREFIXES.stream().noneMatch(key::startsWith);
	}

	// A saved charge count, or null if none or unknown.
	@Nullable
	private static Integer parse(@Nullable String value)
	{
		if (value == null)
		{
			return null;
		}
		try
		{
			int charges = Integer.parseInt(value.trim());
			return charges == UNKNOWN_CHARGES || charges < UNLIMITED_CHARGES ? null : charges;
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	private static String normalized(String name)
	{
		return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	public boolean improvedActive()
	{
		return active(IMPROVED_PLUGIN);
	}

	private boolean active(String pluginClass)
	{
		return pluginManager.getPlugins().stream()
			.anyMatch(plugin -> plugin.getClass().getName().equals(pluginClass) && pluginManager.isPluginActive(plugin));
	}
}
