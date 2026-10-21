package com.groundmarkervariables.variables.label;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.DpsTracker;
import net.runelite.api.Client;

// {dps} -> your damage per second while near the tile, e.g. 4.32. Rich adds your share of the
// damage when other players hit the same NPCs, e.g. "DPS: 4.32 (61%)".
public class DpsLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{dps\\}", Pattern.CASE_INSENSITIVE);

	private final Client client;
	private final RichText richText;
	private final DpsTracker tracker;

	@Inject
	private DpsLabelVariable(Client client, RichText richText, DpsTracker tracker)
	{
		this.client = client;
		this.richText = richText;
		this.tracker = tracker;
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		tracker.markActive();
		return String.format(Locale.ROOT, "%.2f", tracker.dps());
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		String dps = resolvePlain(matcher);
		if (dps == null)
		{
			return null;
		}

		double share = tracker.share();
		return richText.labeled("DPS", share < 0 ? dps : dps + " (" + Math.round(share * 100) + "%)");
	}
}
