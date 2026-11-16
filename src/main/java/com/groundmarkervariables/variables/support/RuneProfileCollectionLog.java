package com.groundmarkervariables.variables.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;

// The local player's collection log pages, from the RuneProfile plugin's PluginMessage API (see its
// docs/external-plugin-messaging.md). Each page is requested once, kept, and requested again when RuneProfile
// says the log changed. Nothing answers if RuneProfile isn't running, so a page unanswered for TIMEOUT_MS counts
// as RuneProfile missing.
@Singleton
public class RuneProfileCollectionLog
{
	private static final String NAMESPACE = "runeprofile";
	private static final String REQUEST = "collection-log-request";
	private static final String RESPONSE = "collection-log";
	private static final String CHANGED = "collection-log-changed";
	private static final String SOURCE_PLUGIN = "Ground Marker Variables";
	private static final String REQUEST_ID_PREFIX = "ground-marker-variables:";
	private static final long TIMEOUT_MS = 10_000;
	// Retry delays after RuneProfile answers "not logged in" or a passing error.
	private static final long NOT_LOGGED_IN_RETRY_MS = 5_000;
	private static final long ERROR_RETRY_MS = 60_000;

	private final EventBus eventBus;
	private final ClientThread clientThread;
	// Keyed by the page as written, lower case.
	private final Map<String, Entry> pages = new ConcurrentHashMap<>();

	@Inject
	private RuneProfileCollectionLog(EventBus eventBus, ClientThread clientThread)
	{
		this.eventBus = eventBus;
		this.clientThread = clientThread;
	}

	// A loaded page; its total is items.size().
	public static final class Page
	{
		public final String name;
		public final int obtained;
		public final List<Item> items;

		private Page(String name, int obtained, List<Item> items)
		{
			this.name = name;
			this.obtained = obtained;
			this.items = items;
		}

		// This page with each item's quantity at least atLeast's (item ID -> quantity), e.g. drops RuneProfile
		// hasn't synced yet.
		public Page withAtLeast(Map<Integer, Integer> atLeast)
		{
			List<Item> merged = new ArrayList<>(items.size());
			int count = 0;
			for (Item item : items)
			{
				int quantity = Math.max(item.quantity, atLeast.getOrDefault(item.id, 0));
				merged.add(new Item(item.id, quantity));
				count += quantity > 0 ? 1 : 0;
			}
			return new Page(name, count, Collections.unmodifiableList(merged));
		}
	}

	public static final class Item
	{
		public final int id;
		public final int quantity;

		private Item(int id, int quantity)
		{
			this.id = id;
			this.quantity = quantity;
		}
	}

	// A page's state: the last loaded page (kept while it's requested again), else an error, else still loading.
	public static final class Entry
	{
		private final String written;
		private String query;
		private boolean aliasTried;
		private long requestedAt;
		private boolean answered;
		// Timed out once; stays so (re-requested now and then) until RuneProfile answers.
		private boolean missing;
		private long retryAt;
		@Nullable
		private volatile Page page;
		@Nullable
		private volatile String error;

		private Entry(String written)
		{
			this.written = written;
			this.query = written;
		}

		@Nullable
		public Page page()
		{
			return page;
		}

		// RuneProfile's message for a page it couldn't load.
		@Nullable
		public String error()
		{
			return error;
		}

		// No answer in time: RuneProfile isn't running.
		public boolean timedOut()
		{
			return missing;
		}

		private boolean unanswered(long now, long after)
		{
			return page == null && error == null && !answered && now - requestedAt > after;
		}
	}

	public void startUp()
	{
		eventBus.register(this);
	}

	public void shutDown()
	{
		eventBus.unregister(this);
		pages.clear();
	}

