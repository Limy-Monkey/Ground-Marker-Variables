package com.groundmarkervariables.variables;

import com.groundmarkervariables.GroundMarkerVariablesConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

// Applies every registered LabelVariable to a marker's label. To add a new base variable
// (one usable directly and inside a {expr == value ? a : b} conditional): write a
// LabelVariable implementation and add it to VariableRegistry — nothing here needs to
// change. BooleanVariable and ConditionalVariable are listed separately since they depend on
// that same registry via ConditionEvaluator (see VariableRegistry's comment for why).
//
// MetronomeLabelVariable is listed separately too, deliberately LAST (after conditional)
// Per Plugin Hub review, metronomes inside of conditionals are too risky for abuse.
public class LabelResolver
{
	private final List<LabelVariable> baseVariables;
	private final BooleanVariable booleanVariable;
	private final ConditionalVariable conditional;
	private final MetronomeLabelVariable metronome;
	private final GroundMarkerVariablesConfig config;

	// Built lazily per variable, then reused -- this instance is shared by every CachedMarker.
	private final Map<LabelVariable, Pattern> prefixedPatterns = new HashMap<>();

	@Inject
	private LabelResolver(VariableRegistry registry, BooleanVariable booleanVariable, ConditionalVariable conditional,
		MetronomeLabelVariable metronome, GroundMarkerVariablesConfig config)
	{
		this.baseVariables = registry.all();
		this.booleanVariable = booleanVariable;
		this.conditional = conditional;
		this.metronome = metronome;
		this.config = config;
	}

	public String resolve(String label)
	{
		if (label == null)
		{
			return null;
		}

		String resolved = label;
		for (LabelVariable variable : baseVariables)
		{
			resolved = applyWithMode(variable, resolved);
		}

		// {expr cmp value} with no ?/: — disjoint from conditional's own ?/:-requiring pattern.
		resolved = applyWithMode(booleanVariable, resolved);

		// Conditional has no ^/&/* support -- it forces Plain for its own <expr> internally.
		resolved = applyPlain(conditional, resolved);

		// Metronome stays last, same as today — see the class comment on why.
		resolved = applyWithMode(metronome, resolved);

		return resolved;
	}

	// Matches this variable's token with an optional ^/&/* mode-override prefix right after
	// its opening brace, then re-matches the variable's own untouched pattern against the
	// prefix-stripped text so resolve() sees the exact match shape it always has.
	private String applyWithMode(LabelVariable variable, String text)
	{
		Pattern prefixed = prefixedPatterns.computeIfAbsent(variable, LabelResolver::withOptionalModePrefix);
		Matcher matcher = prefixed.matcher(text);
		StringBuilder replaced = new StringBuilder();
		while (matcher.find())
		{
			Character prefixChar = matcher.group(1) == null ? null : matcher.group(1).charAt(0);
			TextMode mode = TextMode.forPrefix(prefixChar, config.richTextByDefault());

			String bareToken = prefixChar == null ? matcher.group(0) : "{" + matcher.group(0).substring(2);
			Matcher bareMatcher = variable.pattern().matcher(bareToken);
			String value = bareMatcher.matches() ? variable.resolve(bareMatcher, mode) : null;
			if (value != null)
			{
				matcher.appendReplacement(replaced, Matcher.quoteReplacement(value));
			}
		}
		matcher.appendTail(replaced);
		return replaced.toString();
	}

	private String applyPlain(LabelVariable variable, String text)
	{
		Matcher matcher = variable.pattern().matcher(text);
		StringBuilder replaced = new StringBuilder();
		while (matcher.find())
		{
			String value = variable.resolvePlain(matcher);
			if (value != null)
			{
				matcher.appendReplacement(replaced, Matcher.quoteReplacement(value));
			}
		}
		matcher.appendTail(replaced);
		return replaced.toString();
	}

	// Every LabelVariable pattern starts with the literal "\{" -- inserting the optional
	// prefix group right after it leaves the rest of the pattern, and its group numbering,
	// untouched.
	private static Pattern withOptionalModePrefix(LabelVariable variable)
	{
		String source = variable.pattern().pattern();
		return Pattern.compile("\\{(?:([\\^&*]))?" + source.substring(2), variable.pattern().flags());
	}
}
