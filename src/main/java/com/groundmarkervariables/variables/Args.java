package com.groundmarkervariables.variables;

import com.groundmarkervariables.variables.support.BossAliases;
import com.groundmarkervariables.variables.support.DateLiteralParser;
import com.groundmarkervariables.variables.support.NamedColors;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.runelite.api.Skill;

// A variable's arguments, declared once: an optional main one, then options after commas or spaces -- flags
// ("missing"), numbers ("itemsPerLine=10") and values ("item=Abyssal whip") -- and any extras, free text after commas
// ({charges}'s stored items). Gives the variable's pattern and parsed options, and autocomplete what to suggest, with
// icons (see LabelCompleter#completeArgs).
public final class Args
{
	// Icon syntax for suggestions (see LabelCompleter#icon): a boss's hiscores icon, an item's.
	public static final String BOSS_ICON_PREFIX = "boss:";
	public static final Function<String, String> BOSS_ICON = name -> BOSS_ICON_PREFIX + BossAliases.resolve(name);
	public static final Function<String, String> ITEM_ICON = name -> "<item=" + name + ">";
	// An NPC by name: its chathead if it has one, else the NPC.
	public static final String NPC_ICON_PREFIX = "npc:";
	public static final Function<String, String> NPC_ICON = name -> NPC_ICON_PREFIX + name;
	// Between an icon syntax and its fallback (see firstOf).
	public static final String ICON_FALLBACK = "\n";
	// A named color's swatch, "color:RRGGBB"; none if not a color (e.g. an opacity name).
	public static final String COLOR_ICON_PREFIX = "color:";
	public static final Function<String, String> COLOR_ICON = name ->
	{
		String hex = NamedColors.HEX_BY_NAME.get(name.toLowerCase(Locale.ROOT));
		return hex == null ? null : COLOR_ICON_PREFIX + hex;
	};
	// Every skill's name, lowercase, and its "<name> (Skill)" sprite (none if not a skill).
	public static final Supplier<Collection<String>> SKILLS = () -> Arrays.stream(Skill.values())
		.map(skill -> skill.getName().toLowerCase(Locale.ROOT)).collect(Collectors.toList());
	public static final Function<String, String> SKILL_ICON = name -> skill(name) == null ? null : "<sprite=" + name + " (Skill)>";
	public static final Function<String, String> NO_ICON = name -> null;
	// A main argument, value or extra: ends before any of {}?:<>=!,|& so a trailing comparator or || / && isn't part of it.
	private static final String TEXT = "[^{}?:<>=!,|&]+?";
	// A date value's "-N days" / "N days" (see Options#date).
	private static final Pattern DAYS = Pattern.compile("-?\\s*(\\d+)\\s*days?", Pattern.CASE_INSENSITIVE);

	private final String name;
	// Other names it's written as, accepted but not suggested.
	private final List<String> aliases = new ArrayList<>();
	private String catalogName;
	@Nullable
	private Supplier<Collection<String>> mainNames;
	private Function<String, String> mainIcon = NO_ICON;
	private boolean mainOptional;
	private boolean joined;
	// By its word as typed, lowercase ("missing", "itemsperline=").
	private final Map<String, Option> options = new LinkedHashMap<>();
	// Groups of lowercase words of which only one is suggested.
	private final List<Set<String>> exclusive = new ArrayList<>();
	@Nullable
	private Function<String, Collection<String>> extras;
	private Function<String, String> extrasIcon = NO_ICON;
	private Pattern pattern;
	private Pattern trailingOptions;
	private Pattern valueTyped;
	private Pattern token;
	private Pattern optionOnly;

	private static final class Option
	{
		private final String word;
		private final String regex;
		// A value option's suggestions, by the main argument (null without one), and their icon.
		private final Function<String, Collection<String>> values;
		private final Function<String, String> icon;
		// Suggested only when this holds for the main argument, and only if listed.
		private final Predicate<String> when;
		private boolean listed = true;
		private boolean repeatable;

		private Option(String word, String regex, Function<String, Collection<String>> values, Function<String, String> icon,
			Predicate<String> when)
		{
			this.word = word;
			this.regex = regex;
			this.values = values;
			this.icon = icon;
			this.when = when;
		}

		private boolean isValue()
		{
			return regex.endsWith(TEXT);
		}
	}

	private Args(String name)
	{
		this.name = name;
		this.catalogName = name;
		build();
	}

