package com.groundmarkervariables.chatbox.completion;

import com.groundmarkervariables.GroundMarkerVariablesConfig;
import com.groundmarkervariables.graphics.SplitIcons;
import com.groundmarkervariables.variables.Args;
import com.groundmarkervariables.variables.LabelResolver;
import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.NumericVariableRegistry;
import com.groundmarkervariables.variables.support.Emojis;
import com.groundmarkervariables.variables.support.IconNames;
import com.groundmarkervariables.variables.support.NamedColors;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import net.runelite.api.gameval.SpriteID;

// Autocomplete for the Advanced Label Editor: what can complete the token at the cursor (see Completion). One per
// editor; labels() gives it that editor's recent/nearby labels.
public class LabelCompleter
{
	// The completable name of every variable (variables.json); "lvl_", "col=", ... include what joins them to their argument.
	private static final List<String> VARIABLE_NAMES = VariableCatalog.NAMES;
	private static final List<String> NUMERIC_VARIABLE_NAMES = VARIABLE_NAMES.stream()
		.filter(NumericVariableRegistry.NAMES::contains).collect(Collectors.toList());

	// Variables with an icon option: listed with what "{<name> icon}" shows.
	private static final Set<String> ICON_VARIABLES = VariableCatalog.WITH_ICON;
	// Icon syntax of a boss by name: its hiscores icon, else a sprite of that name.
	private static final String BOSS_ICON = Args.BOSS_ICON_PREFIX;
	// Icon syntax of a color swatch: "color:RRGGBB".
	private static final String COLOR_ICON = "color:";
	// The first icon tag in a resolved icon.
	private static final Pattern ICON_TAG = Pattern.compile("<(?:(sprite|item|npc)=(\\d+)(,head)?|(emoji)=([0-9a-f]+))");
	private static final String SPLIT_OPEN = "{split}";
	private static final String SPLIT_CLOSE = "{/split}";

	// Names that need more typed before the token is complete, so an exact one isn't closed with "}".
	private static final Set<String> VARIABLE_NAMES_NEEDING_MORE = VariableCatalog.REQUIRING_ARGUMENT;



	// {party <template>}: nothing completed, so its parameter hint shows.
	private static final String PARTY_PREFIX = "party ";

	private static final String ICON = "icon";

