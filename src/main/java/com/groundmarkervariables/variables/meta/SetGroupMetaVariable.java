package com.groundmarkervariables.variables.meta;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.support.GroupState;

// {setGroup <name>, <option>, <option>, ...} -- declares <name>'s options, picked from the
// shift+right-click Tile Variables menu of markers using that group. See {group <name>}.
public class SetGroupMetaVariable implements MetaVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{setGroup\\s+([^{}?:<>=!,]+?)\\s*,([^{}]*)\\}", Pattern.CASE_INSENSITIVE);
	private static final int MAX_OPTIONS = 10;

	private final GroupState groupState;

	@Inject
	private SetGroupMetaVariable(GroupState groupState)
	{
		this.groupState = groupState;
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public void apply(Matcher matcher, MetaState state)
	{
		List<String> options = new ArrayList<>();
		for (String option : matcher.group(2).split(","))
		{
			option = option.trim();
			if (!option.isEmpty() && options.size() < MAX_OPTIONS)
			{
				options.add(option);
			}
		}
		groupState.declare(matcher.group(1).trim(), options);
	}
}
