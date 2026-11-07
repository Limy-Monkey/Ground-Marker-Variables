package com.groundmarkervariables.variables.label;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.GroupState;

// {group <name>} -> the option picked for <name> (see SetGroupMetaVariable), the same in every marker.
public class GroupLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{group\\s+([^{}?:<>=!,]+?)\\s*\\}", Pattern.CASE_INSENSITIVE);

	private final GroupState groupState;
	private final RichText richText;

	@Inject
	private GroupLabelVariable(GroupState groupState, RichText richText)
	{
		this.groupState = groupState;
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
		String value = groupState.value(matcher.group(1).trim());
		return value == null ? "" : value;
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		String value = resolvePlain(matcher);
		if (value.isEmpty())
		{
			return "";
		}

		GroupState.Group group = groupState.group(matcher.group(1).trim());
		return richText.labeled(group != null ? group.name : matcher.group(1).trim(), value);
	}
}
