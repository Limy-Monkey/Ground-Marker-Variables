package com.groundmarkervariables.variables.label;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.RuneProfileCollectionLog;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;

// {log <page>[, missing][, noicons][, iconsOnly][, itemsPerLine=N][, maxItems=N]} -> how many items of that collection
// log page the player has, from the RuneProfile plugin (see RuneProfileCollectionLog). Rich Text: "<page>:
// obtained/total" (not with iconsOnly), then the obtained items' icons (or with missing, the rest), itemsPerLine to a
// line, at most maxItems.
public class LogLabelVariable implements LabelVariable
{
	// Excludes ?:<>=! as {kc} does, and "," which starts the options (group 2).
	private static final Pattern PATTERN = Pattern.compile(
		"\\{log\\s+([^{}?:<>=!,]+?)((?:\\s*,\\s*(?:missing|noicons|iconsOnly|(?:itemsPerLine|maxItems)=\\d+))*)\\s*\\}",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern MISSING = Pattern.compile("\\bmissing\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern NO_ICONS = Pattern.compile("\\bnoicons\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern ICONS_ONLY = Pattern.compile("\\biconsOnly\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern ITEMS_PER_LINE = Pattern.compile("itemsPerLine=(\\d+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern MAX_ITEMS = Pattern.compile("maxItems=(\\d+)", Pattern.CASE_INSENSITIVE);
	private static final int DEFAULT_ITEMS_PER_LINE = 10;
	private static final String NOT_FOUND = "RuneProfile plugin not found";

	private final Client client;
	private final RuneProfileCollectionLog collectionLog;
	private final RichText richText;

	@Inject
	private LogLabelVariable(Client client, RuneProfileCollectionLog collectionLog, RichText richText)
	{
		this.client = client;
		this.collectionLog = collectionLog;
		this.richText = richText;
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	// Just the obtained count, for math and comparisons.
	@Override
	public String resolvePlain(Matcher matcher)
	{
		RuneProfileCollectionLog.Entry entry = entry(matcher);
		RuneProfileCollectionLog.Page page = entry == null ? null : entry.page();
		return page == null ? null : String.valueOf(page.obtained);
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

		String options = matcher.group(2);
		boolean iconsOnly = ICONS_ONLY.matcher(options).find();
		String text = iconsOnly ? "" : richText.labeled(page.name, page.obtained + "/" + page.items.size());
		if (NO_ICONS.matcher(options).find())
		{
			return text;
		}

		boolean missing = MISSING.matcher(options).find();
		int perLine = Math.max(1, option(options, ITEMS_PER_LINE, DEFAULT_ITEMS_PER_LINE));
		int max = option(options, MAX_ITEMS, Integer.MAX_VALUE);
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
			sb.append("<item=").append(item.id).append('>');
			shown++;
		}
		return sb.toString();
	}

	private RuneProfileCollectionLog.Entry entry(Matcher matcher)
	{
		String page = matcher.group(1).trim();
		return page.isEmpty() ? null : collectionLog.page(page);
	}

	private static int option(String options, Pattern pattern, int fallback)
	{
		Matcher option = pattern.matcher(options);
		if (!option.find())
		{
			return fallback;
		}
		try
		{
			return Integer.parseInt(option.group(1));
		}
		catch (NumberFormatException e)
		{
			return fallback;
		}
	}
}
