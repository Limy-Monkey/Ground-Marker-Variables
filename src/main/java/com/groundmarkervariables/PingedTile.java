package com.groundmarkervariables;

import java.time.Duration;
import java.time.Instant;

// One temp-cached tile shared by a party member's ping — not persisted, just displayed for a
// while. Mirrors TranslatedMarker's style (a small holder around a CachedMarker).
class PingedTile
{
	private static final Duration FULL_OPACITY_DURATION = Duration.ofSeconds(5);
	private static final Duration FADE_DURATION = Duration.ofSeconds(20);
	static final Duration TTL = FULL_OPACITY_DURATION.plus(FADE_DURATION).plusSeconds(5);

	final CachedMarker marker;
	final long senderMemberId;
	// Snapshotted at receipt -- the sender may leave the party before this entry expires.
	final String senderName;
	final Instant receivedAt;

	PingedTile(CachedMarker marker, long senderMemberId, String senderName, Instant receivedAt)
	{
		this.marker = marker;
		this.senderMemberId = senderMemberId;
		this.senderName = senderName;
		this.receivedAt = receivedAt;
	}

	// 100% for the first 5s, linearly down to 0% over the next 20s, then 0% until expiry.
	float opacity(Instant now)
	{
		Duration age = Duration.between(receivedAt, now);
		if (age.compareTo(FULL_OPACITY_DURATION) <= 0)
		{
			return 1f;
		}

		Duration intoFade = age.minus(FULL_OPACITY_DURATION);
		if (intoFade.compareTo(FADE_DURATION) >= 0)
		{
			return 0f;
		}

		return 1f - (float) intoFade.toMillis() / FADE_DURATION.toMillis();
	}
}
