package com.groundmarkervariables.variables.label;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.MiscellaniaTracker;

// {miscellania} -> the player's Kingdom of Miscellania approval/favour rating, 0-127 -- live in the
// kingdom, estimated elsewhere (see MiscellaniaTracker).
public class MiscellaniaLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{miscellania\\}", Pattern.CASE_INSENSITIVE);

	private final MiscellaniaTracker tracker;
	private final RichText richText;

	@Inject
	private MiscellaniaLabelVariable(MiscellaniaTracker tracker, RichText richText)
	{
		this.tracker = tracker;
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
		return String.valueOf(tracker.approval());
	}

	// Only highlighted at the 127 cap, and only the number, not the /127.
	@Override
	public String resolveRich(Matcher matcher)
	{
		int approval = tracker.approval();
		String value = String.valueOf(approval);
		int max = MiscellaniaTracker.MAX_APPROVAL;
		return (approval == max ? richText.highlightValue(value) : value) + " / " + max;
	}
}
