package com.groundmarkervariables.chatbox.completion;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

// The recent and nearby labels (recent first), as completion's tie-breaker and its only source for values with no
// fixed list (colors, metronome params). Holds the editor's own lists, so its changes (e.g. a removed Recent entry)
// show here.
final class RecentUsage
{
	private List<String> recent = Collections.emptyList();
	private List<String> nearby = Collections.emptyList();

	void set(List<String> recent, List<String> nearby)
	{
		this.recent = recent;
		this.nearby = nearby;
	}

	// The first candidate a recent/nearby label mentions as tokenPrefix + candidate (any case), else the first.
	String pick(List<String> candidates, String tokenPrefix)
	{
		if (candidates.size() == 1)
		{
			return candidates.get(0);
		}
		String lowerPrefix = tokenPrefix.toLowerCase();
		for (List<String> labels : List.of(recent, nearby))
		{
			for (String label : labels)
			{
				String lower = label.toLowerCase();
				for (String candidate : candidates)
				{
					if (lower.contains(lowerPrefix + candidate.toLowerCase()))
					{
						return candidate;
					}
				}
			}
		}
		return candidates.get(0);
	}

	// Every group-1 value of token in a recent/nearby label, recent first.
	Set<String> values(Pattern token)
	{
		Set<String> values = new LinkedHashSet<>();
		for (List<String> labels : List.of(recent, nearby))
		{
			for (String label : labels)
			{
				Matcher matcher = token.matcher(label);
				while (matcher.find())
				{
					values.add(matcher.group(1));
				}
			}
		}
		return values;
	}

	// The first of values(token) that's longer than partial and starts with it.
	@Nullable
	String firstValue(Pattern token, String partial)
	{
		return values(token).stream().filter(value -> value.length() > partial.length() && value.regionMatches(true, 0, partial, 0, partial.length()))
			.findFirst().orElse(null);
	}
}
