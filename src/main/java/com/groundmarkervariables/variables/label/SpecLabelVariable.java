package com.groundmarkervariables.variables.label;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.PartyMemberVariable;
import com.groundmarkervariables.variables.RichText;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.party.PartyMember;

// {spec} -> current special attack energy, 0-100. VarPlayerID.SA_ENERGY maxes at 1000.
public class SpecLabelVariable implements LabelVariable, PartyMemberVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{spec\\}", Pattern.CASE_INSENSITIVE);

	private final Client client;
	private final RichText richText;

	@Inject
	private SpecLabelVariable(Client client, RichText richText)
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
		return String.valueOf(client.getVarpValue(VarPlayerID.SA_ENERGY) / 10);
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		return "Spec: " + richText.highlightValue(resolvePlain(matcher) + "%");
	}

	// PartyDataService already sends spec as 0-100.
	@Override
	public String resolvePlain(Matcher matcher, PartyMember member)
	{
		return String.valueOf(member.getSpecEnergy());
	}

	@Override
	public String resolveRich(Matcher matcher, PartyMember member)
	{
		return richText.highlightValue(member.getSpecEnergy() + "%");
	}

	@Override
	public String resolveRowRich(Matcher matcher, RichText richText)
	{
		return richText.highlightValue(resolvePlain(matcher) + "%");
	}
}
