package com.groundmarkervariables.variables.label;

import java.io.FileDescriptor;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

import com.groundmarkervariables.variables.Args;
import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import net.runelite.api.Client;
import net.runelite.api.EnumID;
import net.runelite.api.WorldType;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.worldhopper.ping.Ping;
import net.runelite.client.plugins.worldhopper.ping.TCPInfo;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;

// {worldInfo} -> current world number, as {world}. Rich: a leading region flag, "World N (type): <players> Players |
// <ping>ms". hideWorld leaves out the number. Region and players are only looked up while resolving; client thread only.
// @Singleton: one ping history, and one world list request.
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class WorldInfoLabelVariable implements LabelVariable
{
	private static final Args ARGS = Args.of("worldInfo").flag("hideWorld");
	private static final int PING_TICKS = 10;
	private static final int MIN_PING_TICKS = 5;
	// EnumID.WORLD_LOCATIONS values for US worlds.
	private static final int LOCATION_US_WEST = -73;
	private static final int LOCATION_US_EAST = -42;
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
	private final RichText richText;
	private final WorldService worldService;
	private final ScheduledExecutorService executor;

	private volatile boolean worldsLoaded;
	private boolean worldsRequested;
	// {tick, round-trip time in µs}
	private final Deque<long[]> pings = new ArrayDeque<>();
	private int pingWorld;

	@Override
	public Pattern pattern()
	{
		return ARGS.pattern();
	}

	@Override
	public Args args()
	{
		return ARGS;
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
		StringBuilder sb = new StringBuilder();

		String flag = flagKey(world);
		if (flag != null)
		{
			sb.append("<flag=").append(flag).append("> ");
		}

		String type = typeName(client.getWorldType());
		if (ARGS.options(matcher).flag("hideWorld"))
		{
			// e.g. "Seasonal: 642 Players | 23ms", or just "642 Players | 23ms".
			sb.append(type == null ? "" : type + ": ");
		}
		else
		{
			sb.append("World ").append(richText.highlightValue(String.valueOf(world)));
			if (type != null)
			{
				sb.append(" (").append(type).append(')');
			}
			sb.append(": ");
		}

		Integer players = players(world);
		Integer ping = averagePing();
		return sb.append(players == null ? "?" : String.valueOf(players))
			.append(" Players | ")
			.append(ping == null ? richText.animatedDots(client.getTickCount()) : richText.highlightValue(String.valueOf(ping)) + "ms")
			.toString();
	}

	// The world type shown after its number, e.g. "Seasonal" or "F2P"; null for a plain members world.
	@Nullable
	public static String typeName(EnumSet<WorldType> types)
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

	@Nullable
	private Integer players(int worldId)
	{
		World world = world(worldId);
		return world == null || world.getPlayers() < 0 ? null : world.getPlayers();
	}

	// Flag image key, e.g. "us_west" or "uk".
	@Nullable
	private String flagKey(int worldId)
	{
		World world = world(worldId);
		if (world == null || world.getRegion() == null)
		{
			return null;
		}

		switch (world.getRegion())
		{
			case UNITED_STATES_OF_AMERICA:
				int location = client.getEnum(EnumID.WORLD_LOCATIONS).getIntValue(worldId);
				return location == LOCATION_US_WEST ? "us_west" : location == LOCATION_US_EAST ? "us_east" : "us";
			case UNITED_KINGDOM:
				return "uk";
			case AUSTRALIA:
				return "aus";
			case GERMANY:
				return "ger";
			case BRAZIL:
				return "br";
			case JAPAN:
				return "jp";
			case SINGAPORE:
				return "sg";
			case SOUTH_AFRICA:
				return "za";
			default:
				return null;
		}
	}

	// Game connection's mean round-trip time over the last PING_TICKS ticks, rounded up to whole
	// ms -- null until there are MIN_PING_TICKS samples.
	@Nullable
	private Integer averagePing()
	{
		int tick = client.getTickCount();
		if (pingWorld != client.getWorld() || (!pings.isEmpty() && pings.peekLast()[0] > tick))
		{
			pings.clear();
			pingWorld = client.getWorld();
		}

		if (pings.isEmpty() || pings.peekLast()[0] != tick)
		{
			FileDescriptor fd = client.getSocketFD();
			TCPInfo info = fd == null ? null : Ping.getTCPInfo(fd);
			if (info != null && info.getRTT() > 0)
			{
				pings.addLast(new long[]{tick, info.getRTT()});
			}
		}

		while (!pings.isEmpty() && pings.peekFirst()[0] <= tick - PING_TICKS)
		{
			pings.removeFirst();
		}

		if (pings.size() < MIN_PING_TICKS)
		{
			return null;
		}

		long sum = 0;
		for (long[] ping : pings)
		{
			sum += ping[1];
		}
		return (int) Math.ceil(sum / (double) pings.size() / 1000.0);
	}

	@Nullable
	private World world(int worldId)
	{
		if (!worldsLoaded)
		{
			// The first getWorlds() blocks until RuneLite's initial fetch.
			if (!worldsRequested)
			{
				worldsRequested = true;
				executor.execute(() ->
				{
					worldService.getWorlds();
					worldsLoaded = true;
				});
			}
			return null;
		}

		WorldResult worlds = worldService.getWorlds();
		return worlds == null ? null : worlds.findWorld(worldId);
	}
}
