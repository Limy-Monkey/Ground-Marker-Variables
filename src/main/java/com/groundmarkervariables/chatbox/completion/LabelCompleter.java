package com.groundmarkervariables.chatbox.completion;

import com.google.gson.Gson;
import com.groundmarkervariables.GroundMarkerVariablesConfig;
import com.groundmarkervariables.variables.LabelResolver;
import com.groundmarkervariables.variables.NumericVariableRegistry;
import com.groundmarkervariables.variables.label.LootLabelVariable;
import com.groundmarkervariables.variables.support.BossAliases;
import com.groundmarkervariables.variables.support.BossHiscoreSkill;
import com.groundmarkervariables.variables.support.CollectionLog;
import com.groundmarkervariables.variables.support.Emojis;
import com.groundmarkervariables.variables.support.IconNames;
import com.groundmarkervariables.variables.support.NamedColors;
import com.groundmarkervariables.variables.support.WeaponStyleTracker;
import java.time.Instant;
import java.util.AbstractMap.SimpleEntry;
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
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.inject.Inject;
import net.runelite.api.Skill;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;

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
	private static final String BOSS_ICON = "boss:";
	// Icon syntax of a color swatch: "color:RRGGBB".
	private static final String COLOR_ICON = "color:";
	// The first icon tag in a resolved icon.
	private static final Pattern ICON_TAG = Pattern.compile("<(?:(sprite|item|npc)=(\\d+)|(emoji)=([0-9a-f]+))");

	// Names that need more typed before the token is complete, so an exact one isn't closed with "}".
	private static final Set<String> VARIABLE_NAMES_NEEDING_MORE = VariableCatalog.REQUIRING_ARGUMENT;

	// {lvl_<skill>} / {boost_<skill>} / {xpRate_<skill>} / {xp_<skill>}.
	private static final List<String> SKILL_PREFIXES = List.of("lvl_", "boost_", "xpRate_", "xp_");
	// {lvl_combat} / {lvl_total}: not real skills, and only for {lvl_}.
	private static final List<String> LEVEL_EXTRAS = VariableCatalog.completions("lvl_");

	// {equip_<slot>}: EquipLabelVariable's slots, plus "quiver".
	private static final String EQUIP_PREFIX = "equip_";
	private static final List<String> EQUIP_SLOTS = VariableCatalog.completions(EQUIP_PREFIX);

	// {kc <boss>} / {pb <boss>} / {rank <boss>}.
	private static final String KC_PREFIX = "kc ";
	private static final String PB_PREFIX = "pb ";
	private static final String RANK_PREFIX = "rank ";
	// Trackable activities with no hiscores entry or BossAliases short form.
	private static final List<String> EXTRA_BOSS_NAMES = List.of(
		"Jubbly Jive", "Tempor Tantrum", "Gwenith Glide", "Galvek", "Obor chests opened", "Agility Arena Total Ticket");

	// {party <template>}: nothing completed, so its parameter hint shows. {dps <template>}: the keyword being typed;
	// <N>s isn't completed.
	private static final String PARTY_PREFIX = "party ";
	private static final String DPS_PREFIX = "dps ";
	private static final List<String> DPS_KEYWORDS = VariableCatalog.completions("dps");
	private static final Pattern TEMPLATE_WORD = Pattern.compile("[a-zA-Z]*$");

	// {divine <potion>} / {active <effect>}.
	private static final String DIVINE_PREFIX = "divine ";
	// {quickPrayers <name>}: the standard prayer book's prayers.
	private static final String QUICK_PRAYERS_PREFIX = "quickPrayers ";
	private static final List<String> PRAYERS = VariableCatalog.completions("quickPrayers");
	private static final List<String> DIVINE_POTIONS = VariableCatalog.completions("divine");
	private static final String ACTIVE_PREFIX = "active ";
	private static final List<String> ACTIVE_EFFECTS = VariableCatalog.completions("active");

	// {hasBarrage <element> ... [icon]} / {hasSurge ...}: each word once. "air" is {hasSurge}'s alias for "wind".
	private static final Pattern SPELL_SET_WORDS = Pattern.compile("(?i)^(hasBarrage|hasSurge)((?:\\s+\\w+)*?)\\s+(\\w*)$");
	private static final Map<String, List<String>> SPELL_SET_OPTIONS = Map.of(
		"hasbarrage", VariableCatalog.completions("hasBarrage"),
		"hassurge", VariableCatalog.completions("hasSurge"));
	private static final Map<String, String> SPELL_SET_ALIASES = Map.of("air", "wind", "wind", "air");

	// {attackStyle, weapon=<name>} / {attackType, weapon=<name>}: names from WeaponStyleTracker.
	private static final Pattern WEAPON_OPTION = Pattern.compile("(?i)^(attack(?:Style|Type)(?:\\s*,\\s*|\\s+))(\\w*)$");
	private static final Pattern WEAPON_VALUE = Pattern.compile("(?i)^attack(?:Style|Type)\\s*,?\\s*weapon\\s*=\\s*(.*)$");
	private static final String WEAPON_OPTION_NAME = "weapon=";

	// {worldInfo hideWorld}.
	private static final Pattern WORLD_INFO_OPTION = Pattern.compile("(?i)^worldInfo(?:\\s*,\\s*|\\s+)(\\w*)$");
	private static final String HIDE_WORLD = "hideWorld";

	// {hasFreeze icon}, {equip_helm icon}, {active thrall icon}, ...: the icon variables and their aliases ({hasSurge}
	// and {hasBarrage} complete "icon" among their words).
	private static final Pattern ICON_OPTION = Pattern.compile("(?i)^(?:" + ICON_VARIABLES.stream()
		.filter(name -> !SPELL_SET_OPTIONS.containsKey(name.toLowerCase(Locale.ROOT)))
		.flatMap(name -> Stream.concat(Stream.of(name), VariableCatalog.ALIASES.getOrDefault(name, List.of()).stream()))
		.map(Pattern::quote).collect(Collectors.joining("|")) + "|active\\s+\\w+|equip_\\w+)\\s+(\\w*)$");
	private static final String ICON = "icon";

	// {loot <monster>[, items=N][, lines=N][, itemsOnly]}: monsters Loot Tracker has recorded, most recent first.
	private static final String LOOT_PREFIX = "loot ";
	private static final String LOOT_TRACKER_GROUP = "loottracker";
	private static final String LOOT_KEY_PREFIX = "drops_";
	private static final Pattern LOOT_OPTION = Pattern.compile("\\s*,?\\s*(?:itemsOnly|(?:items|lines)=\\d+)", Pattern.CASE_INSENSITIVE);
	// "lines" completes straight to lines=2, since 1 is the default.
	private static final List<String> LOOT_OPTIONS = VariableCatalog.completions("loot");

	// {log <page>[, missing][, noicons][, iconsOnly][, itemsPerLine=N][, maxItems=N]}: the collection log's pages.
	private static final String LOG_PREFIX = "log ";
	private static final Pattern LOG_OPTION = Pattern.compile("\\s*,\\s*(?:missing|noicons|iconsOnly|(?:itemsPerLine|maxItems)=\\d+)",
		Pattern.CASE_INSENSITIVE);
	private static final List<String> LOG_OPTIONS = VariableCatalog.completions("log");

	// ":<partial emoji name>" at the cursor. Not after a name character, so ":name:", "9:30" and " : " never start one.
	private static final Pattern EMOJI_PARTIAL = Pattern.compile("(?:^|[^a-z0-9_!().\\-ñ:]):([a-z0-9_!().\\-ñ]+)$");
	// "<npc=<partial name>" / "<item=..." / "<sprite=..." at the cursor (see IconNames), and "<it" -> "<item=" (not
	// right after a name character, so "{hp<sp...}" stays a comparison).
	private static final Pattern ICON_NAME_PARTIAL = Pattern.compile("<(npc|item|sprite)=([^<>{}=,@]*)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern ICON_TAG_PARTIAL = Pattern.compile("(?:^|[^a-z0-9_])<([a-z]+)$", Pattern.CASE_INSENSITIVE);
	private static final List<String> ICON_TAGS = List.of("item=", "npc=", "sprite=");
	// How many NPC/item/sprite names the suggestion list shows.
	private static final int MAX_ICON_NAMES = 50;

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

	// {ground@<dir>}: where the top of the {ground} content faces.
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
	private final ConfigManager configManager;
	private final Gson gson;
	private final Emojis emojis;
	private final WeaponStyleTracker weaponStyleTracker;
	private final IconNames iconNames;
	private final CollectionLog collectionLog;
	private final LabelResolver labelResolver;
	private final RecentUsage recent = new RecentUsage();
	private final List<Rule> rules;
	// Each option's icon, by its icon syntax: worked out once per editor (this completer's lifetime).
	private final Map<String, Optional<Completion.Icon>> icons = new HashMap<>();

	@Inject
	private LabelCompleter(GroundMarkerVariablesConfig config, ConfigManager configManager, Gson gson, Emojis emojis,
		WeaponStyleTracker weaponStyleTracker, IconNames iconNames, CollectionLog collectionLog, LabelResolver labelResolver)
	{
		this.config = config;
		this.configManager = configManager;
		this.gson = gson;
		this.emojis = emojis;
		this.weaponStyleTracker = weaponStyleTracker;
		this.iconNames = iconNames;
		this.collectionLog = collectionLog;
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
				return icon(spriteNamed(name));
			}
			Completion.Icon icon = new Completion.Icon(Completion.Icon.Kind.SPRITE, String.valueOf(sprite));
			icons.put(syntax, Optional.of(icon));
			return icon;
		}

		String expanded = iconNames.expand(emojis.expand(syntax));
		Matcher tag = ICON_TAG.matcher(labelResolver.resolve(expanded));
		Completion.Icon icon = null;
		if (tag.find())
		{
			String kind = tag.group(1) != null ? tag.group(1) : tag.group(3);
			icon = new Completion.Icon(Completion.Icon.Kind.valueOf(kind.toUpperCase(Locale.ROOT)), tag.group(1) != null ? tag.group(2) : tag.group(4));
		}
		if (icon != null || !expanded.equals(syntax) || !syntax.startsWith("<"))
		{
			icons.put(syntax, Optional.ofNullable(icon));
		}
		return icon;
	}

	// completion with icon(label) as each option's icon syntax.
	@Nullable
	private static Completion withIcons(@Nullable Completion completion, Function<String, String> icon)
	{
		return completion == null ? null : completion.withIcons(icon);
	}

	// A named sprite tag, for IconNames to expand.
	private static String spriteNamed(String name)
	{
		return "<sprite=" + name + ">";
	}

	// The freely typed parameters, "name: description", of the variable whose arguments are being typed at cursor,
	// e.g. "{showOneItem " -> "<a>, <b>, …: Parts of item names, …". Empty if none.
	public List<String> parameterHints(String text, int cursor)
	{
		String before = text.substring(0, cursor);
		int open = before.lastIndexOf('{');
		if (open < 0 || before.indexOf('}', open) >= 0)
		{
			return List.of();
		}
		String partial = before.substring(open + 1);
		if (!partial.isEmpty() && "^&*".indexOf(partial.charAt(0)) >= 0)
		{
			partial = partial.substring(1);
		}
		int boundaryEnd = Math.max(lastMatchEnd(MATH_OPERATOR_BOUNDARY, partial), lastMatchEnd(CONDITION_BOUNDARY, partial));
		return VariableCatalog.hints(boundaryEnd >= 0 ? partial.substring(boundaryEnd).stripLeading() : partial);
	}

	// What completes the token ending at cursor in text, or null. Not mid-word: completing there would run into what
	// follows.
	@Nullable
	public Completion complete(String text, int cursor)
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

		Matcher emoji = EMOJI_PARTIAL.matcher(before);
		if (emoji.find())
		{
			return completeEmoji(emoji.group(1));
		}

		int open = before.lastIndexOf('{');
		if (open < 0 || before.indexOf('}', open) >= 0)
		{
			return null;
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
		for (String prefix : SKILL_PREFIXES)
		{
			list.add(prefix(prefix, true, rest -> completeSkill(rest, prefix)));
		}
		list.add(pattern(SPELL_SET_WORDS, this::completeSpellSet));
		list.add(pattern(ICON_OPTION, m -> described(completeWord(m.group(1), ICON, true),
			m.group().startsWith(EQUIP_PREFIX) ? EQUIP_PREFIX : m.group().split("\\s+")[0])));
		list.add(prefix(EQUIP_PREFIX, false, rest -> described(withIcons(match(rest, EQUIP_SLOTS, "{" + EQUIP_PREFIX, "", "}", false),
			slot -> "{" + EQUIP_PREFIX + slot + " icon}"), EQUIP_PREFIX)));
		list.add(prefix(KC_PREFIX, true, rest -> completeBoss(rest, KC_PREFIX, false)));
		list.add(prefix(RANK_PREFIX, false, rest -> completeBoss(rest, RANK_PREFIX, true)));
		list.add(prefix(PARTY_PREFIX, false, rest -> null));
		list.add(prefix(DPS_PREFIX, false, rest -> completeTemplateKeyword(rest, DPS_KEYWORDS, DPS_PREFIX)));
		list.add(prefix(DIVINE_PREFIX, false, rest -> described(match(rest, DIVINE_POTIONS, "{" + DIVINE_PREFIX, "", "}", true), DIVINE_PREFIX)));
		list.add(prefix(QUICK_PRAYERS_PREFIX, false, rest -> described(withIcons(match(rest, PRAYERS, "{" + QUICK_PRAYERS_PREFIX, "", "}", true),
			LabelCompleter::spriteNamed), QUICK_PRAYERS_PREFIX)));
		list.add(prefix(ACTIVE_PREFIX, false, rest -> described(withIcons(match(rest, ACTIVE_EFFECTS, "{" + ACTIVE_PREFIX, "", "}", true),
			effect -> "{" + ACTIVE_PREFIX + effect + " icon}"), ACTIVE_PREFIX)));
		list.add(pattern(WEAPON_VALUE, m -> withIcons(
			match(m.group(1), weaponStyleTracker.weaponNames(), "{" + m.group().substring(0, m.start(1)), "", "}", true),
			name -> "<item=" + name + ">")));
		list.add(pattern(WORLD_INFO_OPTION, m -> described(completeWord(m.group(1), HIDE_WORLD, true), "worldInfo")));
		list.add(pattern(WEAPON_OPTION, m -> described(completeWord(m.group(2), WEAPON_OPTION_NAME, false),
			m.group(1).replaceAll("[\\s,]+$", ""))));
		list.add(prefix(PB_PREFIX, false, rest -> completeBoss(rest, PB_PREFIX, false)));
		list.add(prefix(LOOT_PREFIX, true, this::completeLootMonster));
		list.add(prefix(LOG_PREFIX, true, this::completeLogPage));
		list.add(prefix(METRONOME_NAME, false, this::completeMetronomeParams));
		// "m" alias: only once a digit or +/- follows, so "{mis..." still completes to miscellania.
		list.add((partial, numeric) -> numeric || partial.length() < 2 || !partial.regionMatches(true, 0, METRONOME_ALIAS, 0, 1)
			|| !(Character.isDigit(partial.charAt(1)) || partial.charAt(1) == '+' || partial.charAt(1) == '-')
			? null : orNone(completeMetronomeParams(partial.substring(1))));
		list.add(prefix(COLOR_PREFIX, false, this::completeColorValue));
		list.add(prefix(TILECOL_PREFIX, false, this::completeTileColValue));
		list.add(prefix(HIGHLIGHT_NAME, false, this::completeHighlight));
		list.add(prefix(ALIGN_PREFIX, false, rest -> match(rest, ALIGN_VALUES, "{" + ALIGN_PREFIX, "}", null, true)));
		list.add(pattern(GROUND_FACING, m -> completeGroundFacing(m.group(1))));
		return list;
	}

	// Applies to text starting with prefix (any case), completing what follows it; inMath: also after a math operator.
	private static Rule prefix(String prefix, boolean inMath, Function<String, Completion> complete)
	{
		return (partial, numeric) -> (numeric && !inMath) || !partial.regionMatches(true, 0, prefix, 0, prefix.length())
			? null : orNone(complete.apply(partial.substring(prefix.length())));
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

	// A fixed option word: closes with "}" once exactly typed (if closes), else completes to it.
	@Nullable
	private static Completion completeWord(String word, String option, boolean closes)
	{
		if (closes && word.equalsIgnoreCase(option))
		{
			return Completion.closer("}");
		}
		return option.regionMatches(true, 0, word, 0, word.length()) && word.length() < option.length()
			? Completion.single(option, option.substring(word.length()), null) : null;
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

	private Completion completeSkill(String partial, String prefix)
	{
		List<String> names = new ArrayList<>();
		for (Skill skill : Skill.values())
		{
			names.add(skill.getName().toLowerCase());
		}
		if ("lvl_".equals(prefix))
		{
			names.addAll(LEVEL_EXTRAS);
		}
		return described(withIcons(match(partial, names, "{" + prefix, "", "}", false), skill -> LEVEL_EXTRAS.contains(skill) ? null : spriteNamed(skill)),
			prefix);
	}

	// Each spell word once ("air"/"wind" counting as one).
	private Completion completeSpellSet(Matcher spellSet)
	{
		List<String> used = List.of(spellSet.group(2).trim().toLowerCase(Locale.ROOT).split("\\s+"));
		List<String> options = new ArrayList<>();
		for (String option : SPELL_SET_OPTIONS.get(spellSet.group(1).toLowerCase(Locale.ROOT)))
		{
			if (!used.contains(option) && !used.contains(SPELL_SET_ALIASES.getOrDefault(option, option)))
			{
				options.add(option);
			}
		}
		return described(withIcons(match(spellSet.group(3), options, "{" + spellSet.group().substring(0, spellSet.start(3)), "", "}", true),
			word -> ICON.equals(word) ? null : "{" + spellSet.group(1) + " " + word + " icon}"), spellSet.group(1));
	}

	// Hiscores bosses (no "The ", no colon, as BossKillCountTracker keys them), BossAliases' short forms and names, and
	// EXTRA_BOSS_NAMES. hiscoresOnly ({rank}): only bosses on the hiscores.
	private Completion completeBoss(String partial, String prefix, boolean hiscoresOnly)
	{
		Set<String> names = new LinkedHashSet<>();
		for (HiscoreSkill skill : HiscoreSkill.values())
		{
			if (skill.getType() == HiscoreSkillType.BOSS)
			{
				String name = skill.getName();
				names.add((name.regionMatches(true, 0, "The ", 0, 4) ? name.substring(4) : name).replace(":", ""));
			}
		}
		for (String alias : BossAliases.aliases())
		{
			if (!alias.contains(" - ") && (!hiscoresOnly || BossHiscoreSkill.find(BossAliases.resolve(alias)) != null))
			{
				names.add(alias);
			}
		}
		for (String name : BossAliases.canonicalNames())
		{
			if (!hiscoresOnly || BossHiscoreSkill.find(name) != null)
			{
				names.add(name);
			}
		}
		if (!hiscoresOnly)
		{
			names.addAll(EXTRA_BOSS_NAMES);
		}
		return withIcons(match(partial, names, "{" + prefix, "", "}", false), LabelCompleter::bossIcon);
	}

	// Loot Tracker's monsters (and their BossAliases short names, combined monsters, and players if any were
	// killed); after a full name, an option.
	private Completion completeLootMonster(String partial)
	{
		List<String> monsters = lootMonsters();
		Set<String> names = new LinkedHashSet<>();
		for (String monster : monsters)
		{
			names.add(monster);
			for (String alias : BossAliases.aliases())
			{
				if (!alias.contains(" - ") && !alias.equalsIgnoreCase(monster) && BossAliases.resolve(alias).equalsIgnoreCase(monster))
				{
					names.add(alias);
				}
			}
		}
		for (Map.Entry<String, List<String>> combined : LootLabelVariable.COMBINED_MONSTERS.entrySet())
		{
			if (monsters.stream().anyMatch(monster -> combined.getValue().stream().anyMatch(monster::equalsIgnoreCase)))
			{
				names.add(combined.getKey());
			}
		}
		String playerKeyPrefix = LOOT_KEY_PREFIX + LootLabelVariable.PLAYER_TYPE + "_";
		if (!configManager.getRSProfileConfigurationKeys(LOOT_TRACKER_GROUP, configManager.getRSProfileKey(), playerKeyPrefix).isEmpty())
		{
			names.addAll(LootLabelVariable.PLAYER_NAMES);
		}

		Completion name = withIcons(match(partial, names, "{" + LOOT_PREFIX, "", "}", false), LabelCompleter::bossIcon);
		int split = Math.max(partial.lastIndexOf(' '), partial.lastIndexOf(','));
		if (split >= 0)
		{
			String head = LOOT_OPTION.matcher(partial.substring(0, split)).replaceAll("").replaceAll("[\\s,]+$", "");
			String word = partial.substring(split + 1);
			// With nothing typed yet, a longer name (e.g. "tob" -> "tob hm") still comes first.
			if (names.stream().anyMatch(head::equalsIgnoreCase) && !(word.isEmpty() && name != null && name.preferred >= 0))
			{
				Completion option = described(completeOption(word, LOOT_OPTIONS, !word.isEmpty() && LOOT_OPTION.matcher(word).matches()),
					LOOT_PREFIX);
				if (option != null)
				{
					return option;
				}
			}
		}
		return name;
	}

	// Every monster Loot Tracker has recorded loot for, most recent first.
	private List<String> lootMonsters()
	{
		String profile = configManager.getRSProfileKey();
		List<Map.Entry<String, Instant>> byLast = new ArrayList<>();
		for (String key : configManager.getRSProfileConfigurationKeys(LOOT_TRACKER_GROUP, profile, LOOT_KEY_PREFIX))
		{
			// After the "<TYPE>_" (NPC/EVENT/PLAYER/...).
			String rest = key.substring(LOOT_KEY_PREFIX.length());
			int typeEnd = rest.indexOf('_');
			if (typeEnd < 0)
			{
				continue;
			}
			Instant last = gson.fromJson(configManager.getConfiguration(LOOT_TRACKER_GROUP, profile, key), LootLast.class).last;
			if (last != null)
			{
				byLast.add(new SimpleEntry<>(rest.substring(typeEnd + 1), last));
			}
		}
		byLast.sort((a, b) -> b.getValue().compareTo(a.getValue()));
		return byLast.stream().map(Map.Entry::getKey).collect(Collectors.toList());
	}

	// The "last" field of Loot Tracker's (package-private) ConfigLoot, for Gson.
	private static final class LootLast
	{
		Instant last;
	}

	// The collection log's page names (and BossAliases short names for one); after a full name and a ",", an option.
	@Nullable
	private Completion completeLogPage(String partial)
	{
		List<String> pages = collectionLog.pageNames();
		Set<String> names = new LinkedHashSet<>(pages);
		for (String alias : BossAliases.aliases())
		{
			String boss = BossAliases.resolve(alias);
			if (!alias.contains(" - ") && !alias.equalsIgnoreCase(boss) && pages.stream().anyMatch(boss::equalsIgnoreCase))
			{
				names.add(alias);
			}
		}

		int comma = partial.lastIndexOf(',');
		if (comma >= 0)
		{
			String head = LOG_OPTION.matcher(partial.substring(0, comma)).replaceAll("").trim();
			String word = partial.substring(comma + 1).trim();
			return names.stream().anyMatch(head::equalsIgnoreCase)
				? described(completeOption(word, LOG_OPTIONS, !word.isEmpty() && LOG_OPTION.matcher(", " + word).matches()), LOG_PREFIX) : null;
		}
		return withIcons(match(partial, names, "{" + LOG_PREFIX, "", "}", false), LabelCompleter::bossIcon);
	}

	// A boss, monster or log page's hiscores icon, by its full name (see icon()).
	private static String bossIcon(String name)
	{
		return BOSS_ICON + BossAliases.resolve(name);
	}

	// {party}/{dps} template: only the trailing word, with no closer since more may follow.
	@Nullable
	private Completion completeTemplateKeyword(String template, List<String> keywords, String prefix)
	{
		Matcher word = TEMPLATE_WORD.matcher(template);
		word.find();
		// Empty only right after a separator (e.g. "num."): nothing new typed yet, unlike "{party ".
		if (word.group().isEmpty() && !template.isEmpty())
		{
			return null;
		}
		return described(match(word.group(), keywords, "{" + prefix, "", null, true), prefix);
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
		if (partial.isEmpty() || partial.chars().allMatch(Character::isDigit))
		{
			return null;
		}
		List<String> names = iconNames.matches(kind, partial, MAX_ICON_NAMES);
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
		return Completion.of(starting, partial.length(), closer, starting.isEmpty() ? null : starting.get(0))
			.withContained(contained, partial.length(), closer)
			.withIcons(name -> "<" + kind.tag + "=" + name + ">");
	}

	// Shortest name first unless recent/nearby labels use one; ":" closes it.
	@Nullable
	private Completion completeEmoji(String partial)
	{
		Matches matches = matches(partial, emojis.names());
		matches.names.sort(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()));
		matches.contained.sort(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()));
		return withIcons(complete(matches, partial, ":", ":", ":", true), name -> ":" + name + ":");
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
