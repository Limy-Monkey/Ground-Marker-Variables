package com.groundmarkervariables.variables.label;

import java.awt.Color;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.PartyMemberVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.Emojis;
import com.groundmarkervariables.variables.support.ReadyCheckTracker;
import com.groundmarkervariables.variables.support.ReadyCheckTracker.Status;
import com.groundmarkervariables.variables.support.ResolvingTile;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.party.PartyMember;

// {readyCheck} -> a party member's last ready status from chat: "R" / "ready" / "im r" / "r2p" is a green R, "P" /
// "pot" / "potting" a green P, "not r" / "no r" / "un r" a red R, "brb" / "<N> mins" :zzz:, a new
// boost a super combat potion. Plain: ready / potting / potted / not ready / away / none. Over 99 tiles from this tile,
// or on another plane, it's :telescope: instead, plain "far".
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ReadyCheckLabelVariable implements LabelVariable, PartyMemberVariable {
	private static final Pattern PATTERN = Pattern.compile("\\{readyCheck\\}", Pattern.CASE_INSENSITIVE);
	private static final int FAR_DISTANCE = 99;
	private static final DateTimeFormatter AWAY_TIME = DateTimeFormatter.ofPattern("h:mm");

	private final Client client;
	private final ResolvingTile resolvingTile;
	private final ReadyCheckTracker tracker;
	private final Emojis emojis;

	@Override
	public Pattern pattern() {
		return PATTERN;
	}

	// The status is looked up even when far, since that marks this tile as seen (see ReadyCheckTracker).
	@Override
	public String resolvePlain(Matcher matcher) {
		Status status = tracker.localStatus();
		return isFar(localLocation()) ? "far" : plain(status);
	}

	@Override
	public String resolveRich(Matcher matcher) {
		Status status = tracker.localStatus();
		return isFar(localLocation()) ? farIcon() : rich(status);
	}

	@Override
	public String resolvePlain(Matcher matcher, PartyMember member) {
		Status status = tracker.status(member.getDisplayName());
		return isFar(member) ? "far" : plain(status);
	}

	@Override
	public String resolveRich(Matcher matcher, PartyMember member) {
		Status status = tracker.status(member.getDisplayName());
		return isFar(member) ? farIcon() : rich(status);
	}

	// Going far clears their status, so it isn't still there when they come back.
	private boolean isFar(PartyMember member) {
		if (!isFar(member.getCoord())) {
			return false;
		}

		tracker.clear(member.getDisplayName());
		return true;
	}

	// False without a player location or a tile being resolved.
	private boolean isFar(@Nullable WorldPoint player) {
		WorldPoint tile = resolvingTile.tile;
		return player != null && tile != null && (player.getPlane() != tile.getPlane() || player.distanceTo(tile) > FAR_DISTANCE);
	}

	@Nullable
	private WorldPoint localLocation() {
		return client.getLocalPlayer() == null ? null : client.getLocalPlayer().getWorldLocation();
	}

	private String farIcon() {
		return emojis.expand("{tip=Not nearby}:telescope@lightgray:{/tip}");
	}

	@Override
	public String resolveRowRich(Matcher matcher, RichText richText) {
		return resolveRich(matcher);
	}

	private static String plain(Status status) {
		if (status == null) {
			return "none";
		}

		switch (status.kind) {
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

	private String rich(Status status) {
		if (status == null) {
			return "";
		}

		switch (status.kind) {
			case READY:
				return RichText.colored(Color.GREEN, "R");
			case POTTING:
				return RichText.colored(Color.GREEN, "P");
			case POTTED:
				return "{tip=Potted}<item=" + ItemID._4DOSE2COMBAT + ">{/tip}";
			case NOT_READY:
				return RichText.colored(Color.RED, "R");
			default:
				return "{tip=away at " + AWAY_TIME.format(status.time) + "}" + emojis.expand(":zzz:") + "{/tip}";
		}
	}
}
