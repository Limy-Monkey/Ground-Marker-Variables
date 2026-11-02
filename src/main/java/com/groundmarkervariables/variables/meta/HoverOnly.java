package com.groundmarkervariables.variables.meta;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// {hoverOnly}...{/hoverOnly} -- shown only while the label is hovered. May span lines (\n) and table
// cells (\t); an unclosed block runs to the end of the label.
final class HoverOnly
{
	private static final Pattern BLOCK = Pattern.compile("\\{hoverOnly\\}(.*?)(?:\\{/hoverOnly\\}|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
	private static final Pattern TAG = Pattern.compile("\\{/?hoverOnly\\}", Pattern.CASE_INSENSITIVE);
	private static final String LINE_BREAK = "\\n";

	private HoverOnly()
	{
	}

	static boolean has(String label)
	{
		return TAG.matcher(label).find();
	}

	// With every block's content, as when hovered.
	static String expand(String label)
	{
		return TAG.matcher(label).replaceAll("");
	}

	// Without the blocks. A block that fills whole lines takes its line break with it.
	static String collapse(String label)
	{
		Matcher matcher = BLOCK.matcher(label);
		StringBuilder out = new StringBuilder();
		int last = 0;
		while (matcher.find())
		{
			int start = matcher.start();
			int end = matcher.end();
			boolean lineStart = start == 0 || label.startsWith(LINE_BREAK, start - LINE_BREAK.length());
			if (lineStart && label.startsWith(LINE_BREAK, end))
			{
				end += LINE_BREAK.length();
			}
			else if (lineStart && end == label.length() && start > 0)
			{
				start -= LINE_BREAK.length();
			}

			out.append(label, last, Math.max(last, start));
			last = end;
		}
		return out.append(label.substring(last)).toString();
	}
}
