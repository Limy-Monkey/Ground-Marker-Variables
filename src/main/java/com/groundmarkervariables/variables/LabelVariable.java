package com.groundmarkervariables.variables;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// A {token} substitution ground marker labels can use, e.g. {rsn} or {metronome5}. A regex
// rather than a literal string because some variables carry their own parameters (the "5"
// in {metronome5}) that resolve() needs to read back out of the match via capture groups.
public interface LabelVariable
{
	// Matches every occurrence of this variable in a label, e.g. Pattern.compile("\\{rsn\\}").
	Pattern pattern();

	// Today's concise output, or null if unresolvable (e.g. not logged in yet) -- the match is
	// then left as-is.
	String resolvePlain(Matcher matcher);

	// The more descriptive Rich Text output -- see RichText.
	String resolveRich(Matcher matcher);

	// LabelResolver's dispatch point when it doesn't already know which one it wants; call
	// resolvePlain/resolveRich directly when it does (see ConditionalVariable, always Plain).
	default String resolve(Matcher matcher, TextMode mode)
	{
		return mode == TextMode.RICH ? resolveRich(matcher) : resolvePlain(matcher);
	}
}
