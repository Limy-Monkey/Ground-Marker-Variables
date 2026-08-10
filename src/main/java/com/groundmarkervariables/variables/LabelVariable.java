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

	// Given a match against pattern(), the text to substitute in its place, or null if it
	// can't be resolved right now (e.g. not logged in yet) — the match is then left as-is.
	String resolve(Matcher matcher);
}
