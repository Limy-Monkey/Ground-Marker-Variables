package com.groundmarkervariables.variables.support;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

// Ported from RuneLite's DiscordGameEventType area list (BSD 2-Clause License):
// Copyright (c) 2018, Tomas Slusny <slusnucky@gmail.com>
// Copyright (c) 2018, PandahRS <https://github.com/PandahRS>
// Copyright (c) 2020, Brooklyn <https://github.com/Broooklyn>
// All rights reserved.
// See https://github.com/runelite/runelite/blob/master/LICENSE
//
// Region id -> area name, e.g. 12850 -> "Lumbridge", from region-names.json (area name -> its region ids). Regions not
// listed have no name.
@Slf4j
public final class RegionNames
{
	// Loaded on first use.
	private static final class Names
	{
		private static final Map<Integer, String> BY_REGION = load();
	}

	private RegionNames()
	{
	}

	@Nullable
	public static String name(int regionId)
	{
		return Names.BY_REGION.get(regionId);
	}

	private static Map<Integer, String> load()
	{
		Map<Integer, String> names = new HashMap<>();
		try (Reader reader = new InputStreamReader(RegionNames.class.getResourceAsStream("region-names.json"), StandardCharsets.UTF_8))
		{
			for (Map.Entry<String, JsonElement> area : new JsonParser().parse(reader).getAsJsonObject().entrySet())
			{
				for (JsonElement region : area.getValue().getAsJsonArray())
				{
					names.put(region.getAsInt(), area.getKey());
				}
			}
		}
		catch (Exception e)
		{
			log.warn("Couldn't load region names", e);
		}
		return names;
	}
}
