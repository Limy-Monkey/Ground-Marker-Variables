package com.groundmarkervariables.variables;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;

// {miscellania} -> the player's Kingdom of Miscellania approval/favour rating, 0-127.
class MiscellaniaLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{miscellania\\}", Pattern.CASE_INSENSITIVE);

	private final Client client;

	@Inject
	private MiscellaniaLabelVariable(Client client)
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
		return String.valueOf(client.getVarbitValue(VarbitID.MISC_APPROVAL));
	}
}
