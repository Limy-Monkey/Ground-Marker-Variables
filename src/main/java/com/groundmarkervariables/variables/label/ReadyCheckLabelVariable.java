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
import net.runelite.client.party.PartyMember;

// {readyCheck} -> a party member's last ready status from chat: "R" / "ready" is a green R, "P" /
// "pot" / "potting" a green P, "not r" / "no r" / "un r" a red R, "brb" / "<N> mins" :zzz:.
// Plain: ready / potting / not ready / away / none.
public class ReadyCheckLabelVariable implements LabelVariable, PartyMemberVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{readyCheck\\}", Pattern.CASE_INSENSITIVE);

	private final ReadyCheckTracker tracker;
	private final Emojis emojis;

	@Inject
	private ReadyCheckLabelVariable(ReadyCheckTracker tracker, Emojis emojis)
	{
		this.tracker = tracker;
		this.emojis = emojis;
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		return plain(tracker.localStatus());
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		return rich(tracker.localStatus());
	}

	@Override
	public String resolvePlain(Matcher matcher, PartyMember member)
	{
		return plain(tracker.status(member.getDisplayName()));
	}

	@Override
	public String resolveRich(Matcher matcher, PartyMember member)
	{
		return rich(tracker.status(member.getDisplayName()));
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
			case NOT_READY:
				return RichText.colored(Color.RED, "R");
			default:
				return emojis.expand(":zzz:");
		}
	}
}
