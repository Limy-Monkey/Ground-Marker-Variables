package com.groundmarkervariables.variables.label;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.PartyMemberVariable;
import com.groundmarkervariables.variables.RichText;
import net.runelite.api.Client;
import net.runelite.client.party.PartyMember;

// {runEnergy} -> current run energy, 0-100. Client.getEnergy() is in units of 1/100th percent.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class RunEnergyLabelVariable implements LabelVariable, PartyMemberVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{runEnergy\\}", Pattern.CASE_INSENSITIVE);

	private final Client client;
	private final RichText richText;

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		return String.valueOf(client.getEnergy() / 100);
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		return "Run: " + richText.highlightValue(resolvePlain(matcher)) + " / 100";
	}

	// PartyDataService sends run energy as 0-100, rounded up to tens.
	@Override
	public String resolvePlain(Matcher matcher, PartyMember member)
	{
		return String.valueOf(member.getRunEnergy());
	}

	@Override
	public String resolveRich(Matcher matcher, PartyMember member)
	{
		return richText.highlightValue(resolvePlain(matcher, member));
	}
}
