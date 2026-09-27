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
	private final RichText richText;

	@Inject
	private QuestPointsLabelVariable(Client client, RichText richText)
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
		return String.valueOf(client.getVarpValue(VarPlayerID.QP));
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		return richText.labeled("Quest Points", resolvePlain(matcher));
	}
}
