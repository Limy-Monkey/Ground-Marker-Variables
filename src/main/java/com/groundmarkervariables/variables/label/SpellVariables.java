package com.groundmarkervariables.variables.label;

import com.groundmarkervariables.party.PartyDataSharing;
import com.groundmarkervariables.party.PartyValue;
import com.groundmarkervariables.variables.Args;
import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.PartyMemberVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.Icons;
import com.groundmarkervariables.variables.support.RuneCounter;
import com.groundmarkervariables.variables.support.Spellbooks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.party.PartyMember;
import static com.groundmarkervariables.variables.support.Spellbooks.ANCIENT;
import static com.groundmarkervariables.variables.support.Spellbooks.ARCEUUS;
import static com.groundmarkervariables.variables.support.Spellbooks.LUNAR;
import static com.groundmarkervariables.variables.support.Spellbooks.STANDARD;

// The {has<Spell>} variables, one entry each: true if the player could cast the spell right now -- for a family like
// {hasSurge [wind] [water] ... [icon]}, any of those named, all by default -- on its spellbook (or Spellbook Swap) with
// its runes (RuneCounter: inventory, rune pouch, infinite sources, combo runes). Doesn't check Magic level, quests or
// cooldowns. For a party member it uses their core inventory/gear and shared {spellbook}: with no spellbook yet it's
// true if the rest checks out, and the icon is drawn at 75%. Their Wilderness status isn't known, so sacks don't count.
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SpellVariables
{
	private final Client client;
	private final RichText richText;
	private final PartyDataSharing partyDataSharing;

	private final List<SpellVariable> all = define();

	// Built before the fields above are set: they're only read when a variable resolves.
	private List<SpellVariable> define()
	{
		return List.of(
			single("hasAlchs", "Alchs", STANDARD, Map.of(ItemID.NATURERUNE, 1, ItemID.FIRERUNE, 1), sprite(SpriteID.Magicon.HIGH_LEVEL_ALCHEMY)),
			single("hasEntangle", "Entangles", STANDARD, Map.of(ItemID.EARTHRUNE, 5, ItemID.WATERRUNE, 5, ItemID.NATURERUNE, 4),
				sprite(SpriteID.Magicon2.ENTANGLE)).orWildernessSack(ItemID.BLIGHTED_SACK_ENTANGLE),
			single("hasFreeze", "Freezes", ANCIENT, Map.of(ItemID.WATERRUNE, 6, ItemID.BLOODRUNE, 2, ItemID.DEATHRUNE, 4),
				sprite(SpriteID.Magicon2.ICE_BARRAGE)).orWildernessSack(ItemID.BLIGHTED_SACK_ICEBARRAGE).alias("hasFreezes"),
			single("hasThralls", "Thralls", ARCEUUS, Map.of(ItemID.FIRERUNE, 1, ItemID.BLOODRUNE, 1, ItemID.COSMICRUNE, 1),
				item(ItemID.BOOK_OF_THE_DEAD)).needing(ItemID.BOOK_OF_THE_DEAD),
			single("hasVileVigour", "Vile Vigour", ARCEUUS, Map.of(ItemID.SOULRUNE, 1, ItemID.AIRRUNE, 3), sprite(SpriteID.MagicNecroOn.VILE_VIGOUR)),
			single("hasUndeadGrasp", "Undead Grasp", ARCEUUS, Map.of(ItemID.BLOODRUNE, 1, ItemID.FIRERUNE, 12),
				sprite(SpriteID.MagicNecroOn.UNDEAD_GRASP)),
			single("hasMarkOfDarkness", "Mark of Darkness", ARCEUUS, Map.of(ItemID.COSMICRUNE, 1, ItemID.SOULRUNE, 1),
				sprite(SpriteID.MagicNecroOn.MARK_OF_DARKNESS)),
			single("hasDeathCharge", "Death Charge", ARCEUUS, Map.of(ItemID.BLOODRUNE, 1, ItemID.DEATHRUNE, 1, ItemID.SOULRUNE, 1),
				sprite(SpriteID.MagicNecroOn.DEATH_CHARGE)),
			single("hasVengeance", "Vengeance", LUNAR, Map.of(ItemID.ASTRALRUNE, 4, ItemID.DEATHRUNE, 2, ItemID.EARTHRUNE, 10),
				sprite(SpriteID.LunarMagicOn.VENGEANCE)),
			single("hasSpellbookSwap", "Spellbook Swap", LUNAR, Spellbooks.SPELLBOOK_SWAP_RUNES, sprite(SpriteID.LunarMagicOn.SPELLBOOK_SWAP)),
			family("hasSurge", "Surge", STANDARD,
				spell("Wind Surge", SpriteID.Magicon2.WIND_SURGE, Map.of(ItemID.WRATHRUNE, 1, ItemID.AIRRUNE, 7), "wind", "air"),
				spell("Water Surge", SpriteID.Magicon2.WATER_SURGE, Map.of(ItemID.WRATHRUNE, 1, ItemID.AIRRUNE, 7, ItemID.WATERRUNE, 10), "water"),
				spell("Earth Surge", SpriteID.Magicon2.EARTH_SURGE, Map.of(ItemID.WRATHRUNE, 1, ItemID.AIRRUNE, 7, ItemID.EARTHRUNE, 10), "earth"),
				spell("Fire Surge", SpriteID.Magicon2.FIRE_SURGE, Map.of(ItemID.WRATHRUNE, 1, ItemID.AIRRUNE, 7, ItemID.FIRERUNE, 10), "fire")),
			// Unlike {hasFreeze}, a blighted sack doesn't count.
			family("hasBarrage", "Barrage", ANCIENT,
				spell("Smoke Barrage", SpriteID.Magicon2.SMOKE_BARRAGE,
					Map.of(ItemID.DEATHRUNE, 4, ItemID.BLOODRUNE, 2, ItemID.FIRERUNE, 4, ItemID.AIRRUNE, 4), "smoke"),
				spell("Shadow Barrage", SpriteID.Magicon2.SHADOW_BARRAGE,
					Map.of(ItemID.DEATHRUNE, 4, ItemID.BLOODRUNE, 2, ItemID.AIRRUNE, 4, ItemID.SOULRUNE, 1), "shadow"),
				spell("Blood Barrage", SpriteID.Magicon2.BLOOD_BARRAGE, Map.of(ItemID.DEATHRUNE, 4, ItemID.BLOODRUNE, 4, ItemID.SOULRUNE, 1), "blood"),
				spell("Ice Barrage", SpriteID.Magicon2.ICE_BARRAGE, Map.of(ItemID.DEATHRUNE, 4, ItemID.BLOODRUNE, 2, ItemID.WATERRUNE, 6), "ice"))
		);
	}

	public List<SpellVariable> all()
	{
		return all;
	}

	// One spell, its name text.
	private SpellVariable single(String name, String text, int spellbook, Map<Integer, Integer> runes, Function<Boolean, String> icon)
	{
		return new SpellVariable(name, text, spellbook, List.of(new Spell(List.of(), text, runes, icon)));
	}

	// Spells chosen by their words, text naming several.
	private SpellVariable family(String name, String text, int spellbook, Spell... spells)
	{
		return new SpellVariable(name, text, spellbook, List.of(spells));
	}

	private static Spell spell(String name, int spriteId, Map<Integer, Integer> runes, String... words)
	{
		return new Spell(List.of(words), name, runes, sprite(spriteId));
	}

	private static Function<Boolean, String> sprite(int spriteId)
	{
		return castable -> Icons.sprite(spriteId, castable);
	}

	private static Function<Boolean, String> item(int itemId)
	{
		return castable -> Icons.item(itemId, castable);
	}

	private static final class Spell
	{
		// The words choosing it in a family, e.g. "wind" and "air"; none for a single spell.
		private final List<String> words;
		// Rich Text name when it's the only one, e.g. "Wind Surge".
		private final String name;
		private final Map<Integer, Integer> runes;
		private final Function<Boolean, String> icon;
		// An item that must be carried or worn, or -1.
		private int needed = -1;
		// Carried in the Wilderness on the spellbook, it stands in for the runes, or -1.
		private int sack = -1;

		private Spell(List<String> words, String name, Map<Integer, Integer> runes, Function<Boolean, String> icon)
		{
			this.words = words;
			this.name = name;
			this.runes = runes;
			this.icon = icon;
		}
	}

	public final class SpellVariable implements LabelVariable, PartyMemberVariable
	{
		// Rich Text name for several, e.g. "Surge".
		private final String text;
		private final int spellbook;
		// In {split} order.
		private final List<Spell> spells;
		private final Args args;

		private SpellVariable(String name, String text, int spellbook, List<Spell> spells)
		{
			this.text = text;
			this.spellbook = spellbook;
			this.spells = spells;
			args = Args.of(name);
			for (Spell spell : spells)
			{
				spell.words.forEach(word -> args.iconFlag(word, w -> "{" + name + " " + w + " icon}"));
				if (spell.words.size() > 1)
				{
					args.exclusive(spell.words.toArray(new String[0]));
				}
			}
			args.flag("icon");
		}

		private SpellVariable alias(String name)
		{
			args.alias(name);
			return this;
		}

		// For a single spell.
		private SpellVariable needing(int itemId)
		{
			spells.get(0).needed = itemId;
			return this;
		}

		private SpellVariable orWildernessSack(int itemId)
		{
			spells.get(0).sack = itemId;
			return this;
		}

		@Override
		public Pattern pattern()
		{
			return args.pattern();
		}

		@Override
		public Args args()
		{
			return args;
		}

		@Override
		public String resolvePlain(Matcher matcher)
		{
			return String.valueOf(Boolean.TRUE.equals(any(chosen(matcher), this::castable)));
		}

		@Override
		public String resolveRich(Matcher matcher)
		{
			return rich(matcher, this::castable);
		}

		@Override
		public String resolvePlain(Matcher matcher, PartyMember member)
		{
			return String.valueOf(!Boolean.FALSE.equals(any(chosen(matcher), memberCastable(member))));
		}

		@Override
		public String resolveRich(Matcher matcher, PartyMember member)
		{
			return rich(matcher, memberCastable(member));
		}

		@Override
		public String resolveRowRich(Matcher matcher, RichText richText)
		{
			return resolveRich(matcher);
		}

		// One's name, or text for several, colored by castable; or with icon, their sprites.
		private String rich(Matcher matcher, Function<Spell, Boolean> castable)
		{
			List<Spell> chosen = chosen(matcher);
			if (args.options(matcher).flag("icon"))
			{
				return icon(chosen, castable);
			}
			String name = chosen.size() == 1 ? chosen.get(0).name : text;
			return richText.booleanColored(!Boolean.FALSE.equals(any(chosen, castable)), name);
		}

		// The spells named, in the order named, or all of them.
		private List<Spell> chosen(Matcher matcher)
		{
			List<Spell> chosen = new ArrayList<>();
			for (String word : args.options(matcher).words())
			{
				for (Spell spell : spells)
				{
					if (spell.words.stream().anyMatch(word::equalsIgnoreCase) && !chosen.contains(spell))
					{
						chosen.add(spell);
					}
				}
			}
			return chosen.isEmpty() ? spells : chosen;
		}

		private boolean castable(Spell spell)
		{
			boolean hasNeeded = spell.needed < 0 || carried(spell.needed, InventoryID.INVENTORY) || carried(spell.needed, InventoryID.EQUIPMENT);
			if (hasNeeded && Spellbooks.canCast(client, spellbook, spell.runes))
			{
				return true;
			}
			return spell.sack >= 0 && carried(spell.sack, InventoryID.INVENTORY) && client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) != 0
				&& Spellbooks.canCast(client, spellbook, Map.of());
		}

		// Null when everything checks out but their spellbook is unknown.
		private Function<Spell, Boolean> memberCastable(PartyMember member)
		{
			String spellbookName = partyDataSharing.value(member.getMemberId(), PartyValue.SPELLBOOK);
			return spell ->
			{
				if (spell.needed >= 0 && RuneCounter.itemCount(member.getInv(), spell.needed) <= 0
					&& RuneCounter.itemCount(member.getWorn(), spell.needed) <= 0)
				{
					return false;
				}
				return Spellbooks.canCast(member, spellbookName, spellbook, spell.runes);
			};
		}
	}

	private boolean carried(int itemId, InventoryID container)
	{
		return RuneCounter.itemCount(client, container, itemId) > 0;
	}

	// One's icon, or a {split} of several.
	private static String icon(List<Spell> chosen, Function<Spell, Boolean> castable)
	{
		if (chosen.size() == 1)
		{
			return chosen.get(0).icon.apply(castable.apply(chosen.get(0)));
		}
		StringBuilder icon = new StringBuilder("{split}");
		for (Spell spell : chosen)
		{
			icon.append(spell.icon.apply(castable.apply(spell)));
		}
		return icon.append("{/split}").toString();
	}

	// True if any is castable, otherwise null if any might be (spellbook unknown), otherwise false.
	@Nullable
	private static Boolean any(List<Spell> chosen, Function<Spell, Boolean> castable)
	{
		Boolean result = false;
		for (Spell spell : chosen)
		{
			Boolean value = castable.apply(spell);
			if (Boolean.TRUE.equals(value))
			{
				return true;
			}
			if (value == null)
			{
				result = null;
			}
		}
		return result;
	}
}
