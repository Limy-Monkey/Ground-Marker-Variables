package com.groundmarkervariables.variables;

import java.util.List;
import javax.inject.Inject;

// The "base" value-producing variables — the ones ConditionalVariable evaluates the
// left-hand side of a comparison against. Kept separate from LabelResolver's own list
// (which also includes ConditionalVariable itself) so ConditionalVariable can depend on
// this registry instead of on LabelResolver, avoiding a circular dependency.
//
// MetronomeLabelVariable is deliberately NOT here — see LabelResolver's comment for why it's
// excluded from conditional support entirely (both as <expr> and inside A/B branches).
class VariableRegistry
{
	private final List<LabelVariable> variables;

	@Inject
	private VariableRegistry(
		RsnLabelVariable rsn,
		SpellbookLabelVariable spellbook,
		HasThrallsLabelVariable hasThralls,
		WeaponLabelVariable weapon,
		SkillLevelLabelVariable skillLevel,
		HasFreezeLabelVariable hasFreeze,
		HasEntangleLabelVariable hasEntangle,
		BoostedSkillLevelLabelVariable boostedSkillLevel,
		AttackStyleLabelVariable attackStyle,
		HasItemLabelVariable hasItem)
	{
		this.variables = List.of(
			rsn, spellbook, hasThralls, weapon, skillLevel, hasFreeze, hasEntangle, boostedSkillLevel,
			attackStyle, hasItem);
	}

	List<LabelVariable> all()
	{
		return variables;
	}
}
