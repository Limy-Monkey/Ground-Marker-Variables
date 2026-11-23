package com.groundmarkervariables.variables.label;

import com.groundmarkervariables.variables.Args;
import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.BossAliases;
import com.groundmarkervariables.variables.support.CollectionLog;
import com.groundmarkervariables.variables.support.LootSessions;
import com.groundmarkervariables.variables.support.LootTrackerRecords;
import com.groundmarkervariables.variables.support.RuneProfileCollectionLog;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.QuantityFormatter;

// {loot <monster>, ...options} -> gp value of loot Loot Tracker has recorded for <monster> (plus each addLoot=), or 0 if none. Rich
// Text: items shown like {log}. Options: see variables.json.
public class LootLabelVariable implements LabelVariable
{
	// Rich Text lists every pet, then up to items=N collection log or MIN_ITEM_VALUE+ items.
	private static final long MIN_ITEM_VALUE = 300_000;
	private static final int DEFAULT_ITEMS_SHOWN = 29;
	private static final int DEFAULT_ITEMS_PER_LINE = 10;

	// Fields, so LootSession needs no constructor of its own.
	@Inject
	private ItemManager itemManager;
	@Inject
	private LootTrackerRecords lootTrackerRecords;
	@Inject
	private RichText richText;
	@Inject
	private CollectionLog collectionLog;
	@Inject
	private RuneProfileCollectionLog runeProfile;
	@Inject
	private LootSessions lootSessions;

	// {lootSession}: only what's added since its session started (see LootSessions).
	private final boolean session;
	// The names of what each monster dropped, by lowercase monster (see lootedItems()).
	private final Map<String, List<String>> lootedByMonster = new ConcurrentHashMap<>();
	private final Args args;

	@Inject
	private LootLabelVariable()
	{
		this(false);
	}

	private LootLabelVariable(boolean session)
	{
		this.session = session;
		// The old lines=N is still accepted, but does nothing.
		this.args = Args.of(session ? "lootSession" : "loot")
			.describedAs("loot")
			.main(this::monsterNames, Args.BOSS_ICON)
			.number("items", "itemsPerLine")
			.flag("oneline", "hideVal")
			.value("showItem", this::lootedItems, Args.ITEM_ICON)
			.flag("itemsOnly")
			.value("addLoot", monster -> monsterNames(), Args.BOSS_ICON)
			.value("addLog", monster -> collectionLog.pageSuggestions(), Args.BOSS_ICON)
			.repeatable("showItem", "addLoot", "addLog")
			.number("lines")
			.unlisted("lines");
	}

	// {lootSession <monster>, ...}: {loot}, counting only from its first use (see LootSessions). Reset by Shift
	// right-click > Tile Variables > Reset Session.
	public static class LootSession extends LootLabelVariable
	{
		@Inject
		private LootSession()
		{
			super(true);
		}
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
		// oneline: one row, however many items.
		if (options.flag("oneline"))
		{
			perLine = Integer.MAX_VALUE;
		}
		if (options.flag("itemsOnly"))
		{
			return itemRows(loot.notableItems, perLine, false);
		}

		String header = loot.monster + " (" + richText.highlightValue(String.valueOf(loot.kills)) + ")"
			+ (options.flag("hideVal") ? "" : ": " + richText.highlightValue(QuantityFormatter.quantityToStackSize(loot.gp)));
		List<NotableItem> items = loot.notableItems;
		if (options.flag("oneline"))
		{
			return items.isEmpty() ? header : header + " | " + itemRows(items, perLine, false);
		}
		return header + itemRows(items, perLine, true);
	}

	private static String itemRows(List<NotableItem> items, int perLine, boolean afterTitle)
	{
		return RichText.itemRows(items, item -> item.itemId, item -> item.quantity, perLine, Integer.MAX_VALUE, afterTitle);
	}

	@Nullable
	private Loot loot(Matcher matcher, Args.Options options)
	{
		String monster = args.main(matcher);
		if (monster == null)
		{
			return null;
		}

		LootTrackerRecords.Record record = record(monster);
		for (String added : options.values("addLoot"))
		{
			record = record.plus(record(added));
		}
		Map<Integer, Integer> quantities = new LinkedHashMap<>(record.quantities);
		addLogged(quantities, record.name, options.values("addLog"));

		// Every showItem=<name>, lower case.
		List<String> shownNames = options.values("showItem").stream().map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toList());
		List<NotableItem> pets = new ArrayList<>();
		List<NotableItem> named = new ArrayList<>();
		List<NotableItem> others = new ArrayList<>();
		for (Map.Entry<Integer, Integer> entry : quantities.entrySet())
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

	private LootTrackerRecords.Record record(String monster)
	{
		LootTrackerRecords.Record record = lootTrackerRecords.find(monster);
		return session ? lootSessions.since(monster, record) : record;
	}

	private static boolean containsAny(String itemName, List<String> names)
	{
		String lower = itemName.toLowerCase(Locale.ROOT);
		return names.stream().anyMatch(lower::contains);
	}

	// From RuneProfile's collection log: every obtained item on each addLog= page, and the pets on each monster's (with
	// addLoot=) page, at the higher of its and Loot Tracker's quantity: Loot Tracker may have been cleared since. Not for
	// a session: the log may be from before it.
	private void addLogged(Map<Integer, Integer> quantities, String monster, List<String> logPages)
	{
		if (session)
		{
			return;
		}
		Set<String> seen = new HashSet<>();
		logPages.forEach(page -> addPage(quantities, page, true, seen));
		for (String part : monster.split(" \\+ "))
		{
			addPage(quantities, part, false, seen);
		}
	}

	// written's page's pets, or with all every obtained item; each page once.
	private void addPage(Map<Integer, Integer> quantities, String written, boolean all, Set<String> seen)
	{
		RuneProfileCollectionLog.Page page = runeProfile.page(written).page();
		if (page == null || !seen.add(page.name))
		{
			return;
		}
		for (RuneProfileCollectionLog.Item item : page.items)
		{
			if (item.quantity > 0 && (all || collectionLog.isPet(item.id)))
			{
				// Loot Tracker's id may be noted.
				int id = quantities.keySet().stream().filter(dropped -> itemManager.canonicalize(dropped) == item.id).findFirst().orElse(item.id);
				quantities.merge(id, item.quantity, Math::max);
			}
		}
	}

	// Loot Tracker's monsters, most recent first, each followed by its BossAliases short names; combined monsters; and
	// players if any were killed.
	private Collection<String> monsterNames()
	{
		List<String> monsters = lootTrackerRecords.monsters();
		Set<String> names = new LinkedHashSet<>();
		for (String monster : monsters)
		{
			names.add(monster);
			names.addAll(BossAliases.aliasesOf(List.of(monster)));
		}
		for (Map.Entry<String, List<String>> combined : LootTrackerRecords.COMBINED_MONSTERS.entrySet())
		{
			if (monsters.stream().anyMatch(monster -> combined.getValue().stream().anyMatch(monster::equalsIgnoreCase)))
			{
				names.add(combined.getKey());
			}
		}
		if (lootTrackerRecords.hasPlayerLoot())
		{
			names.addAll(LootTrackerRecords.PLAYER_NAMES);
		}
		return names;
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
