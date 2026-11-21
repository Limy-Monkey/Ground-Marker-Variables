package com.groundmarkervariables.variables.label;

import com.google.gson.Gson;
import com.groundmarkervariables.variables.Args;
import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.BossAliases;
import com.groundmarkervariables.variables.support.CollectionLog;
import com.groundmarkervariables.variables.support.LootSessions;
import com.groundmarkervariables.variables.support.LootTrackerRecords;
import com.groundmarkervariables.variables.support.RuneProfileCollectionLog;
import java.time.Instant;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.QuantityFormatter;

// {loot <monster>[, items=N][, itemsPerLine=N][, oneline][, hideVal][, showItem=<name>]...[, itemsOnly]} -> gp value of loot Loot Tracker has recorded
// for <monster>, or 0 if none. Rich Text: items shown like {log}, or after the header with oneline.
public class LootLabelVariable implements LabelVariable
{
	private static final String LOOT_TRACKER_GROUP = "loottracker";
	private static final String LOOT_KEY_PREFIX = "drops_";
	// Rich Text lists every pet, then up to items=N collection log or MIN_ITEM_VALUE+ items.
	private static final long MIN_ITEM_VALUE = 300_000;
	private static final int DEFAULT_ITEMS_SHOWN = 29;
	private static final int DEFAULT_ITEMS_PER_LINE = 10;

	private final ItemManager itemManager;
	private final LootTrackerRecords lootTrackerRecords;
	private final RichText richText;
	private final CollectionLog collectionLog;
	private final RuneProfileCollectionLog runeProfile;
	private final LootSessions lootSessions;
	private final ConfigManager configManager;
	private final Gson gson;
	// {lootSession}: only what's added since its session started (see LootSessions).
	private final boolean session;
	// The names of what each monster dropped, by lowercase monster (see lootedItems()).
	private final Map<String, List<String>> lootedByMonster = new ConcurrentHashMap<>();
	private final Args args;

	@Inject
	private LootLabelVariable(ItemManager itemManager, LootTrackerRecords lootTrackerRecords, RichText richText, CollectionLog collectionLog,
		RuneProfileCollectionLog runeProfile, LootSessions lootSessions, ConfigManager configManager, Gson gson)
	{
		this(itemManager, lootTrackerRecords, richText, collectionLog, runeProfile, lootSessions, configManager, gson, false);
	}

	LootLabelVariable(ItemManager itemManager, LootTrackerRecords lootTrackerRecords, RichText richText, CollectionLog collectionLog,
		RuneProfileCollectionLog runeProfile, LootSessions lootSessions, ConfigManager configManager, Gson gson, boolean session)
	{
		this.itemManager = itemManager;
		this.lootTrackerRecords = lootTrackerRecords;
		this.richText = richText;
		this.collectionLog = collectionLog;
		this.runeProfile = runeProfile;
		this.lootSessions = lootSessions;
		this.configManager = configManager;
		this.gson = gson;
		this.session = session;
		// The old lines=N is still accepted, but does nothing.
		this.args = Args.of(session ? "lootSession" : "loot")
			.describedAs("loot")
			.main(this::monsterNames, Args.BOSS_ICON)
			.number("items", "itemsPerLine")
			.flag("oneline", "hideVal")
			.value("showItem", this::lootedItems, Args.ITEM_ICON)
			.flag("itemsOnly")
			.number("lines")
			.unlisted("lines");
	}

	@Override
	public Pattern pattern()
	{
		return args.pattern();
	}

