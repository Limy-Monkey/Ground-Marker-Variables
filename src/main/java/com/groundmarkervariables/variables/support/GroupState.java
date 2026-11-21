package com.groundmarkervariables.variables.support;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Singleton;

// {group}/{setGroup}: each group's options, merged from every {setGroup} declaring it in first-seen
// order until shutdown, and its picked option.
@Singleton
public class GroupState
{
	public static final class Group
	{
		public final String name;
		public final List<String> options = new ArrayList<>();

		private Group(String name)
		{
			this.name = name;
		}
	}

	private static final int TEMPORARY_GRACE_TICKS = 5;

	// By lowercase name.
	private final Map<String, Group> groups = new HashMap<>();
	private final Map<String, String> picked = new HashMap<>();
	// Kept across declarations without one, so tiles ordering the options differently share it.
	private final Map<String, String> defaults = new HashMap<>();
	// ~ groups, unpicked once none of their {setGroup} tiles have been resolved for a few ticks.
	private final Set<String> temporary = new HashSet<>();
	private final Map<String, Integer> lastSeen = new HashMap<>();
	private int tick;

	// A {setGroup} for name was resolved, ~ if temporary.
	public void seen(String name, boolean temporary)
	{
		if (temporary)
		{
			this.temporary.add(key(name));
		}
		lastSeen.put(key(name), tick);
	}

	// Once per tick, after the nearby tiles are resolved.
	public void tick()
	{
		tick++;
		for (String key : temporary)
		{
			Integer seen = lastSeen.get(key);
			if (seen == null || tick - seen > TEMPORARY_GRACE_TICKS)
			{
				picked.remove(key);
			}
		}
	}

	public void declare(String name, List<String> options, @Nullable String defaultOption)
	{
		if (options.isEmpty())
		{
			return;
		}

		Group group = groups.computeIfAbsent(key(name), k -> new Group(name));
		for (String option : options)
		{
			if (option(group, option) == null)
			{
				group.options.add(option);
			}
		}

		if (defaultOption != null)
		{
			defaults.put(key(name), defaultOption);
		}
	}

	@Nullable
	public Group group(String name)
	{
		return groups.get(key(name));
	}

	// Every declared group's name, as first declared.
	public List<String> names()
	{
		List<String> names = new ArrayList<>();
		for (Group group : groups.values())
		{
			names.add(group.name);
		}
		return names;
	}

	// The picked option, or if none was picked or it's no longer an option, the default, or the first.
	@Nullable
	public String value(String name)
	{
		Group group = group(name);
		if (group == null)
		{
			return picked.get(key(name));
		}

		String value = option(group, picked.get(key(name)));
		if (value == null)
		{
			value = option(group, defaults.get(key(name)));
		}
		return value != null ? value : group.options.get(0);
	}

	@Nullable
	private static String option(Group group, @Nullable String value)
	{
		for (String option : group.options)
		{
			if (option.equalsIgnoreCase(value))
			{
				return option;
			}
		}
		return null;
	}

	public void pick(String name, String option)
	{
		picked.put(key(name), option);
	}

	public void clear()
	{
		groups.clear();
		picked.clear();
		defaults.clear();
		temporary.clear();
		lastSeen.clear();
		tick = 0;
	}

	private static String key(String name)
	{
		return name.trim().toLowerCase(Locale.ROOT);
	}
}
