package com.groundmarkervariables.markers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

// Every source's markers translated into each tracked world view, and the one drawn per tile: the highest priority
// not hidden (ties: the higher order). Built on demand on the client thread; invalidate() is safe from any thread.
@Singleton
public class RenderedTiles
{
	public static final float PINGED_PRIORITY = 0.55f;
	public static final float SAVED_PRIORITY = 0.6f;
	public static final float SET_PRIORITY = 0.7f;
	public static final float PREVIEW_PRIORITY = 2f;

	private static final Comparator<RenderedTile> BEST_FIRST = Comparator
		.comparingDouble((RenderedTile t) -> -t.marker.priority)
		.thenComparingLong(t -> -t.marker.order);

	// A world view's markers per point, best first, and the drawn one per point.
	private static final class Tiles
	{
		final Map<WorldPoint, List<RenderedTile>> all = new HashMap<>();
		final Map<WorldPoint, RenderedTile> winners = new HashMap<>();
	}

	private final List<TileSource> sources = new CopyOnWriteArrayList<>();
	private final Set<WorldView> tracked = ConcurrentHashMap.newKeySet();
	private final Map<WorldView, Tiles> byWorldView = new ConcurrentHashMap<>();

	@Inject
	private RenderedTiles()
	{
	}

	public void setSources(List<TileSource> sources)
	{
		this.sources.clear();
		this.sources.addAll(sources);
		invalidate();
	}

	public void track(WorldView wv)
	{
		tracked.add(wv);
		byWorldView.remove(wv);
	}

	public void untrack(WorldView wv)
	{
		tracked.remove(wv);
		byWorldView.remove(wv);
	}

	public void clear()
	{
		sources.clear();
		tracked.clear();
		byWorldView.clear();
	}

	// After any source's markers, priorities or hides change.
	public void invalidate()
	{
		byWorldView.clear();
	}

	public Collection<WorldView> worldViews()
	{
		return tracked;
	}

	public Collection<RenderedTile> winners(WorldView wv)
	{
		return tiles(wv).winners.values();
	}

	@Nullable
	public RenderedTile winner(WorldView wv, WorldPoint worldPoint)
	{
		return tiles(wv).winners.get(worldPoint);
	}

	// Every marker at worldPoint, best first, hidden or not.
	public List<RenderedTile> all(WorldView wv, WorldPoint worldPoint)
	{
		return tiles(wv).all.getOrDefault(worldPoint, Collections.emptyList());
	}

	// Every marker in wv, hidden or not.
	public List<RenderedTile> all(WorldView wv)
	{
		List<RenderedTile> all = new ArrayList<>();
		tiles(wv).all.values().forEach(all::addAll);
		return all;
	}

	private Tiles tiles(WorldView wv)
	{
		Tiles tiles = byWorldView.get(wv);
		if (tiles != null)
		{
			return tiles;
		}

		tiles = new Tiles();
		int[] regions = wv.getMapRegions();
		if (regions != null)
		{
			for (int regionId : regions)
			{
				for (TileSource source : sources)
				{
					for (SourceMarker marker : source.markers(regionId))
					{
						GroundMarkerPointData point = marker.marker.source;
						WorldPoint stored = WorldPoint.fromRegion(point.getRegionId(), point.getRegionX(), point.getRegionY(), point.getZ());
						for (WorldPoint worldPoint : WorldPoint.toLocalInstance(wv, stored))
						{
							tiles.all.computeIfAbsent(worldPoint, p -> new ArrayList<>()).add(new RenderedTile(worldPoint, stored, marker));
						}
					}
				}
			}
			for (List<RenderedTile> here : tiles.all.values())
			{
				here.sort(BEST_FIRST);
				for (RenderedTile tile : here)
				{
					if (!tile.marker.source.hidden(tile))
					{
						tiles.winners.put(tile.worldPoint, tile);
						break;
					}
				}
			}
		}
		byWorldView.put(wv, tiles);
		return tiles;
	}
}
