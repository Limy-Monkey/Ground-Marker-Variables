package com.groundmarkervariables.variables.support;

import com.google.gson.JsonElement;
import java.awt.Color;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

// Color names usable as <col=NAME>, e.g. <col=red> -> <col=FF0000>: named-colors.json, plus highlight and limy.
@Slf4j
public final class NamedColors {
	private static final String LIMY = "limy";
	// The Highlight Color config option's color; see setHighlight.
	private static final String HIGHLIGHT = "highlight";
	private static final Map<String, String> COLORS = createHexByName();
	public static final Map<String, String> HEX_BY_NAME = Collections.unmodifiableMap(COLORS);
	// Offered as suggestions: all but limy, which works but stays unlisted.
	public static final Set<String> SUGGESTED = HEX_BY_NAME.keySet().stream().filter(name -> !name.equals(LIMY))
		.collect(Collectors.toCollection(LinkedHashSet::new));

	// Opacity-only names for {tileCol=...} (not valid in <col=...>/{col=...}) -- percent values.
	public static final Map<String, Integer> OPACITY_BY_NAME = createOpacityByName();

	// <col=NAME>, {col=HEX|NAME} as an alias for <col=...>, and {/col} as an alias for </col>.
	private static final Pattern COLOR_ALIAS_PATTERN = Pattern.compile(
		"<col=(" + String.join("|", HEX_BY_NAME.keySet()) + ")>"
			+ "|\\{col=([0-9a-fA-F]{2,6}|" + String.join("|", HEX_BY_NAME.keySet()) + ")\\}"
			+ "|\\{/col\\}",
		Pattern.CASE_INSENSITIVE);

	private NamedColors() {
	}

	// named-colors.json's colors in its order, then limy.
	private static Map<String, String> createHexByName() {
		Map<String, String> colors = new LinkedHashMap<>();
		try (Reader reader = new InputStreamReader(NamedColors.class.getResourceAsStream("named-colors.json"), StandardCharsets.UTF_8)) {
			for (Map.Entry<String, JsonElement> color : new JsonParser().parse(reader).getAsJsonObject().getAsJsonObject("colors").entrySet()) {
				colors.put(color.getKey().toLowerCase(Locale.ROOT), color.getValue().getAsString());
			}
		} catch (Exception e) {
			log.warn("Couldn't load named colors", e);
		}
		colors.put(HIGHLIGHT, "FFC3A0");
		colors.put(LIMY, "FFC3A0");
		return colors;
	}

	// highlight's color, from the Highlight Color config option.
	public static void setHighlight(Color color) {
		COLORS.put(HIGHLIGHT, String.format("%06X", color.getRGB() & 0xFFFFFF));
	}

	private static Map<String, Integer> createOpacityByName() {
		Map<String, Integer> opacities = new LinkedHashMap<>();
		opacities.put("invis", 0);
		opacities.put("translucent", 50);
		opacities.put("opaque", 100);
		opacities.put("solid", 100);
		return Collections.unmodifiableMap(opacities);
	}

	// <col=NAME>/{col=HEX|NAME} become <col=HEX>; {/col} becomes </col> (still unresolved).
	public static String expandColorAliases(String text) {
		Matcher matcher = COLOR_ALIAS_PATTERN.matcher(text);
		if (!matcher.find()) {
			return text;
		}

		StringBuilder result = new StringBuilder();
		matcher.reset();
		while (matcher.find()) {
			String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
			String replacement = value == null ? "</col>" : "<col=" + HEX_BY_NAME.getOrDefault(value.toLowerCase(), value) + ">";
			matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(result);
		return result.toString();
	}
}
