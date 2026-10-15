package com.groundmarkervariables.variables.support;

import java.io.FileDescriptor;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.EnumID;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.worldhopper.ping.Ping;
import net.runelite.client.plugins.worldhopper.ping.TCPInfo;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;

// Current world's region, player count and ping, only looked up while resolving. Client thread only.
@Singleton
public class WorldInfo
{
	private static final int PING_TICKS = 10;
	private static final int MIN_PING_TICKS = 5;
	// EnumID.WORLD_LOCATIONS values for US worlds.
	private static final int LOCATION_US_WEST = -73;
	private static final int LOCATION_US_EAST = -42;

	private final Client client;
	private final WorldService worldService;
	private final ScheduledExecutorService executor;

	private volatile boolean worldsLoaded;
	private boolean worldsRequested;
	// {tick, round-trip time in µs}
	private final Deque<long[]> pings = new ArrayDeque<>();
	private int pingWorld;

	@Inject
	private WorldInfo(Client client, WorldService worldService, ScheduledExecutorService executor)
	{
		this.client = client;
		this.worldService = worldService;
		this.executor = executor;
	}

	@Nullable
	public Integer players(int worldId)
	{
		World world = world(worldId);
		return world == null || world.getPlayers() < 0 ? null : world.getPlayers();
	}

	// Flag image key, e.g. "us_west" or "uk".
	@Nullable
	public String flagKey(int worldId)
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
	public Integer averagePing()
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
