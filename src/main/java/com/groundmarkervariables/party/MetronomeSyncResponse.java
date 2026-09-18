package com.groundmarkervariables.party;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.runelite.client.party.messages.PartyMemberMessage;

// Answers a MetronomeSyncRequest with how many ticks have elapsed since the sender's own
// {metronome} offset — the one value every {metronomeN}/{metronomeN_M} marker's countdown is
// derived from. The sender's identity is PartyMemberMessage's own memberId (set by WSClient
// on receipt), not a field here.
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MetronomeSyncResponse extends PartyMemberMessage
{
	private int elapsedTicks;
}
