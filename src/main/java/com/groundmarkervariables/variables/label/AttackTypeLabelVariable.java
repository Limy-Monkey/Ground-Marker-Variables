package com.groundmarkervariables.variables.label;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.WeaponAttackType;
import com.groundmarkervariables.variables.support.WeaponStyleTracker;
import net.runelite.api.Client;

// {attackType} -> current style's damage type (Stab/Slash/Crush/Ranged/Magic), or "None".
// {attackType, weapon=<name>} -> the last type seen on that weapon this session.
public class AttackTypeLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile(
		"\\{attackType(?:\\s*,?\\s*weapon\\s*=\\s*([^{}]+?))?\\s*\\}", Pattern.CASE_INSENSITIVE);

	private final Client client;
	private final RichText richText;
	private final WeaponStyleTracker tracker;

	@Inject
	private AttackTypeLabelVariable(Client client, RichText richText, WeaponStyleTracker tracker)
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

		WeaponStyleTracker.Style style = AttackStyleLabelVariable.style(tracker, matcher);
		if (style == null)
		{
			return AttackStyleLabelVariable.notSeenText(matcher);
		}

		return style.type == null ? WeaponAttackType.NONE.displayName() : style.type.displayName();
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		WeaponStyleTracker.Style style = AttackStyleLabelVariable.style(tracker, matcher);
		if (style == null)
		{
			return AttackStyleLabelVariable.notSeenText(matcher);
		}

		return richText.labeled(AttackStyleLabelVariable.label(tracker, matcher, "Type", "Attack Type"), resolvePlain(matcher));
	}
}
