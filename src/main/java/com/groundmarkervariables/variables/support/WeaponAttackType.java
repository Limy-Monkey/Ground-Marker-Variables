package com.groundmarkervariables.variables.support;

import lombok.RequiredArgsConstructor;

// The damage type a combat style slot uses -- see WeaponAttackTypes.
@RequiredArgsConstructor
public enum WeaponAttackType
{
	STAB("Stab"),
	SLASH("Slash"),
	CRUSH("Crush"),
	RANGED("Ranged"),
	MAGIC("Magic"),
	NONE("None");

	private final String displayName;

	public String displayName()
	{
		return displayName;
	}
}
