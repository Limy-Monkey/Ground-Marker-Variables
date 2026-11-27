package com.groundmarkervariables.variables.meta;

import com.groundmarkervariables.variables.Args;
import java.awt.Color;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import javax.inject.Inject;

import com.groundmarkervariables.variables.support.NamedColors;

// {hover} or {hover=<color>@N%} -- while the mouse is over this label, it's drawn above other labels
// on a background of <color> (hex or named) at N% opacity.
public class HoverMetaVariable implements MetaVariable {
    // Its value starts with "=", so {hoverOnly} isn't one.
    private static final Args ARGS = Args.of("hover").joined("=" + VALUE).main(List::of, Args.NO_ICON).optional();
    private static final Color DEFAULT_BACKGROUND = new Color(0, 0, 0, 150);

    @Inject
    private HoverMetaVariable() {
    }

    @Override
    public Args args() {
        return ARGS;
    }

    // =<color>@N%, the color's # optional.
    @Override
    public void apply(Matcher matcher, MetaState state) {
        String main = ARGS.main(matcher);
        if (main == null) {
            state.setHoverBackground(DEFAULT_BACKGROUND);
            return;
        }

        String[] value = main.substring(main.startsWith("=#") ? 2 : 1).split("@", 2);
        String hex = value[0].matches("[0-9a-fA-F]{6}") ? value[0] : NamedColors.HEX_BY_NAME.get(value[0].toLowerCase(Locale.ROOT));
        if (hex == null) {
            return;
        }

        int alpha = Math.min(100, Integer.parseInt(value[1].replace("%", ""))) * 255 / 100;
        Color color = new Color(Integer.parseInt(hex, 16));
        state.setHoverBackground(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
    }
}
