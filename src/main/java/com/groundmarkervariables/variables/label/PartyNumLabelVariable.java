package com.groundmarkervariables.variables.label;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.PartyMemberVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.PartyMemberContext;
import net.runelite.client.party.PartyMember;

// {partyNum} -> the {party} row's number, like its "num" keyword, but usable in conditions and math,
// e.g. {partyNum > 3 ? {hoverOnly}}. Nothing outside a {party} row.
public class PartyNumLabelVariable implements LabelVariable, PartyMemberVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{partyNum\\}", Pattern.CASE_INSENSITIVE);

	private final PartyMemberContext memberContext;

	@Inject
	private PartyNumLabelVariable(PartyMemberContext memberContext)
	{
		this.memberContext = memberContext;
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		int number = memberContext.rowNumber();
		return number > 0 ? String.valueOf(number) : null;
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		return resolvePlain(matcher);
	}

	@Override
	public String resolvePlain(Matcher matcher, PartyMember member)
	{
		return resolvePlain(matcher);
	}

	@Override
	public String resolveRich(Matcher matcher, PartyMember member)
	{
		return resolvePlain(matcher);
	}

	@Override
	public String resolveRowRich(Matcher matcher, RichText richText)
	{
		return resolvePlain(matcher);
	}
}
