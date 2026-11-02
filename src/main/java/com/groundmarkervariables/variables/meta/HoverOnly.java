package com.groundmarkervariables.variables.meta;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// {hoverOnly}...{/hoverOnly} -- shown only while the label is hovered; {hoverHide}...{/hoverHide} only while
// it isn't. Either may span lines (\n) and table cells (\t); an unclosed block runs to the end of the label.
final class HoverOnly
{
	private static final Pattern ONLY_BLOCK = block("hoverOnly");
	private static final Pattern HIDE_BLOCK = block("hoverHide");
	private static final Pattern ONLY_TAG = Pattern.compile("\\{/?hoverOnly\\}", Pattern.CASE_INSENSITIVE);
	private static final Pattern HIDE_TAG = Pattern.compile("\\{/?hoverHide\\}", Pattern.CASE_INSENSITIVE);
	private static final String LINE_BREAK = "\\n";

	private HoverOnly()
	{
	}

	private static Pattern block(String name)
	{
		return Pattern.compile("\\{" + name + "\\}(.*?)(?:\\{/" + name + "\\}|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
	}

	static boolean has(String label)
	{
		return ONLY_TAG.matcher(label).find() || HIDE_TAG.matcher(label).find();
	}

	// As when hovered.
	static String expand(String label)
	{
		return ONLY_TAG.matcher(remove(label, HIDE_BLOCK)).replaceAll("");
	}

	// As when not hovered.
	static String collapse(String label)
	{
		return HIDE_TAG.matcher(remove(label, ONLY_BLOCK)).replaceAll("");
	}

	// Without the blocks. A block that fills whole lines takes its line break with it.
	private static String remove(String label, Pattern block)
	{
		Matcher matcher = block.matcher(label);
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
