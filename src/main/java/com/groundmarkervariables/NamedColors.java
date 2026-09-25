package com.groundmarkervariables;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Standard color names usable as <col=NAME>, e.g. <col=red> — expanded to <col=FF0000> at
// render time (see GroundMarkerVariablesOverlay) and offered as autocomplete candidates once
// "<col=" has been typed (see AdvancedLabelEditor).
final class NamedColors
{
	static final Map<String, String> HEX_BY_NAME = createHexByName();

	// <col=NAME>, {col=HEX|NAME} as an alias for <col=...>, and {/col} as an alias for </col>.
	private static final Pattern COLOR_ALIAS_PATTERN = Pattern.compile(
		"<col=(" + String.join("|", HEX_BY_NAME.keySet()) + ")>"
			+ "|\\{col=([0-9a-fA-F]{2,6}|" + String.join("|", HEX_BY_NAME.keySet()) + ")\\}"
			+ "|\\{/col\\}",
		Pattern.CASE_INSENSITIVE);

	private NamedColors()
	{
	}

	private static Map<String, String> createHexByName()
	{
		Map<String, String> colors = new LinkedHashMap<>();
		colors.put("red", "FF0000");
		colors.put("green", "00FF00");
		colors.put("blue", "0000FF");
		colors.put("teal", "00FFFF");
		colors.put("yellow", "FFFF00");
		colors.put("purple", "FF00FF");
		colors.put("white", "FFFFFF");
		colors.put("black", "000000");
		return Collections.unmodifiableMap(colors);
	}

	// <col=NAME>/{col=HEX|NAME} become <col=HEX>; {/col} becomes </col> (still unresolved).
	static String expandColorAliases(String text)
	{
		Matcher matcher = COLOR_ALIAS_PATTERN.matcher(text);
		if (!matcher.find())
		{
			return text;
		}

		StringBuilder result = new StringBuilder();
		matcher.reset();
		while (matcher.find())
		{
			String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
			String replacement = value == null ? "</col>" : "<col=" + HEX_BY_NAME.getOrDefault(value.toLowerCase(), value) + ">";
			matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(result);
		return result.toString();
	}
}
