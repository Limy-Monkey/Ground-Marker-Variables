package com.groundmarkervariables.variables.support;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Singleton;

// {group}/{setGroup}: each group's options as last declared by a {setGroup}, and its picked option.
@Singleton
public class GroupState
{
	public static final class Group
	{
		public final String name;
		public final List<String> options;

		private Group(String name, List<String> options)
		{
			this.name = name;
			this.options = options;
		}
	}

	// By lowercase name.
	private final Map<String, Group> groups = new HashMap<>();
	private final Map<String, String> picked = new HashMap<>();

	public void declare(String name, List<String> options)
	{
		if (!options.isEmpty())
		{
			groups.put(key(name), new Group(name, options));
		}
	}

	@Nullable
	public Group group(String name)
	{
		return groups.get(key(name));
	}

	// The picked option, or the first if none was picked or it's no longer an option.
	@Nullable
	public String value(String name)
	{
		String value = picked.get(key(name));
		Group group = group(name);
		if (group == null)
		{
			return value;
		}

		for (String option : group.options)
		{
			if (option.equalsIgnoreCase(value))
			{
				return option;
			}
		}
		return group.options.get(0);
	}

	public void pick(String name, String option)
	{
		picked.put(key(name), option);
	}

	public void clear()
	{
		groups.clear();
		picked.clear();
	}

	private static String key(String name)
	{
		return name.trim().toLowerCase(Locale.ROOT);
	}
}