	// ":<partial emoji name>" at the cursor. Not after a name character, so ":name:", "9:30" and " : " never start one.
	private static final Pattern EMOJI_PARTIAL = Pattern.compile("(?:^|[^a-z0-9_!().\\-ñ:]):([a-z0-9_!().\\-ñ]+)$");
	// ":name@<color>" being typed.
	private static final Pattern EMOJI_COLOR_PARTIAL = Pattern.compile("(?:^|[^a-z0-9_!().\\-ñ:]):([a-z0-9_!().\\-ñ]+)@([a-zA-Z]*)$");
	// "<npc=<partial name>" / "<item=..." / "<sprite=..." at the cursor (see IconNames), and "<it" -> "<item=" (not
	// right after a name character, so "{hp<sp...}" stays a comparison).
	private static final Pattern ICON_NAME_PARTIAL = Pattern.compile("<(npc|item|sprite)=([^<>{}=,@]*)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern ICON_TAG_PARTIAL = Pattern.compile("(?:^|[^a-z0-9_])<([a-z]+)$", Pattern.CASE_INSENSITIVE);
	private static final List<String> ICON_TAGS = List.of("item=", "npc=", "sprite=");

	// Searches listed first after a bare "<sprite=": each sprite name ends in its "(Category)", so inserting it lists
	// that category. With the sprite shown for it.
	private static final class SpriteCategory
	{
		private final String search;
		private final int spriteId;

		private SpriteCategory(String search, int spriteId)
		{
			this.search = search;
			this.spriteId = spriteId;
		}
	}

	private static final List<SpriteCategory> SPRITE_CATEGORIES = List.of(
		new SpriteCategory("(Prayer)", 946),
		new SpriteCategory("(Spell)", 328),
		new SpriteCategory("(Skill)", 222),
		new SpriteCategory("(Hiscore)", 4281),
		new SpriteCategory("(Map Icon)", 1453),
		new SpriteCategory("(Emote)", 707),
		new SpriteCategory("(Combat)", 238),
		new SpriteCategory("(Equipment)", 159),
		new SpriteCategory("(Tab)", 900),
		new SpriteCategory("(Minimap)", 1067),
		new SpriteCategory("(Hitsplat)", 1358),
		new SpriteCategory("(Friends)", 2828),
		new SpriteCategory("(Ge)", 1108),
		new SpriteCategory("(Bank)", 1041),
		new SpriteCategory("(Sailing)", 7201),
		new SpriteCategory("(Texture)", 476),
		new SpriteCategory("(TFA)", 8360),
		new SpriteCategory("(Mortimer)", 8551),
		new SpriteCategory("(Minigame Teleport)", 7479),
		new SpriteCategory("(Arrow)", 6858),
		new SpriteCategory("(Options)", 1084));

	// {metronomeN[_M]} (or {mN}): the parameter, from recent/nearby usage.
	private static final String METRONOME_NAME = "metronome";
	private static final String METRONOME_ALIAS = "m";
	private static final Pattern METRONOME_TOKEN = Pattern.compile("\\{(?:metronome|m)([+-]?\\d+(?:_\\d+)?)\\}", Pattern.CASE_INSENSITIVE);
	private static final Pattern METRONOME_PARAM = Pattern.compile("[+-]?\\d+(_\\d+)?");
	private static final String DEFAULT_METRONOME = "4";

	// {col=<col>}: a named color, else a hex value from recent/nearby <col=> usage.
	private static final String COLOR_PREFIX = "col=";
	private static final Pattern COLOR_TAG = Pattern.compile("<col=([0-9a-fA-F]{2,6})>", Pattern.CASE_INSENSITIVE);

	// {tileCol=<col>}: a named color, a named opacity, or "N%".
	private static final String TILECOL_PREFIX = "tileCol=";
	private static final Pattern TILECOL_PERCENT = Pattern.compile("\\d{1,3}%");

	// {highlight<corner>[=<col>]}: <col> a named color.
	private static final String HIGHLIGHT_NAME = "highlight";
	private static final List<String> HIGHLIGHT_CORNERS = List.of("SW", "NW", "NE", "SE", "Corners", "All");

	// {/col}, {/align}, ...: diverge from their opening token at the "/".
	private static final List<String> CLOSE_ALIASES = List.of("/col}", "/align}", "/b}", "/i}", "/hoverOnly}", "/hoverHide}", "/tip}", "/ground}");

	// {ground@<dir>}: where the top of the {ground} content faces; "{ground" offers the optional "@".
	private static final Pattern GROUND_NAME = Pattern.compile("(?i)^ground$");
	private static final Pattern GROUND_FACING = Pattern.compile("(?i)^ground@(\\w*)$");
	private static final List<String> GROUND_DIRECTIONS = List.of("n", "ne", "e", "se", "s", "sw", "w", "nw");

	// {align=<align>}.
	private static final String ALIGN_PREFIX = "align=";
	private static final List<String> ALIGN_VALUES = List.of("Left", "Right", "Center");

	// After a math operator only numeric variables fit. "(" needs no padding, as MathVariable's own (e.g. "(kc tob").
	private static final Pattern MATH_OPERATOR_BOUNDARY = Pattern.compile("\\s[+\\-*/]\\s|\\(");
	private static final Pattern CONDITION_BOUNDARY = Pattern.compile("==|!=|<=|>=|<|>|&&|\\|\\|");

	// {spellbook == <name>} / {spellbook != <name>}.
	private static final Pattern SPELLBOOK_CONDITION = Pattern.compile("(?i)\\bspellbook\\s*(==|!=)\\s*(\\w*)$");
	private static final List<String> SPELLBOOK_NAMES = List.of("Standard", "Ancient", "Lunar", "Arceuus");
	private static final Map<String, Integer> SPELLBOOK_SPRITES = Map.of(
		"Standard", SpriteID.SideiconsInterface.MAGIC, "Ancient", SpriteID.SideiconsInterface.SPELLBOOK_ANCIENT_MAGICKS,
		"Lunar", SpriteID.SideiconsInterface.SPELLBOOK_LUNAR, "Arceuus", SpriteID.SideiconsInterface.SPELLBOOK_ARCEUUS);

	// A rule's "applies, but nothing to offer", which stops the search.
	private static final Completion NONE = new Completion(List.of(), -1);

	// A completion rule for the text after "{": null if it doesn't apply (the next is tried), else its result.
	private interface Rule
	{
		@Nullable
		Completion apply(String partial, boolean numeric);
	}

	private final GroundMarkerVariablesConfig config;
	private final Emojis emojis;
	private final IconNames iconNames;
	private final LabelResolver labelResolver;
	private final RecentUsage recent = new RecentUsage();
	private final List<Rule> rules;
	// Each option's icon, by its icon syntax: worked out once per editor (this completer's lifetime).
	private final Map<String, Optional<Completion.Icon>> icons = new HashMap<>();

	@Inject
	private LabelCompleter(GroundMarkerVariablesConfig config, Emojis emojis, IconNames iconNames, LabelResolver labelResolver)
	{
		this.config = config;
		this.emojis = emojis;
		this.iconNames = iconNames;
		this.labelResolver = labelResolver;
		this.rules = rules();
	}

	// The editor's recent and nearby labels (its own lists, kept up to date by it).
	public void labels(List<String> recentLabels, List<String> nearbyLabels)
	{
		recent.set(recentLabels, nearbyLabels);
		iconNames.refreshNearby();
	}

	// Client thread. An option's icon (see Completion.Option#icon): the first sprite or item its syntax resolves to.
	// Cached, except a name not found while IconNames is still loading.
	@Nullable
	public Completion.Icon icon(String syntax)
	{
		Optional<Completion.Icon> cached = icons.get(syntax);
		if (cached != null)
		{
			return cached.orElse(null);
		}

		if (syntax.startsWith(COLOR_ICON))
		{
			return new Completion.Icon(Completion.Icon.Kind.COLOR, syntax.substring(COLOR_ICON.length()));
		}

		if (syntax.startsWith(BOSS_ICON))
		{
			String name = syntax.substring(BOSS_ICON.length());
			Integer sprite = iconNames.bossSprite(name);
			if (sprite == null)
			{
				return icon(spriteNamed(name, "Hiscore"));
			}
			Completion.Icon icon = new Completion.Icon(Completion.Icon.Kind.SPRITE, String.valueOf(sprite));
			icons.put(syntax, Optional.of(icon));
			return icon;
		}

		String expanded = iconNames.expand(emojis.expand(syntax));
		String resolved = labelResolver.resolve(expanded);
		Matcher tag = ICON_TAG.matcher(resolved);
		Completion.Icon icon = null;
		if (tag.find())
		{
			// Inside {split}...{/split}: its icons as one.
			int splitOpen = resolved.lastIndexOf(SPLIT_OPEN, tag.start());
			int splitClose = splitOpen < 0 ? -1 : resolved.indexOf(SPLIT_CLOSE, splitOpen);
			if (splitClose > tag.start())
			{
				List<Completion.Icon> parts = new ArrayList<>();
				do
				{
					parts.add(tagIcon(tag));
				}
				while (parts.size() < SplitIcons.MAX_PARTS && tag.find() && tag.start() < splitClose);
				icon = parts.size() == 1 ? parts.get(0) : Completion.Icon.split(parts);
			}
			else
			{
				icon = tagIcon(tag);
			}
		}
		if (icon != null || !expanded.equals(syntax) || !syntax.startsWith("<"))
		{
			icons.put(syntax, Optional.ofNullable(icon));
		}
		return icon;
	}

	// The icon an ICON_TAG match draws. <npc=ID,head> is its chathead.
	private static Completion.Icon tagIcon(Matcher tag)
	{
		String kind = tag.group(1) != null ? tag.group(1) + (tag.group(3) != null ? "_head" : "") : tag.group(4);
		return new Completion.Icon(Completion.Icon.Kind.valueOf(kind.toUpperCase(Locale.ROOT)), tag.group(1) != null ? tag.group(2) : tag.group(5));
	}

	// completion with icon(label) as each option's icon syntax.
	@Nullable
	private static Completion withIcons(@Nullable Completion completion, Function<String, String> icon)
	{
		return completion == null ? null : completion.withIcons(icon);
	}

	// A named sprite tag, for IconNames to expand.
	// A sprite by its IconNames name, "Name (Category)", e.g. "Piety (Prayer)".
	private static String spriteNamed(String name, String category)
	{
		return "<sprite=" + name + " (" + category + ")>";
	}

	// The freely typed parameters, "name: description", of the variable whose arguments are being typed at cursor,
	// e.g. "{showOneItem " -> "<a>, <b>, ...: Parts of item names, ...". Empty if none.
	public List<String> parameterHints(String text, int cursor)
	{
		String token = token(text, cursor);
		if (token == null)
		{
			return List.of();
		}
		// Not a variable with args()'s option words: those are suggested instead.
		Args args = labelResolver.variables().stream().map(LabelVariable::args)
			.filter(a -> a != null && argsStart(a, token) >= 0).findFirst().orElse(null);
		return VariableCatalog.hints(token).stream().filter(line -> args == null || !args.hasOption(line.split(":", 2)[0]))
			.collect(Collectors.toList());
	}

	// The variable's own line when the token at cursor is exactly its name, e.g. "{rsn" -> "{rsn}: Display name".
	@Nullable
	public String variableSummary(String text, int cursor)
	{
		String token = token(text, cursor);
		return token == null ? null : VariableCatalog.summary(token);
	}

	// The line of the parameter whose required value is being typed at cursor, e.g. "{ground@" -> "@<dir>: ...".
	@Nullable
	public String valueHint(String text, int cursor)
	{
		String token = token(text, cursor);
		return token == null ? null : VariableCatalog.valueHint(token);
	}

	// What's typed at cursor after the open "{" (and any ^&* prefix or operator), or null outside one.
	@Nullable
	private static String token(String text, int cursor)
	{
		String before = text.substring(0, cursor);
		int open = before.lastIndexOf('{');
		if (open < 0 || before.indexOf('}', open) >= 0)
		{
			return null;
		}
		String partial = before.substring(open + 1);
		if (!partial.isEmpty() && "^&*".indexOf(partial.charAt(0)) >= 0)
		{
			partial = partial.substring(1);
		}
		int boundaryEnd = Math.max(lastMatchEnd(MATH_OPERATOR_BOUNDARY, partial), lastMatchEnd(CONDITION_BOUNDARY, partial));
		return boundaryEnd >= 0 ? partial.substring(boundaryEnd).stripLeading() : partial;
	}

	// What completes the token ending at cursor in text, or null. A ghosted "}" is listed first, its tooltip the
	// variable it closes.
	@Nullable
	public Completion complete(String text, int cursor)
	{
		Completion completion = withVariableIcons(completeToken(text, cursor), text, cursor);
		if (completion == null || completion.preferred < 0)
		{
			return completion;
		}
		Completion.Option ghost = completion.options.get(completion.preferred);
		int open = unclosedOpen(text.substring(0, cursor));
		if (ghost.label != null || !ghost.insert.equals("}") || open < 0)
		{
			return completion;
		}
		List<Completion.Option> options = new ArrayList<>();
		options.add(new Completion.Option("}", "}", ghost.replace, null, text.substring(open, cursor) + "}"));
		completion.options.stream().filter(option -> option != ghost).forEach(options::add);
		return new Completion(options, 0);
	}

	// Each "icon" option without an icon given the icon its variable would show once completed, e.g. "{hasSurge icon}"
	// or "{charges toxic blowpipe, icon}" (resolved once, see icon()).
	@Nullable
	private static Completion withVariableIcons(@Nullable Completion completion, String text, int cursor)
	{
		int open = completion == null ? -1 : unclosedOpen(text.substring(0, cursor));
		if (open < 0)
		{
			return completion;
		}
		List<Completion.Option> options = new ArrayList<>(completion.options.size());
		for (Completion.Option option : completion.options)
		{
			if (option.label == null || option.icon != null || !option.label.replaceFirst("^[\\s,]+", "").equalsIgnoreCase(ICON))
			{
				options.add(option);
				continue;
			}
			String variable = text.substring(open, cursor - option.replace) + option.insert;
			options.add(new Completion.Option(option.label, option.insert, option.replace,
				variable.endsWith("}") ? variable : variable + "}", option.description));
		}
		return new Completion(options, completion.preferred);
	}

	// The innermost "{" in before still open (nested pairs skipped), or -1.
	private static int unclosedOpen(String before)
	{
		int depth = 0;
		for (int i = before.length() - 1; i >= 0; i--)
		{
			char c = before.charAt(i);
			if (c == '}')
			{
				depth++;
			}
			else if (c == '{' && depth-- == 0)
			{
				return i;
			}
		}
		return -1;
	}

	// complete() before its "}" row. Not mid-word: completing there would run into what follows.
	@Nullable
	private Completion completeToken(String text, int cursor)
	{
		if (!config.autocomplete() || (cursor < text.length() && Character.isLetterOrDigit(text.charAt(cursor))))
		{
			return null;
		}

		String before = text.substring(0, cursor);
		Matcher iconName = ICON_NAME_PARTIAL.matcher(before);
		if (iconName.find())
		{
			IconNames.Kind kind = IconNames.Kind.valueOf(iconName.group(1).toUpperCase(Locale.ROOT));
			return completeIconName(kind, iconName.group(2), text.startsWith(">", cursor));
		}

		Matcher iconTag = ICON_TAG_PARTIAL.matcher(before);
		if (iconTag.find())
		{
			Completion tag = match(iconTag.group(1), ICON_TAGS, "{", "", null, true);
			if (tag != null && tag.preferred >= 0)
			{
				return tag;
			}
		}

		Matcher emojiColor = EMOJI_COLOR_PARTIAL.matcher(before);
		if (emojiColor.find() && emojis.names().contains(emojiColor.group(1)))
		{
			String name = emojiColor.group(1);
			return withIcons(match(emojiColor.group(2), NamedColors.SUGGESTED, ":" + name + "@", ":", ":", true),
				LabelCompleter::colorIcon);
		}

		Matcher emoji = EMOJI_PARTIAL.matcher(before);
		if (emoji.find())
		{
			return completeEmoji(emoji.group(1));
		}

		int open = before.lastIndexOf('{');
		if (open < 0 || before.indexOf('}', open) >= 0)
		{
			// Right after a nested variable inside one still open, e.g. "{party {rsn}": close the outer one.
			return before.endsWith("}") && unclosedOpen(before) >= 0 && !text.startsWith("}", cursor) ? Completion.closer("}") : null;
		}

		String partial = before.substring(open + 1);
		// {^var} / {&var} / {*var}: a Rich/Plain override prefix.
		if (!partial.isEmpty() && "^&*".indexOf(partial.charAt(0)) >= 0)
		{
			partial = partial.substring(1);
		}
		if (partial.isEmpty())
		{
			return variableNames("", false);
		}

		Matcher spellbook = SPELLBOOK_CONDITION.matcher(partial);
		if (spellbook.find())
		{
			return withIcons(match(spellbook.group(2), SPELLBOOK_NAMES, partial.substring(spellbook.start(), spellbook.start(2)), "", "}", true),
				name -> "<sprite=" + SPELLBOOK_SPRITES.get(name) + ">");
		}

		// {<a> + <b>} / {<expr> < <value> ? ...}: complete what follows the last operator, as if right after "{". After a
		// math operator, only numeric variables.
		int mathEnd = lastMatchEnd(MATH_OPERATOR_BOUNDARY, partial);
		int conditionEnd = lastMatchEnd(CONDITION_BOUNDARY, partial);
		boolean numeric = mathEnd > conditionEnd;
		int boundaryEnd = Math.max(mathEnd, conditionEnd);
		if (boundaryEnd >= 0)
		{
			partial = partial.substring(boundaryEnd).trim();
			if (partial.isEmpty())
			{
				return variableNames("", numeric);
			}
		}

		Completion completion = afterBrace(partial, numeric);
		// A full variable name or word lists what can follow it after a space first (e.g. "{spellbook" -> " icon"); not
		// while a word is still being completed (e.g. "{hasSurge a" -> "air"), only merely contained.
		if (Character.isLetterOrDigit(partial.charAt(partial.length() - 1))
			&& (completion == null || completion.options.stream().noneMatch(option -> option.label != null && option.replace == 0)))
		{
			Completion arguments = afterBrace(partial + " ", numeric);
			if (arguments != null && arguments.hasListed())
			{
				return Completion.withArguments(completion, arguments);
			}
		}
		return completion;
	}

	// The first applicable rule's completion for the text after "{" (and any operator), else variable names.
	@Nullable
	private Completion afterBrace(String partial, boolean numeric)
	{
		for (Rule rule : rules)
		{
			Completion completion = rule.apply(partial, numeric);
			if (completion != null)
			{
				return completion == NONE ? null : completion;
			}
		}
		return partial.chars().allMatch(Character::isLetterOrDigit) ? variableNames(partial, numeric) : null;
	}

	// The rules for the text after "{" (and any operator), first applicable wins.
	private List<Rule> rules()
	{
		List<Rule> list = new ArrayList<>();
		list.add((partial, numeric) -> numeric || partial.charAt(0) != '/' ? null : orNone(match(partial, CLOSE_ALIASES, "{", "", null, true)));
		list.add(prefix(PARTY_PREFIX, false, rest -> null));
		for (LabelVariable variable : labelResolver.variables())
		{
			Args args = variable.args();
			if (args != null)
			{
				list.add(argsRule(args));
			}
		}
		list.add(prefix(METRONOME_NAME, false, this::completeMetronomeParams));
		// "m" alias: only once a digit or +/- follows, so "{mis..." still completes to miscellania.
		list.add((partial, numeric) -> numeric || partial.length() < 2 || !partial.regionMatches(true, 0, METRONOME_ALIAS, 0, 1)
			|| !(Character.isDigit(partial.charAt(1)) || partial.charAt(1) == '+' || partial.charAt(1) == '-')
			? null : orNone(completeMetronomeParams(partial.substring(1))));
		list.add(prefix(COLOR_PREFIX, false, this::completeColorValue));
		list.add(prefix(TILECOL_PREFIX, false, this::completeTileColValue));
		list.add(prefix(HIGHLIGHT_NAME, false, this::completeHighlight));
		list.add(prefix(ALIGN_PREFIX, false, rest -> match(rest, ALIGN_VALUES, "{" + ALIGN_PREFIX, "}", null, true)));
		// "@" listed; Tab still closes with "}".
		list.add(pattern(GROUND_NAME, m -> described(new Completion(List.of(new Completion.Option("@", "@", 0, null),
			new Completion.Option(null, "}", 0, null)), 1), "ground")));
		list.add(pattern(GROUND_FACING, m -> completeGroundFacing(m.group(1))));
		return list;
	}

	// Applies to text starting with prefix (any case), completing what follows it; inMath: also after a math operator.
	private static Rule prefix(String prefix, boolean inMath, Function<String, Completion> complete)
	{
		return (partial, numeric) -> (numeric && !inMath) || !partial.regionMatches(true, 0, prefix, 0, prefix.length())
			? null : orNone(complete.apply(partial.substring(prefix.length())));
	}

	// A variable with args(): applies once its name is followed by a space or comma; after a math operator only if numeric.
	private Rule argsRule(Args args)
	{
		boolean inMath = NumericVariableRegistry.NAMES.contains(args.name());
		return (partial, numeric) ->
		{
			int start = argsStart(args, partial);
			return (numeric && !inMath) || start < 0 ? null : orNone(completeArgs(args, partial.substring(start)));
		};
	}

	// Where args' arguments begin in text: right after one of its names if joined, else after it and a space (or at a
	// comma); -1 if not its.
	private static int argsStart(Args args, String text)
	{
		for (String name : args.names())
		{
			if (args.isJoined() && text.regionMatches(true, 0, name, 0, name.length()))
			{
				return name.length();
			}
			if (text.length() > name.length() && text.regionMatches(true, 0, name, 0, name.length()))
			{
				char next = text.charAt(name.length());
				if (next == ' ' || next == ',')
				{
					return name.length() + (next == ' ' ? 1 : 0);
				}
			}
		}
		return -1;
	}

	// Applies to text pattern matches entirely, never after a math operator.
	private static Rule pattern(Pattern pattern, Function<Matcher, Completion> complete)
	{
		return (partial, numeric) ->
		{
			if (numeric)
			{
				return null;
			}
			Matcher matcher = pattern.matcher(partial);
			return matcher.matches() ? orNone(complete.apply(matcher)) : null;
		};
	}

	private static Completion orNone(@Nullable Completion completion)
	{
		return completion == null ? NONE : completion;
	}

	// The variable names partial starts (numeric ones only after a math operator); every one with no ghost if empty.
	// Each with its icon and description.
	@Nullable
	private Completion variableNames(String partial, boolean numeric)
	{
		boolean needsMore = VARIABLE_NAMES_NEEDING_MORE.stream().anyMatch(partial::equalsIgnoreCase);
		Completion names = withIcons(match(partial, numeric ? NUMERIC_VARIABLE_NAMES : VARIABLE_NAMES, "{", "", needsMore ? null : "}", false),
			name -> ICON_VARIABLES.contains(name) ? "{" + name + " icon}" : null);
		return names == null ? null : names.withDescriptions(VariableCatalog::description);
	}

	// The names starting with partial (any case), those only containing it, and whether one equals it.
	private static final class Matches
	{
		private final List<String> names = new ArrayList<>();
		private final List<String> contained = new ArrayList<>();
		private boolean exact;
	}

	private static Matches matches(String partial, Iterable<String> names)
	{
		Matches matches = new Matches();
		String lower = partial.toLowerCase(Locale.ROOT);
		for (String name : names)
		{
			if (name.length() > partial.length() && name.regionMatches(true, 0, partial, 0, partial.length()))
			{
				matches.names.add(name);
			}
			else if (name.equalsIgnoreCase(partial))
			{
				matches.exact = true;
			}
			else if (!partial.isEmpty() && name.toLowerCase(Locale.ROOT).contains(lower))
			{
				matches.contained.add(name);
			}
		}
		return matches;
	}

	// Each longer name starting with partial completes to the rest of itself, then append; the preferred one per
	// recent/nearby usage of tokenPrefix + name. With none, an exact name gives exactCloser (if any). An empty partial
	// lists every name, preferring one only if ghostWhenEmpty. Names only containing partial are listed after.
	@Nullable
	private Completion match(String partial, Iterable<String> names, String tokenPrefix, String append, @Nullable String exactCloser,
		boolean ghostWhenEmpty)
	{
		return complete(matches(partial, names), partial, tokenPrefix, append, exactCloser, ghostWhenEmpty);
	}

	@Nullable
	private Completion complete(Matches matches, String partial, String tokenPrefix, String append, @Nullable String exactCloser,
		boolean ghostWhenEmpty)
	{
		Completion starting;
		if (!matches.names.isEmpty())
		{
			String preferred = partial.isEmpty() && !ghostWhenEmpty ? null : recent.pick(matches.names, tokenPrefix);
			starting = Completion.of(matches.names, partial.length(), append, preferred);
		}
		else if (matches.exact && exactCloser != null)
		{
			starting = Completion.closer(exactCloser);
		}
		else if (!matches.contained.isEmpty())
		{
			starting = new Completion(List.of(), -1);
		}
		else
		{
			return null;
		}
		return starting.withContained(matches.contained, partial.length(), append);
	}

	// An option after a full name: options listed in order, the first that fits preferred; "}" once one is complete.
	@Nullable
	private static Completion completeOption(String word, List<String> options, boolean complete)
	{
		if (complete)
		{
			return Completion.closer("}");
		}
		List<String> fitting = options.stream()
			.filter(option -> word.length() < option.length() && option.regionMatches(true, 0, word, 0, word.length()))
			.collect(Collectors.toList());
		return fitting.isEmpty() ? null : Completion.of(fitting, word.length(), "", word.isEmpty() ? null : fitting.get(0));
	}



	// A variable with args(): a value being typed after its word (e.g. "item="), else its main argument's names, then
	// once one is given and a comma or option follows, the options (and after a comma, extras) not given yet; "}" once
	// one is complete. A full main argument alone also lists them (see withOptions).
	@Nullable
	private Completion completeArgs(Args args, String partial)
	{
		String prefix = args.name() + " ";
		String catalog = args.catalogName();
		Matcher value = args.valueTyped(partial);
		if (value != null)
		{
			String word = value.group(2);
			String main = args.hasMain() ? args.withoutTrailingOptions(value.group(1)).trim() : null;
			return withIcons(match(value.group(3).replaceFirst("^\\s+", ""), args.values(word, main), "{" + prefix + word, "", "}", true),
				name -> args.valueIcon(word, name));
		}

		Set<String> names = new LinkedHashSet<>(args.mainNames());
		int split = Math.max(partial.lastIndexOf(','), partial.lastIndexOf(' '));
		String before = partial.substring(0, split + 1);
		String head = args.hasMain() ? args.withoutTrailingOptions(before) : "";
		String given = before.substring(head.length());
		String word = partial.substring(split + 1);
		String main = args.hasMain() ? head.trim() : null;
		// With extras, anything before a comma counts.
		boolean mainGiven = main == null || names.stream().anyMatch(main::equalsIgnoreCase)
			|| (args.hasExtras() && !main.isEmpty() && partial.lastIndexOf(',') >= head.length());
		if (mainGiven && (main == null || !word.isEmpty() || !given.isBlank()))
		{
			List<String> unused = args.unusedWords(given, main);
			// An extra may have spaces: all of what follows the comma.
			int comma = partial.lastIndexOf(',');
			Completion option = args.hasExtras() && comma >= head.length()
				? match(partial.substring(comma + 1).replaceFirst("^\\s+", ""), unused, "{" + prefix, "", "}", false)
				: completeOption(word, unused, args.isComplete(word));
			option = described(withIcons(option, args::wordIcon), catalog);
			if (option != null || main == null)
			{
				return option;
			}
		}
		return withOptions(described(withIcons(match(partial, names, "{" + prefix, "", "}", false), args::mainIcon), catalog), partial, names, args);
	}

	// A full main argument typed (any case) also offers each of its options and extras first, after ", " (", items=");
	// Tab still does what it did.
	@Nullable
	private static Completion withOptions(@Nullable Completion completion, String partial, Set<String> names, Args args)
	{
		String main = partial.trim();
		if (names.stream().noneMatch(main::equalsIgnoreCase))
		{
			return completion;
		}
		// After a space, just the word: {equip_helm icon}.
		String separator = partial.endsWith(" ") ? "" : ", ";
		List<String> words = args.unusedWords("", main);
		List<Completion.Option> options = new ArrayList<>();
		for (String word : words)
		{
			options.add(new Completion.Option(separator + word, separator + word, 0, args.wordIcon(word),
				VariableCatalog.completionDescription(args.catalogName(), word)));
		}
		if (completion == null)
		{
			return options.isEmpty() ? null : new Completion(options, -1);
		}
		options.addAll(completion.options);
		return new Completion(options, completion.preferred < 0 ? -1 : completion.preferred + words.size());
	}

	// Each option given the description of the parameter it completes for variable (variables.json), if any.
	@Nullable
	private static Completion described(@Nullable Completion completion, String variable)
	{
		return completion == null ? null : completion.withDescriptions(word -> VariableCatalog.completionDescription(variable, word));
	}

	// Names closed with ">" unless one already follows; numeric IDs aren't completed.
	@Nullable
	private Completion completeIconName(IconNames.Kind kind, String partial, boolean closed)
	{
		// Every name, even before anything's typed; icons load only for the rows shown.
		if (!partial.isEmpty() && partial.chars().allMatch(Character::isDigit))
		{
			return null;
		}
		List<String> names = iconNames.matches(kind, partial);
		if (names.isEmpty())
		{
			return null;
		}
		List<String> starting = new ArrayList<>();
		List<String> contained = new ArrayList<>();
		for (String name : names)
		{
			(name.regionMatches(true, 0, partial, 0, partial.length()) ? starting : contained).add(name);
		}
		String closer = closed ? "" : ">";
		Completion completion = Completion.of(starting, partial.length(), closer, starting.isEmpty() || partial.isEmpty() ? null : starting.get(0))
			.withContained(contained, partial.length(), closer)
			.withIcons(name -> "<" + kind.tag + "=" + name + ">");
		// An NPC with a chathead completes without ">", so ",head" can be offered next.
		if (kind == IconNames.Kind.NPC && !closer.isEmpty())
		{
			List<Completion.Option> options = new ArrayList<>();
			for (Completion.Option option : completion.options)
			{
				boolean open = option.label != null && !option.label.equalsIgnoreCase(partial.trim()) && iconNames.hasHead(option.label);
				options.add(open ? new Completion.Option(option.label, option.insert.substring(0, option.insert.length() - closer.length()),
					option.replace, option.icon, option.description) : option);
			}
			completion = new Completion(options, completion.preferred);
		}
		// A bare "<sprite=" lists the category searches first.
		if (kind == IconNames.Kind.SPRITE && partial.isEmpty())
		{
			List<Completion.Option> options = new ArrayList<>();
			for (SpriteCategory category : SPRITE_CATEGORIES)
			{
				options.add(new Completion.Option(category.search, category.search, 0, "<sprite=" + category.spriteId + ">",
					"Search " + category.search + " sprites"));
			}
			options.addAll(completion.options);
			completion = new Completion(options, completion.preferred < 0 ? -1 : completion.preferred + options.size() - completion.options.size());
		}
		// A typed NPC with a chathead also offers ",head" first.
		if (kind == IconNames.Kind.NPC && iconNames.hasHead(partial))
		{
			List<Completion.Option> options = new ArrayList<>(completion.options);
			options.add(0, new Completion.Option(",head", ",head" + closer, 0, "<npc=" + partial.trim() + ",head>"));
			completion = new Completion(options, completion.preferred < 0 ? -1 : completion.preferred + 1);
		}
		return completion;
	}

	// Shortest name first unless recent/nearby labels use one; ":" closes it. A full name also lists "@" for a color.
	@Nullable
	private Completion completeEmoji(String partial)
	{
		Matches matches = matches(partial, emojis.names());
		matches.names.sort(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()));
		matches.contained.sort(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()));
		Completion completion = withIcons(complete(matches, partial, ":", ":", ":", true), name -> ":" + name + ":");
		if (completion == null || !emojis.names().contains(partial))
		{
			return completion;
		}
		List<Completion.Option> options = new ArrayList<>(completion.options);
		options.add(new Completion.Option("@", "@", 0, null));
		return new Completion(options, completion.preferred);
	}

	// The parameter after "metronome" or "m": digits, an optional "_" and leading +/-. From recent/nearby usage, else
	// 4; "}" once complete.
	@Nullable
	private Completion completeMetronomeParams(String partial)
	{
		for (int i = 0; i < partial.length(); i++)
		{
			char c = partial.charAt(i);
			if (!Character.isDigit(c) && c != '_' && !(i == 0 && (c == '+' || c == '-')))
			{
				return null;
			}
		}

		String param = recent.firstValue(METRONOME_TOKEN, partial);
		if (param != null)
		{
			return Completion.single(param, param.substring(partial.length()), null);
		}
		if (partial.isEmpty() || partial.equals("+") || partial.equals("-"))
		{
			return Completion.single(partial + DEFAULT_METRONOME, DEFAULT_METRONOME, null);
		}
		return METRONOME_PARAM.matcher(partial).matches() ? Completion.closer("}") : null;
	}

	// A named color, else a hex value from recent/nearby <col=> usage (even with nothing typed: there's no fixed list
	// to guess from).
	@Nullable
	private Completion completeColorValue(String partial)
	{
		Completion named = withIcons(match(partial, NamedColors.SUGGESTED, "{" + COLOR_PREFIX, "}", null, true), LabelCompleter::colorIcon);
		if (named != null)
		{
			return named;
		}
		String hex = recent.firstValue(COLOR_TAG, partial);
		return hex == null ? null : Completion.single(hex, hex.substring(partial.length()) + "}", null);
	}

	// A named opacity or color, or "N%"; closed with "}".
	@Nullable
	private Completion completeTileColValue(String partial)
	{
		if (TILECOL_PERCENT.matcher(partial).matches())
		{
			return Completion.closer("}");
		}
		List<String> names = new ArrayList<>(NamedColors.OPACITY_BY_NAME.keySet());
		names.addAll(NamedColors.SUGGESTED);
		return withIcons(match(partial, names, "{" + TILECOL_PREFIX, "}", null, true), LabelCompleter::colorIcon);
	}

	// A named color's swatch icon syntax, or null if name isn't a color (e.g. an opacity name).
	@Nullable
	private static String colorIcon(String name)
	{
		String hex = NamedColors.HEX_BY_NAME.get(name.toLowerCase(Locale.ROOT));
		return hex == null ? null : COLOR_ICON + hex;
	}

	// The corner; once "=" follows a full corner name, a named color closed with "}".
	@Nullable
	private Completion completeHighlight(String rest)
	{
		int eq = rest.indexOf('=');
		if (eq < 0)
		{
			return match(rest, HIGHLIGHT_CORNERS, "{" + HIGHLIGHT_NAME, "", "}", true);
		}
		String corner = rest.substring(0, eq);
		return HIGHLIGHT_CORNERS.stream().anyMatch(corner::equalsIgnoreCase)
			? withIcons(match(rest.substring(eq + 1), NamedColors.SUGGESTED, "{" + HIGHLIGHT_NAME, "}", null, true), LabelCompleter::colorIcon)
			: null;
	}

	// A direction, closed with "}"; an exact one ("n", which "ne"/"nw" also start) closes as it is.
	@Nullable
	private Completion completeGroundFacing(String partial)
	{
		if (GROUND_DIRECTIONS.stream().anyMatch(partial::equalsIgnoreCase))
		{
			List<String> longer = GROUND_DIRECTIONS.stream()
				.filter(dir -> dir.length() > partial.length() && dir.regionMatches(true, 0, partial, 0, partial.length()))
				.collect(Collectors.toList());
			List<Completion.Option> options = new ArrayList<>();
			options.add(new Completion.Option(partial.toLowerCase(Locale.ROOT), "}", 0, null));
			for (String dir : longer)
			{
				options.add(new Completion.Option(dir, dir.substring(partial.length()) + "}", 0, null));
			}
			return new Completion(options, 0);
		}
		return match(partial, GROUND_DIRECTIONS, "{ground@", "}", null, true);
	}

	private static int lastMatchEnd(Pattern pattern, String text)
	{
		int end = -1;
		Matcher matcher = pattern.matcher(text);
		while (matcher.find())
		{
			end = matcher.end();
		}
		return end;
	}
}
