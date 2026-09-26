package com.groundmarkervariables.variables;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;

// {autoRetaliate} (or its {autoRetal} alias) -> true if Auto Retaliate is on. Varp 172, 0 =
// on, nonzero = off — RuneLite hasn't named this one in VarPlayerID; confirmed against the
// Auto Retaliate Warning plugin (https://github.com/ste-h/auto-retaliate-warning), which
// reads it the same way.
class AutoRetaliateLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{autoRetal(?:iate)?\\}", Pattern.CASE_INSENSITIVE);
	private static final int AUTO_RETALIATE_VARP = 172;

	private final Client client;

	@Inject
	private AutoRetaliateLabelVariable(Client client)
	{
		this.client = client;
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolve(Matcher matcher)
	{
		return String.valueOf(client.getVarpValue(AUTO_RETALIATE_VARP) == 0);
	}
}
