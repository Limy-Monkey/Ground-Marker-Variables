package com.groundmarkervariables.variables.label;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.Icons;
import com.groundmarkervariables.variables.support.WeaponStyleTracker;
import net.runelite.api.Client;

// {autocast} -> the last autocast spell seen on a weapon that can autocast, or "None".
// {autocast icon} -> its spellbook icon, or nothing.
public class AutocastLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{autocast(?:\\s+(icon))?\\}", Pattern.CASE_INSENSITIVE);
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
		if (matcher.group(1) != null)
		{
			return icon();
		}

		String spell = resolvePlain(matcher);
		return spell == null ? null : richText.labeled("Autocast", spell);
	}

	private String icon()
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		int sprite = tracker.lastAutocastSprite();
		return sprite > 0 ? Icons.sprite(sprite) : "";
	}
}