	@Override
	public Args args()
	{
		return args;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		Args.Options options = args.options(matcher);
		Loot loot = loot(matcher, options);
		if (loot == null)
		{
			return null;
		}
		return String.valueOf(options.flag("itemsOnly") ? loot.notableItems.size() : loot.gp);
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		Args.Options options = args.options(matcher);
		Loot loot = loot(matcher, options);
		if (loot == null)
		{
			return null;
		}
		int perLine = options.number("itemsPerLine", DEFAULT_ITEMS_PER_LINE);
		if (options.flag("itemsOnly"))
		{
			return options.flag("oneline") ? itemsText(loot.notableItems) : itemRows(loot.notableItems, perLine, false);
		}

		String header = loot.monster + " (" + richText.highlightValue(String.valueOf(loot.kills)) + ")"
			+ (options.flag("hideVal") ? "" : ": " + richText.highlightValue(QuantityFormatter.quantityToStackSize(loot.gp)));
		List<NotableItem> items = loot.notableItems;
		if (options.flag("oneline"))
		{
			return items.isEmpty() ? header : header + " | " + itemsText(items);
		}
		return header + itemRows(items, perLine, true);
	}

	// As {log} shows items: "<item=ID>xN" (N over 1), spaced, perLine to a row; afterTitle starts with a line break.
	private static String itemRows(List<NotableItem> items, int perLine, boolean afterTitle)
	{
		perLine = Math.max(1, perLine);
		StringBuilder sb = new StringBuilder();
		for (int shown = 0; shown < items.size(); shown++)
		{
			if (shown % perLine == 0)
			{
				if (shown > 0 || afterTitle)
				{
					sb.append("\\n");
				}
			}
			else
			{
				sb.append(' ');
			}
			NotableItem item = items.get(shown);
			sb.append("<item=").append(item.itemId).append('>');
			if (item.quantity > 1)
			{
				sb.append('x').append(item.quantity);
			}
		}
		return sb.toString();
	}

	// oneline's items: "2x <item=ID>". <item=ID> is GroundMarkerVariablesOverlay's own inline-icon tag.
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

	@Nullable
	private Loot loot(Matcher matcher, Args.Options options)
	{
		String monster = args.main(matcher);
		if (monster == null)
		{
			return null;
		}

		LootTrackerRecords.Record record = lootTrackerRecords.find(monster);
		if (session)
		{
			record = lootSessions.since(monster, record);
		}
		if (!record.found)
		{
			List<NotableItem> pets = new ArrayList<>();
			addLoggedPets(pets, record.name);
			return new Loot(record.name, 0, 0, pets);
		}

		// Every showItem=<name>, lower case.
		List<String> shownNames = options.values("showItem").stream().map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toList());
		List<NotableItem> pets = new ArrayList<>();
		List<NotableItem> named = new ArrayList<>();
		List<NotableItem> others = new ArrayList<>();
		for (Map.Entry<Integer, Integer> entry : record.quantities.entrySet())
		{
			// Collection log ids are unnoted.
			int canonicalId = itemManager.canonicalize(entry.getKey());
			long price = itemManager.getItemPrice(entry.getKey());
			NotableItem item = new NotableItem(entry.getKey(), entry.getValue(), price);
			if (collectionLog.isPet(canonicalId))
			{
				pets.add(item);
			}
			else if (!shownNames.isEmpty() && containsAny(itemManager.getItemComposition(canonicalId).getName(), shownNames))
			{
				named.add(item);
			}
			else if (price >= MIN_ITEM_VALUE || collectionLog.contains(canonicalId))
			{
				others.add(item);
			}
		}
		addLoggedPets(pets, record.name);
		Comparator<NotableItem> byValue = Comparator.comparingLong((NotableItem item) -> item.price).reversed();
		others.sort(byValue);
		int maxItems = options.number("items", DEFAULT_ITEMS_SHOWN);
		if (others.size() > maxItems)
		{
			others = new ArrayList<>(others.subList(0, maxItems));
		}
		// showItem= ones on top of items=N, in value order with the rest.
		others.addAll(named);
		others.sort(byValue);

