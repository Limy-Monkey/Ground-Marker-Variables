package com.groundmarkervariables.variables.support;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.PartyMemberVariable;
import com.groundmarkervariables.variables.RichText;
import java.util.regex.Matcher;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.party.PartyMember;

// The party member a {party} row is resolving for, or null for the local player.
@Singleton
public class PartyMemberContext
{
	private final Client client;
	private final RichText richText;

	@Nullable
	private PartyMember member;
	private boolean inRow;

	@Inject
	private PartyMemberContext(Client client, RichText richText)
	{
		this.client = client;
		this.richText = richText;
	}

	@Nullable
	public PartyMember member()
	{
		return member;
	}

	// member is null for our own row.
	public void enterRow(@Nullable PartyMember member)
	{
		this.member = member;
		this.inRow = true;
	}

	public void exitRow()
	{
		this.member = null;
		this.inRow = false;
	}

	public boolean isRemote()
	{
		return member != null;
	}

	// A value we don't have for this member: "null" in Plain Text, so conditions compare against it.
	public String unavailable(boolean rich)
	{
		return rich ? richText.animatedDots(client.getTickCount()) : "null";
	}

	// variable's value for the current member, or unavailable if it can't resolve for them.
	public String resolve(LabelVariable variable, Matcher matcher, boolean rich)
	{
		if (member == null)
		{
			if (rich && inRow && variable instanceof PartyMemberVariable)
			{
				return ((PartyMemberVariable) variable).resolveRowRich(matcher, richText);
			}
			return rich ? variable.resolveRich(matcher) : variable.resolvePlain(matcher);
		}

		String value = null;
		if (variable instanceof PartyMemberVariable)
		{
			PartyMemberVariable memberVariable = (PartyMemberVariable) variable;
			value = rich ? memberVariable.resolveRich(matcher, member) : memberVariable.resolvePlain(matcher, member);
		}
		return value != null ? value : unavailable(rich);
	}
}
