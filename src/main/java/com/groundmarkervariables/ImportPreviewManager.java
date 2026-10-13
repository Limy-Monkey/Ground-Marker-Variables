package com.groundmarkervariables;

import com.groundmarkervariables.variables.LabelResolver;
import com.groundmarkervariables.variables.meta.MetaVariableResolver;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

// Unsaved preview of tiles an import (or an unmarked tile's label) is about to add -- cleared
// via the chatbox input's own onClose, which fires on every dismissal path.
@Singleton
public class ImportPreviewManager
{
	private final Map<WorldView, List<TranslatedMarker>> previewByWorldView = new ConcurrentHashMap<>();

	private final GroundMarkerVariablesPlugin plugin;
	private final LabelResolver labelResolver;
	private final MetaVariableResolver metaVariableResolver;

	@Inject
	private ImportPreviewManager(GroundMarkerVariablesPlugin plugin, LabelResolver labelResolver, MetaVariableResolver metaVariableResolver)
	{
		this.plugin = plugin;
		this.labelResolver = labelResolver;
		this.metaVariableResolver = metaVariableResolver;
	}

	// Mirrors GroundMarkerVariablesPlugin#translateWorldView, one-shot.
	public void show(Collection<GroundMarkerPointData> points)
	{
		List<CachedMarker> markers = new ArrayList<>();
		for (GroundMarkerPointData point : points)
		{
			markers.add(new CachedMarker(point, labelResolver, metaVariableResolver));
		}

		Map<WorldView, List<TranslatedMarker>> rebuilt = new ConcurrentHashMap<>();
		for (WorldView wv : plugin.getTrackedWorldViews())
		{
			List<TranslatedMarker> translated = new ArrayList<>();
			for (CachedMarker marker : markers)
			{
				GroundMarkerPointData point = marker.source;
				WorldPoint storedPoint = WorldPoint.fromRegion(point.getRegionId(), point.getRegionX(), point.getRegionY(), point.getZ());
				for (WorldPoint worldPoint : WorldPoint.toLocalInstance(wv, storedPoint))
				{
					translated.add(new TranslatedMarker(worldPoint, marker));
				}
			}
			rebuilt.put(wv, translated);
		}

		previewByWorldView.clear();
		previewByWorldView.putAll(rebuilt);
	}

	public void clear()
	{
		previewByWorldView.clear();
	}

	// Only refreshes markers currently resolving into a tracked WorldView.
	void refresh()
	{
		Set<CachedMarker> visible = new HashSet<>();
		for (List<TranslatedMarker> translated : previewByWorldView.values())
		{
			for (TranslatedMarker marker : translated)
			{
				visible.add(marker.marker);
			}
		}

		visible.forEach(marker -> marker.refresh(labelResolver, metaVariableResolver));
	}

	Collection<TranslatedMarker> getPreviewMarkers(WorldView wv)
	{
		return previewByWorldView.getOrDefault(wv, Collections.emptyList());
	}
}
