package com.groundmarkervariables.variables;

import com.groundmarkervariables.GroundMarkerVariablesConfig;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

// {<expr> <cmp> <value>} — colors <expr>'s own Plain Text value true/false, no ?/: branches.
// ?/: excluded from both groups so this can't collide with real {cond ? A : B} syntax.
//
// {6HourTimeRemaining < <hours>} / {6HourTimeRemaining <= <hours>} is a special case: <hours>
// is a plain number of hours against the live Duration, not the generic comparison above
// (which would misread "1:45" as a clock time). Rich: false is the normal Rich Text, true
// swaps in Boolean False Color as a warning. Plain: false is "", true is the bare value. Any
// other comparator with this variable resolves to a call-to-action instead.
class BooleanVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile(
		"\\{\\s*([^{}?:]+?)\\s*(==|!=|<=|>=|<|>)\\s*([^{}?:]+?)\\s*\\}");
	private static final String UNSUPPORTED_COMPARATOR_TEXT = "Use {6HourTimeRemaining < 1.5} for a 1.5 hour warning";

	// <hours> is a bare number, optionally suffixed with "hour(s)"/"hr(s)" (e.g. "1.5 hours",
	// "1.5hrs"); anything else (e.g. a clock time) is unresolvable in sixHourWarning().
	private static final Pattern HOURS_PATTERN = Pattern.compile("(-?\\d+(?:\\.\\d+)?)\\s*(?:hours?|hrs?)?", Pattern.CASE_INSENSITIVE);

	private final ConditionEvaluator evaluator;
	private final RichText richText;
	private final SixHourTimeRemainingLabelVariable sixHourTimeRemaining;
	private final GroundMarkerVariablesConfig config;

	@Inject
	private BooleanVariable(ConditionEvaluator evaluator, RichText richText,
		SixHourTimeRemainingLabelVariable sixHourTimeRemaining, GroundMarkerVariablesConfig config)
	{
		this.evaluator = evaluator;
		this.richText = richText;
		this.sixHourTimeRemaining = sixHourTimeRemaining;
		this.config = config;
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		if (isSixHourTimeRemaining(matcher.group(1)))
		{
			return resolveSixHourWarning(matcher, false);
		}

		String value = evaluator.resolveExpression(matcher.group(1));
		if (value == null)
		{
			return null;
		}

		return evaluator.evaluate(value, matcher.group(2), matcher.group(3)) == null ? null : value;
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		if (isSixHourTimeRemaining(matcher.group(1)))
		{
			return resolveSixHourWarning(matcher, true);
		}

		String value = evaluator.resolveExpression(matcher.group(1));
		if (value == null)
		{
			return null;
		}

		Boolean result = evaluator.evaluate(value, matcher.group(2), matcher.group(3));
		return result == null ? null : richText.booleanColored(result, value);
	}

	private boolean isSixHourTimeRemaining(String expr)
	{
		return sixHourTimeRemaining.pattern().matcher("{" + expr.trim() + "}").matches();
	}

	private String resolveSixHourWarning(Matcher matcher, boolean rich)
	{
		String comparator = matcher.group(2);
		if (!"<".equals(comparator) && !"<=".equals(comparator))
		{
			return UNSUPPORTED_COMPARATOR_TEXT;
		}

		Boolean warning = sixHourWarning(matcher);
		if (warning == null)
		{
			return null;
		}

		if (!warning)
		{
			return rich ? sixHourTimeRemaining.resolveRich(matcher) : "";
		}

		return rich ? sixHourTimeRemaining.richTextWithColor(config.booleanFalseColor()) : sixHourTimeRemaining.formatPlain();
	}

	private Boolean sixHourWarning(Matcher matcher)
	{
		Matcher hoursMatcher = HOURS_PATTERN.matcher(matcher.group(3).trim());
		if (!hoursMatcher.matches())
		{
			return null;
		}

		double hoursRemaining = sixHourTimeRemaining.remaining().toMinutes() / 60.0;
		return evaluator.evaluate(String.valueOf(hoursRemaining), matcher.group(2), hoursMatcher.group(1));
	}
}
