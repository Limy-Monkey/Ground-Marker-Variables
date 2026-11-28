package com.groundmarkervariables.variables.support;

import com.google.gson.Gson;
import com.groundmarkervariables.GroundMarkerVariablesConfig;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.client.config.ConfigManager;

// Farming patches as the Time Tracking plugin last saw them (its saved "value:unixSeconds" for each), predicted to now as it
// predicts them, by farming.json's tables (scripts/build-farming.jsh, from Time Tracking's own).
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class FarmingPatches {
    private static final String GROUP = "timetracking";
    // Our own key, per account, for when a patch finished (see predict).
    private static final String FINISHED = "farmingDone.";
    // predict()'s for a patch with nothing growing.
    private static final long[] EMPTY = new long[0];

    private final ConfigManager configManager;
    private final Gson gson;
    @Nullable
    private Data data;

    private static final class Data {
        // Kind -> each varbit value's code: "" unknown, "E" empty, else "<ticks left>/<minutes a tick>/<crop's item id>".
        Map<String, String> types;
        // [farm, patch, kind, Time Tracking's key].
        List<List<String>> patches;
    }

    private Data data() {
        if (data == null) {
            data = gson.fromJson(LatestData.text("farming.json"), Data.class);
        }
        return data;
    }

    // Each kind (e.g. Herbs), then each farm's (e.g. Catherby Herbs).
    public Set<String> names() {
        Set<String> names = new LinkedHashSet<>(data().types.keySet());
        data().patches.forEach(patch -> names.add(patch.get(0) + " " + patch.get(2)));
        return names;
    }

    // {when name's patches are done (any: the first; -1 none growing), crop (once done, the last to finish's), diseased,
    // dead, a diseased one, a dead one (see patchName)}; null if Time Tracking hasn't seen any.
    @Nullable
    public long[] done(String name, boolean any) {
        long[] done = null;
        boolean growing = false;
        long now = Instant.now().getEpochSecond();
        long lastFinished = -1;
        long lastCrop = 0;
        for (int index = 0; index < data().patches.size(); index++) {
            List<String> patch = data().patches.get(index);
            long[] predicted = name.equalsIgnoreCase(patch.get(2)) || name.equalsIgnoreCase(patch.get(0) + " " + patch.get(2)) ? predict(patch) : null;
            if (predicted == null) {
                continue;
            }
            done = done != null ? done : new long[]{-1, 0, 0, 0, 0, 0};
            if (predicted != EMPTY) {
                if (!growing || (any ? predicted[0] < done[0] : predicted[0] > done[0])) {
                    done[0] = predicted[0];
                    done[1] = predicted[1];
                }
                if (predicted[2] > 0) {
                    done[(int) predicted[2]]++;
                    done[(int) predicted[2] + 2] = index;
                }
                if (predicted[0] <= now && predicted[3] > lastFinished) {
                    lastFinished = predicted[3];
                    lastCrop = predicted[1];
                }
                growing = true;
            }
        }
        if (growing && done[0] <= now) {
            done[1] = lastCrop;
        }
        return done;
    }

    // A patch by done()'s index: its farm, and its own name if it has one, e.g. Ardougne North.
    public String patchName(long index) {
        List<String> patch = data().patches.get((int) index);
        return (patch.get(0) + " " + patch.get(1)).trim();
    }

    // The kinds with a patch growing, or done since since (unix seconds), the soonest done first.
    public List<String> active(long since) {
        Set<String> kinds = new LinkedHashSet<>();
        for (List<String> patch : data().patches) {
            long[] predicted = predict(patch);
            if (predicted != null && predicted != EMPTY && predicted[3] >= since) {
                kinds.add(patch.get(2));
            }
        }
        List<String> sorted = new ArrayList<>(kinds);
        sorted.sort(Comparator.comparingLong(kind -> done(kind, false)[0]));
        return sorted;
    }

    // A patch as predicted now: when it's done (unix seconds; 0 if it already was when seen), its crop's item id, 2 if
    // diseased or 3 if dead (else 0), and when it finished (0 if unknown); EMPTY if nothing
    // grows; null if unknown.
    @Nullable
    private long[] predict(List<String> patch) {
        String[] seen = String.valueOf(configManager.getRSProfileConfiguration(GROUP, patch.get(3))).split(":");
        String[] codes = data().types.get(patch.get(2)).split(",");
        try {
            int value = Integer.parseInt(seen[0]);
            String code = value < codes.length ? codes[value] : "";
            long seenAt = Long.parseLong(seen[1]);
            if (code.isEmpty() || code.equals("E")) {
                return code.isEmpty() ? null : EMPTY;
            }
            String[] tick = code.split("/");
            int rate = Integer.parseInt(tick[1]);
            int left = Integer.parseInt(tick[0]);
            // Seen done (no ticks left, whatever its tick rate): done now, when unknown.
            boolean growing = rate > 0 && left > 0;
            long at = growing ? tickTime(rate, left, tickTime(rate, 0, seenAt)) : 0;
            // Growing, when it'll finish is saved, for once it's seen done: Time Tracking's time is then only when it was
            // last seen (rewritten while loaded). Seen done without one, it's unknown (0: not recent).
            String key = FINISHED + patch.get(3);
            Long finished = configManager.getRSProfileConfiguration(GroundMarkerVariablesConfig.GROUP, key, long.class);
            if (growing && !Long.valueOf(at).equals(finished)) {
                configManager.setRSProfileConfiguration(GroundMarkerVariablesConfig.GROUP, key, at);
            }
            long finishedAt = growing ? at : finished != null && finished <= seenAt ? finished : 0;
            return new long[]{at, Long.parseLong(tick[2]), tick.length < 4 ? 0 : tick[3].equals("D") ? 2 : 3, finishedAt};
        } catch (RuntimeException e) {
            return null;
        }
    }

    // Minutes until done[0] (see done), 0 once done; null for none (or -1).
    @Nullable
    public static Long minutesLeft(@Nullable long[] done) {
        return done == null || done[0] < 0 ? null : Math.max(0, (done[0] - Instant.now().getEpochSecond() + 59) / 60);
    }

    // The time of the growth tick ticks after the one time is in, by the account's tick offset once Time Tracking is sure
    // enough of it (its FarmingTracker#getTickTime).
    private long tickTime(int rate, int ticks, long time) {
        Integer precision = configManager.getRSProfileConfiguration(GROUP, "farmTickOffsetPrecision", int.class);
        Integer offsetMinutes = configManager.getRSProfileConfiguration(GROUP, "farmTickOffset", int.class);
        // Stored positive, though they're behind.
        long offset = precision != null && offsetMinutes != null && (precision >= rate || precision >= 40) ? offsetMinutes % rate * 60L : 0;
        long shifted = time + offset;
        return shifted - shifted % (rate * 60L) + ticks * rate * 60L - offset;
    }
}
