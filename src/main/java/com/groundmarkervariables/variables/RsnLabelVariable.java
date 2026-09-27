package com.groundmarkervariables.variables;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;

class RsnLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{rsn\\}", Pattern.CASE_INSENSITIVE);

	private final Client client;
	private final RichText richText;

	@Inject
	private RsnLabelVariable(Client client, RichText richText)
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
		return client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null;
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		String rsn = resolvePlain(matcher);
		return rsn == null ? null : richText.labeled("RSN", rsn);
	}
}
