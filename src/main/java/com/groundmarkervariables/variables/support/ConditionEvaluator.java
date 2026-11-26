package com.groundmarkervariables.variables.support;

import java.text.ParseException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.label.MathVariable;
import com.groundmarkervariables.variables.label.SixHourTimeRemainingLabelVariable;
import com.groundmarkervariables.variables.VariableRegistry;
import net.runelite.client.util.QuantityFormatter;

// Shared <expr> [<cmp> <value>] evaluation for ConditionalVariable and BooleanVariable. <expr>
// is resolved by re-wrapping it in braces and testing it against every VariableRegistry
// variable's own pattern(), so this works for any variable without changes here. == and != are
// always case-insensitive string equality; ordering comparators need both sides to parse as
// numbers, or failing that as a clock time (see tryParseTime).
public class ConditionEvaluator {
	// "9pm"/"9:00pm" (am/pm required) or "21:00"/"9:00" (24-hour, no marker) — tryParseTime
	// strips spaces/periods first so "3:45 pm" (as {time} itself renders) also matches.
	private static final DateTimeFormatter TIME_WITH_AMPM =
		new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("h[:mm]a").toFormatter(Locale.ENGLISH);
	private static final DateTimeFormatter TIME_24_HOUR =
		new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("H[:mm]").toFormatter(Locale.ENGLISH);

	// See parseHoursThreshold().
	// A variable's Plain Text when it has no value (see PartyMemberContext); false, and never ordered.
	public static final String NULL = "null";
	private static final Pattern HOURS_PATTERN = Pattern.compile("(-?\\d+(?:\\.\\d+)?)\\s*(?:hours?|hrs?)?", Pattern.CASE_INSENSITIVE);
	private static final Pattern HOURS_MINUTES_PATTERN = Pattern.compile("(-?\\d+):(\\d{1,2})");

	private final List<LabelVariable> variables;
	private final SixHourTimeRemainingLabelVariable sixHourTimeRemaining;
	private final MathVariable mathVariable;
	private final PartyMemberContext memberContext;

	@Inject
	private ConditionEvaluator(VariableRegistry registry, SixHourTimeRemainingLabelVariable sixHourTimeRemaining, MathVariable mathVariable,
							   PartyMemberContext memberContext) {
		this.memberContext = memberContext;
		this.variables = registry.all();
		this.sixHourTimeRemaining = sixHourTimeRemaining;
		this.mathVariable = mathVariable;
	}

	// <expr>'s own Plain Text value, or null if <expr> doesn't match any base variable.
	// MathVariable isn't itself in VariableRegistry (it depends on NumericVariableRegistry
	// instead, to avoid a circular dependency back through here), so it's tried directly.
	public String resolveExpression(String expr) {
		return resolveExpression(expr, false);
	}

	// Like resolveExpression, in Rich Text when rich.
	public String resolveExpression(String expr, boolean rich) {
		Matcher mathMatcher = mathVariable.pattern().matcher("{" + expr + "}");
		if (mathMatcher.matches()) {
			return rich ? mathVariable.resolveRich(mathMatcher) : mathVariable.resolvePlain(mathMatcher);
		}

		for (LabelVariable variable : variables) {
			Matcher wrapped = variable.pattern().matcher("{" + expr + "}");
			if (wrapped.matches()) {
				return memberContext.resolve(variable, wrapped, rich);
			}
		}

		// A literal number, e.g. a resolved {partyCount}.
		return tryParseNumber(expr.trim()) != null ? expr.trim() : null;
	}

	public Boolean evaluateCondition(String expr, String comparator, String expected) {
		if (comparator != null && isSixHourTimeRemaining(expr)) {
			return evaluateSixHourWarning(comparator, expected);
		}

		// Bare, a literal (e.g. a resolved {flag ? ...}'s true) still only counts as a boolean or number.
		String actual = resolveOperand(expr);
		return actual == null ? null : evaluate(actual, comparator, expected);
	}

	// The left side of a comparison: a variable, else the literal text, e.g. {Ancient == spellbook}.
	@Nullable
	public String resolveOperand(String expr) {
		String resolved = resolveExpression(expr);
		if (resolved != null) {
			return resolved;
		}
		String literal = expr.trim();
		return literal.isEmpty() ? null : literal;
	}

	// Shared with BooleanVariable, which also needs to know this for its own </<= restriction
	// and duration-specific Rich/Plain text.
	public boolean isSixHourTimeRemaining(String expr) {
		return sixHourTimeRemaining.pattern().matcher("{" + expr.trim() + "}").matches();
	}

