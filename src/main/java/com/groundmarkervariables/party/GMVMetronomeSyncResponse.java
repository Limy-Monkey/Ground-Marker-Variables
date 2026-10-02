package com.groundmarkervariables.party;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.runelite.client.party.messages.PartyMemberMessage;

// Answers a GMVMetronomeSyncRequest with elapsedTicks (see MetronomeLabelVariable) plus the
// sender's own "Count Down" config, so the target's setting decides counting direction too.
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GMVMetronomeSyncResponse extends PartyMemberMessage
{
	private int elapsedTicks;
	private boolean countDown;
}
