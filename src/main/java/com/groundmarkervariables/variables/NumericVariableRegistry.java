package com.groundmarkervariables.variables;

import com.groundmarkervariables.variables.label.BoostedSkillLevelLabelVariable;
import com.groundmarkervariables.variables.label.CombatAchievementsLabelVariable;
import com.groundmarkervariables.variables.label.KcLabelVariable;
import com.groundmarkervariables.variables.label.LootLabelVariable;
import com.groundmarkervariables.variables.label.MiscellaniaLabelVariable;
import com.groundmarkervariables.variables.label.QuestPointsLabelVariable;
import com.groundmarkervariables.variables.label.RunEnergyLabelVariable;
import com.groundmarkervariables.variables.label.SkillLevelLabelVariable;
import com.groundmarkervariables.variables.label.SlayerPointsLabelVariable;
import com.groundmarkervariables.variables.label.SlayerStreakLabelVariable;
import com.groundmarkervariables.variables.label.SpecLabelVariable;
import com.groundmarkervariables.variables.label.WeekdayLabelVariable;
import com.groundmarkervariables.variables.label.XpRateLabelVariable;

import java.util.List;
import java.util.Set;
import javax.inject.Inject;

// The variables usable as a MathVariable operand -- the ones whose Plain Text is always a
// plain number. Separate from VariableRegistry so MathVariable avoids depending on
// ConditionEvaluator (circular -- see ConditionEvaluator's own comment).
public class NumericVariableRegistry
{
	// AdvancedLabelEditor's own autocomplete name/prefix for each variable above.
	public static final Set<String> NAMES = Set.of(
		"kc", "loot", "lvl_", "boost_", "xpRate_", "runEnergy", "spec", "questPoints", "slayerPoints", "slayerStreak", "miscellania", "weekday",
		"combatAchievement"
	);

	private final List<LabelVariable> variables;

	@Inject
	private NumericVariableRegistry(
		KcLabelVariable kc,
		LootLabelVariable loot,
		SkillLevelLabelVariable skillLevel,
		BoostedSkillLevelLabelVariable boostedSkillLevel,
		XpRateLabelVariable xpRate,
		RunEnergyLabelVariable runEnergy,
		SpecLabelVariable spec,
		QuestPointsLabelVariable questPoints,
		SlayerPointsLabelVariable slayerPoints,
		SlayerStreakLabelVariable slayerStreak,
		MiscellaniaLabelVariable miscellania,
		WeekdayLabelVariable weekday,
		CombatAchievementsLabelVariable combatAchievements)
	{
		this.variables = List.of(kc, loot, skillLevel, boostedSkillLevel, xpRate, runEnergy, spec,
			questPoints, slayerPoints, slayerStreak, miscellania, weekday, combatAchievements);
	}

	public List<LabelVariable> all()
	{
		return variables;
	}
}
