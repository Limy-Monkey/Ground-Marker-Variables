package com.groundmarkervariables.variables.label;

import com.groundmarkervariables.GroundMarkerVariablesConfig;
import com.groundmarkervariables.party.PartyDataSharing;
import com.groundmarkervariables.party.PartyValue;
import com.groundmarkervariables.variables.LabelVariable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.util.ColorUtil;

// {metronomeN} (or {mN}) counts up from 1 to N, or with "Count Down" down from N to 1, then repeats, once per game tick;
// {metronomeN_M} advances once every M ticks. Counted on the server's map clock (VarPlayerID.MAP_CLOCK) from the "Reset
// metronome" hotkey's tick, so every metronome with the same N and M stays in step; with Party Sync, from the sync
// target's published reset tick and direction instead (see PartyDataSharing).
//
// @Singleton: LabelResolver, the hotkey listener and PartyDataSharing share its reset tick.
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class MetronomeLabelVariable implements LabelVariable {
	// Optional +/- before N forces counting up/down, over both config and party sync.
	private static final Pattern PATTERN = Pattern.compile(
		"\\{(?:metronome|m)([+-]?)(\\d+)(?:_(\\d+))?\\}", Pattern.CASE_INSENSITIVE);

	private final Client client;
	private final GroundMarkerVariablesConfig config;
	private final PartyService partyService;
	private final PartyDataSharing partyDataSharing;
	// The map clock tick counting starts from: 0 until reset, the same for everyone.
	private volatile int resetTick;

	public void reset() {
		resetTick = client.getVarpValue(VarPlayerID.MAP_CLOCK);
	}

	// Ours, published to the party.
	public int resetTick() {
		return resetTick;
	}

	// The Party Sync target's published value, if following someone who's sent it.
	@Nullable
	private String synced(PartyValue value) {
		String target = config.syncTarget().replace('_', ' ');
		if (!config.enablePartySync() || target.isEmpty()) {
			return null;
		}
		for (PartyMember member : partyService.getMembers()) {
			if (member.getDisplayName().replace('_', ' ').equalsIgnoreCase(target)) {
				return partyDataSharing.value(member.getMemberId(), value);
			}
		}
		return null;
	}

	// The sync target's reset tick if published, else ours.
	private int countedFrom() {
		try {
			String synced = synced(PartyValue.METRONOME_RESET);
			return synced != null ? Integer.parseInt(synced) : resetTick;
		} catch (NumberFormatException e) {
			return resetTick;
		}
	}

	@Override
	public Pattern pattern() {
		return PATTERN;
	}

	@Override
	public String resolvePlain(Matcher matcher) {
		int max = parseGroup(matcher, 2, -1);
		if (max <= 0) {
			return null;
		}

		int ticksPerStep = matcher.group(3) == null ? 1 : parseGroup(matcher, 3, -1);
		if (ticksPerStep <= 0) {
			return null;
		}

		String direction = matcher.group(1);
		String syncedCountDown = synced(PartyValue.METRONOME_COUNT_DOWN);
		boolean countDown = !direction.isEmpty() ? direction.equals("-")
			: syncedCountDown != null ? Boolean.parseBoolean(syncedCountDown) : config.countDown();

		int step = Math.floorDiv(client.getVarpValue(VarPlayerID.MAP_CLOCK) - countedFrom(), ticksPerStep);
		int position = Math.floorMod(step, max);
		String value = String.valueOf(countDown ? max - position : position + 1);

		if (config.highlightFinalTick() && position == max - 1) {
			value = "<col=" + ColorUtil.colorToHexCode(config.finalTickColor()) + ">" + value + "</col>";
		}

		return value;
	}

	// Rich and Plain are identical for {metronomeN[_M]}.
	@Override
	public String resolveRich(Matcher matcher) {
		return resolvePlain(matcher);
	}

	// \d+ has no upper bound on digit count, so a marker like {metronome99999999999} can
	// overflow int; fall back to a value the caller treats as invalid rather than crashing.
	private static int parseGroup(Matcher matcher, int group, int onOverflow) {
		try {
			return Integer.parseInt(matcher.group(group));
		} catch (NumberFormatException e) {
			return onOverflow;
		}
	}
}
