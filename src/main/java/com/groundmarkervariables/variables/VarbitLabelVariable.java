package com.groundmarkervariables.variables;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;

// {varbitN} -> the current value of varbit N, e.g. {varbit4070} for the spellbook varbit.
// Mainly an escape hatch for varbits none of the other variables here cover yet.
class VarbitLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{varbit(\\d+)\\}", Pattern.CASE_INSENSITIVE);

	private final Client client;

	@Inject
	private VarbitLabelVariable(Client client)
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
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		int varbitId;
		try
		{
			// \d+ has no upper bound on digit count, so {varbit99999999999} can overflow int;
			// treat that the same as an unresolvable match rather than crashing.
			varbitId = Integer.parseInt(matcher.group(1));
		}
		catch (NumberFormatException e)
		{
			return null;
		}

		return String.valueOf(client.getVarbitValue(varbitId));
	}
}
