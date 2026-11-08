package com.groundmarkervariables.variables.label;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;

import com.groundmarkervariables.party.PartyDataSharing;
import com.groundmarkervariables.party.PartyValue;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.Icons;
import com.groundmarkervariables.variables.support.Spellbooks;
import net.runelite.api.Client;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.party.PartyMember;

// {hasSurge [<element>]} — true if the player could cast any elemental surge (or <element>'s) right now:
// Standard spellbook (or Spellbook Swap) with its runes. Doesn't check Magic level. "icon" before or after
// <element>; without one it's a {split} of all four, each dimmed if that one can't be cast.
public class HasSurgeLabelVariable extends SpellLabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{hasSurge(\\s+icon)?(?:\\s+(wind|water|earth|fire))?(\\s+icon)?\\}",
		Pattern.CASE_INSENSITIVE);

	// {split} quadrant order.
	private enum Surge
	{
		WIND("Wind Surge", SpriteID.Magicon2.WIND_SURGE, Map.of(ItemID.WRATHRUNE, 1, ItemID.AIRRUNE, 7)),
		WATER("Water Surge", SpriteID.Magicon2.WATER_SURGE, Map.of(ItemID.WRATHRUNE, 1, ItemID.AIRRUNE, 7, ItemID.WATERRUNE, 10)),
		EARTH("Earth Surge", SpriteID.Magicon2.EARTH_SURGE, Map.of(ItemID.WRATHRUNE, 1, ItemID.AIRRUNE, 7, ItemID.EARTHRUNE, 10)),
		FIRE("Fire Surge", SpriteID.Magicon2.FIRE_SURGE, Map.of(ItemID.WRATHRUNE, 1, ItemID.AIRRUNE, 7, ItemID.FIRERUNE, 10));

		private final String name;
		private final int spriteId;
		private final Map<Integer, Integer> runes;

		Surge(String name, int spriteId, Map<Integer, Integer> runes)
		{
			this.name = name;
			this.spriteId = spriteId;
			this.runes = runes;
		}
	}

	private final Client client;
	private final RichText richText;
	private final PartyDataSharing partyDataSharing;

	@Inject
	private HasSurgeLabelVariable(Client client, RichText richText, PartyDataSharing partyDataSharing)
	{
		super(richText, partyDataSharing);
		this.client = client;
		this.richText = richText;
		this.partyDataSharing = partyDataSharing;
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		return String.valueOf(Boolean.TRUE.equals(any(surges(matcher), this::castable)));
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		return rich(matcher, this::castable);
	}

	@Override
	public String resolvePlain(Matcher matcher, PartyMember member)
	{
		return String.valueOf(!Boolean.FALSE.equals(any(surges(matcher), memberCastable(member))));
	}

	@Override
	public String resolveRich(Matcher matcher, PartyMember member)
	{
		return rich(matcher, memberCastable(member));
	}

	// The base class's own entry points, for all four surges.
	@Override
	protected boolean castable()
	{
		return Boolean.TRUE.equals(any(List.of(Surge.values()), this::castable));
	}

	@Override
	protected Boolean castable(PartyMember member, @Nullable String spellbook)
	{
		return any(List.of(Surge.values()), surge -> Spellbooks.canCast(member, spellbook, Spellbooks.STANDARD, surge.runes));
	}

	@Override
	protected String text()
	{
		return "Surge";
	}

	@Override
	protected String icon(@Nullable Boolean castable)
	{
		return splitIcon(surge -> castable);
	}

	// An element's own sprite, or the {split} of all four; otherwise its name colored by castable.
	private String rich(Matcher matcher, Function<Surge, Boolean> castable)
	{
		List<Surge> surges = surges(matcher);
		boolean icon = matcher.group(1) != null || matcher.group(3) != null;
		if (surges.size() == 1)
		{
			Surge surge = surges.get(0);
			return icon ? Icons.sprite(surge.spriteId, castable.apply(surge)) : richText.booleanColored(!Boolean.FALSE.equals(castable.apply(surge)), surge.name);
		}
		return icon ? splitIcon(castable) : richText.booleanColored(!Boolean.FALSE.equals(any(surges, castable)), text());
	}

	private static List<Surge> surges(Matcher matcher)
	{
		return matcher.group(2) == null ? List.of(Surge.values()) : List.of(Surge.valueOf(matcher.group(2).toUpperCase(Locale.ROOT)));
	}

	private Boolean castable(Surge surge)
	{
		return Spellbooks.canCast(client, Spellbooks.STANDARD, surge.runes);
	}

	private Function<Surge, Boolean> memberCastable(PartyMember member)
	{
		String spellbook = partyDataSharing.value(member.getMemberId(), PartyValue.SPELLBOOK);
		return surge -> Spellbooks.canCast(member, spellbook, Spellbooks.STANDARD, surge.runes);
	}

	// True if any is castable, otherwise null if any might be (spellbook unknown), otherwise false.
	@Nullable
	private static Boolean any(List<Surge> surges, Function<Surge, Boolean> castable)
	{
		Boolean result = false;
		for (Surge surge : surges)
		{
			Boolean value = castable.apply(surge);
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

	private static String splitIcon(Function<Surge, Boolean> castable)
	{
		StringBuilder icon = new StringBuilder("{split}");
		for (Surge surge : Surge.values())
		{
			icon.append(Icons.sprite(surge.spriteId, castable.apply(surge)));
		}
		return icon.append("{/split}").toString();
	}
}
