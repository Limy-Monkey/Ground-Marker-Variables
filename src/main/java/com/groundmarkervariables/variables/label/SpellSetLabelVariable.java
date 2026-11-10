package com.groundmarkervariables.variables.label;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import com.groundmarkervariables.party.PartyDataSharing;
import com.groundmarkervariables.party.PartyValue;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.Icons;
import com.groundmarkervariables.variables.support.Spellbooks;
import net.runelite.api.Client;
import net.runelite.client.party.PartyMember;

// A family of spells, e.g. {hasSurge [<element> ...] [icon]}: true if any of the named ones (all by default) is
// castable. Doesn't check Magic level. The icon is one's sprite, or a {split} of several, each dimmed if not castable.
public abstract class SpellSetLabelVariable extends SpellLabelVariable
{
	protected static final class Spell
	{
		private final String key;
		private final String name;
		private final int spriteId;
		private final Map<Integer, Integer> runes;
		// Other words for it, e.g. "air" for wind.
		private final List<String> aliases;

		protected Spell(String key, String name, int spriteId, Map<Integer, Integer> runes, String... aliases)
		{
			this.key = key;
			this.name = name;
			this.spriteId = spriteId;
			this.runes = runes;
			this.aliases = List.of(aliases);
		}

		private boolean named(String word)
		{
			return key.equals(word) || aliases.contains(word);
		}
	}

	private static final String ICON = "icon";

	private final Client client;
	private final RichText richText;
	private final PartyDataSharing partyDataSharing;
	private final Pattern pattern;
	private final String text;
	private final int spellbook;
	// In {split} order.
	private final List<Spell> spells;

	protected SpellSetLabelVariable(Client client, RichText richText, PartyDataSharing partyDataSharing, String variable, String text,
		int spellbook, List<Spell> spells)
	{
		super(richText, partyDataSharing);
		this.client = client;
		this.richText = richText;
		this.partyDataSharing = partyDataSharing;
		this.text = text;
		this.spellbook = spellbook;
		this.spells = spells;
		String keys = spells.stream().flatMap(spell -> Stream.concat(Stream.of(spell.key), spell.aliases.stream()))
			.collect(Collectors.joining("|"));
		this.pattern = Pattern.compile("\\{" + variable + "((?:\\s+(?:" + ICON + "|" + keys + "))*)\\s*\\}", Pattern.CASE_INSENSITIVE);
	}

	@Override
	public Pattern pattern()
	{
		return pattern;
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

	// SpellLabelVariable's own entry points, for all of them.
	@Override
	protected boolean castable()
	{
		return Boolean.TRUE.equals(any(spells, this::castable));
	}

	@Override
	protected Boolean castable(PartyMember member, @Nullable String spellbookName)
	{
		return any(spells, spell -> Spellbooks.canCast(member, spellbookName, spellbook, spell.runes));
	}

	@Override
	protected String text()
	{
		return text;
	}

	@Override
	protected String icon(@Nullable Boolean castable)
	{
		return icon(spells, spell -> castable);
	}

	// One's name, or the family's for several, colored by castable; or with icon, their sprites.
	private String rich(Matcher matcher, Function<Spell, Boolean> castable)
	{
		List<Spell> chosen = chosen(matcher);
		if (isIcon(matcher))
		{
			return icon(chosen, castable);
		}
		String name = chosen.size() == 1 ? chosen.get(0).name : text;
		return richText.booleanColored(!Boolean.FALSE.equals(any(chosen, castable)), name);
	}

	private static String icon(List<Spell> chosen, Function<Spell, Boolean> castable)
	{
		if (chosen.size() == 1)
		{
			return Icons.sprite(chosen.get(0).spriteId, castable.apply(chosen.get(0)));
		}

		StringBuilder icon = new StringBuilder("{split}");
		for (Spell spell : chosen)
		{
			icon.append(Icons.sprite(spell.spriteId, castable.apply(spell)));
		}
		return icon.append("{/split}").toString();
	}

	// The spells named, in the order named, or all of them in {split} order.
	private List<Spell> chosen(Matcher matcher)
	{
		List<Spell> chosen = new ArrayList<>();
		for (String word : matcher.group(1).trim().toLowerCase(Locale.ROOT).split("\\s+"))
		{
			for (Spell spell : spells)
			{
				if (spell.named(word) && !chosen.contains(spell))
				{
					chosen.add(spell);
				}
			}
		}
		return chosen.isEmpty() ? spells : chosen;
	}

	private static boolean isIcon(Matcher matcher)
	{
		return matcher.group(1).toLowerCase(Locale.ROOT).matches("(?s).*\\b" + ICON + "\\b.*");
	}

	private Boolean castable(Spell spell)
	{
		return Spellbooks.canCast(client, spellbook, spell.runes);
	}

	private Function<Spell, Boolean> memberCastable(PartyMember member)
	{
		String spellbookName = partyDataSharing.value(member.getMemberId(), PartyValue.SPELLBOOK);
		return spell -> Spellbooks.canCast(member, spellbookName, spellbook, spell.runes);
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