	public static Args of(String name)
	{
		return new Args(name);
	}

	// The first of icons that shows, e.g. a boss's hiscores icon, else the NPC's.
	@SafeVarargs
	public static Function<String, String> firstOf(Function<String, String>... icons)
	{
		return name -> Arrays.stream(icons).map(icon -> icon.apply(name)).filter(Objects::nonNull).collect(Collectors.joining(ICON_FALLBACK));
	}

	// The skill named, any case, e.g. "attack"; null if none.
	@Nullable
	public static Skill skill(@Nullable String name)
	{
		for (Skill skill : Skill.values())
		{
			if (skill.getName().equalsIgnoreCase(name))
			{
				return skill;
			}
		}
		return null;
	}

	// Also written as names, e.g. {hasFreezes} for {hasFreeze}.
	public Args alias(String... names)
	{
		aliases.addAll(List.of(names));
		return build();
	}

	// Its options described as variable's in variables.json, e.g. {lootSession} as {loot}.
	public Args describedAs(String variable)
	{
		catalogName = variable;
		return this;
	}

	// A required main argument, suggested from names, each with icon's syntax.
	public Args main(Supplier<Collection<String>> names, Function<String, String> icon)
	{
		mainNames = names;
		mainIcon = icon;
		return build();
	}

	// The main argument (and so the options) may be left out, e.g. bare {charges}.
	public Args optional()
	{
		mainOptional = true;
		return build();
	}

	// The main argument is one word right after the name, e.g. {lvl_attack}.
	public Args joined()
	{
		joined = true;
		return build();
	}

	public boolean isJoined()
	{
		return joined;
	}

	public Args flag(String... words)
	{
		for (String word : words)
		{
			flag(word, main -> true);
		}
		return this;
	}

	// A flag suggested only when the main argument passes when.
	public Args flag(String word, Predicate<String> when)
	{
		add(new Option(word, word, main -> List.of(), NO_ICON, when));
		return build();
	}

	// A flag suggested with icon's syntax.
	public Args iconFlag(String word, Function<String, String> icon)
	{
		add(new Option(word, word, main -> List.of(), icon, main -> true));
		return build();
	}

	public Args number(String... words)
	{
		for (String word : words)
		{
			add(new Option(word + "=", word + "=\\d+", main -> List.of(), NO_ICON, main -> true));
		}
		return build();
	}

	// word=<value>, suggested from values, each with icon's syntax if any.
	public Args value(String word, Supplier<Collection<String>> values, @Nullable Function<String, String> icon)
	{
		return value(word, main -> values.get(), icon);
	}

	// As value(word, values, icon), the suggestions given the main argument. Repeatable (see Options#values); spaces
	// around its "=" allowed.
	public Args value(String word, Function<String, Collection<String>> values, @Nullable Function<String, String> icon)
	{
		add(new Option(word + "=", word + "\\s*=\\s*" + TEXT, values, icon == null ? NO_ICON : icon, main -> true));
		return build();
	}

	// Still accepted, never suggested, e.g. a retired option.
	public Args unlisted(String... words)
	{
		named(words).forEach(option -> option.listed = false);
		return this;
	}

	// Still suggested once given, e.g. showItem=.
	public Args repeatable(String... words)
	{
		named(words).forEach(option -> option.repeatable = true);
		return this;
	}

	private Stream<Option> named(String... words)
	{
		return options.values().stream().filter(option -> Arrays.stream(words).anyMatch(option.word.replace("=", "")::equalsIgnoreCase));
	}

	// Once one of words is given, the others aren't suggested.
	public Args exclusive(String... words)
	{
		exclusive.add(Arrays.stream(words).map(word -> word.toLowerCase(Locale.ROOT)).collect(Collectors.toSet()));
		return this;
	}

	// Any other text after a comma, suggested from names (given the main argument), each with icon's syntax.
	public Args extras(Function<String, Collection<String>> names, Function<String, String> icon)
	{
		extras = names;
		extrasIcon = icon;
		return build();
	}

	public String name()
	{
		return name;
	}

	// Its name, then its aliases.
	public List<String> names()
	{
		List<String> names = new ArrayList<>(List.of(name));
		names.addAll(aliases);
		return names;
	}

	public String catalogName()
	{
		return catalogName;
	}

	public boolean hasMain()
	{
		return mainNames != null;
	}

	public boolean hasExtras()
	{
		return extras != null;
	}

