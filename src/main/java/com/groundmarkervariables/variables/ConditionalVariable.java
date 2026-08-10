package com.groundmarkervariables.variables;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

// {<cond1> [&& or || <cond2>] ? <whenTrue> : <whenFalse>} — each <condN> is
// "<expr> [<cmp> <value>]": <cmp>/<value> are optional, so a bare <expr> (e.g. hasThralls)
// evaluates its own resolved value directly as a "true"/"false" boolean. <cmp> is one of
// == != < > <= >=. <expr> is any other variable's own body (no surrounding braces), e.g.
// "spellbook" or "metronome5_2". Evaluated by re-wrapping <expr> in braces and testing it
// against every VariableRegistry variable's own pattern(), so this works for any current or
// future variable without changes here. == and != are always a case-insensitive string
// (in)equality check; the ordering comparators require both sides to parse as numbers
// (metronome's countdown is the only numeric variable today, but nothing here is
// metronome-specific) and are otherwise unresolvable, same as an unresolvable <expr>.
//
// && / || combine exactly two conditions (not an arbitrary chain — see PATTERN) using
// three-valued logic: an unresolvable side still settles the result if the other side
// already does (false && anything = false, true || anything = true); otherwise the whole
// conditional is left untouched rather than silently picking a branch.
//
// [^{}]+? (not .+?) in every group deliberately stops this from matching at all if an
// <expr>, <value>, or a branch contains its own '{'/'}' — nested variables inside a
// conditional aren't supported (regex can't parse nested braces), so this fails safe by
// leaving the whole expression untouched rather than mis-parsing it. The same non-greedy
// stopping means a third chained &&/|| (not just two conditions) won't parse as intended
// either — the second <expr> just swallows the rest as an unresolvable blob.
class ConditionalVariable implements LabelVariable
{
	private static final String CONDITION = "([^{}]+?)\\s*(?:(==|!=|<=|>=|<|>)\\s*([^{}]+?)\\s*)?";
	private static final Pattern PATTERN = Pattern.compile(
		"\\{\\s*" + CONDITION + "(?:(&&|\\|\\|)\\s*" + CONDITION + ")?\\?\\s*([^{}]+?)\\s*:\\s*([^{}]+?)\\s*\\}");

	private final List<LabelVariable> variables;

	@Inject
	private ConditionalVariable(VariableRegistry registry)
	{
		this.variables = registry.all();
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolve(Matcher matcher)
	{
		Boolean result = evaluateCondition(matcher.group(1), matcher.group(2), matcher.group(3));

		String logicOp = matcher.group(4);
		if (logicOp != null)
		{
			Boolean second = evaluateCondition(matcher.group(5), matcher.group(6), matcher.group(7));
			result = "&&".equals(logicOp) ? and(result, second) : or(result, second);
		}

		String whenTrue = matcher.group(8);
		String whenFalse = matcher.group(9);
		return result == null ? null : (result ? whenTrue : whenFalse);
	}

	private Boolean evaluateCondition(String expr, String comparator, String expected)
	{
		String actual = resolveExpression(expr);
		return actual == null ? null : evaluate(actual, comparator, expected);
	}

	private String resolveExpression(String expr)
	{
		for (LabelVariable variable : variables)
		{
			Matcher wrapped = variable.pattern().matcher("{" + expr + "}");
			if (wrapped.matches())
			{
				return variable.resolve(wrapped);
			}
		}

		return null;
	}

	// Null means "can't be evaluated" (e.g. an ordering comparator against a non-numeric
	// value like a player name), distinct from false, so resolve() can leave the whole
	// conditional untouched rather than silently picking a branch for an unanswerable check.
	private static Boolean evaluate(String actual, String comparator, String expected)
	{
		if (comparator == null)
		{
			return tryParseBoolean(actual);
		}

		if ("==".equals(comparator))
		{
			return actual.equalsIgnoreCase(expected);
		}

		if ("!=".equals(comparator))
		{
			return !actual.equalsIgnoreCase(expected);
		}

		Double actualNum = tryParseNumber(actual);
		Double expectedNum = tryParseNumber(expected);
		if (actualNum == null || expectedNum == null)
		{
			return null;
		}

		switch (comparator)
		{
			case "<":
				return actualNum < expectedNum;
			case ">":
				return actualNum > expectedNum;
			case "<=":
				return actualNum <= expectedNum;
			case ">=":
				return actualNum >= expectedNum;
			default:
				return null;
		}
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

	private static Double tryParseNumber(String value)
	{
		try
		{
			return Double.parseDouble(value);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	// Strict on purpose: a non-boolean variable used bare (e.g. {spellbook ? A : B}) should
	// be unresolvable, not silently treated as false.
	private static Boolean tryParseBoolean(String value)
	{
		if ("true".equalsIgnoreCase(value))
		{
			return Boolean.TRUE;
		}

		if ("false".equalsIgnoreCase(value))
		{
			return Boolean.FALSE;
		}

		return null;
	}
}
