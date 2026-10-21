package com.groundmarkervariables.variables.label;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.support.ConditionEvaluator;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

// {<chain> ? <whenTrue> [: <whenFalse>]} — <chain> is any number of "<expr> [<cmp> <value>]"
// leaves combined with && / ||, with parens overriding the usual && before || precedence.
// Branches may contain {...} tokens nested to any depth, including conditionals, e.g.
// {a ? {b ? X : Y} : Z}. Base variables in the winning branch resolve in LabelResolver's later
// passes; metronome is neutralized here, a metavariable left for MetaVariableResolver.
public class ConditionalVariable implements LabelVariable
{
	private static final String METRONOME_TOKEN =
		"(?i:\\{(?:metronome|m)[+-]?\\d+(?:_\\d+)?\\})";
	private static final Pattern METRONOME_TOKEN_PATTERN = Pattern.compile(METRONOME_TOKEN);
	private static final String NESTED_TOKEN = "\\{[^{}]*\\}";

	private static final Pattern LEAF = Pattern.compile("([^{}]+?)\\s*(?:(==|!=|<=|>=|<|>)\\s*([^{}]+?)\\s*)?");
	private static final Pattern PATTERN = Pattern.compile(
		"\\{([^{}?]+\\?(?:[^{}]|" + NESTED_TOKEN + ")+)\\}");

	private final ConditionEvaluator evaluator;

