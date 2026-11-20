package com.groundmarkervariables.variables.label;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.ConditionEvaluator;
import com.groundmarkervariables.variables.support.DateLiteralParser;
import com.groundmarkervariables.variables.support.GeHistory;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.QuantityFormatter;

// {geHistory[, bought|sold][, item=<name>][, qty|profit|investment][, startDate=<date>][, limitTransactions=N]}: core's
// Grand Exchange trade history (GeHistory). Rich Text: a table of trades, newest first (qty: grouped by item and side), or
// the profit or investment line. Plain Text: with item=, gp sold - gp bought (bought/sold: that side alone), quantity bought - sold (qty), or the
// % RuneLite's price is above the average bought price (investment); profit, realised profit. Otherwise empty.
@Singleton
public class GeHistoryLabelVariable implements LabelVariable
{
	private static final String OPTION = "(?:bought|sold|qty|profit|investment|limitTransactions=\\d+|(?:item|startDate)=[^,{}?:<>=!]+?)";
	private static final Pattern PATTERN = Pattern.compile("\\{geHistory((?:[\\s,]+" + OPTION + ")*)\\s*\\}", Pattern.CASE_INSENSITIVE);
	private static final Pattern DAYS = Pattern.compile("-?\\s*(\\d+)\\s*days?", Pattern.CASE_INSENSITIVE);
	private static final int DEFAULT_LIMIT = 5;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MMM d h:mm a", Locale.ENGLISH).withZone(ZoneId.systemDefault());

	private final Client client;
	private final ClientThread clientThread;
	private final GeHistory geHistory;
	private final ItemManager itemManager;
	private final RichText richText;
	// Item ID -> name, client thread only.
	private final Map<Integer, String> names = new HashMap<>();
	// The traded items' names, newest first (see itemNames()).
	private volatile List<String> itemNames = List.of();

