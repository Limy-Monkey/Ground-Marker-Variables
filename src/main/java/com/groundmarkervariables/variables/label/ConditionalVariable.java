package com.groundmarkervariables.variables.label;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.support.ConditionEvaluator;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

// {<cond1> [&& or || <cond2>] ? <whenTrue> : <whenFalse>} — each <condN> is
// "<expr> [<cmp> <value>]", evaluated via ConditionEvaluator. Nested variables in a branch
// aren't supported (regex can't parse nested braces), except a literal {metronomeN[_M]} /
// {mN[_M]} token (METRONOME_TOKEN), which resolveConditional() neutralizes in the winning
// branch rather than resolving, matching the rest of metronome's conditional block (see
// VariableRegistry).
public class ConditionalVariable implements LabelVariable
{
	private static final String METRONOME_TOKEN =
		"(?i:\\{(?:metronome|m)\\d+(?:_\\d+)?\\})";
	private static final Pattern METRONOME_TOKEN_PATTERN = Pattern.compile(METRONOME_TOKEN);
	private static final String BRANCH = "(?:[^{}]|" + METRONOME_TOKEN + ")+?";

	private static final String CONDITION = "([^{}]+?)\\s*(?:(==|!=|<=|>=|<|>)\\s*([^{}]+?)\\s*)?";
	private static final Pattern PATTERN = Pattern.compile(
		"\\{\\s*" + CONDITION + "(?:(&&|\\|\\|)\\s*" + CONDITION + ")?\\?\\s*(" + BRANCH + ")\\s*:\\s*(" + BRANCH + ")\\s*\\}");

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
		Boolean result = evaluator.evaluateCondition(matcher.group(1), matcher.group(2), matcher.group(3));

		String logicOp = matcher.group(4);
		if (logicOp != null)
		{
			Boolean second = evaluator.evaluateCondition(matcher.group(5), matcher.group(6), matcher.group(7));
			result = "&&".equals(logicOp) ? and(result, second) : or(result, second);
		}

		if (result == null)
		{
			return null;
		}

		String winner = result ? matcher.group(8) : matcher.group(9);
		return METRONOME_TOKEN_PATTERN.matcher(winner).replaceAll("ILLEGAL");
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
