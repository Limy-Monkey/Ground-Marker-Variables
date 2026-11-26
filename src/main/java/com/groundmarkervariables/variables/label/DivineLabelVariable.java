package com.groundmarkervariables.variables.label;

import java.awt.Color;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

import com.groundmarkervariables.variables.Args;
import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;

// {divine <potion>} -> ticks remaining on <potion>'s buff. Rich is the potion's name colored
// green (>25 ticks), yellow (1-25), or red (0, not active).
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DivineLabelVariable implements LabelVariable {
    private static final int YELLOW_THRESHOLD = 25;
    private static final int REFRESH_TICKS = 25;

    // Per refresh varbit, as last seen while resolving.
    private static final class RefreshState {
        private int lastValue = -1;
        private int lastSeenTick = -1;
        private Integer nextRefreshTick;
    }

    private final Map<Integer, RefreshState> refreshStates = new HashMap<>();

    private enum Potion {
        ATTACK("Attack", "Divine Super Attack Potion", VarbitID.DIVINEATTACK_POTION_TIME, VarbitID.DIVINECOMBAT_POTION_TIME),
        STRENGTH("Strength", "Divine Super Strength Potion", VarbitID.DIVINESTRENGTH_POTION_TIME, VarbitID.DIVINECOMBAT_POTION_TIME),
        DEFENCE("Defence", "Divine Super Defence Potion", VarbitID.DIVINEDEFENCE_POTION_TIME,
            VarbitID.DIVINECOMBAT_POTION_TIME, VarbitID.DIVINEBASTION_POTION_TIME, VarbitID.DIVINEBATTLEMAGE_POTION_TIME),
        RANGE("Range", "Divine Ranging Potion", VarbitID.DIVINERANGE_POTION_TIME, VarbitID.DIVINEBASTION_POTION_TIME),
        MAGIC("Magic", "Divine Magic Potion", VarbitID.DIVINEMAGIC_POTION_TIME, VarbitID.DIVINEBATTLEMAGE_POTION_TIME),
        COMBAT("Combat", "Divine Super Combat Potion", VarbitID.DIVINECOMBAT_POTION_TIME),
        BASTION("Bastion", "Divine Bastion Potion", VarbitID.DIVINEBASTION_POTION_TIME),
        BATTLEMAGE("Battlemage", "Divine Battlemage Potion", VarbitID.DIVINEBATTLEMAGE_POTION_TIME),
        SALTS("Salts", "Smelling Salts", true, VarbitID.TOA_MIDRAIDLOOT_STATS_TIMER),
        OVERLOAD("Overload", "Overload", true, VarbitID.RAIDS_OVERLOAD_TIMER,
            VarbitID.NZONE_OVERLOAD_POTION_EFFECTS, VarbitID.DEADMAN_OVERLOAD_POTION_EFFECTS);

        private final String keyword;
        private final String displayName;
        // Varbit counts 25-tick stat refreshes left instead of ticks.
        private final boolean refreshes;
        private final int[] varbits;

        Potion(String keyword, String displayName, int... varbits) {
            this(keyword, displayName, false, varbits);
        }

        Potion(String keyword, String displayName, boolean refreshes, int... varbits) {
            this.keyword = keyword;
            this.displayName = displayName;
            this.refreshes = refreshes;
            this.varbits = varbits;
        }

        private static Potion find(String text) {
            for (Potion potion : values()) {
                if (potion.keyword.equalsIgnoreCase(text)) {
                    return potion;
                }
            }
            return null;
        }
    }

    // Suggesting each potion's keyword.
    private static final Args ARGS = Args.of("divine")
        .main(() -> Arrays.stream(Potion.values()).map(potion -> potion.keyword).collect(Collectors.toList()), potion -> null);

    private final Client client;

    @Override
    public Pattern pattern() {
        return ARGS.pattern();
    }

    @Override
    public Args args() {
        return ARGS;
    }

    @Override
    public String resolvePlain(Matcher matcher) {
        Potion potion = Potion.find(ARGS.main(matcher));
        if (potion == null || client.getLocalPlayer() == null) {
            return null;
        }

        return String.valueOf(ticks(potion));
    }

    @Override
    public String resolveRich(Matcher matcher) {
        Potion potion = Potion.find(ARGS.main(matcher));
        if (potion == null || client.getLocalPlayer() == null) {
            return null;
        }

        int ticks = ticks(potion);
        Color color = ticks == 0 ? Color.RED : ticks <= YELLOW_THRESHOLD ? Color.YELLOW : Color.GREEN;
        return RichText.colored(color, potion.displayName);
    }

    private int ticks(Potion potion) {
        int max = 0;
        for (int varbit : potion.varbits) {
            max = Math.max(max, potion.refreshes ? refreshTicks(varbit) : client.getVarbitValue(varbit));
        }
        return max;
    }

    // Times the next refresh from the tick the varbit was seen changing, like core's Timers & Buffs
    // overload timer. Until a change has been seen on consecutive ticks, estimates a full period.
    private int refreshTicks(int varbit) {
        int value = client.getVarbitValue(varbit);
        int tick = client.getTickCount();
        RefreshState state = refreshStates.computeIfAbsent(varbit, v -> new RefreshState());

        if (value <= 0) {
            state.nextRefreshTick = null;
        } else if (value != state.lastValue) {
            if (state.lastSeenTick < tick - 1) {
                state.nextRefreshTick = null;
            } else if (state.nextRefreshTick == null || state.nextRefreshTick <= tick) {
                state.nextRefreshTick = tick + REFRESH_TICKS;
            }
        }

        state.lastValue = value;
        state.lastSeenTick = tick;

        if (value <= 0) {
            return 0;
        }

        int untilNext = state.nextRefreshTick == null ? REFRESH_TICKS : Math.max(0, state.nextRefreshTick - tick);
        return untilNext + (value - 1) * REFRESH_TICKS;
    }
}
