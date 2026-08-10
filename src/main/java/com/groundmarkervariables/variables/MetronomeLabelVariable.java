package com.groundmarkervariables.variables;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;

// {metronomeN} counts down from N to 1, then restarts at N, advancing once per game tick.
// {metronomeN_M} is the same, but only advances once every M ticks (default M = 1). Driven
// by Client.getTickCount() rather than our own counter so it stays exact regardless of how
// often the overlay redraws, and so every {metronomeN_M} with the same N and M stays in sync.
class MetronomeLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN =
		Pattern.compile("\\{metronome(\\d+)(?:_(\\d+))?\\}", Pattern.CASE_INSENSITIVE);

	private final Client client;

	@Inject
	private MetronomeLabelVariable(Client client)
	{
		this.client = client;
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolve(Matcher matcher)
	{
		int max = parseGroup(matcher, 1, -1);
		if (max <= 0)
		{
			return null;
		}

		int ticksPerStep = matcher.group(2) == null ? 1 : parseGroup(matcher, 2, -1);
		if (ticksPerStep <= 0)
		{
			return null;
		}

		int step = client.getTickCount() / ticksPerStep;
		return String.valueOf(max - (step % max));
	}

	// \d+ has no upper bound on digit count, so a marker like {metronome99999999999} can
	// overflow int; fall back to a value the caller treats as invalid rather than crashing.
	private static int parseGroup(Matcher matcher, int group, int onOverflow)
	{
		try
		{
			return Integer.parseInt(matcher.group(group));
		}
		catch (NumberFormatException e)
		{
			return onOverflow;
		}
	}
}
