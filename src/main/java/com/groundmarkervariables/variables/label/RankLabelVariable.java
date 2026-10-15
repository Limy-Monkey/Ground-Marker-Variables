package com.groundmarkervariables.variables.label;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

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
public class RankLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{rank\\s+([^{}?:<>=!]+?)\\}", Pattern.CASE_INSENSITIVE);

	private final Client client;
	private final BossKillCountTracker tracker;
	private final HiscoreRankLookup rankLookup;
	private final RichText richText;

	@Inject
	private RankLabelVariable(Client client, BossKillCountTracker tracker, HiscoreRankLookup rankLookup, RichText richText)
	{
		this.client = client;
		this.tracker = tracker;
		this.rankLookup = rankLookup;
		this.richText = richText;
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
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

		String canonicalBoss = BossAliases.resolve(matcher.group(1).trim());
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

		String boss = matcher.group(1).trim();
		return boss.isEmpty() ? null : BossHiscoreSkill.find(BossAliases.resolve(boss));
	}
}
