package com.groundmarkervariables.variables.meta;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.groundmarkervariables.variables.label.ConditionalVariable;

// {tileCol}, {highlight} and {borderWidth} only apply written literally, outside any token but a condition on
// {group} alone. protect() hides those before resolving; restore() removes all others, then puts them back.
public final class TileMetaGuard
{
	private static final List<Pattern> TILE_META = List.of(TileColMetaVariable.PATTERN, HighlightMetaVariable.PATTERN,
		BorderWidthMetaVariable.PATTERN);
	private static final char PLACEHOLDER_START = '';
	private static final char PLACEHOLDER_END = '';
	private static final Pattern COMPARISON = Pattern.compile("(.+?)\\s*(==|!=|<=|>=|<|>)\\s*(.*)");
	private static final Pattern GROUP = Pattern.compile("group\\s+[^{}?:<>=!,|&()]+", Pattern.CASE_INSENSITIVE);

	private TileMetaGuard()
	{
	}

	// isVariable: whether text resolves as a variable, so a comparison against it isn't on {group} alone.
	public static String protect(String label, List<String> tokens, Predicate<String> isVariable)
	{
		List<int[]> allowed = new ArrayList<>();
		for (Pattern pattern : TILE_META)
		{
			Matcher matcher = pattern.matcher(label);
			while (matcher.find())
			{
				if (allowed(label, matcher.start(), isVariable))
				{
					allowed.add(new int[] {matcher.start(), matcher.end()});
				}
			}
		}
		allowed.sort((a, b) -> Integer.compare(a[0], b[0]));

		StringBuilder out = new StringBuilder();
		int last = 0;
		for (int[] range : allowed)
		{
			out.append(label, last, range[0]).append(PLACEHOLDER_START).append(tokens.size()).append(PLACEHOLDER_END);
			tokens.add(label.substring(range[0], range[1]));
			last = range[1];
		}
		return out.append(label.substring(last)).toString();
	}

	public static String restore(String resolved, List<String> tokens)
	{
		if (resolved == null)
		{
			return null;
		}

		for (Pattern pattern : TILE_META)
		{
			resolved = pattern.matcher(resolved).replaceAll("");
		}
		for (int i = 0; i < tokens.size(); i++)
		{
			resolved = resolved.replace(PLACEHOLDER_START + String.valueOf(i) + PLACEHOLDER_END, tokens.get(i));
		}
		return resolved;
	}

	// True if every token enclosing pos is a condition on {group} alone, with pos in a branch.
	static boolean allowed(String label, int pos, Predicate<String> isVariable)
	{
		Deque<Integer> open = new ArrayDeque<>();
		for (int i = 0; i < pos; i++)
		{
			char c = label.charAt(i);
			if (c == '{')
			{
				open.push(i);
			}
			else if (c == '}' && !open.isEmpty())
			{
				open.pop();
			}
		}

		for (int start : open)
		{
			int close = ConditionalVariable.matchingBrace(label, start);
			if (close < 0)
			{
				return false;
			}

			String inner = label.substring(start + 1, close);
			int question = topLevelQuestion(inner);
			if (question < 0 || start + 1 + question > pos || !groupOnly(inner.substring(0, question), isVariable))
			{
				return false;
			}
		}
		return true;
	}

	// Each &&/|| part is "group <name>", or one compared with a literal (or [list]).
	static boolean groupOnly(String condition, Predicate<String> isVariable)
	{
		if (condition.contains("{"))
		{
			return false;
		}

		for (String part : condition.split("&&|\\|\\|"))
		{
			String leaf = part.replace('(', ' ').replace(')', ' ').trim();
			Matcher comparison = COMPARISON.matcher(leaf);
			boolean compared = comparison.matches();
			if (!GROUP.matcher(compared ? comparison.group(1).trim() : leaf).matches())
			{
				return false;
			}

			String value = compared ? comparison.group(3).trim() : "";
			if (!value.startsWith("[") && !value.isEmpty() && isVariable.test(value))
			{
				return false;
			}
		}
		return true;
	}

	static int topLevelQuestion(String text)
	{
		int depth = 0;
		for (int i = 0; i < text.length(); i++)
		{
			char c = text.charAt(i);
			if (c == '{')
			{
				depth++;
			}
			else if (c == '}')
			{
				depth--;
			}
			else if (depth == 0 && c == '?')
			{
				return i;
			}
		}
		return -1;
	}
}
