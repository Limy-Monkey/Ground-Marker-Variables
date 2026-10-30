package com.groundmarkervariables.variables.label;

import com.groundmarkervariables.GroundMarkerVariablesConfig;
import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.ConditionEvaluator;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

// {<expr> <cmp> <value>} — colors <expr>'s own Plain Text value true/false, no ?/: branches.
// ? excluded so this can't collide with real {cond ? A : B} syntax (every real conditional has a
// literal '?' before any ':', so that alone is enough -- <value> still allows ':' for clock-time
// values like {time < 9:00}).
//
// {a || b || ...} shows the first true operand, {a && b && ...} the first false one, or else the
// last operand, which may be literal text (even empty, e.g. {hasItem obligator || }). Each operand
// is "<expr>" or "<expr> <cmp> <value>"; || and && can't be mixed in one token.
//
// {6HourTimeRemaining < <hours>} / {6HourTimeRemaining <= <hours>} is a special case: <hours>
// is a plain number of hours (or "h:mm") against the live Duration, not the generic comparison
// above (which would misread "1:45" as a clock time). Rich: false is the normal Rich Text, true
// swaps in Boolean False Color as a warning. Plain: false is "", true is the bare value. Any
// other comparator with this variable resolves to a call-to-action instead.
public class BooleanVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{(?=[^{}?]*(?:==|!=|<|>|\\|\\||&&))([^{}?]+)\\}");
	private static final Pattern COMPARISON = Pattern.compile("\\s*([^{}?:]+?)\\s*(==|!=|<=|>=|<|>)\\s*([^{}?]+?)\\s*");
	private static final String UNSUPPORTED_COMPARATOR_TEXT = "Use {6HourTimeRemaining < 1.5} for a 1.5 hour warning";

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
		return resolve(matcher.group(1), false);
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		return resolve(matcher.group(1), true);
	}

	private String resolve(String body, boolean rich)
	{
		boolean or = body.contains("||");
		if (or || body.contains("&&"))
		{
			return or && body.contains("&&") ? null : resolveLogic(body, or, rich);
		}
		return show(body, rich);
	}

	private String resolveLogic(String body, boolean or, boolean rich)
	{
		String[] operands = body.split(or ? "\\|\\|" : "&&", -1);
		for (int i = 0; i < operands.length; i++)
		{
			String operand = operands[i].trim();
			boolean last = i == operands.length - 1;
			Boolean result = truth(operand);
			if (result == null)
			{
				// The last operand may be literal text; anything else unresolvable leaves the token as-is.
				return last ? operand : null;
			}
			if (result == or || last)
			{
				return show(operand, rich);
			}
		}
		return null;
	}

	// true/false for "<expr>" or "<expr> <cmp> <value>", or null if it can't be evaluated.
	private Boolean truth(String operand)
	{
		Matcher comparison = COMPARISON.matcher(operand);
		if (comparison.matches())
		{
			return evaluator.evaluateCondition(comparison.group(1), comparison.group(2), comparison.group(3));
		}
		return operand.isEmpty() ? null : evaluator.evaluateCondition(operand, null, null);
	}

	// A comparison colored by its result, or a bare <expr> as its own variable renders it.
	private String show(String operand, boolean rich)
	{
		Matcher comparison = COMPARISON.matcher(operand);
		if (!comparison.matches())
		{
			return evaluator.resolveExpression(operand.trim(), rich);
		}

		if (evaluator.isSixHourTimeRemaining(comparison.group(1)))
		{
			return resolveSixHourWarning(comparison, rich);
		}

		String value = evaluator.resolveExpression(comparison.group(1));
		if (value == null)
		{
			return null;
		}

		Boolean result = evaluator.evaluate(value, comparison.group(2), comparison.group(3));
		if (result == null)
		{
			return null;
		}
		return rich ? richText.booleanColored(result, value) : value;
	}

	private String resolveSixHourWarning(Matcher comparison, boolean rich)
	{
		String comparator = comparison.group(2);
		if (!"<".equals(comparator) && !"<=".equals(comparator))
		{
			return UNSUPPORTED_COMPARATOR_TEXT;
		}

		Boolean warning = evaluator.evaluateCondition(comparison.group(1), comparator, comparison.group(3));
		if (warning == null)
		{
			return null;
		}

		if (!warning)
		{
			return rich ? sixHourTimeRemaining.resolveRich(comparison) : "";
		}

		return rich ? sixHourTimeRemaining.richTextWithColor(config.booleanFalseColor()) : sixHourTimeRemaining.formatPlain();
	}
}
