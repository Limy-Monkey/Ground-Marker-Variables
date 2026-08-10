package com.groundmarkervariables.variables;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.EnumID;
import net.runelite.api.ParamID;
import net.runelite.api.StructComposition;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

// {attackStyle} -> the player's current combat style name (e.g. "Accurate", "Aggressive",
// "Defensive Casting"), or "None" if the equipped weapon has no notable style right now
// (e.g. nothing equipped). Ported from gearscape's own resolveAttackStyleIndex/
// buildAttackStyle/weaponTypeStyleNames, which mirror RuneLite core's own
// AttackStylesPlugin#updateAttackStyle/#getWeaponTypeStyles (package-private there, so not
// directly reusable) — looks the equipped weapon's style set up via
// EnumID.WEAPON_STYLES -> a per-style StructComposition -> ParamID.ATTACK_STYLE_NAME.
class AttackStyleLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{attackStyle\\}", Pattern.CASE_INSENSITIVE);
	private static final String NO_STYLE = "None";

	private final Client client;

	@Inject
	private AttackStyleLabelVariable(Client client)
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
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		String style = buildAttackStyle(resolveAttackStyleIndex());
		return style == null ? NO_STYLE : style;
	}

	// COM_MODE is the 0-based index of the currently selected combat style among the
	// equipped weapon's own style set. Staves are the one wrinkle -- index 4 covers both
	// Casting and Defensive Casting, disambiguated by AUTOCAST_DEFMODE -- so this folds that
	// in, giving a single index that uniquely identifies the selected style slot even for
	// weapons like partisans where more than one slot shares the same displayed name.
	private int resolveAttackStyleIndex()
	{
		int styleIndex = client.getVarpValue(VarPlayerID.COM_MODE);
		if (styleIndex == 4)
		{
			styleIndex += client.getVarbitValue(VarbitID.AUTOCAST_DEFMODE);
		}

		return styleIndex;
	}

	// Null if the resolved style is "Other" (no notable style, e.g. an empty weapon slot) or
	// the weapon type isn't in the style table at all.
	private String buildAttackStyle(int attackStyleIndex)
	{
		String[] styleNames = weaponTypeStyleNames(client.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY));
		if (attackStyleIndex < 0 || attackStyleIndex >= styleNames.length)
		{
			return null;
		}

		return styleNames[attackStyleIndex];
	}

	// weaponType == -1 in EnumID.WEAPON_STYLES for a couple of weapon types RuneLite core
	// itself special-cases (blue moon spear, partisan) -- same fallback arrays
	// AttackStylesPlugin uses.
	private String[] weaponTypeStyleNames(int weaponType)
	{
		int weaponStyleEnumId = client.getEnum(EnumID.WEAPON_STYLES).getIntValue(weaponType);
		if (weaponStyleEnumId == -1)
		{
			if (weaponType == 22) // Blue moon spear
			{
				return new String[] {"Accurate", "Aggressive", null, "Defensive", "Casting", "Defensive Casting"};
			}

			if (weaponType == 30) // Partisan
			{
				return new String[] {"Accurate", "Aggressive", "Aggressive", "Defensive"};
			}

			return new String[0];
		}

		int[] weaponStyleStructs = client.getEnum(weaponStyleEnumId).getIntVals();
		String[] styleNames = new String[weaponStyleStructs.length];
		for (int i = 0; i < weaponStyleStructs.length; i++)
		{
			String name = client.getStructComposition(weaponStyleStructs[i]).getStringValue(ParamID.ATTACK_STYLE_NAME);
			if ("Other".equals(name))
			{
				continue; // Leave this slot null -- no notable style, matches AttackStylesPlugin.
			}

			// Index 5 reuses the "Defensive" label for a staff's defensive-casting mode.
			styleNames[i] = (i == 5 && "Defensive".equals(name)) ? "Defensive Casting" : name;
		}

		return styleNames;
	}
}
