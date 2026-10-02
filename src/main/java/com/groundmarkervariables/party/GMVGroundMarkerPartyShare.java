package com.groundmarkervariables.party;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.party.messages.PartyMemberMessage;

// A sender's whole marker set, shared via "Share to Party". colorRgb is a packed ARGB int, not
// java.awt.Color -- same reason as GMVPingedTileShare's own colorRgb.
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GMVGroundMarkerPartyShare extends PartyMemberMessage
{
	private List<SharedMarker> markers;

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SharedMarker
	{
		private WorldPoint point;
		private Integer colorRgb;
		private String label;
	}
}
