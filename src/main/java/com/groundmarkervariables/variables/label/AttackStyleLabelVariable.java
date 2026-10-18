package com.groundmarkervariables.variables.label;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.WeaponAttackType;
import com.groundmarkervariables.variables.support.WeaponStyleTracker;
import net.runelite.api.Client;

// {attackStyle} -> current combat style name, or "None". {attackStyle, weapon=<name>} -> the
// last style seen on that weapon this session.
public class AttackStyleLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile(
		"\\{attackStyle(?:\\s*,?\\s*weapon\\s*=\\s*([^{}]+?))?\\s*\\}", Pattern.CASE_INSENSITIVE);

	private final Client client;
	private final RichText richText;
	private final WeaponStyleTracker tracker;

	@Inject
	private AttackStyleLabelVariable(Client client, RichText richText, WeaponStyleTracker tracker)
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

		WeaponStyleTracker.Style style = style(tracker, matcher);
		return style == null ? notSeenText(matcher) : style.name;
	}

	// Appends the damage type, e.g. "Aggressive (Slash)".
	@Override
	public String resolveRich(Matcher matcher)
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		WeaponStyleTracker.Style style = style(tracker, matcher);
		if (style == null)
		{
			return notSeenText(matcher);
		}

		String value = (style.type == null || style.type == WeaponAttackType.NONE) ? style.name : style.name + " (" + style.type.displayName() + ")";
		return richText.labeled(label(tracker, matcher, "Style", "Attack Style"), value);
	}

	static String label(WeaponStyleTracker tracker, Matcher matcher, String suffix, String defaultLabel)
	{
		return matcher.group(1) == null ? defaultLabel : tracker.weaponName(matcher.group(1)) + " " + suffix;
	}

	static WeaponStyleTracker.Style style(WeaponStyleTracker tracker, Matcher matcher)
	{
		return matcher.group(1) == null ? tracker.current() : tracker.forWeapon(matcher.group(1));
	}

	static String notSeenText(Matcher matcher)
	{
		return "Equip " + matcher.group(1).trim() + " first";
	}
}
