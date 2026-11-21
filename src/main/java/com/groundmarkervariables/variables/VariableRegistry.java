package com.groundmarkervariables.variables;

import com.google.inject.Injector;
import com.groundmarkervariables.variables.label.*;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

// The "base" value-producing variables — the ones ConditionalVariable evaluates the
// left-hand side of a comparison against. Kept separate from LabelResolver's own list
// (which also includes ConditionalVariable itself) so ConditionalVariable can depend on
// this registry instead of on LabelResolver, avoiding a circular dependency.
//
// MetronomeLabelVariable is deliberately NOT here — see LabelResolver's comment for why it's
// excluded from conditional support entirely (both as <expr> and inside A/B branches).
//
// A new variable: a line in SimpleVariables if it takes no arguments, else its class here.
public class VariableRegistry
{
	private final List<LabelVariable> variables;

	@Inject
	private VariableRegistry(Injector injector, SimpleVariables simple, ArgVariables args, SpellVariables spells)
	{
		// The variables defined inline in SimpleVariables, ArgVariables and SpellVariables.
		List<LabelVariable> variables = new ArrayList<>(simple.all());
		variables.addAll(args.all());
		variables.addAll(spells.all());
		for (Class<? extends LabelVariable> type : List.of(
			SkillLevelLabelVariable.class,
			BoostedSkillLevelLabelVariable.class,
			AttackStyleLabelVariable.class,
			AttackTypeLabelVariable.class,
			ShowItemsLabelVariable.class,
			RunePouchLabelVariable.class,
			EquipLabelVariable.class,
			XpRateLabelVariable.class,
			SixHourTimeRemainingLabelVariable.class,
			LootLabelVariable.class,
			LootSessionLabelVariable.class,
			LogLabelVariable.class,
			ChargesLabelVariable.class,
			XpLabelVariable.class,
			RankLabelVariable.class,
			PartyLabelVariable.class,
			ReadyCheckLabelVariable.class,
			DivineLabelVariable.class,
			WorldLabelVariable.class,
			ActiveLabelVariable.class,
			QuickPrayersLabelVariable.class,
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
