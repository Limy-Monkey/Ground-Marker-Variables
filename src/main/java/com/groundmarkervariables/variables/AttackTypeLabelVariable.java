package com.groundmarkervariables.variables;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

// {attackType} -> current combat style's damage type (Stab/Slash/Crush/Ranged/Magic), or
// "None" if the current style has no notable type -- see WeaponAttackTypes.
class AttackTypeLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{attackType\\}", Pattern.CASE_INSENSITIVE);

	private final Client client;
	private final RichText richText;

	@Inject
	private AttackTypeLabelVariable(Client client, RichText richText)
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
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		return typeName();
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		String name = resolvePlain(matcher);
		return name == null ? null : richText.labeled("Attack Type", name);
	}

	private String typeName()
	{
		int weaponCategory = client.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY);
		WeaponAttackType type = WeaponAttackTypes.forStyle(weaponCategory, resolveAttackStyleIndex());
		return type == null ? WeaponAttackType.NONE.displayName() : type.displayName();
	}

	// Mirrors AttackStyleLabelVariable#resolveAttackStyleIndex -- see its own comment.
	private int resolveAttackStyleIndex()
	{
		int styleIndex = client.getVarpValue(VarPlayerID.COM_MODE);
		if (styleIndex == 4)
		{
			styleIndex += client.getVarbitValue(VarbitID.AUTOCAST_DEFMODE);
		}

		return styleIndex;
	}
}