	@Inject
	private GeHistoryLabelVariable(Client client, ClientThread clientThread, GeHistory geHistory, ItemManager itemManager, RichText richText)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.geHistory = geHistory;
		this.itemManager = itemManager;
		this.richText = richText;
	}

	// The traded items' names, newest first, for item= suggestions. Worked out on the client thread; elsewhere, the last
	// ones, with an update queued.
	public List<String> itemNames()
	{
		if (!client.isClientThread())
		{
			clientThread.invoke(this::itemNames);
			return itemNames;
		}
		Set<String> traded = new LinkedHashSet<>();
		List<GeHistory.Trade> trades = geHistory.trades();
		for (int i = trades.size() - 1; i >= 0; i--)
		{
			traded.add(name(trades.get(i).itemId));
		}
		itemNames = List.copyOf(traded);
		return itemNames;
	}

	// The options: comma-separated, the first after a space.
	private static final class Options
	{
		@Nullable Boolean bought;
		@Nullable String item;
		boolean qty;
		boolean profit;
		boolean investment;
		@Nullable Instant start;
		int limit = DEFAULT_LIMIT;

		Options(String text)
		{
			for (String token : text.split(","))
			{
				String option = token.trim();
				String lower = option.toLowerCase(Locale.ROOT);
				if (lower.startsWith("item="))
				{
					item = option.substring("item=".length()).trim();
				}
				else if (lower.startsWith("startdate="))
				{
					start = start(option.substring("startDate=".length()).trim());
				}
				else if (lower.startsWith("limittransactions="))
				{
					limit = Integer.parseInt(lower.substring("limitTransactions=".length()));
				}
				else
				{
					for (String word : lower.split("\\s+"))
					{
						bought = word.equals("bought") ? Boolean.TRUE : word.equals("sold") ? Boolean.FALSE : bought;
						qty |= word.equals("qty");
						profit |= word.equals("profit");
						investment |= word.equals("investment");
					}
				}
			}
		}

		// "today", "-N days" / "N days" (back from now), or a date {date} math accepts; null if none of those.
		@Nullable
		private static Instant start(String value)
		{
			if (value.equalsIgnoreCase("today"))
			{
				return LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
			}
			Matcher days = DAYS.matcher(value);
			if (days.matches())
			{
				return Instant.now().minus(Long.parseLong(days.group(1)), ChronoUnit.DAYS);
			}
			LocalDate date = DateLiteralParser.parse(value);
			return date == null ? null : date.atStartOfDay(ZoneId.systemDefault()).toInstant();
		}
	}

	// One item's totals across its trades.
	private static final class Totals
	{
		long boughtQty;
		long soldQty;
		long boughtGp;
		long soldGp;
		int itemId = -1;

		long held()
		{
			return boughtQty - soldQty;
		}

		// Average cost: each sold one that was bought, at its average sell price minus its average buy price.
		long realisedProfit()
		{
			long matched = Math.min(boughtQty, soldQty);
			return matched == 0 ? 0 : Math.round(matched * ((double) soldGp / soldQty - (double) boughtGp / boughtQty));
		}
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		Options options = new Options(matcher.group(1));
		if (options.profit)
		{
			return String.valueOf(totals(options).values().stream().mapToLong(Totals::realisedProfit).sum());
		}
		if (options.item == null)
		{
			return "";
		}
		Totals totals = itemTotals(options);
		if (options.investment)
		{
			Double change = change(totals);
			return change == null ? ConditionEvaluator.NULL : String.valueOf(Math.round(change * 10) / 10.0);
		}
		if (options.qty)
		{
			return String.valueOf(options.bought == null ? totals.held() : options.bought ? totals.boughtQty : totals.soldQty);
		}
		return String.valueOf(options.bought == null ? totals.soldGp - totals.boughtGp : options.bought ? totals.boughtGp : totals.soldGp);
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		Options options = new Options(matcher.group(1));
		if (options.profit)
		{
			Map<Integer, Totals> totals = totals(options);
			long profit = totals.values().stream().mapToLong(Totals::realisedProfit).sum();
			String text = title(options) + " profit: " + richText.highlightValue((profit > 0 ? "+" : "") + QuantityFormatter.quantityToStackSize(profit));
			// One item's held count; across items they don't add up.
			return options.item == null ? text : text + " (" + QuantityFormatter.formatNumber(itemTotals(options).held()) + " held)";
		}
		if (options.investment && options.item != null)
		{
			Totals totals = itemTotals(options);
			String text = title(options) + " price: " + richText.highlightValue(QuantityFormatter.quantityToStackSize(price(totals)));
			Double change = change(totals);
			return change == null ? text : text + " (" + (change > 0 ? "+" : "") + Math.round(change) + "%)";
		}
		return table(options);
	}

	// A table row: one trade, or with qty every trade of one item on one side.
	private static final class Row
	{
		final boolean bought;
		final int itemId;
		long quantity;
		long gp;
		int trades;
		// The newest trade's time.
		final Instant time;

		Row(GeHistory.Trade trade)
		{
			bought = trade.bought;
			itemId = trade.itemId;
			time = trade.time;
		}

		void add(GeHistory.Trade trade)
		{
			quantity += trade.quantity;
			gp += trade.price * trade.quantity;
			trades++;
		}
	}

	// The matching trades (with qty, grouped by item and side), newest first, one per row:
	// "<bought/sold>\t<qty>\t<icon> <name>\t<total>", the item carrying a tooltip of the (average) price each and time;
	// at most limitTransactions.
	private String table(Options options)
	{
		StringBuilder text = new StringBuilder(title(options));
		if (options.bought != null)
		{
			text.append(options.bought ? " (bought)" : " (sold)");
		}
		Map<String, Row> rows = new LinkedHashMap<>();
		List<GeHistory.Trade> trades = geHistory.trades();
		for (int i = trades.size() - 1; i >= 0; i--)
		{
			GeHistory.Trade trade = trades.get(i);
			if (!included(trade, options) || (options.bought != null && trade.bought != options.bought))
			{
				continue;
			}
			String key = options.qty ? trade.itemId + (trade.bought ? "b" : "s") : String.valueOf(i);
			if (!rows.containsKey(key) && rows.size() >= options.limit)
			{
				continue;
			}
			rows.computeIfAbsent(key, k -> new Row(trade)).add(trade);
		}
		for (Row row : rows.values())
		{
			String each = QuantityFormatter.formatNumber(Math.round((double) row.gp / row.quantity)) + " gp each"
				+ (row.trades > 1 ? " (average of " + row.trades + " trades, last " : ", ") + TIME.format(row.time)
				+ (row.trades > 1 ? ")" : "");
			text.append("\\n<sprite=").append(row.bought ? SpriteID.GeSmallicons.COLLECTION_BOX_OFFER_BUY
					: SpriteID.GeSmallicons.COLLECTION_BOX_OFFER_SELL).append('>')
				.append("\\t").append(QuantityFormatter.formatNumber(row.quantity))
				.append("\\t{tip=").append(each).append("}<item=").append(row.itemId).append("> ")
				.append(name(row.itemId)).append("{/tip}")
				.append("\\t").append(richText.highlightValue(QuantityFormatter.quantityToStackSize(row.gp)));
		}
		return rows.isEmpty() ? text.append("\\nNo trades").toString() : text.toString();
	}

	// The item's name as the game spells it once it's been traded, else as typed; "GE history" without item=.
	private String title(Options options)
	{
		if (options.item == null)
		{
			return "GE history";
		}
		Totals totals = itemTotals(options);
		return totals.itemId < 0 ? options.item : name(totals.itemId);
	}

	// Each traded item's totals (both sides), by item ID.
	private Map<Integer, Totals> totals(Options options)
	{
		Map<Integer, Totals> byItem = new LinkedHashMap<>();
		for (GeHistory.Trade trade : geHistory.trades())
		{
			if (!included(trade, options))
			{
				continue;
			}
			Totals totals = byItem.computeIfAbsent(trade.itemId, id -> new Totals());
			totals.itemId = trade.itemId;
			if (trade.bought)
			{
				totals.boughtQty += trade.quantity;
				totals.boughtGp += trade.price * trade.quantity;
			}
			else
			{
				totals.soldQty += trade.quantity;
				totals.soldGp += trade.price * trade.quantity;
			}
		}
		return byItem;
	}

	// The item= item's totals; empty if it hasn't been traded.
	private Totals itemTotals(Options options)
	{
		return totals(options).values().stream().findFirst().orElseGet(Totals::new);
	}

	private long price(Totals totals)
	{
		return totals.itemId < 0 ? 0 : itemManager.getItemPrice(totals.itemId);
	}

	// The % RuneLite's price is above the weighted average bought price; null if none was bought.
	@Nullable
	private Double change(Totals totals)
	{
		if (totals.boughtQty == 0)
		{
			return null;
		}
		double average = (double) totals.boughtGp / totals.boughtQty;
		return (price(totals) - average) / average * 100;
	}

	// Within startDate and the item= item, if given.
	private boolean included(GeHistory.Trade trade, Options options)
	{
		return (options.start == null || !trade.time.isBefore(options.start))
			&& (options.item == null || loose(name(trade.itemId)).equals(loose(options.item)));
	}

	private String name(int itemId)
	{
		return names.computeIfAbsent(itemId, id -> itemManager.getItemComposition(id).getName());
	}

	private static String loose(String name)
	{
		return name.trim().toLowerCase(Locale.ROOT).replace("'", "");
	}
}