	// The page's state, requesting it the first time and when a retry is due; unanswered, again every
	// ERROR_RETRY_MS in case RuneProfile was turned on since.
	public Entry page(String written)
	{
		String key = written.toLowerCase(Locale.ROOT);
		Entry entry = pages.get(key);
		long now = System.currentTimeMillis();
		if (entry == null)
		{
			entry = new Entry(written);
			pages.put(key, entry);
			request(key, entry);
			return entry;
		}

		if (entry.unanswered(now, TIMEOUT_MS))
		{
			entry.missing = true;
		}
		if ((entry.retryAt > 0 && now >= entry.retryAt) || entry.unanswered(now, ERROR_RETRY_MS))
		{
			entry.retryAt = 0;
			request(key, entry);
		}
		return entry;
	}

	private void request(String key, Entry entry)
	{
		entry.requestedAt = System.currentTimeMillis();
		entry.answered = false;
		Map<String, Object> data = Map.of("sourcePlugin", SOURCE_PLUGIN, "page", entry.query, "requestId", REQUEST_ID_PREFIX + key);
		// Not mid-resolve: RuneProfile handles the request inside post().
		clientThread.invokeLater(() -> eventBus.post(new PluginMessage(NAMESPACE, REQUEST, data)));
	}

	// Another account may log in next.
	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			pages.clear();
		}
	}

	@Subscribe
	public void onPluginMessage(PluginMessage message)
	{
		if (!NAMESPACE.equals(message.getNamespace()))
		{
			return;
		}

		if (CHANGED.equals(message.getName()))
		{
			pages.forEach(this::request);
			return;
		}

		Object requestId = message.getData().get("requestId");
		if (!RESPONSE.equals(message.getName()) || !(requestId instanceof String) || !((String) requestId).startsWith(REQUEST_ID_PREFIX))
		{
			return;
		}
		String key = ((String) requestId).substring(REQUEST_ID_PREFIX.length());
		Entry entry = pages.get(key);
		if (entry != null)
		{
			answer(key, entry, message.getData());
		}
	}

	private void answer(String key, Entry entry, Map<String, Object> data)
	{
		entry.answered = true;
		entry.missing = false;
		if (Boolean.TRUE.equals(data.get("success")))
		{
			entry.page = parsePage(data);
			entry.error = null;
			return;
		}

		Object reason = data.get("reason");
		Object error = data.get("error");
		String alias = BossAliases.resolve(entry.written);
		if ("PAGE_NOT_FOUND".equals(reason) && !entry.aliasTried && !alias.equalsIgnoreCase(entry.written))
		{
			// RuneProfile's page names and aliases aren't ours; try the boss our alias stands for.
			entry.aliasTried = true;
			entry.query = alias;
			request(key, entry);
			return;
		}

		if ("NOT_LOGGED_IN".equals(reason))
		{
			entry.retryAt = System.currentTimeMillis() + NOT_LOGGED_IN_RETRY_MS;
			return;
		}
		if ("ERROR".equals(reason))
		{
			entry.retryAt = System.currentTimeMillis() + ERROR_RETRY_MS;
		}
		// A loaded page outlasts a later failure.
		if (entry.page == null)
		{
			entry.error = error instanceof String ? (String) error : "Collection log unavailable";
		}
	}

	private static Page parsePage(Map<String, Object> data)
	{
		List<Item> items = new ArrayList<>();
		int obtained = 0;
		Object list = data.get("items");
		if (list instanceof List)
		{
			for (Object element : (List<?>) list)
			{
				if (!(element instanceof Map))
				{
					continue;
				}
				Map<?, ?> item = (Map<?, ?>) element;
				if (item.get("id") instanceof Number)
				{
					int quantity = item.get("quantity") instanceof Number ? ((Number) item.get("quantity")).intValue() : 0;
					items.add(new Item(((Number) item.get("id")).intValue(), quantity));
					obtained += quantity > 0 ? 1 : 0;
				}
			}
		}
		Object name = data.get("name");
		return new Page(name instanceof String ? (String) name : String.valueOf(data.get("page")), obtained, Collections.unmodifiableList(items));
	}
}
