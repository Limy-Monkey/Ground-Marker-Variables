package com.groundmarkervariables.variables;

// VarbitID.SLAYER_MODIFIER_ID -> display name. Not exposed anywhere in RuneLite itself (only
// ever compared to 2, unnamed) -- these names/IDs are reverse-engineered from observation.
enum SlayerModifierType
{
	SLAYER_POINTS(1, "Slayer Points"),
	TASK_QUANTITY(2, "Task Quantity"),
	CLUE_SCROLL_RATE(3, "Clue Scroll Rate"),
	SUPERIOR_DROP_RATE(4, "Superior Drop Rate"),
	SLAYER_XP(5, "Superior Slayer XP");

	private final int id;
	private final String displayName;

	SlayerModifierType(int id, String displayName)
	{
		this.id = id;
		this.displayName = displayName;
	}

	String displayName()
	{
		return displayName;
	}

	static SlayerModifierType fromId(int id)
	{
		for (SlayerModifierType type : values())
		{
			if (type.id == id)
			{
				return type;
			}
		}

		return null;
	}
}
