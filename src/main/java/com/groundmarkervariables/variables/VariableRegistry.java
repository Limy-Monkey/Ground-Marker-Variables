package com.groundmarkervariables.variables;

import com.groundmarkervariables.variables.label.*;

import java.util.List;
import javax.inject.Inject;

// The "base" value-producing variables — the ones ConditionalVariable evaluates the
// left-hand side of a comparison against. Kept separate from LabelResolver's own list
// (which also includes ConditionalVariable itself) so ConditionalVariable can depend on
// this registry instead of on LabelResolver, avoiding a circular dependency.
//
// MetronomeLabelVariable is deliberately NOT here — see LabelResolver's comment for why it's
// excluded from conditional support entirely (both as <expr> and inside A/B branches).
public class VariableRegistry
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
		AttackTypeLabelVariable attackType,
		HasItemLabelVariable hasItem,
		MiscellaniaLabelVariable miscellania,
		HasAlchsLabelVariable hasAlchs,
		TimeLabelVariable time,
		DateLabelVariable date,
		WeekdayLabelVariable weekday,
		QuestPointsLabelVariable questPoints,
		EquipLabelVariable equip,
		KcLabelVariable kc,
		PbLabelVariable pb,
		AutoRetaliateLabelVariable autoRetaliate,
		XpRateLabelVariable xpRate,
		RunEnergyLabelVariable runEnergy,
		SpecLabelVariable spec,
		SixHourTimeRemainingLabelVariable sixHourTimeRemaining,
		SlayerTaskLabelVariable slayerTask,
		SlayerStreakLabelVariable slayerStreak,
		SlayerPointsLabelVariable slayerPoints,
		LootLabelVariable loot,
		CombatAchievementsLabelVariable combatAchievements,
		NumItemLabelVariable numItem,
		NumDosesLabelVariable numDoses,
		XpLabelVariable xp,
		RankLabelVariable rank,
		PartyLabelVariable party,
		DivineLabelVariable divine,
		WorldLabelVariable world,
		HasVileVigourLabelVariable hasVileVigour)
	{
		this.variables = List.of(
			rsn, spellbook, hasThralls, weapon, skillLevel, hasFreeze, hasEntangle, boostedSkillLevel,
			attackStyle, attackType, hasItem, miscellania, hasAlchs, time, date, weekday, questPoints, equip, kc, pb, autoRetaliate, xpRate,
			runEnergy, spec, sixHourTimeRemaining, slayerTask, slayerStreak, slayerPoints, loot, combatAchievements, numItem, numDoses, xp, rank,
			party, divine, world, hasVileVigour);
	}

	public List<LabelVariable> all()
	{
		return variables;
	}
}
