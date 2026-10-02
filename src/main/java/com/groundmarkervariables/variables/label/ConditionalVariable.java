package com.groundmarkervariables.variables.label;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.support.ConditionEvaluator;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

// {<cond1> [&& or || <cond2>] ? <whenTrue> : <whenFalse>} — each <condN> is
// "<expr> [<cmp> <value>]", evaluated via ConditionEvaluator (shared with BooleanVariable).
// && / || combine exactly two conditions (not an arbitrary chain — see PATTERN) using
// three-valued logic: an unresolvable side still settles the result if the other side already
// does (false && anything = false, true || anything = true); otherwise the whole conditional
// is left untouched rather than silently picking a branch.
//
// [^{}]+? (not .+?) in every group deliberately stops this from matching at all if an
// <expr>, <value>, or a branch contains its own '{'/'}' — nested variables inside a
// conditional aren't supported (regex can't parse nested braces), so this fails safe by
// leaving the whole expression untouched rather than mis-parsing it. The same non-greedy
// stopping means a third chained &&/|| (not just two conditions) won't parse as intended
// either — the second <expr> just swallows the rest as an unresolvable blob.
//
// The two branch groups are the one deliberate exception: they also allow a literal
// {metronomeN} / {metronomeN_M} token (optionally with a "+/- offset" suffix — see
// MetronomeLabelVariable), or its {mN} / {mN_M} alias, specifically (METRONOME_TOKEN), so a
// branch containing one doesn't just make the whole conditional fail to match. resolveConditional()
// neutralizes any such token in the winning branch (see METRONOME_TOKEN_PATTERN), matching
// every other way metronome is blocked from conditional use (see VariableRegistry).
public class ConditionalVariable implements LabelVariable
{
	private static final String METRONOME_TOKEN =
		"(?i:\\{(?:metronome|m)\\d+(?:_\\d+)?\\s*(?:[+-]\\s*\\d+)?\\})";
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
