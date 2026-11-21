package com.groundmarkervariables.variables.label;

import com.groundmarkervariables.variables.Args;
import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.BossAliases;
import com.groundmarkervariables.variables.support.BossKillCountTracker;
import com.groundmarkervariables.variables.support.CollectionLog;
import com.groundmarkervariables.variables.support.LootTrackerRecords;
import com.groundmarkervariables.variables.support.RuneProfileCollectionLog;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;

// {log <page>[, missing][, noicons][, iconsOnly][, itemsPerLine=N][, maxItems=N]} -> how many items of that collection
// log page the player has, from the RuneProfile plugin (see RuneProfileCollectionLog). Rich Text: "<page>:
// obtained/total" (not with iconsOnly), then the obtained items' icons (or with missing, the rest), itemsPerLine to a
// line, at most maxItems.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class LogLabelVariable implements LabelVariable
{
	private static final int DEFAULT_ITEMS_PER_LINE = 10;
	private static final String NOT_FOUND = "RuneProfile plugin not found";

	private final Client client;
	private final RuneProfileCollectionLog collectionLog;
	private final LootTrackerRecords lootTrackerRecords;
	private final ItemManager itemManager;
	private final BossKillCountTracker killCounts;
	private final RichText richText;
	private final CollectionLog pages;

	private final Args args = Args.of("log")
		.main(this::pageNames, Args.BOSS_ICON)
		.flag("missing", "noicons", "iconsOnly")
		.number("itemsPerLine", "maxItems");

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

	// Just the obtained count, for math and comparisons.
	@Override
	public String resolvePlain(Matcher matcher)
	{
		RuneProfileCollectionLog.Entry entry = entry(matcher);
		RuneProfileCollectionLog.Page page = entry == null ? null : entry.page();
		return page == null ? null : String.valueOf(withLoot(page, matcher).obtained);
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		RuneProfileCollectionLog.Entry entry = entry(matcher);
		if (entry == null)
		{
			return null;
		}
		RuneProfileCollectionLog.Page page = entry.page();
		if (page == null)
		{
			return entry.error() != null ? entry.error() : entry.timedOut() ? NOT_FOUND : richText.animatedDots(client.getTickCount());
		}
		page = withLoot(page, matcher);

		Args.Options options = args.options(matcher);
		boolean iconsOnly = options.flag("iconsOnly");
		String text = iconsOnly ? "" : richText.labeled(page.name, page.obtained + "/" + page.items.size()) + kcText(page, matcher);
		if (options.flag("noicons"))
		{
			return text;
		}

		boolean missing = options.flag("missing");
		int perLine = Math.max(1, options.number("itemsPerLine", DEFAULT_ITEMS_PER_LINE));
		int max = options.number("maxItems", Integer.MAX_VALUE);
		StringBuilder sb = new StringBuilder(text);
		int shown = 0;
		for (RuneProfileCollectionLog.Item item : page.items)
		{
			if ((item.quantity > 0) == missing)
			{
				continue;
			}
			if (shown >= max)
			{
				break;
			}
			// Without a title, no break before the first row.
			if (shown % perLine == 0 && (shown > 0 || !iconsOnly))
			{
				sb.append("\\n");
			}
			else if (shown % perLine != 0)
			{
				sb.append(' ');
			}
			sb.append("<item=").append(item.id).append('>');
			if (item.quantity > 1)
			{
				sb.append('x').append(item.quantity);
			}
			shown++;
		}
		return sb.toString();
	}

	// " kc: N" from {kc}'s tracker, by the page's name or as written; empty if unknown.
	private String kcText(RuneProfileCollectionLog.Page page, Matcher matcher)
	{
		Integer kc = killCounts.getKc(BossAliases.resolve(page.name));
		if (kc == null)
		{
			kc = killCounts.getKc(BossAliases.resolve(matcher.group(1).trim()));
		}
		return kc == null ? "" : " kc: " + richText.highlightValue(String.valueOf(kc));
	}

	// Add LootTracker data unknown by RuneProfile yet
	private RuneProfileCollectionLog.Page withLoot(RuneProfileCollectionLog.Page page, Matcher matcher)
	{
		String written = matcher.group(1).trim();
		LootTrackerRecords.Record record = lootTrackerRecords.find(page.name);
		if (!record.found && !page.name.equalsIgnoreCase(written))
		{
			record = lootTrackerRecords.find(written);
		}
		if (!record.found)
		{
			return page;
		}
		Map<Integer, Integer> unnoted = new HashMap<>();
		record.quantities.forEach((id, quantity) -> unnoted.merge(itemManager.canonicalize(id), quantity, Integer::sum));
		return page.withAtLeast(unnoted);
	}

	private RuneProfileCollectionLog.Entry entry(Matcher matcher)
	{
		String page = matcher.group(1).trim();
		return page.isEmpty() ? null : collectionLog.page(page);
	}

	// The collection log's page names, and BossAliases short names for one.
	private Collection<String> pageNames()
	{
		List<String> names = pages.pageNames();
		Set<String> all = new LinkedHashSet<>(names);
		for (String alias : BossAliases.aliases())
		{
			String boss = BossAliases.resolve(alias);
			if (!alias.contains(" - ") && !alias.equalsIgnoreCase(boss) && names.stream().anyMatch(boss::equalsIgnoreCase))
			{
				all.add(alias);
			}
		}
		return all;
	}
}
