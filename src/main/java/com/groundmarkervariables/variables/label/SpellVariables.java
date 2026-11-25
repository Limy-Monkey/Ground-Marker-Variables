package com.groundmarkervariables.variables.label;

import com.groundmarkervariables.party.PartyDataSharing;
import com.groundmarkervariables.party.PartyValue;
import com.groundmarkervariables.variables.Args;
import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.PartyMemberVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.ConditionEvaluator;
import com.groundmarkervariables.variables.support.Icons;
import com.groundmarkervariables.variables.support.RuneCounter;
import com.groundmarkervariables.variables.support.Spellbooks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemComposition;
import net.runelite.api.ParamID;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.party.PartyMember;

// The {has<Spell>} variables, one entry each: true if the player could cast the spell right now -- for a family like
// {hasSurge [wind] [water] ... [icon]}, any of those named, all by default -- on its spellbook (or Spellbook Swap) with
// its runes (RuneCounter: inventory, rune pouch, infinite sources, combo runes). Doesn't check Magic level, quests or
// cooldowns. For a party member it uses their core inventory/gear and shared {spellbook}: with no spellbook yet it's
// true if the rest checks out, and the icon is drawn at 75%. Their Wilderness status isn't known, so sacks don't count.
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SpellVariables {
	// A spell definition's params: its spellbook (as Spellbooks), icon, and rune / count pairs (the game's magic_runetype_n /
	// magic_runecount_n).
	private static final int SPELL_SPELLBOOK = 336;
	private static final int SPELL_ICON = 597;
	private static final int[] RUNE_PARAMS = {365, 366, 367, 368, 369, 370, 606, 607};
	// Every spell variable's: its icon instead; no Spellbook Swap; and the level needed (see castable()).
	private static final String[] FLAGS = {"icon", "noSbs", "checkLvl"};

	private final Client client;
	private final RichText richText;
	private final PartyDataSharing partyDataSharing;

	private final List<SpellVariable> all = define();
	// See cacheSpells(); read by {hasSpell}'s suggestions on the editor's key thread.
	private volatile Map<String, GameSpell> cacheSpells = Map.of();

	// Built before the fields above are set: they're only read when a variable resolves.
	private List<SpellVariable> define() {
		return List.of(
			hasSpell(),
			single("hasAlchs", "Alchs", "High Level Alchemy"),
			single("hasEntangle", "Entangles", "Entangle").orWildernessSack(ItemID.BLIGHTED_SACK_ENTANGLE),
			single("hasFreeze", "Freezes", "Ice Barrage").orWildernessSack(ItemID.BLIGHTED_SACK_ICEBARRAGE).alias("hasFreezes"),
			// Greater thralls, with the book's icon.
			single("hasThralls", "Thralls", "Resurrect Greater Ghost").needing(ItemID.BOOK_OF_THE_DEAD),
			single("hasVileVigour", "Vile Vigour", "Vile Vigour"),
			single("hasUndeadGrasp", "Undead Grasp", "Undead Grasp"),
			single("hasMarkOfDarkness", "Mark of Darkness", "Mark of Darkness"),
			single("hasDeathCharge", "Death Charge", "Death Charge"),
			single("hasVengeance", "Vengeance", "Vengeance"),
			single("hasSpellbookSwap", "Spellbook Swap", "Spellbook Swap"),
			family("hasSurge", "Surge", spell("Wind Surge", "wind", "air"), spell("Water Surge", "water"), spell("Earth Surge", "earth"),
				spell("Fire Surge", "fire")),
			// Unlike {hasFreeze}, a blighted sack doesn't count.
			family("hasBarrage", "Barrage", spell("Smoke Barrage", "smoke"), spell("Shadow Barrage", "shadow"), spell("Blood Barrage", "blood"),
				spell("Ice Barrage", "ice"))
		);
	}

	public List<SpellVariable> all() {
		return all;
	}

	// One of the game's spells, by its name there; text its Rich Text name.
	private SpellVariable single(String name, String text, String spell) {
		return family(name, text, new Spell(List.of(), text, spell));
	}

	// One of the game's spells by its name there, chosen in a family by words.
	private static Spell spell(String name, String... words) {
		return new Spell(List.of(words), name, name);
	}

	// Spells chosen by their words (in the order named; all if none), text naming several.
	private SpellVariable family(String name, String text, Spell... spells) {
		List<Spell> all = List.of(spells);
		Args args = Args.of(name);
		for (Spell spell : all) {
			spell.words.forEach(word -> args.iconFlag(word, w -> "{" + name + " " + w + " icon}"));
			if (spell.words.size() > 1) {
				args.exclusive(spell.words.toArray(new String[0]));
			}
		}
		args.flag(FLAGS);
		return new SpellVariable(text, args, all, matcher -> {
			List<Spell> chosen = new ArrayList<>();
			for (String word : args.options(matcher).words()) {
				for (Spell spell : all) {
					if (spell.words.stream().anyMatch(word::equalsIgnoreCase) && !chosen.contains(spell)) {
						chosen.add(spell);
					}
				}
			}
			return chosen.isEmpty() ? all : chosen;
		});
	}

	// {hasSpell <name> [icon]}: any spell in the game's spellbooks, read from its cache.
	private SpellVariable hasSpell() {
		Args args = Args.of("hasSpell").main(() -> cacheSpells.keySet(), name -> "<sprite=" + name + " (Spell)>").flag(FLAGS)
			.mainDescribed(name -> cacheSpells.containsKey(name) ? cacheSpells.get(name).hint : null);
		return new SpellVariable("", args, List.of(), matcher -> {
			String name = args.main(matcher);
			return name == null || !cacheSpells().containsKey(name) ? List.of() : List.of(spell(name));
		});
	}

	// The game's spells by name (any case), each its spellbook, level, runes (as the game checks them: up to 4 rune and count
	// params) and icon; the first of a name kept. Empty until the cache can be read; on the client thread.
	private Map<String, GameSpell> cacheSpells() {
		if (cacheSpells.isEmpty() && client.getGameState().getState() >= GameState.LOGIN_SCREEN.getState()) {
			Map<String, GameSpell> spells = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			for (int book : client.getEnum(EnumID.SPELLBOOKS).getIntVals()) {
				for (int id : client.getEnum(book).getIntVals()) {
					ItemComposition definition = client.getItemDefinition(id);
					String name = definition.getStringValue(ParamID.SPELL_NAME);
					int spellbook = definition.getIntValue(SPELL_SPELLBOOK);
					int level = definition.getIntValue(ParamID.SPELL_LEVELREQ);
					Map<Integer, Integer> runes = new LinkedHashMap<>();
					List<String> costs = new ArrayList<>();
					for (int i = 0; i < RUNE_PARAMS.length; i += 2) {
						int rune = definition.getIntValue(RUNE_PARAMS[i]);
						if (rune > 0) {
							int count = definition.getIntValue(RUNE_PARAMS[i + 1]);
							runes.merge(rune, count, Integer::sum);
							costs.add(count + " " + client.getItemDefinition(rune).getName().replaceFirst("(?i) runes?$", ""));
						}
					}
					if (name != null && !name.isEmpty() && spellbook >= 0 && spellbook < Spellbooks.NAMES.size()) {
						// e.g. "Lvl 39 Standard: 2 Air, 2 Earth, 1 Chaos".
						String hint = "Lvl " + level + " " + Spellbooks.NAMES.get(spellbook) + (costs.isEmpty() ? "" : ": " + String.join(", ", costs));
						spells.putIfAbsent(name, new GameSpell(spellbook, level, runes, definition.getIntValue(SPELL_ICON), hint));
					}
				}
			}
			cacheSpells = spells;
		}
		return cacheSpells;
	}

	// Each of the game's spells: its name and icon sprite.
	public void forEachSpellSprite(BiConsumer<String, Integer> action) {
		cacheSpells().forEach((name, spell) -> action.accept(name, spell.sprite));
	}

	// A spell as the game defines it (see cacheSpells()).
	@Value
	private static class GameSpell {
		int spellbook;
		int level;
		Map<Integer, Integer> runes;
		int sprite;
		// {hasSpell}'s suggestion description: level, spellbook and runes.
		String hint;
	}

	// A spell as a variable names it.
	@RequiredArgsConstructor
	private static final class Spell {
		// The words choosing it in a family, e.g. "wind" and "air"; none for a single spell.
		private final List<String> words;
		// Rich Text name when it's the only one, e.g. "Wind Surge".
		private final String name;
		// The game's spell, by its name there.
		private final String game;
		// An item that must be carried or worn, its icon then the spell's; or -1.
		private int needed = -1;
		// Carried in the Wilderness on the spellbook, it stands in for the runes, or -1.
		private int sack = -1;
	}

	@RequiredArgsConstructor
	public final class SpellVariable implements LabelVariable, PartyMemberVariable {
		// Rich Text name for several, e.g. "Surge".
		private final String text;
		private final Args args;
		// Its own, for needing() and orWildernessSack().
		private final List<Spell> spells;
		// The spells a match names, in {split} order; none for an unknown {hasSpell} name.
		private final Function<Matcher, List<Spell>> chosen;

		private SpellVariable alias(String name) {
			args.alias(name);
			return this;
		}

		// For a single spell.
		private SpellVariable needing(int itemId) {
			spells.get(0).needed = itemId;
			return this;
		}

		private SpellVariable orWildernessSack(int itemId) {
			spells.get(0).sack = itemId;
			return this;
		}

		@Override
		public Pattern pattern() {
			return args.pattern();
		}

		@Override
		public Args args() {
			return args;
		}

		// An unknown {hasSpell} name is null.
		@Override
		public String resolvePlain(Matcher matcher) {
			List<Spell> chosen = this.chosen.apply(matcher);
			return chosen.isEmpty() ? ConditionEvaluator.NULL : String.valueOf(Boolean.TRUE.equals(any(chosen, castable(matcher))));
		}

		@Override
		public String resolveRich(Matcher matcher) {
			return rich(matcher, castable(matcher));
		}

		@Override
		public String resolvePlain(Matcher matcher, PartyMember member) {
			List<Spell> chosen = this.chosen.apply(matcher);
			return chosen.isEmpty() ? ConditionEvaluator.NULL
				: String.valueOf(!Boolean.FALSE.equals(any(chosen, memberCastable(member, matcher))));
		}

		@Override
		public String resolveRich(Matcher matcher, PartyMember member) {
			return rich(matcher, memberCastable(member, matcher));
		}

		@Override
		public String resolveRowRich(Matcher matcher, RichText richText) {
			return resolveRich(matcher);
		}

		// One's name, or text for several, colored by castable; or with icon, their sprites.
		private String rich(Matcher matcher, Function<Spell, Boolean> castable) {
			List<Spell> chosen = this.chosen.apply(matcher);
			if (chosen.isEmpty()) {
				return "";
			}
			if (args.options(matcher).flag("icon")) {
				return icon(chosen, castable);
			}
			String name = chosen.size() == 1 ? chosen.get(0).name : text;
			return richText.booleanColored(!Boolean.FALSE.equals(any(chosen, castable)), name);
		}

		// With noSbs, only on its own spellbook; with checkLvl, also at its level (boosted Magic).
		private Function<Spell, Boolean> castable(Matcher matcher) {
			Args.Options options = args.options(matcher);
			boolean swap = !options.flag("noSbs");
			int magic = options.flag("checkLvl") ? client.getBoostedSkillLevel(Skill.MAGIC) : Integer.MAX_VALUE;
			return spell -> {
				GameSpell game = cacheSpells().get(spell.game);
				if (game == null || game.level > magic) {
					return false;
				}
				boolean hasNeeded = spell.needed < 0 || carried(spell.needed, InventoryID.INVENTORY) || carried(spell.needed, InventoryID.EQUIPMENT);
				if (hasNeeded && Spellbooks.canCast(client, game.spellbook, game.runes, swap)) {
					return true;
				}
				return spell.sack >= 0 && carried(spell.sack, InventoryID.INVENTORY) && client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) != 0
					&& Spellbooks.canCast(client, game.spellbook, Map.of(), swap);
			};
		}

		// As castable(); null when everything checks out but their spellbook, or with checkLvl their Magic level, is unknown.
		private Function<Spell, Boolean> memberCastable(PartyMember member, Matcher matcher) {
			Args.Options options = args.options(matcher);
			String spellbookName = partyDataSharing.value(member.getMemberId(), PartyValue.SPELLBOOK);
			return spell -> {
				if (spell.needed >= 0 && RuneCounter.itemCount(member.getInv(), spell.needed) <= 0
					&& RuneCounter.itemCount(member.getWorn(), spell.needed) <= 0) {
					return false;
				}
				GameSpell game = cacheSpells().get(spell.game);
				Boolean castable = game == null ? null : Spellbooks.canCast(member, spellbookName, game.spellbook, game.runes, !options.flag("noSbs"));
				return options.flag("checkLvl") && Boolean.TRUE.equals(castable) ? null : castable;
			};
		}
	}

	private boolean carried(int itemId, InventoryID container) {
		return RuneCounter.itemCount(client, container, itemId) > 0;
	}

	// One's icon (the game's, or its needed item's), or a {split} of several.
	private String icon(List<Spell> chosen, Function<Spell, Boolean> castable) {
		StringBuilder icon = new StringBuilder();
		for (Spell spell : chosen) {
			GameSpell game = cacheSpells().get(spell.game);
			Boolean on = castable.apply(spell);
			icon.append(spell.needed >= 0 ? Icons.item(spell.needed, on) : game == null ? "" : Icons.sprite(game.sprite, on));
		}
		return chosen.size() == 1 ? icon.toString() : "{split}" + icon + "{/split}";
	}

	// True if any is castable, otherwise null if any might be (spellbook unknown), otherwise false.
	@Nullable
	private static Boolean any(List<Spell> chosen, Function<Spell, Boolean> castable) {
		Boolean result = false;
		for (Spell spell : chosen) {
			Boolean value = castable.apply(spell);
			if (Boolean.TRUE.equals(value)) {
				return true;
			}
			if (value == null) {
				result = null;
			}
		}
		return result;
	}
}
