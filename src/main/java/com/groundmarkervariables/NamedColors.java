package com.groundmarkervariables;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

// Standard color names usable as <col=NAME>, e.g. <col=red> — expanded to <col=FF0000> at
// render time (see GroundMarkerVariablesOverlay) and offered as autocomplete candidates once
// "<col=" has been typed (see AdvancedLabelEditor).
final class NamedColors
{
	static final Map<String, String> HEX_BY_NAME = createHexByName();

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
}
