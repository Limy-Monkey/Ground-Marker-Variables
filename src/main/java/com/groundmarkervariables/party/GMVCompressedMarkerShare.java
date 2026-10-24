package com.groundmarkervariables.party;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.runelite.client.party.messages.PartyMemberMessage;

// A sender's whole marker set: core's marker JSON, compressed with MarkerCompression, in base64.
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GMVCompressedMarkerShare extends PartyMemberMessage
{
	private int version;
	private String data;
}
