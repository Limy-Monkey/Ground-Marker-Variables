package com.groundmarkervariables.variables;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarPlayerID;

// {questPoints} -> the player's current quest points.
class QuestPointsLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{questPoints\\}", Pattern.CASE_INSENSITIVE);

	private final Client client;

	@Inject
	private QuestPointsLabelVariable(Client client)
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
		return String.valueOf(client.getVarpValue(VarPlayerID.QP));
	}
}
