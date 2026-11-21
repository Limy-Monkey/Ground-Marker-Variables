package com.groundmarkervariables.chatbox.completion;

import com.google.gson.Gson;
import com.groundmarkervariables.GroundMarkerVariablesConfig;
import com.groundmarkervariables.graphics.SplitIcons;
import com.groundmarkervariables.variables.LabelResolver;
import com.groundmarkervariables.variables.NumericVariableRegistry;
import com.groundmarkervariables.variables.label.GeHistoryLabelVariable;
import com.groundmarkervariables.variables.support.BossAliases;
import com.groundmarkervariables.variables.support.BossHiscoreSkill;
import com.groundmarkervariables.variables.support.CollectionLog;
import com.groundmarkervariables.variables.support.Emojis;
import com.groundmarkervariables.variables.support.IconNames;
import com.groundmarkervariables.variables.support.ItemCharges;
import com.groundmarkervariables.variables.support.LootTrackerRecords;
import com.groundmarkervariables.variables.support.NamedColors;
import com.groundmarkervariables.variables.support.WeaponStyleTracker;
import java.time.Instant;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
import net.runelite.client.game.ItemManager;
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
	private static final Pattern ICON_TAG = Pattern.compile("<(?:(sprite|item|npc)=(\\d+)(,head)?|(emoji)=([0-9a-f]+))");
	private static final String SPLIT_OPEN = "{split}";
	private static final String SPLIT_CLOSE = "{/split}";

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

	// {charges <item>}: items Item Charges Improved / Item Charges have saved charges for, while they're on.
	private static final String CHARGES_PREFIX = "charges ";
	private static final String PRICE_PREFIX = "price ";
	// {geHistory ...}: comma-separated options, the first after a space.
	private static final String GE_HISTORY = "geHistory";
	private static final Pattern GE_HISTORY_OPTIONS = Pattern.compile("(?i)^geHistory(\\s.*)$");
	private static final Pattern GE_HISTORY_WORD = Pattern.compile("(?i)bought|sold|qty|profit|investment|limitTransactions=\\d+");
	// At most one of each.
	private static final List<List<String>> GE_HISTORY_EXCLUSIVE = List.of(List.of("bought", "sold"), List.of("qty", "profit", "investment"));
	private static final List<String> GE_HISTORY_WORDS = VariableCatalog.completions(GE_HISTORY);
	private static final List<String> GE_HISTORY_START_DATES = List.of("today", "-7 days", "-30 days");
	private static final String CHARGES_ICONS = "icons";

	// {party <template>}: nothing completed, so its parameter hint shows.
	private static final String PARTY_PREFIX = "party ";

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

	// {loot <monster>[, items=N][, itemsPerLine=N][, oneline][, hideVal][, showItem=<name>]...[, itemsOnly]}: monsters Loot Tracker has recorded, most
	// recent first. The old lines=N still counts as an option, so it isn't taken as part of a name.
	private static final String LOOT_PREFIX = "loot ";
	private static final String LOOT_SESSION_PREFIX = "lootSession ";
	private static final String LOOT_TRACKER_GROUP = "loottracker";
	private static final String LOOT_KEY_PREFIX = "drops_";
	private static final Pattern LOOT_OPTION = Pattern.compile("\\s*,?\\s*(?:itemsOnly|oneline|hideVal|showItem=[^,{}?:<>=!]*|(?:items|itemsPerLine|lines)=\\d+)",
		Pattern.CASE_INSENSITIVE);
	private static final List<String> LOOT_OPTIONS = VariableCatalog.completions("loot");
	// "<monster and options>, showItem=<value being typed>".
	private static final Pattern SHOW_ITEM_VALUE = Pattern.compile("(?i)^(.*?),\\s*showItem=([^,{}?:<>=!]*)$");

	// {log <page>[, missing][, noicons][, iconsOnly][, itemsPerLine=N][, maxItems=N]}: the collection log's pages.
	private static final String LOG_PREFIX = "log ";
	private static final Pattern LOG_OPTION = Pattern.compile("\\s*,\\s*(?:missing|noicons|iconsOnly|(?:itemsPerLine|maxItems)=\\d+)",
		Pattern.CASE_INSENSITIVE);
	private static final List<String> LOG_OPTIONS = VariableCatalog.completions("log");

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
	private final ConfigManager configManager;
	private final Gson gson;
	private final Emojis emojis;
	private final WeaponStyleTracker weaponStyleTracker;
	private final IconNames iconNames;
	private final CollectionLog collectionLog;
	private final ItemCharges itemCharges;
	private final LootTrackerRecords lootTrackerRecords;
	private final LabelResolver labelResolver;
	private final ItemManager itemManager;
	private final GeHistoryLabelVariable geHistory;
	private final RecentUsage recent = new RecentUsage();
	private final List<Rule> rules;
	// Each option's icon, by its icon syntax: worked out once per editor (this completer's lifetime).
	private final Map<String, Optional<Completion.Icon>> icons = new HashMap<>();
	// {charges <item>,}: the names of what each container holds, by lowercase item (see completeChargesOption()).
	private final Map<String, List<String>> storedItemNames = new ConcurrentHashMap<>();
	// {loot <monster>, showItem=}: the names of what each monster dropped, by lowercase monster (see completeLootedItem()).
	private final Map<String, List<String>> lootedItemNames = new ConcurrentHashMap<>();

	@Inject
	private LabelCompleter(GroundMarkerVariablesConfig config, ConfigManager configManager, Gson gson, Emojis emojis,
		WeaponStyleTracker weaponStyleTracker, IconNames iconNames, CollectionLog collectionLog, ItemCharges itemCharges,
		LootTrackerRecords lootTrackerRecords, LabelResolver labelResolver, ItemManager itemManager, GeHistoryLabelVariable geHistory)
	{
		this.itemManager = itemManager;
		this.geHistory = geHistory;
		this.config = config;
		this.configManager = configManager;
		this.gson = gson;
		this.emojis = emojis;
		this.weaponStyleTracker = weaponStyleTracker;
		this.iconNames = iconNames;
		this.collectionLog = collectionLog;
		this.itemCharges = itemCharges;
		this.lootTrackerRecords = lootTrackerRecords;
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
		return token == null ? List.of() : VariableCatalog.hints(token);
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
		list.add(prefix(CHARGES_PREFIX, true, this::completeCharges));
		list.add(pattern(GE_HISTORY_OPTIONS, m -> completeGeHistory(m.group(1))));
		// RuneLite's price list: every tradeable item.
		list.add(prefix(PRICE_PREFIX, true, rest -> described(withIcons(match(rest,
			itemManager.search("").stream().map(item -> item.getName()).distinct().collect(Collectors.toList()), "{" + PRICE_PREFIX, "", "}", true),
			item -> "<item=" + item + ">"), PRICE_PREFIX)));
		list.add(prefix(RANK_PREFIX, false, rest -> completeBoss(rest, RANK_PREFIX, true)));
		list.add(prefix(PARTY_PREFIX, false, rest -> null));
		list.add(prefix(DIVINE_PREFIX, false, rest -> described(match(rest, DIVINE_POTIONS, "{" + DIVINE_PREFIX, "", "}", true), DIVINE_PREFIX)));
		list.add(prefix(QUICK_PRAYERS_PREFIX, false, rest -> described(withIcons(match(rest, PRAYERS, "{" + QUICK_PRAYERS_PREFIX, "", "}", true),
			prayer -> spriteNamed(prayer, "Prayer")), QUICK_PRAYERS_PREFIX)));
		list.add(prefix(ACTIVE_PREFIX, false, rest -> described(withIcons(match(rest, ACTIVE_EFFECTS, "{" + ACTIVE_PREFIX, "", "}", true),
			effect -> "{" + ACTIVE_PREFIX + effect + " icon}"), ACTIVE_PREFIX)));
		list.add(pattern(WEAPON_VALUE, m -> withIcons(
			match(m.group(1), weaponStyleTracker.weaponNames(), "{" + m.group().substring(0, m.start(1)), "", "}", true),
			name -> "<item=" + name + ">")));
		list.add(pattern(WORLD_INFO_OPTION, m -> described(completeWord(m.group(1), HIDE_WORLD, true), "worldInfo")));
		list.add(pattern(WEAPON_OPTION, m -> described(completeWord(m.group(2), WEAPON_OPTION_NAME, false),
			m.group(1).replaceAll("[\\s,]+$", ""))));
		list.add(prefix(PB_PREFIX, false, rest -> completeBoss(rest, PB_PREFIX, false)));
		list.add(prefix(LOOT_PREFIX, true, rest -> completeLootMonster(rest, LOOT_PREFIX)));
		list.add(prefix(LOOT_SESSION_PREFIX, true, rest -> completeLootMonster(rest, LOOT_SESSION_PREFIX)));
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
		return described(withIcons(match(partial, names, "{" + prefix, "", "}", false), skill -> LEVEL_EXTRAS.contains(skill) ? null : spriteNamed(skill, "Skill")),
			prefix);
	}

	// The option being typed after "geHistory" (those not already typed; one per GE_HISTORY_EXCLUSIVE group), or the
	// value after item= (traded items) or startDate=; "}" once one is complete.
	@Nullable
	private Completion completeGeHistory(String rest)
	{
		int comma = rest.lastIndexOf(',');
		String typed = comma < 0 ? "" : rest.substring(0, comma).toLowerCase(Locale.ROOT);
		String word = rest.substring(comma + 1).replaceFirst("^\\s+", "");
		String lower = word.toLowerCase(Locale.ROOT);
		if (lower.startsWith("item="))
		{
			return described(withIcons(match(word.substring("item=".length()), geHistory.itemNames(), "{" + GE_HISTORY + " item=", "", "}", true),
				item -> "<item=" + item + ">"), GE_HISTORY);
		}
		if (lower.startsWith("startdate="))
		{
			return described(match(word.substring("startDate=".length()), GE_HISTORY_START_DATES, "{" + GE_HISTORY + " startDate=", "", "}", true),
				GE_HISTORY);
		}
		List<String> unused = GE_HISTORY_WORDS.stream()
			.filter(option -> !typed.contains(option.toLowerCase(Locale.ROOT)))
			.filter(option -> GE_HISTORY_EXCLUSIVE.stream().noneMatch(group -> group.contains(option) && group.stream().anyMatch(typed::contains)))
			.collect(Collectors.toList());
		return described(completeOption(word, unused, GE_HISTORY_WORD.matcher(word).matches()), GE_HISTORY);
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

	// The items with saved charges, by their in-game names where one matches (see IconNames#itemNamed).
	private Completion completeCharges(String partial)
	{
		int comma = partial.indexOf(',');
		if (comma >= 0)
		{
			return completeChargesOption(partial.substring(0, comma).trim(), partial.substring(comma + 1));
		}
		Set<String> names = new LinkedHashSet<>();
		for (String name : itemCharges.names())
		{
			String item = iconNames.itemNamed(name);
			names.add(item != null ? item : name);
		}
		Completion name = withIcons(match(partial, names, "{" + CHARGES_PREFIX, "", "}", true), item -> "<item=" + item + ">");

		// A full name (any case and apostrophes) also offers its options first, after ", "; Tab still does what it did.
		String typed = partial.trim().toLowerCase(Locale.ROOT).replace("'", "");
		if (names.stream().noneMatch(item -> item.toLowerCase(Locale.ROOT).replace("'", "").equals(typed)))
		{
			return name;
		}
		List<String> words = chargesOptions(partial.trim());
		List<Completion.Option> options = new ArrayList<>();
		for (String word : words)
		{
			options.add(new Completion.Option(", " + word, ", " + word, 0, chargesOptionIcon(word),
				VariableCatalog.completionDescription("charges", word)));
		}
		if (name == null)
		{
			return new Completion(options, -1);
		}
		options.addAll(name.options);
		return new Completion(options, name.preferred < 0 ? -1 : name.preferred + words.size());
	}

	// After "<item>,": its options not yet given (see chargesOptions()).
	@Nullable
	private Completion completeChargesOption(String item, String options)
	{
		int last = options.lastIndexOf(',');
		String word = options.substring(last + 1).replaceFirst("^\\s+", "");
		Set<String> used = new HashSet<>();
		for (String option : options.substring(0, Math.max(0, last)).split(","))
		{
			used.add(option.trim().toLowerCase(Locale.ROOT));
		}
		List<String> fitting = chargesOptions(item);
		fitting.removeIf(option -> used.contains(option.toLowerCase(Locale.ROOT)));
		return withIcons(match(word, fitting, "{" + CHARGES_PREFIX, "", "}", false), LabelCompleter::chargesOptionIcon);
	}

	// item's options: "icon", then for Improved's containers "icons" and the names of what they hold. The names are
	// saved where they can be looked up (client thread) for Tab to use elsewhere.
	private List<String> chargesOptions(String item)
	{
		String key = item.toLowerCase(Locale.ROOT);
		List<String> stored = itemCharges.storedNames(item);
		if (stored != null)
		{
			storedItemNames.put(key, stored);
		}
		else
		{
			stored = storedItemNames.getOrDefault(key, List.of());
		}
		List<String> options = new ArrayList<>();
		options.add(ICON);
		if (!stored.isEmpty())
		{
			options.add(CHARGES_ICONS);
			options.addAll(stored);
		}
		return options;
	}

	// A stored item's icon; none for "icon" / "icons".
	@Nullable
	private static String chargesOptionIcon(String option)
	{
		return option.equals(ICON) || option.equals(CHARGES_ICONS) ? null : "<item=" + option + ">";
	}

	// Loot Tracker's monsters (and their BossAliases short names, combined monsters, and players if any were
	// killed); after a full name, an option. prefix is "loot " or "lootSession "; both take {loot}'s options.
	private Completion completeLootMonster(String partial, String prefix)
	{
		Matcher showItem = SHOW_ITEM_VALUE.matcher(partial);
		if (showItem.matches())
		{
			String monster = LOOT_OPTION.matcher(showItem.group(1)).replaceAll("").replaceAll("[\\s,]+$", "");
			return completeLootedItem(monster, showItem.group(2), prefix);
		}

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
		for (Map.Entry<String, List<String>> combined : LootTrackerRecords.COMBINED_MONSTERS.entrySet())
		{
			if (monsters.stream().anyMatch(monster -> combined.getValue().stream().anyMatch(monster::equalsIgnoreCase)))
			{
				names.add(combined.getKey());
			}
		}
		String playerKeyPrefix = LOOT_KEY_PREFIX + LootTrackerRecords.PLAYER_TYPE + "_";
		if (!configManager.getRSProfileConfigurationKeys(LOOT_TRACKER_GROUP, configManager.getRSProfileKey(), playerKeyPrefix).isEmpty())
		{
			names.addAll(LootTrackerRecords.PLAYER_NAMES);
		}

		Completion name = withIcons(match(partial, names, "{" + prefix, "", "}", false), LabelCompleter::bossIcon);
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
		return withOptions(name, partial, names, LOOT_OPTIONS, LOOT_PREFIX);
	}

	// showItem=<value>: the names of the items monster has dropped. Looked up where they can be (client thread) and
	// saved for Tab to use elsewhere.
	@Nullable
	private Completion completeLootedItem(String monster, String value, String prefix)
	{
		String key = monster.toLowerCase(Locale.ROOT);
		List<String> looted = lootTrackerRecords.itemNames(monster);
		if (looted != null)
		{
			lootedItemNames.put(key, looted);
		}
		else
		{
			looted = lootedItemNames.getOrDefault(key, List.of());
		}
		return withIcons(match(value.replaceFirst("^\\s+", ""), looted, "{" + prefix, "", null, false), item -> "<item=" + item + ">");
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
		return withOptions(withIcons(match(partial, names, "{" + LOG_PREFIX, "", "}", false), LabelCompleter::bossIcon), partial, names,
			LOG_OPTIONS, LOG_PREFIX);
	}

	// A full name typed (any case) also offers each of variable's options first, after ", " (", items="); Tab still
	// does what it did.
	@Nullable
	private static Completion withOptions(@Nullable Completion completion, String partial, Set<String> names, List<String> optionWords,
		String variable)
	{
		if (completion == null || names.stream().noneMatch(partial.trim()::equalsIgnoreCase))
		{
			return completion;
		}
		List<Completion.Option> options = new ArrayList<>();
		for (String word : optionWords)
		{
			options.add(new Completion.Option(", " + word, ", " + word, 0, null, VariableCatalog.completionDescription(variable, word)));
		}
		options.addAll(completion.options);
		return new Completion(options, completion.preferred < 0 ? -1 : completion.preferred + optionWords.size());
	}

	// A boss, monster or log page's hiscores icon, by its full name (see icon()).
	private static String bossIcon(String name)
	{
		return BOSS_ICON + BossAliases.resolve(name);
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
