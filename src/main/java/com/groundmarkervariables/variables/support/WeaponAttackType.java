package com.groundmarkervariables.variables.support;

// The damage type a combat style slot uses -- see WeaponAttackTypes.
public enum WeaponAttackType
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

	public String displayName()
	{
		return displayName;
	}
}
