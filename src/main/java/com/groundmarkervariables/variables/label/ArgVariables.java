package com.groundmarkervariables.variables.label;

import com.groundmarkervariables.variables.ArgVariable;
import com.groundmarkervariables.variables.Args;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.BossAliases;
import com.groundmarkervariables.variables.support.BossHiscoreSkill;
import com.groundmarkervariables.variables.support.BossKillCountTracker;
import com.groundmarkervariables.variables.support.ConditionEvaluator;
import com.groundmarkervariables.variables.support.GroupState;
import com.groundmarkervariables.variables.support.HiscoreRankLookup;
import com.groundmarkervariables.variables.support.IconNames;
import com.groundmarkervariables.variables.support.ItemQuantityFinder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.party.PartyMember;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.xptracker.XpTrackerPlugin;
import net.runelite.client.plugins.xptracker.XpTrackerService;
import net.runelite.client.util.QuantityFormatter;

// The one-argument variables, {name <arg>}, one line each (plus their variables.json entry). number() ones are
// MathVariable operands; .party() ones also resolve for other members in {party} rows.
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ArgVariables {
    // Chat Commands' own personal best data, read-only.
    private static final String PERSONAL_BEST_GROUP = "personalbest";
    private static final Pattern DOSE_COUNT = Pattern.compile("\\((\\d+)\\)");
    // Suggested for {numDoses}, without it: a name ending in "(N)".
    private static final Pattern DOSE_NAME = Pattern.compile("\\((\\d)\\)$");
    // oldschool.runescape.wiki/w/Experience, indexed by level - 1.
    private static final int[] LEVEL_XP = {
        0, 83, 174, 276, 388, 512, 650, 801, 969, 1154,
        1358, 1584, 1833, 2107, 2411, 2746, 3115, 3523, 3973, 4470,
        5018, 5624, 6291, 7028, 7842, 8740, 9730, 10824, 12031, 13363,
        14833, 16456, 18247, 20224, 22406, 24815, 27473, 30408, 33648, 37224,
        41171, 45529, 50339, 55649, 61512, 67983, 75127, 83014, 91721, 101333,
        111945, 123660, 136594, 150872, 166636, 184040, 203254, 224466, 247886, 273742,
        302288, 333804, 368599, 407015, 449428, 496254, 547953, 605032, 668051, 737627,
        814445, 899257, 992895, 1096278, 1210421, 1336443, 1475581, 1629200, 1798808, 1986068,
        2192818, 2421087, 2673114, 2951373, 3258594, 3597792, 3972294, 4385776, 4842295, 5346332,
        5902831, 6517253, 7195629, 7944614, 8771558, 9684577, 10692629, 11805606, 13034431, 14391160,
        15889109, 17542976, 19368992, 21385073, 23611006, 26068632, 28782069, 31777943, 35085654, 38737661,
        42769801, 47221641, 52136869, 57563718, 63555443, 70170840, 77474828, 85539082, 94442737, 104273167,
        115126838, 127110260, 140341028, 154948977, 171077457, 188884740
    };

    private final Client client;
    private final RichText richText;
    private final ConfigManager configManager;
    private final ItemManager itemManager;
    private final IconNames iconNames;
    private final BossKillCountTracker killCounts;
    private final HiscoreRankLookup hiscores;
    private final GroupState groupState;
    private final XpTrackerService xpTrackerService;
    private final PluginManager pluginManager;
    // {numDoses} suggestion -> its highest dose's name, e.g. "Prayer potion" -> "Prayer potion(4)" (see doseNames()).
    private volatile Map<String, String> highestDose = Map.of();

    private final List<ArgVariable> all = define();

    // Built before the fields above are set: they're only read when a lambda runs.
    private List<ArgVariable> define() {
        return List.of(
            number("kc", boss -> Objects.requireNonNullElse(killCounts.getKc(BossAliases.resolve(boss)), 0),
                (boss, v) -> richText.labeled(BossAliases.resolve(boss) + " kc", v))
                .party(this::memberKc,
                    (boss, member) -> memberKc(boss, member) == null ? richText.animatedDots(client.getTickCount()) : richText.highlightValue(memberKc(boss, member).toString()),
                    (boss, v) -> richText.highlightValue(v))
                .suggest(() -> BossAliases.suggestions(false), Args.BOSS_ICON),
            text("pb", this::pb, (boss, v) -> richText.labeled(BossAliases.resolve(boss) + " pb", v))
                .suggest(() -> BossAliases.suggestions(false), Args.BOSS_ICON),
            text("group", name -> Objects.requireNonNullElse(groupState.value(name), ""),
                (name, v) -> v.isEmpty() ? "" : richText.labeled(groupState.group(name) != null ? groupState.group(name).name : name, v))
                .suggest(() -> groupState.names(), null),
            number("price", item -> Objects.requireNonNullElse(itemPrice(item), ConditionEvaluator.NULL),
                (item, v) -> itemPrice(item) == null ? null : richText.labeled(iconNames.itemNamed(item), QuantityFormatter.quantityToStackSize(itemPrice(item))))
                .suggest(() -> itemManager.search("").stream().map(price -> price.getName()).distinct().collect(Collectors.toList()), Args.ITEM_ICON),
            text("hasItem", typed -> loggedIn() ? !items(typed, null).isEmpty() : null, (typed, v) -> heldItem(typed, null))
                .party((typed, member) -> !items(typed, member).isEmpty(), this::heldItem, (typed, v) -> heldItem(typed, null))
                .suggest(this::itemNames, Args.ITEM_ICON)
                .listed(),
            number("numItem", typed -> loggedIn() ? quantity(items(typed, null), false) : null,
                (typed, v) -> richText.labeled(typed + " count", stack(v)))
                .party((typed, member) -> quantity(items(typed, member), false),
                    (typed, member) -> richText.highlightValue(stack(String.valueOf(quantity(items(typed, member), false)))),
                    (typed, v) -> richText.highlightValue(stack(v)))
                .suggest(this::itemNames, Args.ITEM_ICON),
            number("numDoses", typed -> loggedIn() ? quantity(items(typed, null), true) : null,
                (typed, v) -> richText.labeled(typed + " doses", stack(v)))
                .party((typed, member) -> quantity(items(typed, member), true),
                    (typed, member) -> richText.highlightValue(stack(String.valueOf(quantity(items(typed, member), true)))),
                    (typed, v) -> richText.highlightValue(stack(v)))
                .suggest(this::doseNames, name -> Args.ITEM_ICON.apply(highestDose.getOrDefault(name, name))),
            number("lvl_", this::level, (name, v) -> richText.labeled(skillLabel(name), v))
                .joined()
                .party(ArgVariables::statBase, (name, member) -> highlighted(statBase(name, member)), (name, v) -> richText.highlightValue(v))
                .suggest(() -> Stream.concat(Args.SKILLS.get().stream(), Stream.of("combat", "total")).collect(Collectors.toList()), Args.SKILL_ICON),
            number("boost_", name -> loggedIn() && Args.skill(name) != null ? client.getBoostedSkillLevel(Args.skill(name)) : null,
                (name, v) -> Args.skill(name).getName() + ": " + richText.highlightValue(v) + " / " + client.getRealSkillLevel(Args.skill(name)))
                .joined()
                .party(ArgVariables::statBoost, (name, member) -> highlighted(statBoost(name, member)), (name, v) -> richText.highlightValue(v))
                .suggest(Args.SKILLS, Args.SKILL_ICON),
            number("xpRate_", name -> !loggedIn() || Args.skill(name) == null ? null
                    : xpTrackerRunning() ? xpTrackerService.getXpHr(Args.skill(name)) : "N/A",
                (name, v) -> !xpTrackerRunning() ? "Enable XP Tracker Plugin"
                    : richText.labeled(Args.skill(name).getName() + " XP/hr", stack(v)))
                .joined()
                .suggest(Args.SKILLS, Args.SKILL_ICON),
            number("xp_", this::xp, this::xpRich)
                .joined()
                .suggest(Args.SKILLS, Args.SKILL_ICON)
        );
    }

    public List<ArgVariable> all() {
        return all;
    }

    public List<ArgVariable> numeric() {
        return all.stream().filter(ArgVariable::numeric).collect(Collectors.toList());
    }

    private static ArgVariable number(String name, Function<String, Object> value, BiFunction<String, String, String> rich) {
        return new ArgVariable(name, true, value, rich);
    }

    private static ArgVariable text(String name, Function<String, Object> value, BiFunction<String, String, String> rich) {
        return new ArgVariable(name, false, value, rich);
    }

    private boolean loggedIn() {
        return client.getLocalPlayer() != null;
    }

    // A skill's real level, or combat / total level; null logged out.
    @Nullable
    private Integer level(String name) {
        if (!loggedIn()) {
            return null;
        }
        if ("combat".equalsIgnoreCase(name)) {
            return client.getLocalPlayer().getCombatLevel();
        }
        if ("total".equalsIgnoreCase(name)) {
            return client.getTotalLevel();
        }
        Skill skill = Args.skill(name);
        return skill == null ? null : client.getRealSkillLevel(skill);
    }

    private static String skillLabel(String name) {
        return "combat".equalsIgnoreCase(name) ? "Combat" : "total".equalsIgnoreCase(name) ? "Total" : Args.skill(name).getName();
    }

    // A member's base / boosted level in a skill; null until they've sent it (a base of 0), and for combat / total.
    @Nullable
    private static Integer statBase(String name, PartyMember member) {
        Skill skill = Args.skill(name);
        return skill == null || member.getStatBase(skill) <= 0 ? null : member.getStatBase(skill);
    }

    @Nullable
    private static Integer statBoost(String name, PartyMember member) {
        return statBase(name, member) == null ? null : member.getStatBoost(Args.skill(name));
    }

    @Nullable
    private String highlighted(@Nullable Object value) {
        return value == null ? null : richText.highlightValue(String.valueOf(value));
    }

    // The XP needed for a level 1-126, else the skill's XP (logged in); null otherwise.
    @Nullable
    private Object xp(String name) {
        if (name.matches("\\d{1,3}")) {
            int level = Integer.parseInt(name);
            return level >= 1 && level <= LEVEL_XP.length ? LEVEL_XP[level - 1] : null;
        }
        Skill skill = Args.skill(name);
        return skill == null || !loggedIn() ? null : client.getSkillExperience(skill);
    }

    // For a skill, also its level and hiscores rank (dots while unavailable: loading or unranked).
    private String xpRich(String name, String xp) {
        String formatted = richText.highlightValue(String.format("%,d", Long.parseLong(xp)));
        Skill skill = Args.skill(name);
        if (skill == null) {
            return formatted;
        }
        Long rank = hiscores.rank(HiscoreSkill.valueOf(skill.name()));
        String rankText = rank == null ? richText.animatedDots(client.getTickCount()) : richText.highlightValue(String.format("%,d", rank));
        return skill.getName() + " Level: " + richText.highlightValue(String.valueOf(client.getRealSkillLevel(skill)))
            + " | Experience: " + formatted + " | Rank: " + rankText;
    }

    private boolean xpTrackerRunning() {
        return pluginManager.getPlugins().stream()
            .anyMatch(plugin -> plugin.getClass() == XpTrackerPlugin.class && pluginManager.isPluginActive(plugin));
    }

    // Every item's name, carried ones first (see IconNames#matches).
    private List<String> itemNames() {
        return iconNames.matches(IconNames.Kind.ITEM, "");
    }

    // Each dosed item once, without its "(N)" (e.g. "Prayer potion"), carried ones first; noting its highest dose's name
    // for its icon.
    private List<String> doseNames() {
        Map<String, String> highest = new LinkedHashMap<>();
        Map<String, Integer> doses = new HashMap<>();
        for (String name : itemNames()) {
            Matcher dose = DOSE_NAME.matcher(name);
            if (!dose.find()) {
                continue;
            }
            String base = name.substring(0, dose.start()).trim();
            int count = Integer.parseInt(dose.group(1));
            if (count > doses.getOrDefault(base, 0)) {
                doses.put(base, count);
                highest.put(base, name);
            }
        }
        highestDose = highest;
        return new ArrayList<>(highest.keySet());
    }

    // From the hiscores: null while loading, and for bosses not on them.
    @Nullable
    private Integer memberKc(String boss, PartyMember member) {
        HiscoreSkill skill = BossHiscoreSkill.find(BossAliases.resolve(boss));
        String name = member.getDisplayName();
        return skill == null || name == null || name.isEmpty() ? null : hiscores.kc(name, skill);
    }

    // Mirrors ChatCommandsPlugin#secondsToTimeString so this matches !pb's own format.
    private String pb(String boss) {
        Double seconds = configManager.getRSProfileConfiguration(PERSONAL_BEST_GROUP, BossAliases.resolve(boss).toLowerCase(Locale.ENGLISH), double.class);
        if (seconds == null) {
            return "None";
        }
        int hours = (int) (Math.floor(seconds) / 3600);
        int minutes = (int) (Math.floor(seconds / 60) % 60);
        double secs = seconds % 60;
        String prefix = hours > 0 ? String.format("%d:%02d:", hours, minutes) : String.format("%d:", minutes);
        return prefix + (Math.floor(secs) == secs ? String.format("%02d", (int) secs) : String.format("%05.2f", secs));
    }

    @Nullable
    private Long itemPrice(String item) {
        String name = iconNames.itemNamed(item);
        Integer id = name == null ? null : iconNames.id(IconNames.Kind.ITEM, name);
        return id == null ? null : itemManager.getItemPrice(id);
    }

    // Ours when member is null.
    // The held matches of each of typed's comma-separated names (only {hasItem} allows several), in order.
    private List<ItemQuantityFinder.Match> items(String typed, @Nullable PartyMember member) {
        List<ItemQuantityFinder.Match> matches = new ArrayList<>();
        for (String name : typed.split(",")) {
            String search = name.trim().toLowerCase(Locale.ROOT);
            if (!search.isEmpty()) {
                matches.addAll(member == null ? ItemQuantityFinder.findAll(client, itemManager, search) : ItemQuantityFinder.findAll(member, itemManager, search));
            }
        }
        return matches;
    }

    // The first held match's name colored true, else typed colored false.
    @Nullable
    private String heldItem(String typed, @Nullable PartyMember member) {
        if (member == null && !loggedIn()) {
            return null;
        }
        List<ItemQuantityFinder.Match> matches = items(typed, member);
        return richText.booleanColored(!matches.isEmpty(), matches.isEmpty() ? typed : matches.get(0).name);
    }

    // Doses by the "(N)" in each name; items without one aren't counted.
    private static int quantity(List<ItemQuantityFinder.Match> matches, boolean doses) {
        int total = 0;
        for (ItemQuantityFinder.Match match : matches) {
            Matcher dose = DOSE_COUNT.matcher(match.name);
            if (!doses) {
                total += match.quantity;
            } else if (dose.find()) {
                total += match.quantity * Integer.parseInt(dose.group(1));
            }
        }
        return total;
    }

    private static String stack(String quantity) {
        return QuantityFormatter.quantityToStackSize(Long.parseLong(quantity));
    }
}
