package com.groundmarkervariables.variables.label;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.WorldInfo;
import net.runelite.api.Client;
import net.runelite.api.WorldType;

// {world} / {worldInfo} -> current world number. Rich: "World N (type)"; {worldInfo} adds a
// leading region flag and ": <players> Players | <ping>ms".
public class WorldLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{world(Info)?\\}", Pattern.CASE_INSENSITIVE);

	// Most specific first. Members/skill total worlds show no type.
	private static final List<Map.Entry<WorldType, String>> TYPE_NAMES = List.of(
		Map.entry(WorldType.BETA_WORLD, "Beta"),
		Map.entry(WorldType.TOURNAMENT_WORLD, "Tournament"),
		Map.entry(WorldType.SEASONAL, "Seasonal"),
		Map.entry(WorldType.DEADMAN, "Deadman"),
		Map.entry(WorldType.FRESH_START_WORLD, "Fresh Start"),
		Map.entry(WorldType.HARDCORE_MAIN, "Hardcore"),
		Map.entry(WorldType.LAST_MAN_STANDING, "LMS"),
		Map.entry(WorldType.BOUNTY, "Bounty Hunter"),
		Map.entry(WorldType.PVP_ARENA, "PvP Arena"),
		Map.entry(WorldType.QUEST_SPEEDRUNNING, "Quest"),
		Map.entry(WorldType.NOSAVE_MODE, "No Save"),
		Map.entry(WorldType.HIGH_RISK, "High Risk"),
		Map.entry(WorldType.PVP, "PvP"));

	private final Client client;
	private final WorldInfo worldInfo;
	private final RichText richText;

	@Inject
	private WorldLabelVariable(Client client, WorldInfo worldInfo, RichText richText)
	{
		this.client = client;
		this.worldInfo = worldInfo;
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
		return client.getLocalPlayer() == null ? null : String.valueOf(client.getWorld());
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		int world = client.getWorld();
		boolean info = matcher.group(1) != null;
		StringBuilder sb = new StringBuilder();

		String flag = info ? worldInfo.flagKey(world) : null;
		if (flag != null)
		{
			sb.append("<flag=").append(flag).append("> ");
		}

		sb.append("World ").append(richText.highlightValue(String.valueOf(world)));

		String type = typeName(client.getWorldType());
		if (type != null)
		{
			sb.append(" (").append(type).append(')');
		}

		if (info)
		{
			Integer players = worldInfo.players(world);
			Integer ping = worldInfo.averagePing();
			sb.append(": ").append(players == null ? "?" : String.valueOf(players))
				.append(" Players | ")
				.append(ping == null ? richText.animatedDots(client.getTickCount()) : richText.highlightValue(String.valueOf(ping)) + "ms");
		}

		return sb.toString();
	}

	private static String typeName(EnumSet<WorldType> types)
	{
		for (Map.Entry<WorldType, String> entry : TYPE_NAMES)
		{
			if (types.contains(entry.getKey()))
			{
				return entry.getValue();
			}
		}

		return types.contains(WorldType.MEMBERS) ? null : "F2P";
	}
}
