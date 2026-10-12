package com.groundmarkervariables.variables.meta;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// A {name=value} (or bare {name}) token that configures how a marker's tile renders, rather
// than substituting into the visible label text -- e.g. {tileCol=red}. Always stripped from
// the label before normal LabelVariable resolution runs (see MetaVariableResolver).
public interface MetaVariable
{
	Pattern pattern();

	// Applies this token's effect onto state, using matcher's own capture groups.
	void apply(Matcher matcher, MetaState state);
}
