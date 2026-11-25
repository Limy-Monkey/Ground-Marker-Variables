package com.groundmarkervariables.variables.support;

import java.util.EnumSet;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;

import net.runelite.api.Client;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.hiscore.HiscoreManager;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;

// The local player's own hiscore rank for a skill/boss, via HiscoreManager's own async cache --
// null means unavailable (still loading, or genuinely unranked; its API can't tell those apart).
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class HiscoreRankLookup {
	private final Client client;
	private final HiscoreManager hiscoreManager;

	public Long rank(HiscoreSkill skill) {
		if (client.getLocalPlayer() == null) {
			return null;
		}

		HiscoreResult result = hiscoreManager.lookupAsync(client.getLocalPlayer().getName(), localEndpoint());
		if (result == null) {
			return null;
		}

		long rank = result.getSkill(skill).getRank();
		return rank <= -1 ? null : rank;
	}

	// Another player's boss kc, on our world type's hiscores; 0 if unranked, null while loading.
	public Integer kc(String player, HiscoreSkill skill) {
		HiscoreResult result = hiscoreManager.lookupAsync(player, HiscoreEndpoint.fromWorldTypes(client.getWorldType()));
		if (result == null) {
			return null;
		}

		return Math.max(result.getSkill(skill).getLevel(), 0);
	}

	// Mirrors ChatCommandsPlugin's own local-player-only endpoint detection.
	private HiscoreEndpoint localEndpoint() {
		EnumSet<WorldType> worldType = client.getWorldType();
		HiscoreEndpoint endpoint = HiscoreEndpoint.fromWorldTypes(worldType);
		if (endpoint != HiscoreEndpoint.NORMAL) {
			return endpoint;
		}

		switch (client.getVarbitValue(VarbitID.IRONMAN)) {
			case 1:
				return HiscoreEndpoint.IRONMAN;
			case 2:
				return HiscoreEndpoint.ULTIMATE_IRONMAN;
			case 3:
				return HiscoreEndpoint.HARDCORE_IRONMAN;
			default:
				return HiscoreEndpoint.NORMAL;
		}
	}
}