	@Inject
	private ConditionalVariable(ConditionEvaluator evaluator)
	{
		this.evaluator = evaluator;
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	// No Rich/Plain distinction of its own -- output is whichever branch's literal text won.
	@Override
	public String resolvePlain(Matcher matcher)
	{
		return resolveConditional(matcher);
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		return resolveConditional(matcher);
	}

	private String resolveConditional(Matcher matcher)
	{
		return resolveToken(matcher.group());
	}

	// Resolves each top-level {...} conditional in text. Other tokens are left as-is.
	public String resolveAll(String text)
	{
		StringBuilder out = new StringBuilder();
		int i = 0;
		while (i < text.length())
		{
			int open = text.indexOf('{', i);
			int close = open < 0 ? -1 : matchingBrace(text, open);
			if (close < 0)
			{
				out.append(text, i, text.length());
				break;
			}

			String token = text.substring(open, close + 1);
			String resolved;
			try
			{
				resolved = resolveToken(token);
			}
			catch (RuntimeException e)
			{
				resolved = "ERROR";
			}
			out.append(text, i, open).append(resolved == null ? token : resolved);
			i = close + 1;
		}
		return out.toString();
	}

	// Null if token isn't a resolvable conditional. Only the winning branch's own nested
	// conditionals are resolved.
	private String resolveToken(String token)
	{
		String inner = token.substring(1, token.length() - 1);
		int question = topLevel(inner, 0, '?');
		if (question < 0 || inner.substring(0, question).contains("{"))
		{
			return null;
		}

		Boolean result = evaluate(inner.substring(0, question));
		if (result == null)
		{
			return null;
		}

		// No : means an empty else.
		int colon = topLevel(inner, question + 1, ':');
		String whenTrue = colon < 0 ? inner.substring(question + 1) : inner.substring(question + 1, colon);
		String whenFalse = colon < 0 ? "" : inner.substring(colon + 1);
		String winner = METRONOME_TOKEN_PATTERN.matcher((result ? whenTrue : whenFalse).trim()).replaceAll("ILLEGAL");
		return resolveAll(winner);
	}

	private static int matchingBrace(String text, int open)
	{
		int depth = 0;
		for (int i = open; i < text.length(); i++)
		{
			char c = text.charAt(i);
			if (c == '{')
			{
				depth++;
			}
			else if (c == '}' && --depth == 0)
			{
				return i;
			}
		}
		return -1;
	}

	// First c outside any {...} at or after from. A : between digits (9:00) doesn't count.
	private static int topLevel(String text, int from, char c)
	{
		int depth = 0;
		for (int i = from; i < text.length(); i++)
		{
			char ch = text.charAt(i);
			if (ch == '{')
			{
				depth++;
			}
			else if (ch == '}')
			{
				depth--;
			}
			else if (depth == 0 && ch == c && !(c == ':' && isClockColon(text, i)))
			{
				return i;
			}
		}
		return -1;
	}

	private static boolean isClockColon(String text, int i)
	{
		return i > 0 && i + 1 < text.length() && Character.isDigit(text.charAt(i - 1)) && Character.isDigit(text.charAt(i + 1));
	}

	// Splits on top-level (paren-depth 0) && / ||, then collapses && pairs before || pairs --
	// same flat two-pass shape as MathVariable's own * / before + -.
	private Boolean evaluate(String text)
	{
		List<Boolean> values = new ArrayList<>();
		List<String> operators = new ArrayList<>();

		int depth = 0;
		int last = 0;
		for (int i = 0; i < text.length(); i++)
		{
			char c = text.charAt(i);
			if (c == '(')
			{
				depth++;
			}
			else if (c == ')')
			{
				depth--;
			}
			else if (depth == 0 && (text.startsWith("&&", i) || text.startsWith("||", i)))
			{
				values.add(evaluateFactor(text.substring(last, i)));
				operators.add(text.substring(i, i + 2));
				i++;
				last = i + 1;
			}
		}
		values.add(evaluateFactor(text.substring(last)));

		for (int i = 0; i < operators.size(); )
		{
			if (!"&&".equals(operators.get(i)))
			{
				i++;
				continue;
			}

			values.set(i, and(values.get(i), values.get(i + 1)));
			values.remove(i + 1);
			operators.remove(i);
		}

		Boolean result = values.get(0);
		for (Boolean value : values.subList(1, values.size()))
		{
			result = or(result, value);
		}
		return result;
	}

	// A factor fully wrapped in one matching paren pair recurses; otherwise it's a leaf. A
	// leaf's own <expr>/<value> may still contain balanced parens of its own (e.g. a
	// MathVariable expression) -- those are left untouched for ConditionEvaluator to resolve.
	private Boolean evaluateFactor(String text)
	{
		String trimmed = text.trim();
		if (isFullyWrapped(trimmed))
		{
			return evaluate(trimmed.substring(1, trimmed.length() - 1));
		}

		Matcher leaf = LEAF.matcher(trimmed);
		return leaf.matches() ? evaluator.evaluateCondition(leaf.group(1), leaf.group(2), leaf.group(3)) : null;
	}

	private static boolean isFullyWrapped(String text)
	{
		if (text.length() < 2 || text.charAt(0) != '(' || text.charAt(text.length() - 1) != ')')
		{
			return false;
		}

		int depth = 0;
		for (int i = 0; i < text.length(); i++)
		{
			if (text.charAt(i) == '(')
			{
				depth++;
			}
			else if (text.charAt(i) == ')')
			{
				depth--;
				if (depth == 0 && i != text.length() - 1)
				{
					return false;
				}
			}
		}
		return depth == 0;
	}

	// Three-valued AND: false wins regardless of the other side (even if unresolvable);
	// otherwise null propagates unless both sides are known true.
	private static Boolean and(Boolean a, Boolean b)
	{
		if (Boolean.FALSE.equals(a) || Boolean.FALSE.equals(b))
		{
			return Boolean.FALSE;
		}

		return (a == null || b == null) ? null : Boolean.TRUE;
	}

	// Three-valued OR: true wins regardless of the other side; otherwise null propagates
	// unless both sides are known false.
	private static Boolean or(Boolean a, Boolean b)
	{
		if (Boolean.TRUE.equals(a) || Boolean.TRUE.equals(b))
		{
			return Boolean.TRUE;
		}

		return (a == null || b == null) ? null : Boolean.FALSE;
	}
}
