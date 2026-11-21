package com.groundmarkervariables.variables.label;

import com.groundmarkervariables.variables.ArgVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.BossAliases;
import com.groundmarkervariables.variables.support.BossHiscoreSkill;
import com.groundmarkervariables.variables.support.BossKillCountTracker;
import com.groundmarkervariables.variables.support.ConditionEvaluator;
import com.groundmarkervariables.variables.support.GroupState;
import com.groundmarkervariables.variables.support.HiscoreRankLookup;
import com.groundmarkervariables.variables.support.IconNames;
import com.groundmarkervariables.variables.support.ItemQuantityFinder;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.party.PartyMember;
import net.runelite.client.util.QuantityFormatter;

// The one-argument variables, {name <arg>}, one line each (plus their variables.json entry). number() ones are
// MathVariable operands; .party() ones also resolve for other members in {party} rows.
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ArgVariables
{
	// Chat Commands' own personal best data, read-only.
	private static final String PERSONAL_BEST_GROUP = "personalbest";
	private static final Pattern DOSE_COUNT = Pattern.compile("\\((\\d+)\\)");

	private final Client client;
	private final RichText richText;
	private final ConfigManager configManager;
	private final ItemManager itemManager;
	private final IconNames iconNames;
	private final BossKillCountTracker killCounts;
	private final HiscoreRankLookup hiscores;
	private final GroupState groupState;

	private final List<ArgVariable> all = define();

	// Built before the fields above are set: they're only read when a lambda runs.
	private List<ArgVariable> define()
	{
		return List.of(
			number("kc", boss -> Objects.requireNonNullElse(killCounts.getKc(BossAliases.resolve(boss)), 0),
				(boss, v) -> richText.labeled(BossAliases.resolve(boss) + " kc", v))
				.party(this::memberKc,
					(boss, member) -> memberKc(boss, member) == null ? richText.animatedDots(client.getTickCount()) : richText.highlightValue(memberKc(boss, member).toString()),
					(boss, v) -> richText.highlightValue(v)),
			text("pb", this::pb, (boss, v) -> richText.labeled(BossAliases.resolve(boss) + " pb", v)),
			text("group", name -> Objects.requireNonNullElse(groupState.value(name), ""),
				(name, v) -> v.isEmpty() ? "" : richText.labeled(groupState.group(name) != null ? groupState.group(name).name : name, v)),
			number("price", item -> Objects.requireNonNullElse(itemPrice(item), ConditionEvaluator.NULL),
				(item, v) -> itemPrice(item) == null ? null : richText.labeled(iconNames.itemNamed(item), QuantityFormatter.quantityToStackSize(itemPrice(item)))),
			text("hasItem", typed -> loggedIn() ? !items(typed, null).isEmpty() : null, (typed, v) -> heldItem(typed, null))
				.party((typed, member) -> !items(typed, member).isEmpty(), this::heldItem, (typed, v) -> heldItem(typed, null)),
			number("numItem", typed -> loggedIn() ? quantity(items(typed, null), false) : null,
				(typed, v) -> richText.labeled(typed + " count", stack(v)))
				.party((typed, member) -> quantity(items(typed, member), false),
					(typed, member) -> richText.highlightValue(stack(String.valueOf(quantity(items(typed, member), false)))),
					(typed, v) -> richText.highlightValue(stack(v))),
			number("numDoses", typed -> loggedIn() ? quantity(items(typed, null), true) : null,
				(typed, v) -> richText.labeled(typed + " doses", stack(v)))
				.party((typed, member) -> quantity(items(typed, member), true),
					(typed, member) -> richText.highlightValue(stack(String.valueOf(quantity(items(typed, member), true)))),
					(typed, v) -> richText.highlightValue(stack(v)))
		);
	}

	public List<ArgVariable> all()
	{
		return all;
	}

	public List<ArgVariable> numeric()
	{
		return all.stream().filter(ArgVariable::numeric).collect(Collectors.toList());
	}

	private static ArgVariable number(String names, Function<String, Object> value, BiFunction<String, String, String> rich)
	{
		return new ArgVariable(names, true, value, rich);
	}

	private static ArgVariable text(String names, Function<String, Object> value, BiFunction<String, String, String> rich)
	{
		return new ArgVariable(names, false, value, rich);
	}

	private boolean loggedIn()
	{
		return client.getLocalPlayer() != null;
	}

	// From the hiscores: null while loading, and for bosses not on them.
	@Nullable
	private Integer memberKc(String boss, PartyMember member)
	{
		HiscoreSkill skill = BossHiscoreSkill.find(BossAliases.resolve(boss));
		String name = member.getDisplayName();
		return skill == null || name == null || name.isEmpty() ? null : hiscores.kc(name, skill);
	}

	// Mirrors ChatCommandsPlugin#secondsToTimeString so this matches !pb's own format.
	private String pb(String boss)
	{
		Double seconds = configManager.getRSProfileConfiguration(PERSONAL_BEST_GROUP, BossAliases.resolve(boss).toLowerCase(Locale.ENGLISH), double.class);
		if (seconds == null)
		{
			return "None";
		}
		int hours = (int) (Math.floor(seconds) / 3600);
		int minutes = (int) (Math.floor(seconds / 60) % 60);
		double secs = seconds % 60;
		String prefix = hours > 0 ? String.format("%d:%02d:", hours, minutes) : String.format("%d:", minutes);
		return prefix + (Math.floor(secs) == secs ? String.format("%02d", (int) secs) : String.format("%05.2f", secs));
	}

	@Nullable
	private Long itemPrice(String item)
	{
		String name = iconNames.itemNamed(item);
		Integer id = name == null ? null : iconNames.id(IconNames.Kind.ITEM, name);
		return id == null ? null : itemManager.getItemPrice(id);
	}

	// Ours when member is null.
	private List<ItemQuantityFinder.Match> items(String typed, @Nullable PartyMember member)
	{
		String search = typed.toLowerCase(Locale.ROOT);
		return member == null ? ItemQuantityFinder.findAll(client, itemManager, search) : ItemQuantityFinder.findAll(member, itemManager, search);
	}

	// The first held match's name colored true, else typed colored false.
	@Nullable
	private String heldItem(String typed, @Nullable PartyMember member)
	{
		if (member == null && !loggedIn())
		{
			return null;
		}
		List<ItemQuantityFinder.Match> matches = items(typed, member);
		return richText.booleanColored(!matches.isEmpty(), matches.isEmpty() ? typed : matches.get(0).name);
	}

	// Doses by the "(N)" in each name; items without one aren't counted.
	private static int quantity(List<ItemQuantityFinder.Match> matches, boolean doses)
	{
		int total = 0;
		for (ItemQuantityFinder.Match match : matches)
		{
			Matcher dose = DOSE_COUNT.matcher(match.name);
			if (!doses)
			{
				total += match.quantity;
			}
			else if (dose.find())
			{
				total += match.quantity * Integer.parseInt(dose.group(1));
			}
		}
		return total;
	}

	private static String stack(String quantity)
	{
		return QuantityFormatter.quantityToStackSize(Long.parseLong(quantity));
	}
}
