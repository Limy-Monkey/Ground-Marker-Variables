package com.groundmarkervariables.variables.label;

import com.groundmarkervariables.variables.Args;
import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.Emojis;
import com.groundmarkervariables.variables.support.FarmingPatches;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;

// {farming [kinds, ...][ any][ countdown]} -> minutes until the patches are done (any: the first). Rich: a line per kind, or
// with none, per kind growing or recently done.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class FarmingLabelVariable implements LabelVariable {
    // How long done patches stay listed by bare {farming}.
    private static final long RECENT = TimeUnit.HOURS.toSeconds(24);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE ", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final FarmingPatches patches;
    private final RichText richText;
    private final Emojis emojis;
    // More patches after commas, e.g. {farming Herbs, Fruit Trees}.
    private final Args args = Args.of("farming").main(this::names, this::crop).optional().flag("any", "countdown")
        .extras(main -> names(), this::crop);

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
        boolean any = flag(matcher, "any");
        return shown(matcher).stream().map(name -> FarmingPatches.minutesLeft(patches.done(name, any))).filter(Objects::nonNull)
            .min(Long::compare).map(String::valueOf).orElse(null);
    }

    // A line each of shown()'s, as a list unless just one is named.
    @Override
    public String resolveRich(Matcher matcher) {
        boolean list = named(matcher).size() != 1;
        String lines = shown(matcher).stream().map(name -> rich(name, flag(matcher, "any"), list, flag(matcher, "countdown")))
            .filter(Objects::nonNull).collect(Collectors.joining("\\n"));
        return lines.isEmpty() ? null : lines;
    }

    // The patches named, or bare, each kind with a patch growing or done in the last RECENT.
    private List<String> shown(Matcher matcher) {
        List<String> named = named(matcher);
        return !named.isEmpty() ? named : patches.active(Instant.now().getEpochSecond() - RECENT);
    }

    // The patches named: the main argument, then those after commas.
    private List<String> named(Matcher matcher) {
        List<String> named = new ArrayList<>();
        if (args.main(matcher) != null) {
            named.add(args.main(matcher));
        }
        named.addAll(args.options(matcher).extras());
        return named;
    }

    private boolean flag(Matcher matcher, String word) {
        return args.options(matcher).flag(word);
    }

    // "Herbs: <crop> 1:05" (a day or more: d:hh:mm); in a list, "<crop> Herbs:\t<ETA, or with countdown the time left>\t<flags>".
    // The crop is the patch's that decides the time.
    @Nullable
    private String rich(String name, boolean any, boolean list, boolean countdown) {
        long[] done = patches.done(name, any);
        if (done == null) {
            return null;
        }
        Long minutes = FarmingPatches.minutesLeft(done);
        String left = minutes == null ? "Empty" : minutes == 0 ? "Ready" : list && !countdown ? eta(done[0])
            : minutes >= 1440 ? String.format("%d:%02d:%02d", minutes / 1440, minutes / 60 % 24, minutes % 60)
            : String.format("%d:%02d", minutes / 60, minutes % 60);
        // As the patches are named, e.g. herbs -> Herbs.
        String title = patches.names().stream().filter(name::equalsIgnoreCase).findFirst().orElse(name);
        String crop = done[1] > 0 ? "<item=" + done[1] + "> " : "";
        String flags = attention(done);
        return list ? crop + title + ":\\t" + richText.highlightValue(left) + (flags.isEmpty() ? "" : "\\t" + flags)
            : title + ": " + crop + richText.highlightValue(left) + (flags.isEmpty() ? "" : " " + flags);
    }

    // When, local time, as {time} writes it: "3:05 pm", with the day if not today ("Thu 3:05 pm").
    private static String eta(long at) {
        ZonedDateTime when = Instant.ofEpochSecond(at).atZone(ZoneId.systemDefault());
        return (when.toLocalDate().equals(LocalDate.now()) ? "" : when.format(DAY)) + when.format(TIME).toLowerCase(Locale.ROOT);
    }

    // As {flag}{tip=N Patches diseased}<plant cure>{/tip}{tip=N Patches dead}:skull_crossbones:{/tip}{/flag} would show
    // (one by name, e.g. "Catherby patch diseased"): nothing if none is diseased or dead.
    private String attention(long[] done) {
        return done[2] + done[3] == 0 ? "" : emojis.expand(":triangular_flag_on_post:")
            + (done[2] == 0 ? "" : "{tip=" + patches(done[2], done[4]) + " diseased}<item=" + ItemID.PLANT_CURE + ">{/tip}")
            + (done[3] == 0 ? "" : "{tip=" + patches(done[3], done[5]) + " dead}" + emojis.expand(":skull_crossbones:") + "{/tip}");
    }

    // "N Patches", or the one by name.
    private String patches(long count, long one) {
        return count == 1 ? patches.patchName(one) + " patch" : count + " Patches";
    }
}
