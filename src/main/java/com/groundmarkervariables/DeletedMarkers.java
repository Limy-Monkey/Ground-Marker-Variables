package com.groundmarkervariables;

import java.util.List;
import javax.inject.Singleton;

// The last batch of markers deleted by MarkerManager or the world map "Clear" option, for a
// single undo. Memory only -- never saved, gone on restart.
@Singleton
class DeletedMarkers
{
	private List<GroundMarkerPointData> last = List.of();

	void remember(List<GroundMarkerPointData> points)
	{
		last = List.copyOf(points);
	}

	List<GroundMarkerPointData> last()
	{
		return last;
	}

	void clear()
	{
		last = List.of();
	}
}
