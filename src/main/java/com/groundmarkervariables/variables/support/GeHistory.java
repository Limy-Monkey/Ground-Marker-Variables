package com.groundmarkervariables.variables.support;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

// The logged-in account's Grand Exchange trades, as core's Grand Exchange plugin records them (oldest first). Parsed
// again only when its saved history changes.
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class GeHistory
{
	private static final String GROUP = "grandexchange";
	private static final String KEY = "tradeHistory";

	public static final class Trade
	{
		public final boolean bought;
		public final int itemId;
		public final int quantity;
		// Average gp each.
		public final long price;
		public final Instant time;

		private Trade(boolean bought, int itemId, int quantity, long price, Instant time)
		{
			this.bought = bought;
			this.itemId = itemId;
			this.quantity = quantity;
			this.price = price;
			this.time = time;
		}
	}

	private final ConfigManager configManager;
	private String parsedJson;
	private List<Trade> trades = List.of();

	public synchronized List<Trade> trades()
	{
		String json = configManager.getRSProfileConfiguration(GROUP, KEY);
		if (!Objects.equals(json, parsedJson))
		{
			parsedJson = json;
			trades = parse(json);
		}
		return trades;
	}

	private static List<Trade> parse(String json)
	{
		if (json == null)
		{
			return List.of();
		}
		List<Trade> parsed = new ArrayList<>();
		try
		{
			for (JsonElement element : new JsonParser().parse(json).getAsJsonArray())
			{
				JsonObject trade = element.getAsJsonObject();
				parsed.add(new Trade(trade.get("b").getAsBoolean(), trade.get("i").getAsInt(), trade.get("q").getAsInt(),
					trade.get("p").getAsLong(), Instant.ofEpochMilli(trade.get("t").getAsLong())));
			}
		}
		catch (RuntimeException e)
		{
			log.warn("Couldn't read the Grand Exchange trade history", e);
		}
		return Collections.unmodifiableList(parsed);
	}
}
