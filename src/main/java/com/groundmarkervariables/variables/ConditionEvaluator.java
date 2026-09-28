package com.groundmarkervariables.variables;

import java.text.ParseException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import javax.inject.Inject;
import net.runelite.client.util.QuantityFormatter;

// Shared <expr> [<cmp> <value>] evaluation for ConditionalVariable and BooleanVariable. <expr>
// is resolved by re-wrapping it in braces and testing it against every VariableRegistry
// variable's own pattern(), so this works for any variable without changes here. == and != are
// always case-insensitive string equality; ordering comparators need both sides to parse as
// numbers, or failing that as a clock time (see tryParseTime).
class ConditionEvaluator
{
	// "9pm"/"9:00pm" (am/pm required) or "21:00"/"9:00" (24-hour, no marker) — tryParseTime
	// strips spaces/periods first so "3:45 pm" (as {time} itself renders) also matches.
	private static final DateTimeFormatter TIME_WITH_AMPM =
		new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("h[:mm]a").toFormatter(Locale.ENGLISH);
	private static final DateTimeFormatter TIME_24_HOUR =
		new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("H[:mm]").toFormatter(Locale.ENGLISH);

	private final List<LabelVariable> variables;

	@Inject
	private ConditionEvaluator(VariableRegistry registry)
	{
		this.variables = registry.all();
	}

	// <expr>'s own Plain Text value, or null if <expr> doesn't match any base variable.
	String resolveExpression(String expr)
	{
		for (LabelVariable variable : variables)
		{
			Matcher wrapped = variable.pattern().matcher("{" + expr + "}");
			if (wrapped.matches())
			{
				return variable.resolvePlain(wrapped); // always Plain — see callers' comments
			}
		}

		return null;
	}

	Boolean evaluateCondition(String expr, String comparator, String expected)
	{
		String actual = resolveExpression(expr);
		return actual == null ? null : evaluate(actual, comparator, expected);
	}

	// Null means "can't be evaluated" (e.g. an ordering comparator against a non-numeric
	// value like a player name), distinct from false, so callers can leave things untouched
	// rather than silently picking a result for an unanswerable check.
	Boolean evaluate(String actual, String comparator, String expected)
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
		if (actualNum != null && expectedNum != null)
		{
			return compareOrder(comparator, actualNum, expectedNum);
		}

		LocalTime actualTime = tryParseTime(actual);
		LocalTime expectedTime = tryParseTime(expected);
		if (actualTime != null && expectedTime != null)
		{
			return compareOrder(comparator, actualTime.toSecondOfDay(), expectedTime.toSecondOfDay());
		}

		return null;
	}

	private static Boolean compareOrder(String comparator, double actual, double expected)
	{
		switch (comparator)
		{
			case "<":
				return actual < expected;
			case ">":
				return actual > expected;
			case "<=":
				return actual <= expected;
			case ">=":
				return actual >= expected;
			default:
				return null;
		}
	}

	private static LocalTime tryParseTime(String value)
	{
		String normalized = value.trim().toLowerCase(Locale.ENGLISH).replace(".", "").replaceAll("\\s+", "");
		if (normalized.isEmpty())
		{
			return null;
		}

		try
		{
			return LocalTime.parse(normalized, TIME_WITH_AMPM);
		}
		catch (DateTimeParseException e)
		{
			// Not "9pm"-shaped — fall through and try it as a bare 24-hour time instead.
		}

		try
		{
			return LocalTime.parse(normalized, TIME_24_HOUR);
		}
		catch (DateTimeParseException e)
		{
			return null;
		}
	}

	// Falls back to RuneLite's own K/M/B stack-size suffix, e.g. "10.5k" -> 10500, so
	// {loot monster > 10.5k} works the same way {loot}'s own Rich Text displays gp.
	private static Double tryParseNumber(String value)
	{
		try
		{
			return Double.parseDouble(value);
		}
		catch (NumberFormatException e)
		{
			// Not a bare number — fall through and try it as a suffixed stack size instead.
		}

		try
		{
			return (double) QuantityFormatter.parseQuantity(value);
		}
		catch (ParseException e)
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
