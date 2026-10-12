package com.groundmarkervariables.variables.label;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.NumericVariableRegistry;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.ConditionEvaluator;
import com.groundmarkervariables.variables.support.DateLiteralParser;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import net.runelite.client.util.QuantityFormatter;

// {<a> <op> <b> [<op> <c> ...]} -- arithmetic over NumericVariableRegistry's own variables
// and/or literal numbers (e.g. "5", "1.5k"). Parens override the usual * / before + -
// precedence. Not usable with {metronomeN}. Also supports "<date literal> - date" (or
// reversed) as a day-count countdown, since {date}'s own Plain Text isn't a number.
public class MathVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{([^{}]+?(?:\\s[+\\-*/]\\s[^{}]+?)+)\\}");
	private static final Pattern OPERATOR = Pattern.compile("\\s([+\\-*/])\\s");
	private static final String DATE_VARIABLE = "date";

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
		try
		{
			Long days = evaluateDateCountdown(matcher.group(1));
			if (days != null)
			{
				return String.valueOf(days);
			}

			Double value = evaluate(matcher.group(1));
			return value == null ? null : formatPlain(value);
		}
		catch (RuntimeException e)
		{
			return "NaN";
		}
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		try
		{
			Long days = evaluateDateCountdown(matcher.group(1));

			// "day(s)" only applies to a bare date pair -- once anything's chained after it
			// (a second operator), fall through to the normal plain-number formatting below.
			Matcher opMatcher = OPERATOR.matcher(matcher.group(1));
			if (days != null && opMatcher.find() && !opMatcher.find())
			{
				return richText.highlightValue(days + (days == 1 || days == -1 ? " day" : " days"));
			}

			Double value = days != null ? (double) days : evaluate(matcher.group(1));
			return value == null ? null : richText.highlightValue(QuantityFormatter.quantityToStackSize(Math.round(value)));
		}
		catch (RuntimeException e)
		{
			return "NaN";
		}
	}

	// Only matches if the FIRST operator is "-" and pairs "date" with a parseable date literal
	// as the first two operands -- date math only applies at the very start of a chain (or,
	// via evaluateInner(), the very start of a parenthesized group). Whatever follows (e.g.
	// "+ 2") is handled by the normal evaluate().
	private Long evaluateDateCountdown(String expression)
	{
		Matcher opMatcher = OPERATOR.matcher(expression);
		if (!opMatcher.find() || !"-".equals(opMatcher.group(1)))
		{
			return null;
		}

		String first = expression.substring(0, opMatcher.start()).trim();
		int secondStart = opMatcher.end();

		String second;
		String remainder;
		if (opMatcher.find())
		{
			second = expression.substring(secondStart, opMatcher.start()).trim();
			remainder = expression.substring(opMatcher.start());
		}
		else
		{
			second = expression.substring(secondStart).trim();
			remainder = "";
		}

		LocalDate firstDate = dateValueOf(first);
		LocalDate secondDate = dateValueOf(second);
		if (firstDate == null || secondDate == null)
		{
			return null;
		}

		long dayCount = ChronoUnit.DAYS.between(secondDate, firstDate);
		if (remainder.isEmpty())
		{
			return dayCount;
		}

		Double result = evaluate(dayCount + remainder);
		return result == null ? null : Math.round(result);
	}

	// "date" means today; otherwise must parse as a date literal.
	private static LocalDate dateValueOf(String text)
	{
		return DATE_VARIABLE.equalsIgnoreCase(text) ? LocalDate.now() : DateLiteralParser.parse(text);
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

			Double inner = evaluateInner(reduced.substring(open + 1, close));
			if (inner == null)
			{
				return null;
			}

			reduced = reduced.substring(0, open) + formatPlain(inner) + reduced.substring(close + 1);
		}

		return evaluateFlat(reduced);
	}

	// A paren group's own content may itself start with a date pairing.
	private Double evaluateInner(String text)
	{
		Long days = evaluateDateCountdown(text);
		return days != null ? (double) days : evaluateFlat(text);
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
