package com.groundmarkervariables.variables.meta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// {align=left/right/center} / {/align} -- scoped from the line it's on through the next
// {align}/{/align} (which resets to center), so it's handled separately from MetaVariable.
final class LineAlignment
{
	private static final Pattern TOKEN = Pattern.compile("\\{align=(left|right|center)\\}|\\{/align\\}", Pattern.CASE_INSENSITIVE);

	private LineAlignment()
	{
	}

	static final class Result
	{
		final String label;
		final List<TextAlign> lineAlignments;

		private Result(String label, List<TextAlign> lineAlignments)
		{
			this.label = label;
			this.lineAlignments = lineAlignments;
		}
	}

	static Result strip(String label)
	{
		String[] rawLines = label.split("\\\\n", -1);
		List<String> strippedLines = new ArrayList<>();
		List<TextAlign> alignments = new ArrayList<>();
		TextAlign current = TextAlign.CENTER;

		for (String line : rawLines)
		{
			Matcher matcher = TOKEN.matcher(line);
			StringBuilder stripped = new StringBuilder();
			int last = 0;
			while (matcher.find())
			{
				stripped.append(line, last, matcher.start());
				last = matcher.end();
				current = matcher.group(1) == null ? TextAlign.CENTER : TextAlign.valueOf(matcher.group(1).toUpperCase(Locale.ROOT));
			}
			stripped.append(line.substring(last));

			strippedLines.add(stripped.toString());
			alignments.add(current);
		}

		return new Result(String.join("\\n", strippedLines), alignments);
	}
}
