package com.groundmarkervariables.party;

import com.groundmarkervariables.markers.GroundMarkerPointData;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;

// One sender's pending shared marker set -- removed by its own scheduled task after TTL (see
// GroundMarkerPartySharingManager), not persisted or rendered until actually imported.
@RequiredArgsConstructor
class PendingPartyShare {
    static final Duration TTL = Duration.ofSeconds(120);

    final long senderMemberId;
    final String senderName;
    final List<GroundMarkerPointData> markers;
    final Instant receivedAt;
}
