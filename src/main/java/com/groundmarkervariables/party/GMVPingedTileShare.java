package com.groundmarkervariables.party;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.party.messages.PartyMemberMessage;

// A marked tile, shared after the owner pings it. colorRgb is a plain packed ARGB int (not
// java.awt.Color) since the party websocket's Gson has none of RuneLite's usual type adapters --
// null means "use the recipient's own default marker color", mirroring GroundMarkerPointData.
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GMVPingedTileShare extends PartyMemberMessage
{
	private WorldPoint point;
	private Integer colorRgb;
	private String label;
}
