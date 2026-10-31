package com.groundmarkervariables.variables.label;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.PartyMemberVariable;
import com.groundmarkervariables.variables.RichText;
import net.runelite.api.Client;
import net.runelite.client.party.PartyMember;

public class RsnLabelVariable implements LabelVariable, PartyMemberVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{rsn\\}", Pattern.CASE_INSENSITIVE);

	private final Client client;
	private final RichText richText;

	@Inject
	private RsnLabelVariable(Client client, RichText richText)
	{
		this.client = client;
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
		return client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null;
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		String rsn = resolvePlain(matcher);
		return rsn == null ? null : richText.labeled("RSN", rsn);
	}

	@Override
	public String resolvePlain(Matcher matcher, PartyMember member)
	{
		return member.getDisplayName();
	}

	@Override
	public String resolveRich(Matcher matcher, PartyMember member)
	{
		return richText.highlightValue(member.getDisplayName());
	}
}
