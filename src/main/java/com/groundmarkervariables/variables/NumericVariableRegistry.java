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
public class NumericVariableRegistry {
	// LabelCompleter's autocomplete name/prefix for each variable above.
	public static final Set<String> NAMES = Set.of(
		"kc", "loot", "lootSession", "log", "lvl_", "boost_", "xpRate_", "runEnergy", "spec", "questPoints", "slayerPoints", "slayerStreak", "miscellania", "weekday",
		"combatAchievement", "numItem", "numDoses", "charges", "partyNum", "party", "invSpace", "xp_", "price"
	);

	private final List<LabelVariable> variables;

	@Inject
	private NumericVariableRegistry(Injector injector, SimpleVariables simple, ArgVariables args) {
		// SimpleVariables' and ArgVariables' number() variables.
		List<LabelVariable> variables = new ArrayList<>(simple.numeric());
		variables.addAll(args.numeric());
		for (Class<? extends LabelVariable> type : List.of(
			LootLabelVariable.class,
			LootLabelVariable.LootSession.class,
			LogLabelVariable.class,
			ChargesLabelVariable.class,
			PartyLabelVariable.class)) {
			variables.add(injector.getInstance(type));
		}
		this.variables = List.copyOf(variables);
	}

	public List<LabelVariable> all() {
		return variables;
	}
}
