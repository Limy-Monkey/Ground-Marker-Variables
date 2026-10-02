package com.groundmarkervariables.variables.support;

import static com.groundmarkervariables.variables.support.WeaponAttackType.CRUSH;
import static com.groundmarkervariables.variables.support.WeaponAttackType.MAGIC;
import static com.groundmarkervariables.variables.support.WeaponAttackType.NONE;
import static com.groundmarkervariables.variables.support.WeaponAttackType.RANGED;
import static com.groundmarkervariables.variables.support.WeaponAttackType.SLASH;
import static com.groundmarkervariables.variables.support.WeaponAttackType.STAB;

// VarbitID.COMBAT_WEAPON_CATEGORY -> attack type per style slot. Not exposed by the game or
// RuneLite at all (AttackStylesPlugin only derives style names, never type) -- ordinal-indexed,
// sourced from the client's own combat_interface_setup.cs2 script (see ngraves95/attacktimer's
// WeaponType.java; its weaponType==22/30 fallbacks match ours exactly, confirming the ordinals
// line up). null means no style in that slot, matching weaponTypeStyleNames' own null slots.
public final class WeaponAttackTypes
{
	private static final WeaponAttackType[][] BY_WEAPON_CATEGORY = {
		{CRUSH, CRUSH, null, CRUSH}, // 0
		{SLASH, SLASH, CRUSH, SLASH}, // 1
		{CRUSH, CRUSH, null, CRUSH}, // 2
		{RANGED, RANGED, null, RANGED}, // 3
		{SLASH, SLASH, STAB, SLASH}, // 4
		{RANGED, RANGED, null, RANGED}, // 5
		{SLASH, RANGED, MAGIC, null}, // 6
		{RANGED, RANGED, null, RANGED}, // 7
		{NONE, CRUSH, null, null}, // 8
		{SLASH, SLASH, STAB, SLASH}, // 9
		{SLASH, SLASH, CRUSH, SLASH}, // 10
		{STAB, STAB, CRUSH, STAB}, // 11
		{STAB, SLASH, null, STAB}, // 12
		{CRUSH, CRUSH, null, CRUSH}, // 13
		{SLASH, SLASH, CRUSH, SLASH}, // 14
		{STAB, SLASH, CRUSH, STAB}, // 15
		{CRUSH, CRUSH, STAB, CRUSH}, // 16
		{STAB, STAB, SLASH, STAB}, // 17
		{CRUSH, CRUSH, null, CRUSH, MAGIC, MAGIC}, // 18
		{RANGED, RANGED, null, RANGED}, // 19
		{SLASH, SLASH, null, SLASH}, // 20
		{STAB, SLASH, null, CRUSH, MAGIC, MAGIC}, // 21
		{STAB, SLASH, null, CRUSH, MAGIC, MAGIC}, // 22 -- Blue moon spear's fallback slot
		{SLASH, SLASH, CRUSH, SLASH}, // 23
		{MAGIC, MAGIC, null, MAGIC}, // 24
		{STAB, SLASH, CRUSH, STAB}, // 25
		{STAB, SLASH, null, STAB}, // 26
		{CRUSH, CRUSH, null, CRUSH}, // 27
		{CRUSH, null, null, NONE}, // 28
		{MAGIC, MAGIC, null, MAGIC}, // 29
		{STAB, STAB, CRUSH, STAB}, // 30 -- Partisan's fallback slot
		null, // 31
		null, // 32
		null, // 33
		null, // 34
		{SLASH, SLASH, null, SLASH}, // 35
	};

	private WeaponAttackTypes()
	{
	}

	public static WeaponAttackType forStyle(int weaponCategory, int styleIndex)
	{
		if (weaponCategory < 0 || weaponCategory >= BY_WEAPON_CATEGORY.length)
		{
			return null;
		}

		WeaponAttackType[] types = BY_WEAPON_CATEGORY[weaponCategory];
		if (types == null || styleIndex < 0 || styleIndex >= types.length)
		{
			return null;
		}

		return types[styleIndex];
	}
}
