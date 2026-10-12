package com.groundmarkervariables.variables.meta;

import java.util.List;
import java.util.regex.Matcher;
import javax.inject.Inject;

// Strips every meta-variable token out of a label and collects their effect into a MetaState.
// Run on the already LabelResolver-resolved label, not the raw one, so a meta token written
// inside a conditional branch survives to be stripped/applied here.
public class MetaVariableResolver
{
	private final List<MetaVariable> metaVariables;

	@Inject
	private MetaVariableResolver(MetaVariableRegistry registry)
	{
		this.metaVariables = registry.all();
	}

	public static final class Result
	{
		public final String label;
		public final MetaState state;

		private Result(String label, MetaState state)
		{
			this.label = label;
			this.state = state;
		}
	}

	public Result resolve(String label)
	{
		MetaState state = new MetaState();
		if (label == null)
		{
			return new Result(null, state);
		}

		String remaining = label;
		for (MetaVariable meta : metaVariables)
		{
			remaining = strip(meta, remaining, state);
		}
		return new Result(remaining, state);
	}

	private static String strip(MetaVariable meta, String text, MetaState state)
	{
		Matcher matcher = meta.pattern().matcher(text);
		if (!matcher.find())
		{
			return text;
		}

		matcher.reset();
		StringBuilder result = new StringBuilder();
		while (matcher.find())
		{
			try
			{
				meta.apply(matcher, state);
			}
			catch (RuntimeException e)
			{
				// Malformed value -- drop the token without changing state.
			}
			matcher.appendReplacement(result, "");
		}
		matcher.appendTail(result);
		return result.toString();
	}
}
