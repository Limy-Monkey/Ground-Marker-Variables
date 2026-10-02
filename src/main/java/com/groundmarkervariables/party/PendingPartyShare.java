package com.groundmarkervariables.party;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

// One sender's pending shared marker set -- removed by its own scheduled task after TTL (see
// GroundMarkerPartySharingManager), not persisted or rendered until actually imported.
class PendingPartyShare
{
	static final Duration TTL = Duration.ofSeconds(120);

	final long senderMemberId;
	final String senderName;
	final List<GMVGroundMarkerPartyShare.SharedMarker> markers;
	final Instant receivedAt;

	PendingPartyShare(long senderMemberId, String senderName, List<GMVGroundMarkerPartyShare.SharedMarker> markers, Instant receivedAt)
	{
		this.senderMemberId = senderMemberId;
		this.senderName = senderName;
		this.markers = markers;
		this.receivedAt = receivedAt;
	}
}
