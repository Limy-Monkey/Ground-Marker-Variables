package com.groundmarkervariables;

import com.groundmarkervariables.variables.LabelResolver;
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

// Unsaved preview of tiles an import confirmation is about to add -- cleared via
// ChatboxTextMenuInput's own onClose, which fires on every dismissal path.
@Singleton
class ImportPreviewManager
{
	private final Map<WorldView, List<TranslatedMarker>> previewByWorldView = new ConcurrentHashMap<>();

	private final GroundMarkerVariablesPlugin plugin;
	private final LabelResolver labelResolver;

	@Inject
	private ImportPreviewManager(GroundMarkerVariablesPlugin plugin, LabelResolver labelResolver)
	{
		this.plugin = plugin;
		this.labelResolver = labelResolver;
	}

	// Mirrors GroundMarkerVariablesPlugin#translateWorldView, one-shot.
	void show(Collection<GroundMarkerPointData> points)
	{
		List<CachedMarker> markers = new ArrayList<>();
		for (GroundMarkerPointData point : points)
		{
			markers.add(new CachedMarker(point, labelResolver));
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

	void clear()
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

		visible.forEach(marker -> marker.refresh(labelResolver));
	}

	Collection<TranslatedMarker> getPreviewMarkers(WorldView wv)
	{
		return previewByWorldView.getOrDefault(wv, Collections.emptyList());
	}
}
