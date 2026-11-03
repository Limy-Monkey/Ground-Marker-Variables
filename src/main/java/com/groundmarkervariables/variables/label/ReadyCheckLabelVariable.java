package com.groundmarkervariables.variables.label;

import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.PartyMemberVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.Emojis;
import com.groundmarkervariables.variables.support.ReadyCheckTracker;
import com.groundmarkervariables.variables.support.ReadyCheckTracker.Status;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.party.PartyMember;

// {readyCheck} -> a party member's last ready status from chat: "R" / "ready" / "im r" / "r2p" is a green R, "P" /
// "pot" / "potting" a green P, "not r" / "no r" / "un r" a red R, "brb" / "<N> mins" :zzz:, a new
// boost a super combat potion. Plain: ready / potting / potted / not ready / away / none. Over 99 tiles from this tile (see {playerDistance})
// it's :telescope: instead, plain "far".
public class ReadyCheckLabelVariable implements LabelVariable, PartyMemberVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{readyCheck\\}", Pattern.CASE_INSENSITIVE);
	private static final int FAR_DISTANCE = 99;

	private final ReadyCheckTracker tracker;
	private final PlayerDistanceLabelVariable playerDistance;
	private final Emojis emojis;

	@Inject
	private ReadyCheckLabelVariable(ReadyCheckTracker tracker, PlayerDistanceLabelVariable playerDistance, Emojis emojis)
	{
		this.tracker = tracker;
		this.playerDistance = playerDistance;
		this.emojis = emojis;
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	// The status is looked up even when far, since that marks this tile as seen (see ReadyCheckTracker).
	@Override
	public String resolvePlain(Matcher matcher)
	{
		Status status = tracker.localStatus();
		return isFar(playerDistance.localDistance()) ? "far" : plain(status);
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		Status status = tracker.localStatus();
		return isFar(playerDistance.localDistance()) ? farIcon() : rich(status);
	}

	@Override
	public String resolvePlain(Matcher matcher, PartyMember member)
	{
		Status status = tracker.status(member.getDisplayName());
		return isFar(member) ? "far" : plain(status);
	}

	@Override
	public String resolveRich(Matcher matcher, PartyMember member)
	{
		Status status = tracker.status(member.getDisplayName());
		return isFar(member) ? farIcon() : rich(status);
	}

	// Going far clears their status, so it isn't still there when they come back.
	private boolean isFar(PartyMember member)
	{
		if (!isFar(playerDistance.distance(member.getCoord())))
		{
			return false;
		}

		tracker.clear(member.getDisplayName());
		return true;
	}

	private static boolean isFar(Integer distance)
	{
		return distance != null && distance > FAR_DISTANCE;
	}

	private String farIcon()
	{
		return emojis.expand("{tip=Not nearby}:telescope@lightgray:{/tip}");
	}

	@Override
	public String resolveRowRich(Matcher matcher, RichText richText)
	{
		return resolveRich(matcher);
	}

	private static String plain(Status status)
	{
		if (status == null)
		{
			return "none";
		}

		switch (status)
		{
			case READY:
				return "ready";
			case POTTING:
				return "potting";
			case POTTED:
				return "potted";
			case NOT_READY:
				return "not ready";
			default:
				return "away";
		}
	}

	private String rich(Status status)
	{
		if (status == null)
		{
			return "";
		}

		switch (status)
		{
			case READY:
				return RichText.colored(Color.GREEN, "R");
			case POTTING:
				return RichText.colored(Color.GREEN, "P");
			case POTTED:
				return "{tip=Potted}<item=" + ItemID._4DOSE2COMBAT + ">{/tip}";
			case NOT_READY:
				return RichText.colored(Color.RED, "R");
			default:
				return emojis.expand(":zzz:");
		}
	}
}
