package com.groundmarkervariables.variables;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;

class RsnLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{rsn\\}", Pattern.CASE_INSENSITIVE);

	private final Client client;

	@Inject
	private RsnLabelVariable(Client client)
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
		return client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null;
	}
}
