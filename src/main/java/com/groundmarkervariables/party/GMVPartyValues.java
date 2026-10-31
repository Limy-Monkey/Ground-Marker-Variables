package com.groundmarkervariables.party;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.runelite.client.party.messages.PartyMemberMessage;

// A sender's changed PartyValues: name -> plain text. Keyed by String, since an unknown enum key
// wouldn't deserialize.
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GMVPartyValues extends PartyMemberMessage
{
	private Map<String, String> values;
}
