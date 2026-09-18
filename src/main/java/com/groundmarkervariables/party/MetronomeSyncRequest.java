package com.groundmarkervariables.party;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.runelite.client.party.messages.PartyMemberMessage;

// Asks the party member named target to respond with a MetronomeSyncResponse.
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MetronomeSyncRequest extends PartyMemberMessage
{
	private String target;
}
