package com.groundmarkervariables.variables.label;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.NumericVariableRegistry;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.ConditionEvaluator;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import net.runelite.client.util.QuantityFormatter;

// {<a> <op> <b> [<op> <c> ...]} -- arithmetic over NumericVariableRegistry's own variables
// and/or literal numbers (e.g. "5", "1.5k"). Parens override the usual * / before + -
// precedence. Not usable with {metronomeN}.
//
// Must run before Kc/Pb/Loot/HasItem -- their own free-text capture allows +-*/ (real boss/item
// names can contain them unspaced, e.g. "TzTok-Jad"), so this has to claim a real math
// expression first. Mandatory whitespace around each operator is the disambiguator.
public class MathVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{([^{}]+?(?:\\s[+\\-*/]\\s[^{}]+?)+)\\}");
	private static final Pattern OPERATOR = Pattern.compile("\\s([+\\-*/])\\s");

	private final List<LabelVariable> numericVariables;
	private final RichText richText;

	@Inject
	private MathVariable(NumericVariableRegistry registry, RichText richText)
	{
		this.numericVariables = registry.all();
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
		Double value = evaluate(matcher.group(1));
		return value == null ? null : formatPlain(value);
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		Double value = evaluate(matcher.group(1));
		return value == null ? null : richText.highlightValue(QuantityFormatter.quantityToStackSize(Math.round(value)));
	}

	// Reduces the innermost "(...)" first, then moves outwards. Paren is always space padded.
	private Double evaluate(String expression)
	{
		String reduced = expression;
		while (reduced.contains("("))
		{
			int close = reduced.indexOf(')');
			if (close < 0)
			{
				return null;
			}

			int open = reduced.lastIndexOf('(', close);
			if (open < 0)
			{
				return null;
			}

			Double inner = evaluateFlat(reduced.substring(open + 1, close));
			if (inner == null)
			{
				return null;
			}

			reduced = reduced.substring(0, open) + formatPlain(inner) + reduced.substring(close + 1);
		}

		return evaluateFlat(reduced);
	}

	// Splits on whitespace-padded operators, resolves every operand, then applies standard
	// precedence (* and / before + and -) left to right within each pass.
	private Double evaluateFlat(String expression)
	{
		List<Double> operands = new ArrayList<>();
		List<Character> operators = new ArrayList<>();

		Matcher opMatcher = OPERATOR.matcher(expression);
		int last = 0;
		while (opMatcher.find())
		{
			Double operand = resolveOperand(expression.substring(last, opMatcher.start()));
			if (operand == null)
			{
				return null;
			}
			operands.add(operand);
			operators.add(opMatcher.group(1).charAt(0));
			last = opMatcher.end();
		}

		Double finalOperand = resolveOperand(expression.substring(last));
		if (finalOperand == null)
		{
			return null;
		}
		operands.add(finalOperand);

		for (int i = 0; i < operators.size(); )
		{
			char op = operators.get(i);
			if (op != '*' && op != '/')
			{
				i++;
				continue;
			}

			double left = operands.get(i);
			double right = operands.get(i + 1);
			if (op == '/' && right == 0)
			{
				return null;
			}

			operands.set(i, op == '*' ? left * right : left / right);
			operands.remove(i + 1);
			operators.remove(i);
		}

		double total = operands.get(0);
		for (int i = 0; i < operators.size(); i++)
		{
			total = operators.get(i) == '+' ? total + operands.get(i + 1) : total - operands.get(i + 1);
		}

		return total;
	}

	// A literal number (plain or "1.5k"-style), or another variable's own Plain Text value.
	private Double resolveOperand(String text)
	{
		text = text.trim();
		Double literal = ConditionEvaluator.tryParseNumber(text);
		if (literal != null)
		{
			return literal;
		}

		for (LabelVariable variable : numericVariables)
		{
			Matcher wrapped = variable.pattern().matcher("{" + text + "}");
			if (wrapped.matches())
			{
				String resolved = variable.resolvePlain(wrapped);
				return resolved == null ? null : ConditionEvaluator.tryParseNumber(resolved);
			}
		}

		return null;
	}

	private static String formatPlain(double value)
	{
		return value == Math.rint(value) && !Double.isInfinite(value)
			? String.valueOf((long) value)
			: String.valueOf(value);
	}
}
