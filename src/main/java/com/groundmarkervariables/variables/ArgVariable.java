package com.groundmarkervariables.variables;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.runelite.client.party.PartyMember;

// A one-argument LabelVariable defined inline (see ArgVariables): {name <arg>}, arg trimmed and ending before any of
// {}?:<>=!, (so a trailing comparator isn't part of it). Plain is value(arg)'s text, unavailable while null; Rich is
// rich(arg, Plain), which may itself be null.
public class ArgVariable implements LabelVariable
{
	private final Pattern pattern;
	private final boolean numeric;
	private final Function<String, Object> value;
	private final BiFunction<String, String, String> rich;

	// names: a regex alternation, e.g. "kc" or "a|b". numeric: usable in MathVariable.
	public ArgVariable(String names, boolean numeric, Function<String, Object> value, BiFunction<String, String, String> rich)
	{
		this(Pattern.compile("\\{(?:" + names + ")\\s+([^{}?:<>=!,]+?)\\s*\\}", Pattern.CASE_INSENSITIVE), numeric, value, rich);
	}

	private ArgVariable(Pattern pattern, boolean numeric, Function<String, Object> value, BiFunction<String, String, String> rich)
	{
		this.pattern = pattern;
		this.numeric = numeric;
		this.value = value;
		this.rich = rich;
	}

	public boolean numeric()
	{
		return numeric;
	}

	// Also in {party} rows: member's value and Rich Text there, and row(arg, Plain) for our own row.
	public ArgVariable party(BiFunction<String, PartyMember, Object> member, BiFunction<String, PartyMember, String> memberRich,
		BiFunction<String, String, String> row)
	{
		return new Party(this, member, memberRich, row);
	}

	@Override
	public Pattern pattern()
	{
		return pattern;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		String arg = arg(matcher);
		return arg == null ? null : text(value.apply(arg));
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		String plain = resolvePlain(matcher);
		return plain == null ? null : rich.apply(arg(matcher), plain);
	}

	@Nullable
	private static String arg(Matcher matcher)
	{
		String arg = matcher.group(1).trim();
		return arg.isEmpty() ? null : arg;
	}

	@Nullable
	private static String text(@Nullable Object value)
	{
		return value == null ? null : String.valueOf(value);
	}

	private static final class Party extends ArgVariable implements PartyMemberVariable
	{
		private final BiFunction<String, PartyMember, Object> member;
		private final BiFunction<String, PartyMember, String> memberRich;
		private final BiFunction<String, String, String> row;

		private Party(ArgVariable from, BiFunction<String, PartyMember, Object> member, BiFunction<String, PartyMember, String> memberRich,
			BiFunction<String, String, String> row)
		{
			super(from.pattern, from.numeric, from.value, from.rich);
			this.member = member;
			this.memberRich = memberRich;
			this.row = row;
		}

		@Override
		public String resolvePlain(Matcher matcher, PartyMember member)
		{
			String arg = arg(matcher);
			return arg == null ? null : text(this.member.apply(arg, member));
		}

		@Override
		public String resolveRich(Matcher matcher, PartyMember member)
		{
			String arg = arg(matcher);
			return arg == null ? null : memberRich.apply(arg, member);
		}

		@Override
		public String resolveRowRich(Matcher matcher, RichText richText)
		{
			String plain = resolvePlain(matcher);
			return plain == null ? null : row.apply(arg(matcher), plain);
		}
	}
}
