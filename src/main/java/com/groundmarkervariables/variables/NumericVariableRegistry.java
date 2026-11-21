package com.groundmarkervariables.variables;

import com.google.inject.Injector;
import com.groundmarkervariables.variables.label.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

// The variables usable as a MathVariable operand -- the ones whose Plain Text is always a
// plain number. Separate from VariableRegistry so MathVariable avoids depending on
// ConditionEvaluator (circular -- see ConditionEvaluator's own comment).
public class NumericVariableRegistry
{
	// LabelCompleter's autocomplete name/prefix for each variable above.
	public static final Set<String> NAMES = Set.of(
		"kc", "loot", "lootSession", "log", "lvl_", "boost_", "xpRate_", "runEnergy", "spec", "questPoints", "slayerPoints", "slayerStreak", "miscellania", "weekday",
		"combatAchievement", "numItem", "numDoses", "charges", "partyNum", "party", "invSpace", "xp_", "price", "geHistory"
	);

	private final List<LabelVariable> variables;

	@Inject
	private NumericVariableRegistry(Injector injector, SimpleVariables simple)
	{
		// SimpleVariables' number() variables.
		List<LabelVariable> variables = new ArrayList<>(simple.numeric());
		for (Class<? extends LabelVariable> type : List.of(
			KcLabelVariable.class,
			LootLabelVariable.class,
			LootSessionLabelVariable.class,
			LogLabelVariable.class,
			SkillLevelLabelVariable.class,
			BoostedSkillLevelLabelVariable.class,
			XpRateLabelVariable.class,
			NumItemLabelVariable.class,
			NumDosesLabelVariable.class,
			ChargesLabelVariable.class,
			PartyLabelVariable.class,
			XpLabelVariable.class,
			PriceLabelVariable.class,
			GeHistoryLabelVariable.class))
		{
			variables.add(injector.getInstance(type));
		}
		this.variables = List.copyOf(variables);
	}

	public List<LabelVariable> all()
	{
		return variables;
	}
}