	// Group 1 is the main argument if any, the next the options.
	public Pattern pattern()
	{
		return pattern;
	}

	public Collection<String> mainNames()
	{
		return mainNames == null ? List.of() : mainNames.get();
	}

	@Nullable
	public String mainIcon(String main)
	{
		return mainIcon.apply(main);
	}

	// Each listed option as typed to complete it, e.g. "missing", "itemsPerLine=".
	public List<String> words()
	{
		return options.values().stream().filter(option -> option.listed).map(option -> option.word).collect(Collectors.toList());
	}

	// The words (and extras) still to suggest after the options given (as typed), for main: listed and available, not
	// given, nor exclusive with one given.
	public List<String> unusedWords(String given, @Nullable String main)
	{
		Set<String> words = new HashSet<>();
		for (String token : given.toLowerCase(Locale.ROOT).replaceAll("\\s*=\\s*", "=").split("[\\s,]+"))
		{
			words.add(token.contains("=") ? token.substring(0, token.indexOf('=') + 1) : token);
		}
		List<String> unused = options.values().stream()
			.filter(option -> option.listed && option.when.test(main) && (option.repeatable || !words.contains(option.word.toLowerCase(Locale.ROOT))))
			.map(option -> option.word)
			.filter(word -> exclusive.stream().noneMatch(group -> group.contains(word.toLowerCase(Locale.ROOT))
				&& group.stream().anyMatch(words::contains)))
			.collect(Collectors.toList());
		if (extras != null && main != null)
		{
			Set<String> texts = Arrays.stream(given.split(",")).map(text -> text.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
			extras.apply(main).stream().filter(extra -> !texts.contains(extra.toLowerCase(Locale.ROOT))).forEach(unused::add);
		}
		return unused;
	}

	// A suggested word's icon syntax: an iconFlag's or an extra's, none for other options.
	@Nullable
	public String wordIcon(String word)
	{
		Option option = option(word);
		return option == null ? extrasIcon.apply(word) : option.isValue() ? null : option.icon.apply(word);
	}

	// Whether name (e.g. "itemsPerLine", "itemsPerLine=N" or "bought / sold") names one of the options.
	public boolean hasOption(String name)
	{
		for (String part : name.split("/"))
		{
			String lower = part.trim().toLowerCase(Locale.ROOT).replaceFirst("=.*", "");
			if (options.containsKey(lower) || options.containsKey(lower + "="))
			{
				return true;
			}
		}
		return false;
	}

	// word is a whole flag or number, e.g. "missing", "maxItems=5".
	public boolean isComplete(String word)
	{
		return options.values().stream().anyMatch(option -> !option.isValue() && word.matches("(?i)" + option.regex));
	}

	// text without the options and extras (and separators) at its end, e.g. "Vorkath, missing," -> "Vorkath".
	public String withoutTrailingOptions(String text)
	{
		return trailingOptions.matcher(text).replaceFirst("");
	}

	// A value being typed at the end of text: group 1 what's before, 2 its word ("item="), 3 the value so far. Else null.
	@Nullable
	public Matcher valueTyped(String text)
	{
		Matcher matcher = valueTyped.matcher(text);
		return valueTyped.pattern().isEmpty() || !matcher.matches() ? null : matcher;
	}

	// The suggestions for a value option's word ("item=", or as typed, "item ="), given the main argument, and each
	// one's icon syntax.
	public Collection<String> values(String word, @Nullable String main)
	{
		Option option = option(word);
		return option == null ? List.of() : option.values.apply(main);
	}

	@Nullable
	public String valueIcon(String word, String value)
	{
		Option option = option(word);
		return option == null ? null : option.icon.apply(value);
	}

	@Nullable
	private Option option(String word)
	{
		return options.get(word.replaceAll("\\s", "").toLowerCase(Locale.ROOT));
	}

	@Nullable
	public String main(Matcher matcher)
	{
		String main = mainNames == null ? null : matcher.group(1);
		return main == null || main.trim().isEmpty() ? null : main.trim();
	}

	// The skill the main argument names, e.g. {lvl_attack}; null if none.
	@Nullable
	public Skill skill(Matcher matcher)
	{
		return skill(main(matcher));
	}

	public Options options(Matcher matcher)
	{
		String text = matcher.group(mainNames == null ? 1 : 2);
		return new Options(text == null ? "" : text, token, optionOnly);
	}

	private void add(Option option)
	{
		options.put(option.word.toLowerCase(Locale.ROOT), option);
	}

	private Args build()
	{
		// With none, it matches nothing (not empty).
		String option = options.isEmpty() ? "(?!)" : "(?:" + options.values().stream().map(o -> o.regex).collect(Collectors.joining("|")) + ")";
		String afterComma = extras == null ? option : "(?:" + option + "|" + TEXT + ")";
		// One option: after a comma (or an extra), or a space.
		String item = "(?:\\s*,\\s*" + afterComma + "|\\s+" + option + ")";
		String rest = "((?:" + item + ")*)";
		String main = mainNames == null ? rest : (joined ? "(\\w+?)" : "\\s+(" + TEXT + ")") + rest;
		String names = names().stream().map(Pattern::quote).collect(Collectors.joining("|"));
		pattern = Pattern.compile("\\{(?:" + names + ")" + (mainOptional ? "(?:" + main + ")?" : main) + "[\\s,]*\\}", Pattern.CASE_INSENSITIVE);
		trailingOptions = Pattern.compile("(?:" + item + ")*[\\s,]*$", Pattern.CASE_INSENSITIVE);
		// Then only more options to the end, so a value or extra takes everything up to the next option.
		token = Pattern.compile("(?:\\s*,\\s*(" + afterComma + ")|\\s+(" + option + "))(?=(?:" + item + ")*[\\s,]*$)", Pattern.CASE_INSENSITIVE);
		optionOnly = Pattern.compile(option, Pattern.CASE_INSENSITIVE);
		String valueWords = options.values().stream().filter(Option::isValue)
			.map(o -> Pattern.quote(o.word.substring(0, o.word.length() - 1)) + "\\s*=\\s*").collect(Collectors.joining("|"));
		valueTyped = Pattern.compile(valueWords.isEmpty() ? "" : "(.*?)(?:^|[\\s,]+)(" + valueWords + ")([^{}?:<>=!,|&]*)", Pattern.CASE_INSENSITIVE);
		return this;
	}

	// The options a match gave: flags present, numbers and values by word, and extras.
	public static final class Options
	{
		// Lowercase word (without "=") -> its values, "" for a flag.
		private final Map<String, List<String>> given = new LinkedHashMap<>();
		private final List<String> extras = new ArrayList<>();

		private Options(String text, Pattern token, Pattern optionOnly)
		{
			Matcher matcher = token.matcher(text);
			while (matcher.find())
			{
				String found = (matcher.group(1) != null ? matcher.group(1) : matcher.group(2)).trim();
				if (!optionOnly.matcher(found).matches())
				{
					extras.add(found);
					continue;
				}
				String[] parts = found.split("=", 2);
				given.computeIfAbsent(parts[0].trim().toLowerCase(Locale.ROOT), word -> new ArrayList<>()).add(parts.length > 1 ? parts[1].trim() : "");
			}
		}

		public boolean flag(String word)
		{
			return given.containsKey(word.toLowerCase(Locale.ROOT));
		}

		// The words given, lowercase, in the order given.
		public List<String> words()
		{
			return new ArrayList<>(given.keySet());
		}

		// The last one given.
		@Nullable
		public String value(String word)
		{
			List<String> values = values(word);
			return values.isEmpty() ? null : values.get(values.size() - 1);
		}

		public List<String> values(String word)
		{
			return given.getOrDefault(word.toLowerCase(Locale.ROOT), List.of());
		}

		public List<String> extras()
		{
			return extras;
		}

		// fallback if not given or too large.
		public int number(String word, int fallback)
		{
			String value = value(word);
			try
			{
				return value == null || value.isEmpty() ? fallback : Integer.parseInt(value);
			}
			catch (NumberFormatException e)
			{
				return fallback;
			}
		}

		// A date value's time: today's or the date's start, or N days back from now; null if not given or unreadable.
		@Nullable
		public Instant date(String word)
		{
			String value = value(word);
			if (value == null)
			{
				return null;
			}
			if (value.equalsIgnoreCase("today"))
			{
				return LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
			}
			Matcher days = DAYS.matcher(value);
			if (days.matches())
			{
				return Instant.now().minus(Long.parseLong(days.group(1)), ChronoUnit.DAYS);
			}
			LocalDate date = DateLiteralParser.parse(value);
			return date == null ? null : date.atStartOfDay(ZoneId.systemDefault()).toInstant();
		}
	}
}
