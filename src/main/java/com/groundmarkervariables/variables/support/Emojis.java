package com.groundmarkervariables.variables.support;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Singleton;

// :name: emojis. No source yet, so the name list is always empty and nothing expands.
@Singleton
public class Emojis
{
	private static final Pattern EMOJI = Pattern.compile(":([a-z0-9_!().\\-ñ]+):");

	private final Map<String, String> names = Collections.emptyMap();

	// ":balloon:" -> "<emoji=1f388>", the overlay's own inline image tag. Unknown names are left as-is.
	public String expand(String label)
	{
		if (label == null || label.indexOf(':') < 0 || names.isEmpty())
		{
			return label;
		}

		Matcher matcher = EMOJI.matcher(label);
		StringBuilder sb = new StringBuilder();
		while (matcher.find())
		{
			String codepoint = names.get(matcher.group(1));
			matcher.appendReplacement(sb, Matcher.quoteReplacement(codepoint == null ? matcher.group() : "<emoji=" + codepoint + ">"));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	public Set<String> names()
	{
		return names.keySet();
	}

	@Nullable
	public BufferedImage image(String codepoint)
	{
		return null;
	}
}
