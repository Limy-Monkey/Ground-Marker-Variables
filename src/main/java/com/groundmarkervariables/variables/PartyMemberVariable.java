package com.groundmarkervariables.variables;

import java.util.regex.Matcher;
import net.runelite.client.party.PartyMember;

// A LabelVariable that can also resolve for another party member, from RuneLite's PartyDataService
// data, in that member's {party} row. Variables without it are unavailable there. Null means the
// member hasn't sent that data yet.
public interface PartyMemberVariable
{
	String resolvePlain(Matcher matcher);

	String resolvePlain(Matcher matcher, PartyMember member);

	String resolveRich(Matcher matcher, PartyMember member);

	// Our own {party} row, styled like resolveRich(matcher, member).
	default String resolveRowRich(Matcher matcher, RichText richText)
	{
		String plain = resolvePlain(matcher);
		return plain == null ? null : richText.highlightValue(plain);
	}

	default String resolve(Matcher matcher, TextMode mode, PartyMember member)
	{
		return mode == TextMode.RICH ? resolveRich(matcher, member) : resolvePlain(matcher, member);
	}
}
