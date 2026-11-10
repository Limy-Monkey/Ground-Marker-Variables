package com.groundmarkervariables.variables.meta;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// {ground}...{/ground} -- the icons inside are drawn flat on the tile, top to the north; text inside stays in the
// label. An unclosed block runs to the label's end.
final class GroundIcons
{
	private static final Pattern BLOCK = Pattern.compile("\\{ground\\}(.*?)(?:\\{/ground\\}|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
	private static final Pattern TAG = Pattern.compile("\\{/?ground\\}", Pattern.CASE_INSENSITIVE);
	// The overlay's icon tags. An unclosed {split} runs to the line's end.
	private static final Pattern ICON = Pattern.compile(
		"\\{split\\}.*?(?:\\{/split\\}|(?=\\\\n)|$)|<item=\\d+(?:@\\d{1,3}%)?>|<flag=[a-z_]+>|<emoji=[0-9a-f]+(?:@[0-9a-f]{6})?>"
			+ "|<sprite=\\d+(?:,\\d+)?(?:@\\d{1,3}%)?>|<badge(?:=\\d{1,3})?>|<npc=\\d+(?:,head)?>",
		Pattern.CASE_INSENSITIVE);
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
			content.append(content.length() > 0 ? LINE_BREAK : "").append(block.group(1));
		}
		return content.toString();
	}

	// The label without the blocks' icons. A block left empty that filled its whole line takes the line break
	// with it, and empty lines left at either end are dropped.
	static String remove(String label)
	{
		Matcher block = BLOCK.matcher(label);
		StringBuilder out = new StringBuilder();
		int last = 0;
		while (block.find())
		{
			out.append(label, last, block.start());
			String text = ICON.matcher(block.group(1)).replaceAll("");
			out.append(text);
			last = block.end();
			boolean lineStart = block.start() == 0 || label.startsWith(LINE_BREAK, block.start() - LINE_BREAK.length());
			if (text.trim().isEmpty() && lineStart && label.startsWith(LINE_BREAK, last))
			{
				last += LINE_BREAK.length();
			}
		}
		out.append(label.substring(last));
		String text = TAG.matcher(out).replaceAll("").replaceAll("^(?:\\s*\\\\n)+|(?:\\\\n\\s*)+$", "");
		return text.replace(LINE_BREAK, "").trim().isEmpty() ? "" : text;
	}
}
