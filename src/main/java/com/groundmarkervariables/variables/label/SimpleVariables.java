package com.groundmarkervariables.variables.label;

import com.groundmarkervariables.GroundMarkerVariablesConfig;
import com.groundmarkervariables.party.PartyDataSharing;
import com.groundmarkervariables.party.PartyValue;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.SimpleVariable;
import com.groundmarkervariables.variables.support.Icons;
import com.groundmarkervariables.variables.support.MiscellaniaTracker;
import com.groundmarkervariables.variables.support.PartyMemberContext;
import com.groundmarkervariables.variables.support.SlayerModifierType;
import com.groundmarkervariables.variables.support.Spellbooks;
import com.groundmarkervariables.variables.support.WeaponStyleTracker;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.slayer.SlayerPlugin;
import net.runelite.client.plugins.slayer.SlayerPluginService;

// The no-argument variables, one line each (plus their variables.json entry). number() ones are MathVariable
// operands; .party() ones also resolve for other members in {party} rows.
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SimpleVariables {
    private static final String[] CA_TIER_NAMES = {"Easy", "Medium", "Hard", "Elite", "Master", "Grandmaster"};
    private static final int[] CA_TIER_THRESHOLDS = {
        VarbitID.CA_THRESHOLD_EASY, VarbitID.CA_THRESHOLD_MEDIUM, VarbitID.CA_THRESHOLD_HARD,
        VarbitID.CA_THRESHOLD_ELITE, VarbitID.CA_THRESHOLD_MASTER, VarbitID.CA_THRESHOLD_GRANDMASTER
    };
    private static final int KRYSTILIA_SLAYER_MASTER = 7;
    private static final int MORTIMER_SLAYER_MASTER = 10;
    // 0 = on. Not named in VarPlayerID; read the same way by the Auto Retaliate Warning plugin.
    private static final int AUTO_RETALIATE_VARP = 172;
    private static final int INVENTORY_SIZE = 28;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMMM d", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_12 = DateTimeFormatter.ofPattern("h:mm");
    private static final DateTimeFormatter TIME_24 = DateTimeFormatter.ofPattern("HH:mm");
    // By Spellbooks.NAMES index.
    private static final int[] SPELLBOOK_ICONS = {
        SpriteID.SideiconsInterface.MAGIC, SpriteID.SideiconsInterface.SPELLBOOK_ANCIENT_MAGICKS,
        SpriteID.SideiconsInterface.SPELLBOOK_LUNAR, SpriteID.SideiconsInterface.SPELLBOOK_ARCEUUS
    };

    private final Client client;
    private final GroundMarkerVariablesConfig config;
    private final RichText richText;
    private final MiscellaniaTracker miscellania;
    private final PartyMemberContext memberContext;
    private final PartyDataSharing partyDataSharing;
    private final ItemManager itemManager;
    private final WeaponStyleTracker weaponStyleTracker;
    private final SlayerPluginService slayerPluginService;
    private final PluginManager pluginManager;

    private final List<SimpleVariable> all = define();

    // Built before the fields above are set: they're only read when a lambda runs.
    private List<SimpleVariable> define() {
        Supplier<Object> partyNum = () -> memberContext.rowNumber() > 0 ? memberContext.rowNumber() : null;
        return List.of(
            text("rsn", () -> loggedIn() ? client.getLocalPlayer().getName() : null, v -> labeled("RSN", v))
                .party(PartyMember::getDisplayName, this::highlight),
            // "World N (type)", e.g. "World 302", "World 301 (F2P)".
            text("world", () -> loggedIn() ? client.getWorld() : null,
                v -> "World " + highlight(v) + (WorldInfoLabelVariable.typeName(client.getWorldType()) == null ? ""
                    : " (" + WorldInfoLabelVariable.typeName(client.getWorldType()) + ")")),
            // A member's spellbook is our own shared PartyValue, unavailable until they've sent it.
            text("spellbook", () -> loggedIn() ? Spellbooks.name(client) : null, v -> labeled("Spellbook", v))
                .icon(() -> loggedIn() ? spellbookIcon(Spellbooks.name(client)) : null)
                .party(this::memberSpellbook, this::highlight, member -> spellbookIcon(memberSpellbook(member))),
            // "Unarmed" with an empty weapon slot, whose icon is nothing.
            text("weapon", () -> loggedIn() ? weaponName(weaponId(items(InventoryID.WORN))) : null, v -> labeled("Weapon", v))
                .icon(() -> loggedIn() ? weaponIcon(weaponId(items(InventoryID.WORN))) : null)
                .party(member -> weaponName(weaponId(member.getWorn())), this::highlight, member -> weaponIcon(weaponId(member.getWorn()))),
            // The last autocast spell seen on a weapon that can autocast; its icon is its spellbook sprite.
            text("autocast", () -> loggedIn() ? Objects.requireNonNullElse(weaponStyleTracker.lastAutocast(), "None") : null,
                v -> labeled("Autocast", v))
                .icon(() -> !loggedIn() ? null : weaponStyleTracker.lastAutocastSprite() > 0 ? Icons.sprite(weaponStyleTracker.lastAutocastSprite()) : ""),
            // From core's Slayer plugin, which must be on.
            text("slayerTask", () -> !slayerPluginRunning() ? "N/A" : Objects.requireNonNullElse(slayerPluginService.getTask(), "None"),
                v -> slayerTaskSummary()),
            number("questPoints", () -> client.getVarpValue(VarPlayerID.QP), v -> labeled("Quest Points", v)),
            number("slayerPoints", () -> client.getVarbitValue(VarbitID.SLAYER_POINTS), v -> slayerSummary()),
            number("slayerStreak", this::slayerStreak, v -> slayerSummary()),
            number("combatAchievements?", this::combatAchievementTier,
                v -> labeled("Combat Achievement tier", v.equals("0") ? "None" : CA_TIER_NAMES[Integer.parseInt(v) - 1])),
            text("autoRetal(?:iate)?", () -> client.getVarpValue(AUTO_RETALIATE_VARP) == 0,
                v -> richText.booleanColored(Boolean.parseBoolean(v) != config.invertAutoRetaliate(), "Auto Retaliate")),
            // VarPlayerID.SA_ENERGY is 0-1000; party data is already 0-100.
            number("spec", () -> client.getVarpValue(VarPlayerID.SA_ENERGY) / 10, v -> "Spec: " + highlight(v + "%"))
                .party(PartyMember::getSpecEnergy, v -> highlight(v + "%")),
            // getEnergy() is in 1/100ths; party data is 0-100, rounded up to tens.
            number("runEnergy", () -> client.getEnergy() / 100, v -> "Run: " + highlight(v) + " / 100")
                .party(PartyMember::getRunEnergy, this::highlight),
            number("invSpace", () -> loggedIn() ? freeSlots(items(InventoryID.INV)) : null,
                v -> highlight(v) + (v.equals("1") ? " inventory space" : " inventory spaces"))
                .party(member -> member.getInv() == null ? null : freeSlots(member.getInv()), this::highlight),
            // 1 (Monday) - 7 (Sunday), as DayOfWeek numbers them.
            number("weekday", () -> LocalDate.now().getDayOfWeek().getValue(),
                v -> labeled("Weekday", LocalDate.now().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH))),
            // e.g. "October 12th 2026"; usable in math as a countdown, e.g. {Oct 20 - date}.
            text("date", SimpleVariables::date, v -> labeled("Date", v)),
            text("time", () -> LocalTime.now().format(TIME_12) + (LocalTime.now().getHour() < 12 ? " am" : " pm"), v -> v),
            text("time24", () -> LocalTime.now().format(TIME_24), v -> v),
            // Highlighted only at the cap, and only the number.
            number("miscellania", () -> miscellania.approval(),
                v -> (v.equals(String.valueOf(MiscellaniaTracker.MAX_APPROVAL)) ? highlight(v) : v) + " / " + MiscellaniaTracker.MAX_APPROVAL),
            // The {party} row's number, like its "num" keyword; nothing outside a row.
            number("partyNum", partyNum, v -> v)
                .party(member -> partyNum.get(), v -> v)
        );
    }

    public List<SimpleVariable> all() {
        return all;
    }

    public List<SimpleVariable> numeric() {
        return all.stream().filter(SimpleVariable::numeric).collect(Collectors.toList());
    }

    // Count of tier thresholds the player's points have reached, 0-6.
    public int combatAchievementTier() {
        int points = client.getVarbitValue(VarbitID.CA_POINTS);
        int tier = 0;
        for (int threshold : CA_TIER_THRESHOLDS) {
            if (points < client.getVarbitValue(threshold)) {
                break;
            }
            tier++;
        }
        return tier;
    }

    private static SimpleVariable number(String names, Supplier<Object> value, UnaryOperator<String> rich) {
        return new SimpleVariable(names, true, value, rich);
    }

    private static SimpleVariable text(String names, Supplier<Object> value, UnaryOperator<String> rich) {
        return new SimpleVariable(names, false, value, rich);
    }

    private String labeled(String label, String value) {
        return richText.labeled(label, value);
    }

    private String highlight(String value) {
        return richText.highlightValue(value);
    }

    private boolean loggedIn() {
        return client.getLocalPlayer() != null;
    }

    @Nullable
    private String memberSpellbook(PartyMember member) {
        return partyDataSharing.value(member.getMemberId(), PartyValue.SPELLBOOK);
    }

    @Nullable
    private static String spellbookIcon(@Nullable String name) {
        int index = name == null ? -1 : Spellbooks.NAMES.indexOf(name);
        return index < 0 ? null : Icons.sprite(SPELLBOOK_ICONS[index]);
    }

    @Nullable
    private Item[] items(int containerId) {
        ItemContainer container = client.getItemContainer(containerId);
        return container == null ? null : container.getItems();
    }

    // <= 0 when unarmed.
    private static int weaponId(@Nullable Item[] worn) {
        int slot = EquipmentInventorySlot.WEAPON.getSlotIdx();
        Item weapon = worn == null || slot >= worn.length ? null : worn[slot];
        return weapon == null ? -1 : weapon.getId();
    }

    private String weaponName(int itemId) {
        return itemId <= 0 ? "Unarmed" : itemManager.getItemComposition(itemId).getName();
    }

    private static String weaponIcon(int itemId) {
        return itemId <= 0 ? "" : Icons.item(itemId);
    }

    private boolean slayerPluginRunning() {
        return pluginManager.getPlugins().stream()
            .anyMatch(plugin -> plugin.getClass() == SlayerPlugin.class && pluginManager.isPluginActive(plugin));
    }

    // getInitialAmount() already includes a count-type Mortimer modifier, so it isn't added again; other modifier
    // kinds share its varbits, so any nonzero value is shown.
    private String slayerTaskSummary() {
        if (!slayerPluginRunning()) {
            return "Enable Slayer Plugin";
        }
        String task = slayerPluginService.getTask();
        if (task == null) {
            return "Slayer Task: " + highlight("None");
        }

        StringBuilder result = new StringBuilder("Slayer Task: ").append(highlight(task))
            .append(" (").append(slayerPluginService.getRemainingAmount()).append(" / ").append(slayerPluginService.getInitialAmount()).append(")");
        String location = slayerPluginService.getTaskLocation();
        if (location != null && !location.isEmpty()) {
            result.append(" Location: ").append(highlight(location));
        }
        int modifier = client.getVarbitValue(VarbitID.SLAYER_MODIFIER_VALUE);
        if (client.getVarbitValue(VarbitID.SLAYER_MASTER) == MORTIMER_SLAYER_MASTER && modifier != 0) {
            int signed = client.getVarbitValue(VarbitID.SLAYER_MODIFIER_NEGATIVE) == 1 ? -modifier : modifier;
            SlayerModifierType type = SlayerModifierType.fromId(client.getVarbitValue(VarbitID.SLAYER_MODIFIER_ID));
            result.append(" Modifier: ").append(highlight((signed > 0 ? "+" : "") + signed + (type == null ? "" : " " + type.displayName())));
        }
        return result.toString();
    }

    // Raw varbits, as core's SlayerPlugin reads them; that plugin needn't be on.
    private String slayerSummary() {
        return "You've completed " + highlight(slayerStreak() + " tasks") + " in a row and currently have a total of "
            + highlight(client.getVarbitValue(VarbitID.SLAYER_POINTS) + " points") + ".";
    }

    private int slayerStreak() {
        switch (client.getVarbitValue(VarbitID.SLAYER_MASTER)) {
            case KRYSTILIA_SLAYER_MASTER:
                return client.getVarbitValue(VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED);
            case MORTIMER_SLAYER_MASTER:
                return client.getVarpValue(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED);
            default:
                return client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED);
        }
    }

    // No container yet is an empty inventory.
    private static int freeSlots(@Nullable Item[] items) {
        return items == null ? INVENTORY_SIZE : Math.max(0, INVENTORY_SIZE - (int) Arrays.stream(items).filter(item -> item.getId() != -1).count());
    }

    private static String date() {
        LocalDate today = LocalDate.now();
        int day = today.getDayOfMonth();
        String suffix = day >= 11 && day <= 13 ? "th" : day % 10 == 1 ? "st" : day % 10 == 2 ? "nd" : day % 10 == 3 ? "rd" : "th";
        return today.format(DATE) + suffix + " " + today.getYear();
    }
}
