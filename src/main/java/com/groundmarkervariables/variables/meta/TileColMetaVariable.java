package com.groundmarkervariables.variables.meta;

import java.awt.Color;
import com.groundmarkervariables.variables.Args;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;

import com.groundmarkervariables.variables.support.NamedColors;

// {tileCol=<col>} -- <col> is a hex/named color (RRGGBB, same as <col=...>), an 8-digit hex
// with a leading alpha byte (AARRGGBB), "N%", or an opacity name (invis/opaque/solid) to keep
// the tile's own color at that opacity.
public class TileColMetaVariable implements MetaVariable {
    static final Args ARGS = Args.of("tileCol=").joined(VALUE)
        .main(() -> Stream.concat(NamedColors.OPACITY_BY_NAME.keySet().stream(), NamedColors.SUGGESTED.stream()).collect(Collectors.toList()), Args.COLOR_ICON);

    @Inject
    private TileColMetaVariable() {
    }

    @Override
    public Args args() {
        return ARGS;
    }

    @Override
    public void apply(Matcher matcher, MetaState state) {
        String value = ARGS.main(matcher);
        Integer opacityName = NamedColors.OPACITY_BY_NAME.get(value.toLowerCase(Locale.ROOT));
        if (opacityName != null) {
            state.setTileOpacity(opacityName / 100f);
            return;
        }

        if (value.endsWith("%")) {
            float percent = Math.max(0, Math.min(100, Float.parseFloat(value.substring(0, value.length() - 1))));
            state.setTileOpacity(percent / 100f);
            return;
        }

        String hex = NamedColors.HEX_BY_NAME.getOrDefault(value.toLowerCase(Locale.ROOT), value);
        if (hex.length() == 8) {
            state.setTileOpacity(Integer.parseInt(hex.substring(0, 2), 16) / 255f);
            hex = hex.substring(2);
        }

        state.setTileColor(new Color(Integer.parseInt(hex, 16)));
    }
}