		List<NotableItem> notableItems = new ArrayList<>(pets);
		notableItems.addAll(others);
		return new Loot(record.name, record.kills, record.gp, notableItems);
	}

	private static boolean containsAny(String itemName, List<String> names)
	{
		String lower = itemName.toLowerCase(Locale.ROOT);
		return names.stream().anyMatch(lower::contains);
	}

	// Pets RuneProfile's collection log page for monster has that Loot Tracker doesn't (e.g. cleared since). Not for a
	// session: they may be from before it.
	private void addLoggedPets(List<NotableItem> pets, String monster)
	{
		if (session)
		{
			return;
		}
		RuneProfileCollectionLog.Page page = runeProfile.page(monster).page();
		if (page == null)
		{
			return;
		}
		for (RuneProfileCollectionLog.Item item : page.items)
		{
			if (item.quantity > 0 && collectionLog.isPet(item.id)
				&& pets.stream().noneMatch(pet -> itemManager.canonicalize(pet.itemId) == item.id))
			{
				pets.add(new NotableItem(item.id, item.quantity, 0));
			}
		}
	}

	// Loot Tracker's monsters, most recent first, with their BossAliases short names, combined monsters, and players if
	// any were killed.
	private Collection<String> monsterNames()
	{
		List<String> monsters = lootMonsters();
		Set<String> names = new LinkedHashSet<>();
		for (String monster : monsters)
		{
			names.add(monster);
			for (String alias : BossAliases.aliases())
			{
				if (!alias.contains(" - ") && !alias.equalsIgnoreCase(monster) && BossAliases.resolve(alias).equalsIgnoreCase(monster))
				{
					names.add(alias);
				}
			}
		}
		for (Map.Entry<String, List<String>> combined : LootTrackerRecords.COMBINED_MONSTERS.entrySet())
		{
			if (monsters.stream().anyMatch(monster -> combined.getValue().stream().anyMatch(monster::equalsIgnoreCase)))
			{
				names.add(combined.getKey());
			}
		}
		String playerKeyPrefix = LOOT_KEY_PREFIX + LootTrackerRecords.PLAYER_TYPE + "_";
		if (!configManager.getRSProfileConfigurationKeys(LOOT_TRACKER_GROUP, configManager.getRSProfileKey(), playerKeyPrefix).isEmpty())
		{
			names.addAll(LootTrackerRecords.PLAYER_NAMES);
		}
		return names;
	}

	// Every monster Loot Tracker has recorded loot for, most recent first.
	private List<String> lootMonsters()
	{
		String profile = configManager.getRSProfileKey();
		List<Map.Entry<String, Instant>> byLast = new ArrayList<>();
		for (String key : configManager.getRSProfileConfigurationKeys(LOOT_TRACKER_GROUP, profile, LOOT_KEY_PREFIX))
		{
			// After the "<TYPE>_" (NPC/EVENT/PLAYER/...).
			String rest = key.substring(LOOT_KEY_PREFIX.length());
			int typeEnd = rest.indexOf('_');
			if (typeEnd < 0)
			{
				continue;
			}
			Instant last = gson.fromJson(configManager.getConfiguration(LOOT_TRACKER_GROUP, profile, key), LootLast.class).last;
			if (last != null)
			{
				byLast.add(new SimpleEntry<>(rest.substring(typeEnd + 1), last));
			}
		}
		byLast.sort((a, b) -> b.getValue().compareTo(a.getValue()));
		return byLast.stream().map(Map.Entry::getKey).collect(Collectors.toList());
	}

	// The names of what monster dropped, for showItem=. Looked up where they can be (client thread), else the last
	// names seen.
	private List<String> lootedItems(@Nullable String monster)
	{
		if (monster == null)
		{
			return List.of();
		}
		String key = monster.toLowerCase(Locale.ROOT);
		List<String> looted = lootTrackerRecords.itemNames(monster);
		if (looted != null)
		{
			lootedByMonster.put(key, looted);
			return looted;
		}
		return lootedByMonster.getOrDefault(key, List.of());
	}

	// The "last" field of Loot Tracker's (package-private) ConfigLoot, for Gson.
	private static final class LootLast
	{
		Instant last;
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
