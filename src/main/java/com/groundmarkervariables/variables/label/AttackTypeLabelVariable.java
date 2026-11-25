package com.groundmarkervariables.variables.label;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;

import com.groundmarkervariables.variables.Args;
import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.WeaponAttackType;
import com.groundmarkervariables.variables.support.WeaponStyleTracker;
import net.runelite.api.Client;

// {attackType} -> current style's damage type (Stab/Slash/Crush/Ranged/Magic), or "None".
// {attackType, weapon=<name>} -> the last type seen on that weapon this session.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class AttackTypeLabelVariable implements LabelVariable {
	private final Client client;
	private final RichText richText;
	private final WeaponStyleTracker tracker;

	private final Args args = AttackStyleLabelVariable.weaponArgs("attackType", this::weaponNames);

	@Override
	public Pattern pattern() {
		return args.pattern();
	}

	@Override
	public Args args() {
		return args;
	}

	@Override
	public String resolvePlain(Matcher matcher) {
		if (client.getLocalPlayer() == null) {
			return null;
		}

		String weapon = AttackStyleLabelVariable.weapon(args, matcher);
		WeaponStyleTracker.Style style = AttackStyleLabelVariable.style(tracker, weapon);
		if (style == null) {
			return AttackStyleLabelVariable.notSeenText(weapon);
		}

		return style.type == null ? WeaponAttackType.NONE.displayName() : style.type.displayName();
	}

	@Override
	public String resolveRich(Matcher matcher) {
		if (client.getLocalPlayer() == null) {
			return null;
		}

		String weapon = AttackStyleLabelVariable.weapon(args, matcher);
		if (AttackStyleLabelVariable.style(tracker, weapon) == null) {
			return AttackStyleLabelVariable.notSeenText(weapon);
		}

		return richText.labeled(AttackStyleLabelVariable.label(tracker, weapon, "Type", "Attack Type"), resolvePlain(matcher));
	}

	private Collection<String> weaponNames() {
		return tracker.weaponNames();
	}
}
