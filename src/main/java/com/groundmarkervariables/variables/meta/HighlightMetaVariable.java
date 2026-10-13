package com.groundmarkervariables.variables.meta;

import java.awt.Color;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.GroundMarkerVariablesConfig;
import com.groundmarkervariables.variables.support.NamedColors;
import net.runelite.client.util.ColorUtil;

// {highlight<corner>} / {highlight<corner>=<col>} -- SW/NW/NE/SE, or Corners/All for all four.
public class HighlightMetaVariable implements MetaVariable
{
	private static final Pattern PATTERN = Pattern.compile(
		"\\{highlight(SW|NW|NE|SE|Corners|All)(?:=(" + String.join("|", NamedColors.HEX_BY_NAME.keySet())
			+ "|" + String.join("|", NamedColors.OPACITY_BY_NAME.keySet()) + "))?\\}",
		Pattern.CASE_INSENSITIVE);

	private final GroundMarkerVariablesConfig config;

	@Inject
	private HighlightMetaVariable(GroundMarkerVariablesConfig config)
	{
		this.config = config;
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public void apply(Matcher matcher, MetaState state)
	{
		Color color = resolveColor(matcher.group(2));
		for (HighlightCorner corner : corners(matcher.group(1)))
		{
			state.setHighlight(corner, color);
		}
	}

	private Color resolveColor(String value)
	{
		if (value == null)
		{
			return config.highlightColor();
		}

		Integer opacity = NamedColors.OPACITY_BY_NAME.get(value.toLowerCase(Locale.ROOT));
		if (opacity != null)
		{
			return ColorUtil.colorWithAlpha(config.highlightColor(), Math.round(opacity / 100f * 255));
		}

		return new Color(Integer.parseInt(NamedColors.HEX_BY_NAME.get(value.toLowerCase(Locale.ROOT)), 16));
	}

	private static List<HighlightCorner> corners(String arg)
	{
		if ("corners".equalsIgnoreCase(arg) || "all".equalsIgnoreCase(arg))
		{
			return List.of(HighlightCorner.values());
		}

		return List.of(HighlightCorner.valueOf(arg.toUpperCase(Locale.ROOT)));
	}
}
