package com.groundmarkervariables.variables.label;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.PartyMemberVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.ResolvingTile;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.party.PartyMember;

// {playerDistance} -> tiles between the player and this tile: 0 standing on it, 1 next to it, and so
// on (diagonals count as 1), capped at 100. 101 on another plane.
public class PlayerDistanceLabelVariable implements LabelVariable, PartyMemberVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{playerDistance\\}", Pattern.CASE_INSENSITIVE);
	private static final int MAX_DISTANCE = 100;
	private static final int OTHER_PLANE = 101;

	private final Client client;
	private final ResolvingTile resolvingTile;
	private final RichText richText;

	@Inject
	private PlayerDistanceLabelVariable(Client client, ResolvingTile resolvingTile, RichText richText)
	{
		this.client = client;
		this.resolvingTile = resolvingTile;
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
		return format(localDistance());
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		String distance = resolvePlain(matcher);
		return distance == null ? null : richText.labeled("Distance", distance);
	}

	@Override
	public String resolvePlain(Matcher matcher, PartyMember member)
	{
		return format(distance(member.getCoord()));
	}

	@Override
	public String resolveRich(Matcher matcher, PartyMember member)
	{
		String distance = resolvePlain(matcher, member);
		return distance == null ? null : richText.highlightValue(distance);
	}

	// Null without a player location or a tile being resolved.
	@Nullable
	public Integer distance(@Nullable WorldPoint player)
	{
		WorldPoint tile = resolvingTile.tile;
		if (player == null || tile == null)
		{
			return null;
		}

		if (player.getPlane() != tile.getPlane())
		{
			return OTHER_PLANE;
		}

		return Math.min(player.distanceTo(tile), MAX_DISTANCE);
	}

	@Nullable
	public Integer localDistance()
	{
		return client.getLocalPlayer() == null ? null : distance(client.getLocalPlayer().getWorldLocation());
	}

	private static String format(@Nullable Integer distance)
	{
		return distance == null ? null : String.valueOf(distance);
	}
}
