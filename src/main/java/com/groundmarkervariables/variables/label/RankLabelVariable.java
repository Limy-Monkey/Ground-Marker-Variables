package com.groundmarkervariables.variables.label;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;

import com.groundmarkervariables.variables.Args;
import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.BossAliases;
import com.groundmarkervariables.variables.support.BossHiscoreSkill;
import com.groundmarkervariables.variables.support.BossKillCountTracker;
import com.groundmarkervariables.variables.support.HiscoreRankLookup;
import net.runelite.api.Client;
import net.runelite.client.hiscore.HiscoreSkill;

// {rank <boss>} -> the player's hiscores rank for <boss> (case-insensitive, aliases allowed
// same as {kc}). Kc comes from the existing BossKillCountTracker; rank from HiscoreRankLookup,
// shown as animated dots while unavailable (still loading, or genuinely unranked).
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class RankLabelVariable implements LabelVariable
{
	// Suggesting only the bosses on the hiscores.
	private static final Args ARGS = Args.of("rank").main(() -> BossAliases.suggestions(true), Args.BOSS_ICON);

	private final Client client;
	private final BossKillCountTracker tracker;
	private final HiscoreRankLookup rankLookup;
	private final RichText richText;

	@Override
	public Pattern pattern()
	{
		return ARGS.pattern();
	}

	@Override
	public Args args()
	{
		return ARGS;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		HiscoreSkill skill = findSkill(matcher);
		if (skill == null)
		{
			return null;
		}

		Long rank = rankLookup.rank(skill);
		return rank == null ? null : String.valueOf(rank);
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		HiscoreSkill skill = findSkill(matcher);
		if (skill == null)
		{
			return null;
		}

		String canonicalBoss = BossAliases.resolve(ARGS.main(matcher));
		Integer kc = tracker.getKc(canonicalBoss);
		Long rank = rankLookup.rank(skill);
		String rankText = rank == null ? richText.animatedDots(client.getTickCount()) : richText.highlightValue(String.format("%,d", rank));

		return canonicalBoss + ": " + richText.highlightValue(String.valueOf(kc == null ? 0 : kc)) + " Rank: " + rankText;
	}

	private HiscoreSkill findSkill(Matcher matcher)
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		String boss = ARGS.main(matcher);
		return boss == null ? null : BossHiscoreSkill.find(BossAliases.resolve(boss));
	}
}
