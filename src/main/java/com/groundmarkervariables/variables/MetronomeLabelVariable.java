package com.groundmarkervariables.variables;

import com.groundmarkervariables.GroundMarkerVariablesConfig;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;

// {metronomeN} counts up from 1 to N, or with the "Count Down" config option, down from N to
// 1, then repeats, advancing once per game tick. {metronomeN_M} is the same, but only
// advances once every M ticks (default M = 1). Driven
// by Client.getTickCount() rather than our own counter so it stays exact regardless of how
// often the overlay redraws, and so every {metronomeN_M} with the same N and M stays in sync
// — offset by the "Reset metronome" hotkey's tick (see offset()) so every metronome can be
// re-synced to a moment the player chooses, e.g. the start of a boss fight.
//
// @Singleton because this is injected at two separate points (LabelResolver and the reset
// hotkey listener, in the main com.groundmarkervariables package) that must share the same
// offset state — Guice hands out a fresh instance per injection point otherwise. Public (unlike
// every other LabelVariable) for the same reason: the hotkey listener outside this package
// needs to call offset().
@Singleton
public class MetronomeLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN =
		Pattern.compile("\\{metronome(\\d+)(?:_(\\d+))?\\}", Pattern.CASE_INSENSITIVE);

	private final Client client;
	private final GroundMarkerVariablesConfig config;
	private volatile int offsetTick;

	@Inject
	private MetronomeLabelVariable(Client client, GroundMarkerVariablesConfig config)
	{
		this.client = client;
		this.config = config;
	}

	public void offset()
	{
		offsetTick = client.getTickCount();
	}

	// Ticks elapsed since offsetTick — the one value every marker's countdown is derived
	// from, sent to party members as a MetronomeSyncResponse.
	public int elapsedTicks()
	{
		return client.getTickCount() - offsetTick;
	}

	// Applies a party member's elapsedTicks so this client's countdown matches theirs.
	public void syncTo(int remoteElapsedTicks)
	{
		offsetTick = client.getTickCount() - remoteElapsedTicks;
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

		int step = (client.getTickCount() - offsetTick) / ticksPerStep;
		int position = step % max;
		return String.valueOf(config.countDown() ? max - position : position + 1);
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
