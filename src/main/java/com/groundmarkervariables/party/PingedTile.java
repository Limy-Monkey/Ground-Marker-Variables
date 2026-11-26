package com.groundmarkervariables.party;

import com.groundmarkervariables.markers.CachedMarker;

import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

// One temp-cached tile shared by a party member's ping — not persisted, just displayed for a
// while. A small holder around a CachedMarker.
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class PingedTile {
    private static final Duration FULL_OPACITY_DURATION = Duration.ofSeconds(5);
    private static final Duration FADE_DURATION = Duration.ofSeconds(20);
    static final Duration TTL = FULL_OPACITY_DURATION.plus(FADE_DURATION).plusSeconds(5);

    public final CachedMarker marker;
    final long senderMemberId;
    // Snapshotted at receipt -- the sender may leave the party before this entry expires.
    final String senderName;
    final Instant receivedAt;

    // 100% for the first 5s, linearly down to 0% over the next 20s, then 0% until expiry.
    public float opacity(Instant now) {
        Duration age = Duration.between(receivedAt, now);
        if (age.compareTo(FULL_OPACITY_DURATION) <= 0) {
            return 1f;
        }

        Duration intoFade = age.minus(FULL_OPACITY_DURATION);
        if (intoFade.compareTo(FADE_DURATION) >= 0) {
            return 0f;
        }

        return 1f - (float) intoFade.toMillis() / FADE_DURATION.toMillis();
    }
}
