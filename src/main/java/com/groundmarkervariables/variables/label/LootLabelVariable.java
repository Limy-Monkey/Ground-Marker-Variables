package com.groundmarkervariables.variables.label;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.CollectionLog;
import com.groundmarkervariables.variables.support.LootSessions;
import com.groundmarkervariables.variables.support.LootTrackerRecords;
import com.groundmarkervariables.variables.support.RuneProfileCollectionLog;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.QuantityFormatter;

// {loot <monster>[, items=N][, itemsPerLine=N][, oneline][, hideVal][, showItem=<name>]...[, itemsOnly]} -> gp value of loot Loot Tracker has recorded
// for <monster>, or 0 if none. Rich Text: items shown like {log}, or after the header with oneline.
public class LootLabelVariable implements LabelVariable
{
	// "{<name> <monster>, <options>}": excludes ?:<>=! so a trailing comparator (e.g. "{loot monster > 10000}") isn't
	// swallowed into the monster name. Group 2 is the options; the old lines=N is still accepted, but does nothing.
	private static Pattern pattern(String name)
	{
		return Pattern.compile("\\{" + name
			+ "\\s+([^{}?:<>=!]+?)((?:\\s*,?\\s*(?:itemsOnly|oneline|hideVal|showItem=[^,{}?:<>=!]+|(?:items|itemsPerLine|lines)=\\d+))*)\\s*,?\\s*\\}",
			Pattern.CASE_INSENSITIVE);
	}
	private static final Pattern OPTION = Pattern.compile("(items|itemsPerLine)=(\\d+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern ITEMS_ONLY = Pattern.compile("itemsOnly", Pattern.CASE_INSENSITIVE);
	private static final Pattern ONELINE = Pattern.compile("\\boneline\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern HIDE_VAL = Pattern.compile("\\bhideVal\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern SHOW_ITEM = Pattern.compile("showItem=([^,{}?:<>=!]+)", Pattern.CASE_INSENSITIVE);
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
	// {lootSession}: only what's added since its session started (see LootSessions).
	private final boolean session;
	private final Pattern pattern;

	@Inject
	private LootLabelVariable(ItemManager itemManager, LootTrackerRecords lootTrackerRecords, RichText richText, CollectionLog collectionLog,
		RuneProfileCollectionLog runeProfile, LootSessions lootSessions)
	{
		this(itemManager, lootTrackerRecords, richText, collectionLog, runeProfile, lootSessions, false);
	}

	LootLabelVariable(ItemManager itemManager, LootTrackerRecords lootTrackerRecords, RichText richText, CollectionLog collectionLog,
		RuneProfileCollectionLog runeProfile, LootSessions lootSessions, boolean session)
	{
		this.itemManager = itemManager;
		this.lootTrackerRecords = lootTrackerRecords;
		this.richText = richText;
		this.collectionLog = collectionLog;
		this.runeProfile = runeProfile;
		this.lootSessions = lootSessions;
		this.session = session;
		this.pattern = pattern(session ? "lootSession" : "loot");
	}

	@Override
	public Pattern pattern()
	{
		return pattern;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		Loot loot = loot(matcher);
		if (loot == null)
		{
			return null;
		}
		return String.valueOf(itemsOnly(matcher) ? loot.notableItems.size() : loot.gp);
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		Loot loot = loot(matcher);
		if (loot == null)
		{
			return null;
		}
		if (itemsOnly(matcher))
		{
			return itemsOnlyText(loot.notableItems, matcher);
		}

		String header = loot.monster + " (" + richText.highlightValue(String.valueOf(loot.kills)) + ")"
			+ (HIDE_VAL.matcher(matcher.group(2)).find() ? "" : ": " + richText.highlightValue(QuantityFormatter.quantityToStackSize(loot.gp)));
		List<NotableItem> items = loot.notableItems;
		if (oneline(matcher))
		{
			return items.isEmpty() ? header : header + " | " + itemsText(items);
		}
		return header + itemRows(items, option(matcher, "itemsPerLine", DEFAULT_ITEMS_PER_LINE), true);
	}

	private static boolean itemsOnly(Matcher matcher)
	{
		return ITEMS_ONLY.matcher(matcher.group(2)).find();
	}

	private static boolean oneline(Matcher matcher)
	{
		return ONELINE.matcher(matcher.group(2)).find();
	}

	// Just the items: on one line (oneline), else in rows.
	private static String itemsOnlyText(List<NotableItem> items, Matcher matcher)
	{
		return oneline(matcher) ? itemsText(items) : itemRows(items, option(matcher, "itemsPerLine", DEFAULT_ITEMS_PER_LINE), false);
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

		List<String> shownNames = shownNames(matcher);
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
		int maxItems = option(matcher, "items", DEFAULT_ITEMS_SHOWN);
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

	// Every showItem=<name>, lower case.
	private static List<String> shownNames(Matcher matcher)
	{
		List<String> names = new ArrayList<>();
		Matcher showItem = SHOW_ITEM.matcher(matcher.group(2));
		while (showItem.find())
		{
			names.add(showItem.group(1).trim().toLowerCase(Locale.ROOT));
		}
		return names;
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
