package com.groundmarkervariables.variables;

import com.groundmarkervariables.GroundMarkerVariablesConfig;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.party.PartyService;
import net.runelite.client.util.ColorUtil;

// {metronomeN} (or its {mN} alias) counts up from 1 to N, or with the "Count Down" config
// option, down from N to 1, then repeats, advancing once per game tick. {metronomeN_M} is
// the same, but only advances once every M ticks (default M = 1). Driven
// by Client.getTickCount() rather than our own counter so it stays exact regardless of how
// often the overlay redraws, and so every {metronomeN_M} with the same N and M stays in sync
// — offset by the "Reset metronome" hotkey's tick (see offset()) so every metronome can be
// re-synced to a moment the player chooses, e.g. the start of a boss fight.
//
// {metronomeN +/- offset} (also {metronomeN_M +/- offset}) shifts just that token's phase by
// "offset" steps, local to resolve() only.
//
// @Singleton because this is injected at two separate points (LabelResolver and the reset
// hotkey listener, in the main com.groundmarkervariables package) that must share the same
// offset state — Guice hands out a fresh instance per injection point otherwise. Public (unlike
// every other LabelVariable) for the same reason: the hotkey listener outside this package
// needs to call offset().
@Singleton
public class MetronomeLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile(
		"\\{(?:metronome|m)(\\d+)(?:_(\\d+))?\\s*(?:([+-])\\s*(\\d+))?\\}", Pattern.CASE_INSENSITIVE);

	// Sync target's "Count Down" stops overriding our own config after this many ticks (30s)
	// with no new MetronomeSyncResponse.
	private static final int SYNC_TIMEOUT_TICKS = 50;

	private final Client client;
	private final GroundMarkerVariablesConfig config;
	private final PartyService partyService;
	private volatile int offsetTick;
	private volatile boolean syncedCountDown;
	private volatile int lastSyncTick = -SYNC_TIMEOUT_TICKS - 1;

	@Inject
	private MetronomeLabelVariable(Client client, GroundMarkerVariablesConfig config, PartyService partyService)
	{
		this.client = client;
		this.config = config;
		this.partyService = partyService;
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

	// Applies a party member's elapsedTicks/countDown so this client matches theirs.
	public void syncTo(int remoteElapsedTicks, boolean remoteCountDown)
	{
		offsetTick = client.getTickCount() - remoteElapsedTicks;
		syncedCountDown = remoteCountDown;
		lastSyncTick = client.getTickCount();
	}

	// The sync target's countDown, unless stale (no response in SYNC_TIMEOUT_TICKS) or we've
	// left the party -- then back to our own config, same as if never synced.
	private boolean effectiveCountDown()
	{
		if (client.getTickCount() - lastSyncTick < SYNC_TIMEOUT_TICKS)
		{
			if (!partyService.isInParty())
			{
				lastSyncTick = -SYNC_TIMEOUT_TICKS;
				return config.countDown();
			}

			return syncedCountDown;
		}

		return config.countDown();
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolvePlain(Matcher matcher)
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

		int tokenOffset = 0;
		if (matcher.group(3) != null)
		{
			int amount = parseGroup(matcher, 4, -1);
			if (amount < 0)
			{
				return null;
			}

			// offset is in steps, not raw ticks.
			tokenOffset = ("-".equals(matcher.group(3)) ? -amount : amount) * ticksPerStep;
		}

		// floorDiv/floorMod, not / and % — a negative tokenOffset can push this token's own
		// elapsed count below zero.
		int step = Math.floorDiv(client.getTickCount() - offsetTick + tokenOffset, ticksPerStep);
		int position = Math.floorMod(step, max);
		String value = String.valueOf(effectiveCountDown() ? max - position : position + 1);

		if (config.highlightFinalTick() && position == max - 1)
		{
			value = "<col=" + ColorUtil.colorToHexCode(config.finalTickColor()) + ">" + value + "</col>";
		}

		return value;
	}

	// Rich and Plain are identical for {metronomeN[_M]}.
	@Override
	public String resolveRich(Matcher matcher)
	{
		return resolvePlain(matcher);
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
