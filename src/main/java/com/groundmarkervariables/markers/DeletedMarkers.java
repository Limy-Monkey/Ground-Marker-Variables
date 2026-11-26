package com.groundmarkervariables.markers;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

// The markers' recycling bin: each saved marker deleted (by MarkerManager or the world map "Clear" option), and each tile
// deleted from an own set (see MarkerSets#deleteTiles), by key().
@Singleton
public class DeletedMarkers extends RecyclingBin<String, DeletedMarkers.Tile> {
	// A deleted marker: a saved one, or a set's with its set's key and its entry as it was.
	@RequiredArgsConstructor
	public static final class Tile {
		@Nullable
		public final String setKey;
		@Nullable
		public final MarkerSet.Entry entry;
		public final GroundMarkerPointData tile;
	}

	public void add(Tile deleted) {
		add(key(deleted.setKey, deleted.entry, deleted.tile), deleted);
	}

	public void addAll(Collection<GroundMarkerPointData> points) {
		points.forEach(point -> add(new Tile(null, null, point)));
	}

	// Those deleted from setKey's set, or saved markers for null.
	public List<Tile> from(@Nullable String setKey) {
		return values().stream().filter(deleted -> Objects.equals(deleted.setKey, setKey)).collect(Collectors.toList());
	}

	// Its set (if any), entry and place.
	public static String key(@Nullable String setKey, @Nullable MarkerSet.Entry entry, GroundMarkerPointData tile) {
		return setKey + "\0" + (entry == null ? "" : entry.key) + "\0" + tile.worldPoint();
	}
}
