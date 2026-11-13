package com.groundmarkervariables.variables.support;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

// Ported from RuneLite's ChatCommandsPlugin#longBossName (BSD 2-Clause License):
// Copyright (c) 2017, Adam <Adam@sigterm.info>. All rights reserved.
// See https://github.com/runelite/runelite/blob/master/LICENSE
//
// Resolves a short alias (e.g. "cg", "cox cm 3") to the canonical name BossKillCountTracker stores kill counts under,
// from boss-aliases.json; unmatched text is title-cased instead, as longBossName does.
@Slf4j
public final class BossAliases
{
	// Loaded on first use; lowercase alias -> canonical name, in the file's order.
	private static final class Aliases
	{
		private static final Map<String, String> BY_ALIAS = load();
	}

	private BossAliases()
	{
	}

	public static String resolve(String boss)
	{
		String alias = Aliases.BY_ALIAS.get(boss.toLowerCase(Locale.ENGLISH));
		return alias != null ? alias : capitalize(boss);
	}

	public static Set<String> aliases()
	{
		return Collections.unmodifiableSet(Aliases.BY_ALIAS.keySet());
	}

	// Every distinct canonical name an alias resolves to, e.g. "Chambers of Xeric".
	public static Set<String> canonicalNames()
	{
		return new LinkedHashSet<>(Aliases.BY_ALIAS.values());
	}

	private static Map<String, String> load()
	{
		Map<String, String> aliases = new LinkedHashMap<>();
		try (Reader reader = new InputStreamReader(BossAliases.class.getResourceAsStream("boss-aliases.json"), StandardCharsets.UTF_8))
		{
			for (Map.Entry<String, JsonElement> alias : new JsonParser().parse(reader).getAsJsonObject().entrySet())
			{
				aliases.put(alias.getKey(), alias.getValue().getAsString());
			}
		}
		catch (Exception e)
		{
			log.warn("Couldn't load boss aliases", e);
		}
		return aliases;
	}

	// Capitalize bosses to their standard format in osrs
	private static String capitalize(String value)
	{
		char[] chars = value.toCharArray();
		boolean capitalizeNext = true;
		for (int i = 0; i < chars.length; i++)
		{
			if (Character.isWhitespace(chars[i]))
			{
				capitalizeNext = true;
			}
			else if (capitalizeNext)
			{
				chars[i] = Character.toTitleCase(chars[i]);
				capitalizeNext = false;
			}
		}

		return new String(chars);
	}
}
