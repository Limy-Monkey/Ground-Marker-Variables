package com.groundmarkervariables.variables;

import java.util.Collection;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.runelite.client.party.PartyMember;

// A one-argument LabelVariable defined inline (see ArgVariables): {name <arg>}, arg trimmed and ending before any of
// {}?:<>=!, (so a trailing comparator isn't part of it). Plain is value(arg)'s text, unavailable while null; Rich is
// rich(arg, Plain), which may itself be null.
public class ArgVariable implements LabelVariable {
	private final String name;
	private Pattern pattern;
	private final boolean numeric;
	private final Function<String, Object> value;
	private final BiFunction<String, String, String> rich;
	// Autocomplete's suggestions for arg, if any (see suggest()).
	@Nullable
	private Args args;
	private boolean joined;

	// name: e.g. "kc". numeric: usable in MathVariable.
	public ArgVariable(String name, boolean numeric, Function<String, Object> value, BiFunction<String, String, String> rich) {
		this(name, Pattern.compile("\\{" + name + "\\s+([^{}?:<>=!,]+?)\\s*\\}", Pattern.CASE_INSENSITIVE), numeric, value, rich, null, false);
	}

	private ArgVariable(String name, Pattern pattern, boolean numeric, Function<String, Object> value, BiFunction<String, String, String> rich,
		@Nullable Args args, boolean joined) {
		this.name = name;
		this.pattern = pattern;
		this.numeric = numeric;
		this.value = value;
		this.rich = rich;
		this.args = args;
		this.joined = joined;
	}

	public boolean numeric() {
		return numeric;
	}

	// arg is one word right after the name, e.g. {lvl_attack}.
	public ArgVariable joined() {
		joined = true;
		pattern = Pattern.compile("\\{" + name + "(\\w+?)\\}", Pattern.CASE_INSENSITIVE);
		if (args != null) {
			args.joined();
		}
		return this;
	}

	// arg may be a comma-separated list, e.g. {hasItem salve, blowpipe}, each part suggested as arg is (after suggest()).
	public ArgVariable listed() {
		pattern = Pattern.compile("\\{" + name + "\\s+([^{}?:<>=!]+?)\\s*\\}", Pattern.CASE_INSENSITIVE);
		args.extras(main -> args.mainNames(), args::mainIcon);
		return this;
	}

	// Autocomplete suggests arg from names, each with icon's syntax if any.
	public ArgVariable suggest(Supplier<Collection<String>> names, @Nullable Function<String, String> icon) {
		args = Args.of(name).main(names, icon == null ? value -> null : icon);
		if (joined) {
			args.joined();
		}
		return this;
	}

	@Nullable
	@Override
	public Args args() {
		return args;
	}

	// Also in {party} rows: member's value and Rich Text there, and row(arg, Plain) for our own row.
	public ArgVariable party(BiFunction<String, PartyMember, Object> member, BiFunction<String, PartyMember, String> memberRich,
		BiFunction<String, String, String> row) {
		return new Party(this, member, memberRich, row);
	}

	@Override
	public Pattern pattern() {
		return pattern;
	}

	@Override
	public String resolvePlain(Matcher matcher) {
		String arg = arg(matcher);
		return arg == null ? null : text(value.apply(arg));
	}

	@Override
	public String resolveRich(Matcher matcher) {
		String plain = resolvePlain(matcher);
		return plain == null ? null : rich.apply(arg(matcher), plain);
	}

	@Nullable
	private static String arg(Matcher matcher) {
		String arg = matcher.group(1).trim();
		return arg.isEmpty() ? null : arg;
	}

	@Nullable
	private static String text(@Nullable Object value) {
		return value == null ? null : String.valueOf(value);
	}

	private static final class Party extends ArgVariable implements PartyMemberVariable {
		private final BiFunction<String, PartyMember, Object> member;
		private final BiFunction<String, PartyMember, String> memberRich;
		private final BiFunction<String, String, String> row;

		private Party(ArgVariable from, BiFunction<String, PartyMember, Object> member, BiFunction<String, PartyMember, String> memberRich,
			BiFunction<String, String, String> row) {
			super(from.name, from.pattern, from.numeric, from.value, from.rich, from.args, from.joined);
			this.member = member;
			this.memberRich = memberRich;
			this.row = row;
		}

		@Override
		public String resolvePlain(Matcher matcher, PartyMember member) {
			String arg = arg(matcher);
			return arg == null ? null : text(this.member.apply(arg, member));
		}

		@Override
		public String resolveRich(Matcher matcher, PartyMember member) {
			String arg = arg(matcher);
			return arg == null ? null : memberRich.apply(arg, member);
		}

		@Override
		public String resolveRowRich(Matcher matcher, RichText richText) {
			String plain = resolvePlain(matcher);
			return plain == null ? null : row.apply(arg(matcher), plain);
		}
	}
}
