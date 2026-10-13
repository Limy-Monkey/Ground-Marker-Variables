package com.groundmarkervariables.variables.meta;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

// {borderWidth=<width>} -- this tile's own outline width, same units as the Border width config,
// capped at MAX_WIDTH.
public class BorderWidthMetaVariable implements MetaVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{borderWidth=(\\d+(?:\\.\\d+)?)\\}", Pattern.CASE_INSENSITIVE);
	private static final float MAX_WIDTH = 10f;

	@Inject
	private BorderWidthMetaVariable()
	{
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public void apply(Matcher matcher, MetaState state)
	{
		state.setBorderWidth(Math.min(MAX_WIDTH, Float.parseFloat(matcher.group(1))));
	}
}
