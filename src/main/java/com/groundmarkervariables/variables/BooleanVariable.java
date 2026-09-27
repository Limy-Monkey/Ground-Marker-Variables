package com.groundmarkervariables.variables;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

// {<expr> <cmp> <value>} — colors <expr>'s own Plain Text value true/false, no ?/: branches.
// ?/: excluded from both groups so this can't collide with real {cond ? A : B} syntax.
class BooleanVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile(
		"\\{\\s*([^{}?:]+?)\\s*(==|!=|<=|>=|<|>)\\s*([^{}?:]+?)\\s*\\}");

	private final ConditionEvaluator evaluator;
	private final RichText richText;

	@Inject
	private BooleanVariable(ConditionEvaluator evaluator, RichText richText)
	{
		this.evaluator = evaluator;
		this.richText = richText;
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
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
		String value = evaluator.resolveExpression(matcher.group(1));
		if (value == null)
		{
			return null;
		}

		Boolean result = evaluator.evaluate(value, matcher.group(2), matcher.group(3));
		return result == null ? null : richText.booleanColored(result, value);
	}
}
