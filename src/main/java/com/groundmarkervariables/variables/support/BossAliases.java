package com.groundmarkervariables.variables.support;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;

// Ported from RuneLite's ChatCommandsPlugin#longBossName (BSD 2-Clause License):
// Copyright (c) 2017, Adam <Adam@sigterm.info>. All rights reserved.
// See https://github.com/runelite/runelite/blob/master/LICENSE
//
// Resolves a short alias (e.g. "cg", "cox cm 3") to the canonical name BossKillCountTracker stores kill counts under,
// from boss-aliases.json (the bundled one, or GitHub's newer one); unmatched text is title-cased instead, as longBossName
// does.
@Slf4j
public final class BossAliases
{
	// Trackable activities with no hiscores entry or short form here.
	private static final List<String> EXTRA_NAMES = List.of(
		"Jubbly Jive", "Tempor Tantrum", "Gwenith Glide", "Galvek", "Obor chests opened", "Agility Arena Total Ticket");

	// Lowercase alias -> canonical name, in the file's order: the bundled file's, loaded on first use, until update()
	// replaces them.
	@Nullable
	private static volatile Map<String, String> byAlias;

	private BossAliases()
	{
	}

	public static String resolve(String boss)
	{
		String alias = byAlias().get(boss.toLowerCase(Locale.ENGLISH));
		return alias != null ? alias : capitalize(boss);
	}

	public static Set<String> aliases()
	{
		return Collections.unmodifiableSet(byAlias().keySet());
	}

	// Every distinct canonical name an alias resolves to, e.g. "Chambers of Xeric".
	public static Set<String> canonicalNames()
	{
		return new LinkedHashSet<>(byAlias().values());
	}

	// A newer boss-aliases.json (see LatestData); a damaged one is ignored.
	public static void update(String json)
	{
		try
		{
			byAlias = parse(new StringReader(json));
		}
		catch (Exception e)
		{
			log.warn("Couldn't read the latest boss aliases", e);
		}
	}

	// Bosses to suggest: the hiscores' (no "The ", no colon, as BossKillCountTracker keys them), the short forms and
	// names here, and EXTRA_NAMES. hiscoresOnly ({rank}): only bosses on the hiscores.
	public static Set<String> suggestions(boolean hiscoresOnly)
	{
		Set<String> names = new LinkedHashSet<>();
		for (HiscoreSkill skill : HiscoreSkill.values())
		{
			if (skill.getType() == HiscoreSkillType.BOSS)
			{
				String name = skill.getName();
				names.add((name.regionMatches(true, 0, "The ", 0, 4) ? name.substring(4) : name).replace(":", ""));
			}
		}
		for (String alias : aliases())
		{
			if (!alias.contains(" - ") && (!hiscoresOnly || BossHiscoreSkill.find(resolve(alias)) != null))
			{
				names.add(alias);
			}
		}
		for (String name : canonicalNames())
		{
			if (!hiscoresOnly || BossHiscoreSkill.find(name) != null)
			{
				names.add(name);
			}
		}
		if (!hiscoresOnly)
		{
			names.addAll(EXTRA_NAMES);
		}
		return names;
	}

	private static Map<String, String> byAlias()
	{
		Map<String, String> loaded = byAlias;
		if (loaded == null)
		{
			try (Reader reader = new InputStreamReader(BossAliases.class.getResourceAsStream("boss-aliases.json"), StandardCharsets.UTF_8))
			{
				loaded = parse(reader);
			}
			catch (Exception e)
			{
				log.warn("Couldn't load boss aliases", e);
				loaded = Map.of();
			}
			byAlias = loaded;
		}
		return loaded;
	}

	private static Map<String, String> parse(Reader reader)
	{
		Map<String, String> aliases = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> alias : new JsonParser().parse(reader).getAsJsonObject().entrySet())
		{
			aliases.put(alias.getKey(), alias.getValue().getAsString());
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
