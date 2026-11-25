package com.groundmarkervariables.markers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;

// Deleted things by key, newest first, until restored, deleted permanently (WAIT after going in at the earliest) or the
// client closes. Memory only.
public class RecyclingBin<K, V> {
	private static final Duration WAIT = Duration.ofMinutes(30);

	@RequiredArgsConstructor
	private static final class Item<V> {
		final V value;
		final long at;
	}

	private final Map<K, Item<V>> items = new LinkedHashMap<>();

	// Newest, so last in.
	public void add(K key, V value) {
		items.remove(key);
		items.put(key, new Item<>(value, System.currentTimeMillis()));
	}

	public List<V> values() {
		List<V> values = new ArrayList<>();
		items.values().forEach(item -> values.add(0, item.value));
		return values;
	}

	public int size() {
		return items.size();
	}

	@Nullable
	public V get(K key) {
		Item<V> item = items.get(key);
		return item == null ? null : item.value;
	}

	// Restored, or deleted permanently.
	@Nullable
	public V remove(K key) {
		Item<V> item = items.remove(key);
		return item == null ? null : item.value;
	}

	// Minutes until key's can be deleted permanently, 0 once it can.
	public long minutesLeft(K key) {
		Item<V> item = items.get(key);
		long left = item == null ? 0 : item.at + WAIT.toMillis() - System.currentTimeMillis();
		return left <= 0 ? 0 : (left + 59_999) / 60_000;
	}

	public void clear() {
		items.clear();
	}
}
