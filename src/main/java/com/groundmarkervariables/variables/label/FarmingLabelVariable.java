package com.groundmarkervariables.variables.label;

import com.groundmarkervariables.variables.Args;
import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.Emojis;
import com.groundmarkervariables.variables.support.FarmingPatches;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;

// {farming <kind or farm's kind>[ any]} -> minutes until all its patches are done (with any, the first), 0 once done; empty
// while Time Tracking hasn't seen them or nothing grows. Rich Text: e.g. "Herbs: <crop icon> 1:05", "Herbs: Ready", "Herbs:
// Empty". Bare {farming}: every kind growing or recently done, a line each.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class FarmingLabelVariable implements LabelVariable {
    // How long done patches stay listed by bare {farming}.
    private static final long RECENT = TimeUnit.HOURS.toSeconds(24);

    private final FarmingPatches patches;
    private final RichText richText;
    private final Emojis emojis;
    private final Args args = Args.of("farming").main(this::names, this::crop).optional().flag("any");

    private Collection<String> names() {
        return patches.names();
    }

    // Suggested with the crop the label would show, if any.
    @Nullable
    private String crop(String name) {
        long[] done = patches.done(name, false);
        return done == null || done[1] <= 0 ? null : "<item=" + done[1] + ">";
    }

    @Override
    public Pattern pattern() {
        return args.pattern();
    }

    @Override
    public Args args() {
        return args;
    }

    // The minutes until the first of shown()'s is done.
    @Override
    public String resolvePlain(Matcher matcher) {
        boolean any = args.options(matcher).flag("any");
        return shown(matcher).stream().map(name -> FarmingPatches.minutesLeft(patches.done(name, any))).filter(Objects::nonNull)
            .min(Long::compare).map(String::valueOf).orElse(null);
    }

    // A line each of shown()'s.
    @Override
    public String resolveRich(Matcher matcher) {
        boolean any = args.options(matcher).flag("any");
        String lines = shown(matcher).stream().map(name -> rich(name, any)).filter(Objects::nonNull).collect(Collectors.joining("\\n"));
        return lines.isEmpty() ? null : lines;
    }

    // The patches named, or bare, each kind with a patch growing or done in the last RECENT.
    private List<String> shown(Matcher matcher) {
        String name = args.main(matcher);
        return name != null ? List.of(name) : patches.active(Instant.now().getEpochSecond() - RECENT);
    }

    // Then the crop's icon: the patch's that decides the time.
    @Nullable
    private String rich(String name, boolean any) {
        long[] done = patches.done(name, any);
        if (done == null) {
            return null;
        }
        Long minutes = FarmingPatches.minutesLeft(done);
        String left = minutes == null ? "Empty" : minutes == 0 ? "Ready" : minutes / 60 + ":" + String.format("%02d", minutes % 60);
        // As the patches are named, e.g. herbs -> Herbs.
        return patches.names().stream().filter(name::equalsIgnoreCase).findFirst().orElse(name) + ": "
            + (done[1] > 0 ? "<item=" + done[1] + "> " : "") + richText.highlightValue(left) + attention(done);
    }

    // As {flag}{tip=N Patches diseased}<plant cure>{/tip}{tip=N Patches dead}:skull_crossbones:{/tip}{/flag} would show
    // (one by name, e.g. "Catherby patch diseased"): nothing if none is diseased or dead.
    private String attention(long[] done) {
        return done[2] + done[3] == 0 ? "" : " " + emojis.expand(":triangular_flag_on_post:")
            + (done[2] == 0 ? "" : "{tip=" + patches(done[2], done[4]) + " diseased}<item=" + ItemID.PLANT_CURE + ">{/tip}")
            + (done[3] == 0 ? "" : "{tip=" + patches(done[3], done[5]) + " dead}" + emojis.expand(":skull_crossbones:") + "{/tip}");
    }

    // "N Patches", or the one by name.
    private String patches(long count, long one) {
        return count == 1 ? patches.patchName(one) + " patch" : count + " Patches";
    }
}
