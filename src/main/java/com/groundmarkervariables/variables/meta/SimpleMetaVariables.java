package com.groundmarkervariables.variables.meta;

import com.groundmarkervariables.variables.Args;
import com.groundmarkervariables.variables.support.NamedColors;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

// The formatting and tile tokens' values ({col=red}, {align=left}, {ground@n}, ...), as Args for autocomplete.
@UtilityClass
public class SimpleMetaVariables {
    // {col=} also offers recentColors.
    public List<Args> args(Supplier<Collection<String>> recentColors) {
        return List.of(
            joined("col=", () -> Stream.concat(NamedColors.SUGGESTED.stream(), recentColors.get().stream()), Args.COLOR_ICON),
            TileColMetaVariable.ARGS,
            HighlightMetaVariable.ARGS,
            joined("align=", () -> Stream.of("Left", "Right", "Center"), Args.NO_ICON),
            joined("ground@", () -> Stream.of("n", "ne", "e", "se", "s", "sw", "w", "nw"), Args.NO_ICON).describedAs("ground"));
    }

    private Args joined(String name, Supplier<Stream<String>> values, Function<String, String> icon) {
        return Args.of(name).joined().main(() -> values.get().collect(Collectors.toList()), icon);
    }
}
