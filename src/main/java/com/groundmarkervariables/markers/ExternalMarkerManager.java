package com.groundmarkervariables.markers;

import com.groundmarkervariables.variables.LabelResolver;
import com.groundmarkervariables.variables.meta.MetaVariableResolver;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import javax.inject.Singleton;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

// Markers other plugins render through us (see PluginMessageHandler), in memory only, as a TileSource. Each group has
// its sending plugin, a priority and the user's hides. Client thread only.
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ExternalMarkerManager implements TileSource
{
	public static final float DEFAULT_PRIORITY = 0.5f;
	public static final int MAX_MARKERS = 2000;

	@RequiredArgsConstructor
	static final class Group
	{
		final String name;
		String plugin;
		float priority;
		// Set order, for ties.
		long order;
		List<CachedMarker> markers = List.of();
		// Hidden by the user: the whole group, or tiles by stored (region) point. Kept across sets.
		boolean hidden;
		final Set<WorldPoint> hiddenTiles = new HashSet<>();
	}

	private final RenderedTiles renderedTiles;
	private final LabelResolver labelResolver;
	private final MetaVariableResolver metaVariableResolver;

	private final Map<String, Group> groups = new HashMap<>();
	// Every group's markers by region.
	private final Map<Integer, List<SourceMarker>> byRegion = new HashMap<>();
	private long order;
	// Marker Sets, while open: told when a group is set or cleared.
	@Nullable
	private Runnable listener;

	public void shutDown()
	{
		groups.clear();
		byRegion.clear();
	}

	// Replaces the group's markers, plugin and priority; the user's hides are kept.
	public void set(String plugin, String name, float priority, List<GroundMarkerPointData> points)
	{
		Group group = groups.computeIfAbsent(name, Group::new);
		group.plugin = plugin;
		group.priority = priority;
		group.order = ++order;
		List<CachedMarker> markers = new ArrayList<>(points.size());
		for (GroundMarkerPointData point : points)
		{
			markers.add(new CachedMarker(point, labelResolver, metaVariableResolver));
		}
		group.markers = markers;
		changed();
	}

	public void clear(String name)
	{
		if (groups.remove(name) != null)
		{
			changed();
		}
	}

	private void changed()
	{
		byRegion.clear();
		for (Group group : groups.values())
		{
			for (CachedMarker marker : group.markers)
			{
				byRegion.computeIfAbsent(marker.source.getRegionId(), r -> new ArrayList<>())
					.add(new SourceMarker(marker, this, group.priority, group.order, group));
			}
		}
		hidesChanged();
	}

	void setListener(@Nullable Runnable listener)
	{
		this.listener = listener;
	}

	// Each group as a marker set for Marker Sets: titled by its name, by its plugin, one entry per label.
	List<MarkerSet> sets()
	{
		List<MarkerSet> sets = new ArrayList<>();
		for (Group group : groups.values())
		{
			List<GroundMarkerPointData> points = new ArrayList<>();
			group.markers.forEach(marker -> points.add(marker.source));
			sets.add(new MarkerSet(MarkerSet.Kind.PLUGIN, group.name, group.name, group.plugin, 0, null, MarkerSets.entriesByLabel(points)));
		}
		return sets;
	}

	boolean shown(String name)
	{
		Group group = groups.get(name);
		return group != null && !group.hidden;
	}

	// None of tiles hidden.
	boolean shown(String name, List<GroundMarkerPointData> tiles)
	{
		Group group = groups.get(name);
		return group != null && tiles.stream().noneMatch(tile -> group.hiddenTiles.contains(tile.worldPoint()));
	}

	void show(String name, boolean shown)
	{
		Group group = groups.get(name);
		if (group != null)
		{
			group.hidden = !shown;
			hidesChanged();
		}
	}

	void show(String name, List<GroundMarkerPointData> tiles, boolean shown)
	{
		Group group = groups.get(name);
		if (group == null)
		{
			return;
		}
		for (GroundMarkerPointData tile : tiles)
		{
			if (shown)
			{
				group.hiddenTiles.remove(tile.worldPoint());
			}
			else
			{
				group.hiddenTiles.add(tile.worldPoint());
			}
		}
		hidesChanged();
	}

	private void hidesChanged()
	{
		renderedTiles.invalidate();
		if (listener != null)
		{
			listener.run();
		}
	}

	@Override
	public Collection<SourceMarker> markers(int regionId)
	{
		return byRegion.getOrDefault(regionId, Collections.emptyList());
	}

	@Override
	public boolean hidden(RenderedTile tile)
	{
		Group group = (Group) tile.marker.owner;
		return group.hidden || group.hiddenTiles.contains(tile.stored);
	}

	// A Shift right-click Tile Variables option.
	@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
	public static final class MenuOption
	{
		public final String option;
		public final String target;
		public final Runnable action;
	}

	// Hide the drawn group marker's tile or group, or show hidden ones on this tile (an instanced point).
	public List<MenuOption> menuOptions(WorldView wv, WorldPoint worldPoint)
	{
		List<MenuOption> options = new ArrayList<>();
		if (groups.isEmpty())
		{
			return options;
		}

		RenderedTile drawn = renderedTiles.winner(wv, worldPoint);
		if (drawn != null && drawn.marker.source == this)
		{
			Group group = (Group) drawn.marker.owner;
			WorldPoint stored = drawn.stored;
			options.add(option("Hide", group.name + " marker", () -> group.hiddenTiles.add(stored)));
			options.add(option("Hide group", group.name + " (" + group.plugin + ")", () -> group.hidden = true));
		}

		Set<Group> hiddenGroups = new LinkedHashSet<>();
		for (RenderedTile here : renderedTiles.all(wv, worldPoint))
		{
			if (here.marker.source != this)
			{
				continue;
			}
			Group group = (Group) here.marker.owner;
			WorldPoint stored = here.stored;
			if (group.hiddenTiles.contains(stored))
			{
				options.add(option("Show", group.name + " marker", () -> group.hiddenTiles.remove(stored)));
			}
			if (group.hidden)
			{
				hiddenGroups.add(group);
			}
		}
		for (Group group : hiddenGroups)
		{
			options.add(option("Show group", group.name + " (" + group.plugin + ")", () -> group.hidden = false));
		}
		return options;
	}

	private MenuOption option(String option, String target, Runnable action)
	{
		return new MenuOption(option, target, () ->
		{
			action.run();
			renderedTiles.invalidate();
		});
	}
}
