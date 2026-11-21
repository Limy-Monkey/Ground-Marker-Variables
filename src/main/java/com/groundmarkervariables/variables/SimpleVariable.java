package com.groundmarkervariables.variables;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.runelite.client.party.PartyMember;

// A no-argument LabelVariable defined inline (see SimpleVariables). Plain is value's text, unavailable while null;
// Rich is rich applied to Plain, or with icon(), "{name icon}" is the icon instead.
public class SimpleVariable implements LabelVariable
{
	private final String names;
	private final Pattern pattern;
	private final boolean numeric;
	private final Supplier<Object> value;
	private final UnaryOperator<String> rich;
	@Nullable
	private final Supplier<String> icon;

	// names: a regex alternation, e.g. "autoRetal(?:iate)?" or "a|b". numeric: usable in MathVariable.
	public SimpleVariable(String names, boolean numeric, Supplier<Object> value, UnaryOperator<String> rich)
	{
		this(names, numeric, value, rich, null);
	}

	private SimpleVariable(String names, boolean numeric, Supplier<Object> value, UnaryOperator<String> rich,
		@Nullable Supplier<String> icon)
	{
		this.names = names;
		this.pattern = Pattern.compile("\\{(?:" + names + ")" + (icon == null ? "" : "(\\s+icon)?") + "\\}", Pattern.CASE_INSENSITIVE);
		this.numeric = numeric;
		this.value = value;
		this.rich = rich;
		this.icon = icon;
	}

	public boolean numeric()
	{
		return numeric;
	}

	// Adds "{name icon}", whose Rich Text is icon's. Before party().
	public SimpleVariable icon(Supplier<String> icon)
	{
		return new SimpleVariable(names, numeric, value, rich, icon);
	}

	// Also in {party} rows: member's value, styled by row there and in our own row.
	public SimpleVariable party(Function<PartyMember, Object> member, UnaryOperator<String> row)
	{
		return party(member, row, m -> null);
	}

	// As party(member, row), with memberIcon as a member's "{name icon}".
	public SimpleVariable party(Function<PartyMember, Object> member, UnaryOperator<String> row, Function<PartyMember, String> memberIcon)
	{
		return new Party(this, member, row, memberIcon);
	}

	@Override
	public Pattern pattern()
	{
		return pattern;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		return text(value.get());
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		return iconRequested(matcher) ? icon.get() : styled(resolvePlain(matcher), rich);
	}

	private boolean iconRequested(Matcher matcher)
	{
		return icon != null && matcher.group(1) != null;
	}

	@Nullable
	private static String text(@Nullable Object value)
	{
		return value == null ? null : String.valueOf(value);
	}

	@Nullable
	private static String styled(@Nullable String plain, UnaryOperator<String> style)
	{
		return plain == null ? null : style.apply(plain);
	}

	private static final class Party extends SimpleVariable implements PartyMemberVariable
	{
		private final Function<PartyMember, Object> member;
		private final UnaryOperator<String> row;
		private final Function<PartyMember, String> memberIcon;

		private Party(SimpleVariable from, Function<PartyMember, Object> member, UnaryOperator<String> row,
			Function<PartyMember, String> memberIcon)
		{
			super(from.names, from.numeric, from.value, from.rich, from.icon);
			this.member = member;
			this.row = row;
			this.memberIcon = memberIcon;
		}

		@Override
		public String resolvePlain(Matcher matcher, PartyMember member)
		{
			return text(this.member.apply(member));
		}

		@Override
		public String resolveRich(Matcher matcher, PartyMember member)
		{
			return super.iconRequested(matcher) ? memberIcon.apply(member) : styled(resolvePlain(matcher, member), row);
		}

		// Our own icon as it is outside a row.
		@Override
		public String resolveRowRich(Matcher matcher, RichText richText)
		{
			return super.iconRequested(matcher) ? resolveRich(matcher) : styled(resolvePlain(matcher), row);
		}
	}
}
