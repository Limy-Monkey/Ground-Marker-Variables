package com.groundmarkervariables.variables.label;

import java.util.Collection;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;

import com.groundmarkervariables.variables.Args;
import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.WeaponAttackType;
import com.groundmarkervariables.variables.support.WeaponStyleTracker;
import net.runelite.api.Client;

// {attackStyle} -> current combat style name, or "None". {attackStyle, weapon=<name>} -> the
// last style seen on that weapon this session.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class AttackStyleLabelVariable implements LabelVariable
{
	private final Client client;
	private final RichText richText;
	private final WeaponStyleTracker tracker;

	private final Args args = weaponArgs("attackStyle", this::weaponNames);

	@Override
	public Pattern pattern()
	{
		return args.pattern();
	}

	@Override
	public Args args()
	{
		return args;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		String weapon = weapon(args, matcher);
		WeaponStyleTracker.Style style = style(tracker, weapon);
		return style == null ? notSeenText(weapon) : style.name;
	}

	// Appends the damage type, e.g. "Aggressive (Slash)".
	@Override
	public String resolveRich(Matcher matcher)
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		String weapon = weapon(args, matcher);
		WeaponStyleTracker.Style style = style(tracker, weapon);
		if (style == null)
		{
			return notSeenText(weapon);
		}

		String value = (style.type == null || style.type == WeaponAttackType.NONE) ? style.name : style.name + " (" + style.type.displayName() + ")";
		return richText.labeled(label(tracker, weapon, "Style", "Attack Style"), value);
	}

	private Collection<String> weaponNames()
	{
		return tracker.weaponNames();
	}

	// {<name>[, weapon=<name>]}, suggesting the weapons seen this session.
	static Args weaponArgs(String name, Supplier<Collection<String>> weapons)
	{
		return Args.of(name).value("weapon", weapons, Args.ITEM_ICON);
	}

	@Nullable
	static String weapon(Args args, Matcher matcher)
	{
		return args.options(matcher).value("weapon");
	}

	static String label(WeaponStyleTracker tracker, @Nullable String weapon, String suffix, String defaultLabel)
	{
		return weapon == null ? defaultLabel : tracker.weaponName(weapon) + " " + suffix;
	}

	@Nullable
	static WeaponStyleTracker.Style style(WeaponStyleTracker tracker, @Nullable String weapon)
	{
		return weapon == null ? tracker.current() : tracker.forWeapon(weapon);
	}

	static String notSeenText(String weapon)
	{
		return "Equip " + weapon + " first";
	}
}