	// {6HourTimeRemaining}'s own Plain Text is an "h:mm" duration string -- comparing that
	// against <hours> the normal way would misparse both as clock times.
	private Boolean evaluateSixHourWarning(String comparator, String expected) {
		Double threshold = parseHoursThreshold(expected.trim());
		if (threshold == null) {
			return null;
		}

		double hoursRemaining = sixHourTimeRemaining.remaining().toMinutes() / 60.0;
		return evaluate(String.valueOf(hoursRemaining), comparator, String.valueOf(threshold));
	}

	// <hours> is either a bare number, optionally suffixed with "hour(s)"/"hr(s)" (e.g. "1.5
	// hours", "1.5hrs"), or "h:mm" matching {6HourTimeRemaining}'s own Plain Text (e.g. "5:45").
	private static Double parseHoursThreshold(String value) {
		Matcher hoursMatcher = HOURS_PATTERN.matcher(value);
		if (hoursMatcher.matches()) {
			return Double.parseDouble(hoursMatcher.group(1));
		}

		Matcher hoursMinutesMatcher = HOURS_MINUTES_PATTERN.matcher(value);
		if (hoursMinutesMatcher.matches()) {
			return Integer.parseInt(hoursMinutesMatcher.group(1)) + Integer.parseInt(hoursMinutesMatcher.group(2)) / 60.0;
		}

		return null;
	}

	// Null means "can't be evaluated" (e.g. an ordering comparator against a non-numeric
	// value like a player name), distinct from false, so callers can leave things untouched
	// rather than silently picking a result for an unanswerable check.
	public Boolean evaluate(String actual, String comparator, String expected) {
		if (comparator == null) {
			return tryParseBoolean(actual);
		}

		// == [a, b, c]: equal to any item, != [a, b, c]: to none.
		List<String> items = listItems(expected);
		if (items != null && ("==".equals(comparator) || "!=".equals(comparator))) {
			return "==".equals(comparator) == items.stream().anyMatch(actual::equalsIgnoreCase);
		}

		String resolvedExpected = resolveExpression(expected);
		if (resolvedExpected != null) {
			expected = resolvedExpected;
		}

		if ("==".equals(comparator)) {
			return actual.equalsIgnoreCase(expected);
		}

		if ("!=".equals(comparator)) {
			return !actual.equalsIgnoreCase(expected);
		}

		if (NULL.equalsIgnoreCase(actual) || NULL.equalsIgnoreCase(expected)) {
			return Boolean.FALSE;
		}

		Double actualNum = tryParseNumber(actual);
		Double expectedNum = tryParseNumber(expected);
		if (actualNum != null && expectedNum != null) {
			return compareOrder(comparator, actualNum, expectedNum);
		}

		LocalTime actualTime = tryParseTime(actual);
		LocalTime expectedTime = tryParseTime(expected);
		if (actualTime != null && expectedTime != null) {
			return compareOrder(comparator, actualTime.toSecondOfDay(), expectedTime.toSecondOfDay());
		}

		return null;
	}

	// The trimmed items of a "[a, b, c]" value, or null if it isn't one.
	@Nullable
	private static List<String> listItems(String value) {
		String trimmed = value.trim();
		if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
			return null;
		}

		List<String> items = new ArrayList<>();
		for (String item : trimmed.substring(1, trimmed.length() - 1).split(",")) {
			items.add(item.trim());
		}
		return items;
	}

	private static Boolean compareOrder(String comparator, double actual, double expected) {
		switch (comparator) {
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

	public static LocalTime tryParseTime(String value) {
		String normalized = value.trim().toLowerCase(Locale.ENGLISH).replace(".", "").replaceAll("\\s+", "");
		if (normalized.isEmpty()) {
			return null;
		}

		try {
			return LocalTime.parse(normalized, TIME_WITH_AMPM);
		} catch (DateTimeParseException e) {
			// Not "9pm"-shaped — fall through and try it as a bare 24-hour time instead.
		}

		try {
			return LocalTime.parse(normalized, TIME_24_HOUR);
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	// Falls back to RuneLite's own K/M/B stack-size suffix, e.g. "10.5k" -> 10500, so
	// {loot monster > 10.5k} works the same way {loot}'s own Rich Text displays gp.
	public static Double tryParseNumber(String value) {
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			// Not a bare number — fall through and try it as a suffixed stack size instead.
		}

		try {
			return (double) QuantityFormatter.parseQuantity(value);
		} catch (ParseException e) {
			return null;
		}
	}

	// Strict on purpose: a non-boolean variable used bare (e.g. {spellbook ? A : B}) should
	// be unresolvable, not silently treated as false. A number (even 0) is true; "null" is false.
	private static Boolean tryParseBoolean(String value) {
		if ("true".equalsIgnoreCase(value)) {
			return Boolean.TRUE;
		}

		if ("false".equalsIgnoreCase(value) || NULL.equalsIgnoreCase(value)) {
			return Boolean.FALSE;
		}

		return tryParseNumber(value) != null ? Boolean.TRUE : null;
	}
}
