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
	private static final int MAX_APPROVAL = 127;

	private final Client client;
	private final RichText richText;

	@Inject
	private MiscellaniaLabelVariable(Client client, RichText richText)
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
		return String.valueOf(client.getVarbitValue(VarbitID.MISC_APPROVAL));
	}

	// Only highlighted at the 127 cap, and only the number, not the /127.
	@Override
	public String resolveRich(Matcher matcher)
	{
		int approval = client.getVarbitValue(VarbitID.MISC_APPROVAL);
		String value = String.valueOf(approval);
		return (approval == MAX_APPROVAL ? richText.highlightValue(value) : value) + " / " + MAX_APPROVAL;
	}
}
