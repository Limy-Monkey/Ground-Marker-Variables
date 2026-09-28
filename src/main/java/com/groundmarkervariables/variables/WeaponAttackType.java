package com.groundmarkervariables.variables;

// The damage type a combat style slot uses -- see WeaponAttackTypes.
enum WeaponAttackType
{
	STAB("Stab"),
	SLASH("Slash"),
	CRUSH("Crush"),
	RANGED("Ranged"),
	MAGIC("Magic"),
	NONE("None");

	private final String displayName;

	WeaponAttackType(String displayName)
	{
		this.displayName = displayName;
	}

	String displayName()
	{
		return displayName;
	}
}
