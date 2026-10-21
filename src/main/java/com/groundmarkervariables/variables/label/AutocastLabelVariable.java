package com.groundmarkervariables.variables.label;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.WeaponStyleTracker;
import net.runelite.api.Client;

// {autocast} -> the last autocast spell seen on a weapon that can autocast, or "None".
public class AutocastLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{autocast\\}", Pattern.CASE_INSENSITIVE);
	private static final String NONE = "None";

	private final Client client;
	private final RichText richText;
	private final WeaponStyleTracker tracker;

	@Inject
	private AutocastLabelVariable(Client client, RichText richText, WeaponStyleTracker tracker)
	{
		this.client = client;
		this.richText = richText;
		this.tracker = tracker;
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		String spell = tracker.lastAutocast();
		return spell == null ? NONE : spell;
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		String spell = resolvePlain(matcher);
		return spell == null ? null : richText.labeled("Autocast", spell);
	}
}
