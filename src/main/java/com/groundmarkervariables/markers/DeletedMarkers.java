package com.groundmarkervariables.markers;

import java.util.Collection;
import javax.inject.Singleton;

// The markers' recycling bin: each marker deleted by MarkerManager or the world map "Clear" option, keyed by itself.
@Singleton
public class DeletedMarkers extends RecyclingBin<GroundMarkerPointData, GroundMarkerPointData> {
	public void addAll(Collection<GroundMarkerPointData> points) {
		points.forEach(point -> add(point, point));
	}

	public void removeAll(Collection<GroundMarkerPointData> points) {
		points.forEach(this::remove);
	}
}
