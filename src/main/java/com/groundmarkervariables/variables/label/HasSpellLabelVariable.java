package com.groundmarkervariables.variables.label;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;

import com.groundmarkervariables.party.PartyDataSharing;
import com.groundmarkervariables.party.PartyValue;
import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.PartyMemberVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.Icons;
import com.groundmarkervariables.variables.support.RuneCounter;
import com.groundmarkervariables.variables.support.Spellbooks;
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

// {has<Spell>[ icon]}: true if the player could cast that spell right now, on its spellbook (or Spellbook Swap) with
// its runes (RuneCounter: inventory, rune pouch, infinite sources, combo runes). Doesn't check Magic level, quests or
// cooldowns. For a party member it uses their core inventory/gear and shared {spellbook}: with no spellbook yet it's
// true if the rest checks out, and the icon is drawn at 75%. Their Wilderness status isn't known, so sacks don't count.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class HasSpellLabelVariable implements LabelVariable, PartyMemberVariable
{
	private static final List<Spell> SPELLS = List.of(
		hasSpell("hasAlchs", "Alchs", STANDARD, Map.of(ItemID.NATURERUNE, 1, ItemID.FIRERUNE, 1), sprite(SpriteID.Magicon.HIGH_LEVEL_ALCHEMY)),
		hasSpell("hasEntangle", "Entangles", STANDARD, Map.of(ItemID.EARTHRUNE, 5, ItemID.WATERRUNE, 5, ItemID.NATURERUNE, 4),
			sprite(SpriteID.Magicon2.ENTANGLE)).orWildernessSack(ItemID.BLIGHTED_SACK_ENTANGLE),
		hasSpell("hasFreeze", "Freezes", ANCIENT, Map.of(ItemID.WATERRUNE, 6, ItemID.BLOODRUNE, 2, ItemID.DEATHRUNE, 4),
			sprite(SpriteID.Magicon2.ICE_BARRAGE)).orWildernessSack(ItemID.BLIGHTED_SACK_ICEBARRAGE).alias("hasFreezes"),
		hasSpell("hasThralls", "Thralls", ARCEUUS, Map.of(ItemID.FIRERUNE, 1, ItemID.BLOODRUNE, 1, ItemID.COSMICRUNE, 1),
			item(ItemID.BOOK_OF_THE_DEAD)).needing(ItemID.BOOK_OF_THE_DEAD),
		hasSpell("hasVileVigour", "Vile Vigour", ARCEUUS, Map.of(ItemID.SOULRUNE, 1, ItemID.AIRRUNE, 3), sprite(SpriteID.MagicNecroOn.VILE_VIGOUR)),
		hasSpell("hasUndeadGrasp", "Undead Grasp", ARCEUUS, Map.of(ItemID.BLOODRUNE, 1, ItemID.FIRERUNE, 12),
			sprite(SpriteID.MagicNecroOn.UNDEAD_GRASP)),
		hasSpell("hasMarkOfDarkness", "Mark of Darkness", ARCEUUS, Map.of(ItemID.COSMICRUNE, 1, ItemID.SOULRUNE, 1),
			sprite(SpriteID.MagicNecroOn.MARK_OF_DARKNESS)),
		hasSpell("hasDeathCharge", "Death Charge", ARCEUUS, Map.of(ItemID.BLOODRUNE, 1, ItemID.DEATHRUNE, 1, ItemID.SOULRUNE, 1),
			sprite(SpriteID.MagicNecroOn.DEATH_CHARGE)),
		hasSpell("hasVengeance", "Vengeance", LUNAR, Map.of(ItemID.ASTRALRUNE, 4, ItemID.DEATHRUNE, 2, ItemID.EARTHRUNE, 10),
			sprite(SpriteID.LunarMagicOn.VENGEANCE)),
		hasSpell("hasSpellbookSwap", "Spellbook Swap", LUNAR, Spellbooks.SPELLBOOK_SWAP_RUNES, sprite(SpriteID.LunarMagicOn.SPELLBOOK_SWAP)));

	private static final Map<String, Spell> BY_NAME = new HashMap<>();
	private static final Pattern PATTERN;

	static
	{
		for (Spell spell : SPELLS)
		{
			spell.names.forEach(name -> BY_NAME.put(name.toLowerCase(Locale.ROOT), spell));
		}
		String names = SPELLS.stream().flatMap(spell -> spell.names.stream()).collect(Collectors.joining("|"));
		PATTERN = Pattern.compile("\\{(" + names + ")(\\s+icon)?\\}", Pattern.CASE_INSENSITIVE);
	}

	private static final class Spell
	{
		private final List<String> names;
		// Rich Text name, e.g. "Freezes".
		private final String text;
		private final int spellbook;
		private final Map<Integer, Integer> runes;
		private final Function<Boolean, String> icon;
		// An item that must be carried or worn, or -1.
		private int needed = -1;
		// Carried in the Wilderness on the spellbook, it stands in for the runes, or -1.
		private int sack = -1;

		private Spell(String name, String text, int spellbook, Map<Integer, Integer> runes, Function<Boolean, String> icon)
		{
			this.names = new ArrayList<>(List.of(name));
			this.text = text;
			this.spellbook = spellbook;
			this.runes = runes;
			this.icon = icon;
		}

		private Spell alias(String name)
		{
			names.add(name);
			return this;
		}

		private Spell needing(int itemId)
		{
			needed = itemId;
			return this;
		}

		private Spell orWildernessSack(int itemId)
		{
			sack = itemId;
			return this;
		}
	}

	private static Spell hasSpell(String name, String text, int spellbook, Map<Integer, Integer> runes, Function<Boolean, String> icon)
	{
		return new Spell(name, text, spellbook, runes, icon);
	}

	private static Function<Boolean, String> sprite(int spriteId)
	{
		return castable -> Icons.sprite(spriteId, castable);
	}

	private static Function<Boolean, String> item(int itemId)
	{
		return castable -> Icons.item(itemId, castable);
	}

	private final Client client;
	private final RichText richText;
	private final PartyDataSharing partyDataSharing;

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		return String.valueOf(castable(spell(matcher)));
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		Spell spell = spell(matcher);
		boolean castable = castable(spell);
		return matcher.group(2) != null ? spell.icon.apply(castable) : richText.booleanColored(castable, spell.text);
	}

	@Override
	public String resolvePlain(Matcher matcher, PartyMember member)
	{
		return String.valueOf(!Boolean.FALSE.equals(castable(spell(matcher), member)));
	}

	@Override
	public String resolveRich(Matcher matcher, PartyMember member)
	{
		Spell spell = spell(matcher);
		Boolean castable = castable(spell, member);
		return matcher.group(2) != null ? spell.icon.apply(castable) : richText.booleanColored(!Boolean.FALSE.equals(castable), spell.text);
	}

	@Override
	public String resolveRowRich(Matcher matcher, RichText richText)
	{
		return resolveRich(matcher);
	}

	private static Spell spell(Matcher matcher)
	{
		return BY_NAME.get(matcher.group(1).toLowerCase(Locale.ROOT));
	}

	private boolean castable(Spell spell)
	{
		boolean hasNeeded = spell.needed < 0 || carried(spell.needed, InventoryID.INVENTORY) || carried(spell.needed, InventoryID.EQUIPMENT);
		if (hasNeeded && Spellbooks.canCast(client, spell.spellbook, spell.runes))
		{
			return true;
		}
		return spell.sack >= 0 && carried(spell.sack, InventoryID.INVENTORY) && client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) != 0
			&& Spellbooks.canCast(client, spell.spellbook, Map.of());
	}

	// Null when everything checks out but their spellbook is unknown.
	@Nullable
	private Boolean castable(Spell spell, PartyMember member)
	{
		if (spell.needed >= 0 && RuneCounter.itemCount(member.getInv(), spell.needed) <= 0 && RuneCounter.itemCount(member.getWorn(), spell.needed) <= 0)
		{
			return false;
		}
		return Spellbooks.canCast(member, partyDataSharing.value(member.getMemberId(), PartyValue.SPELLBOOK), spell.spellbook, spell.runes);
	}

	private boolean carried(int itemId, InventoryID container)
	{
		return RuneCounter.itemCount(client, container, itemId) > 0;
	}
}
