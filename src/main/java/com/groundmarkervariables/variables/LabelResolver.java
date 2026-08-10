package com.groundmarkervariables.variables;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import javax.inject.Inject;

// Applies every registered LabelVariable to a marker's label. To add a new base variable
// (one usable directly and inside a {expr == value ? a : b} conditional): write a
// LabelVariable implementation and add it to VariableRegistry — nothing here needs to
// change. ConditionalVariable itself is listed separately since it depends on that same
// registry (see VariableRegistry's comment for why).
//
// MetronomeLabelVariable is listed separately too, deliberately LAST (after conditional)
// Per Plugin Hub review, metronomes inside of conditionals are too risky for abuse.
public class LabelResolver
{
	private final List<LabelVariable> variables;

	@Inject
	private LabelResolver(VariableRegistry registry, ConditionalVariable conditional, MetronomeLabelVariable metronome)
	{
		List<LabelVariable> all = new ArrayList<>(registry.all());
		all.add(conditional);
		all.add(metronome);
		this.variables = List.copyOf(all);
	}

	public String resolve(String label)
	{
		if (label == null)
		{
			return null;
		}

		String resolved = label;
		for (LabelVariable variable : variables)
		{
			Matcher matcher = variable.pattern().matcher(resolved);
			StringBuilder replaced = new StringBuilder();
			while (matcher.find())
			{
				String value = variable.resolve(matcher);
				if (value != null)
				{
					matcher.appendReplacement(replaced, Matcher.quoteReplacement(value));
				}
			}
			matcher.appendTail(replaced);
			resolved = replaced.toString();
		}

		return resolved;
	}
}
