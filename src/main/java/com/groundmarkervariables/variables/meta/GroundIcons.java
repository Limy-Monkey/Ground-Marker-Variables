package com.groundmarkervariables.variables.meta;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// {ground[@dir]}...{/ground} -- the icons and text inside are drawn flat on the tile, top to the north (or dir: n, ne,
// e, se, s, sw, w, nw), text below the icons. An unclosed block runs to the label's end.
final class GroundIcons
{
	private static final String DIRECTION = "(?:@(ne|nw|se|sw|n|e|s|w))?";
	private static final Pattern BLOCK = Pattern.compile("\\{ground" + DIRECTION + "\\}(.*?)(?:\\{/ground\\}|$)",
		Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
	static final Pattern TAG = Pattern.compile("\\{ground" + DIRECTION + "\\}|\\{/ground\\}", Pattern.CASE_INSENSITIVE);
	private static final List<String> DIRECTIONS = List.of("n", "ne", "e", "se", "s", "sw", "w", "nw");
	private static final String LINE_BREAK = "\\n";

	private GroundIcons()
	{
	}

	static boolean has(String label)
	{
		return TAG.matcher(label).find();
	}

	// Everything inside the blocks, one per line.
	static String content(String label)
	{
		StringBuilder content = new StringBuilder();
		Matcher block = BLOCK.matcher(label);
		while (block.find())
		{
			content.append(content.length() > 0 ? LINE_BREAK : "").append(block.group(2));
		}
		return content.toString();
	}

	// The first block's @dir, in degrees clockwise from north; 0 without one.
	static int facing(String label)
	{
		Matcher block = BLOCK.matcher(label);
		return block.find() && block.group(1) != null ? DIRECTIONS.indexOf(block.group(1).toLowerCase(Locale.ROOT)) * 45 : 0;
	}

	// The label without the blocks. A block that filled its whole line takes the line break with it, and empty
	// lines left at either end are dropped.
	static String remove(String label)
	{
		Matcher block = BLOCK.matcher(label);
		StringBuilder out = new StringBuilder();
		int last = 0;
		while (block.find())
		{
			out.append(label, last, block.start());
			last = block.end();
			boolean lineStart = block.start() == 0 || label.startsWith(LINE_BREAK, block.start() - LINE_BREAK.length());
			if (lineStart && label.startsWith(LINE_BREAK, last))
			{
				last += LINE_BREAK.length();
			}
		}
		out.append(label.substring(last));
		String text = TAG.matcher(out).replaceAll("").replaceAll("^(?:\\s*\\\\n)+|(?:\\\\n\\s*)+$", "");
		return text.replace(LINE_BREAK, "").trim().isEmpty() ? "" : text;
	}
}
