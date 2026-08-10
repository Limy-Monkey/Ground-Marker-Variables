package com.groundmarkervariables.variables;

import java.util.List;
import javax.inject.Inject;

// The "base" value-producing variables — the ones ConditionalVariable evaluates the
// left-hand side of a comparison against. Kept separate from LabelResolver's own list
// (which also includes ConditionalVariable itself) so ConditionalVariable can depend on
// this registry instead of on LabelResolver, avoiding a circular dependency.
class VariableRegistry
{
	private final List<LabelVariable> variables;

	@Inject
	private VariableRegistry(
		RsnLabelVariable rsn,
		SpellbookLabelVariable spellbook,
		MetronomeLabelVariable metronome,
		HasThrallsLabelVariable hasThralls,
		WeaponLabelVariable weapon,
		SkillLevelLabelVariable skillLevel,
		HasFreezeLabelVariable hasFreeze,
		HasEntangleLabelVariable hasEntangle,
		BoostedSkillLevelLabelVariable boostedSkillLevel,
		AttackStyleLabelVariable attackStyle)
	{
		this.variables = List.of(
			rsn, spellbook, metronome, hasThralls, weapon, skillLevel, hasFreeze, hasEntangle, boostedSkillLevel,
			attackStyle);
	}

	List<LabelVariable> all()
	{
		return variables;
	}
}
