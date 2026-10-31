package com.groundmarkervariables.party;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.runelite.api.Skill;
import net.runelite.client.party.PartyDataType;
import net.runelite.client.party.messages.PartyMemberMessage;

// The core data a member's {party} tiles need from the rest of the party. Names a client doesn't
// know arrive as null.
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GMVPartySubscriptions extends PartyMemberMessage
{
	private List<PartyDataType> core;
	private List<Skill> skills;
}
