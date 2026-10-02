package com.groundmarkervariables.variables.label;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import net.runelite.api.Client;

// {runEnergy} -> current run energy, 0-100. Client.getEnergy() is in units of 1/100th percent.
public class RunEnergyLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{runEnergy\\}", Pattern.CASE_INSENSITIVE);

	private final Client client;
	private final RichText richText;

	@Inject
	private RunEnergyLabelVariable(Client client, RichText richText)
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
		return String.valueOf(client.getEnergy() / 100);
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		return "Run: " + richText.highlightValue(resolvePlain(matcher)) + " / 100";
	}
}
