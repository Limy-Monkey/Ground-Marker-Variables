package com.groundmarkervariables.variables;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarPlayerID;

// {spec} -> current special attack energy, 0-100. VarPlayerID.SA_ENERGY maxes at 1000.
class SpecLabelVariable implements LabelVariable
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
}
