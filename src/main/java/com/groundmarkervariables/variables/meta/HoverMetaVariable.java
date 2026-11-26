package com.groundmarkervariables.variables.meta;

import java.awt.Color;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.support.NamedColors;

// {hover} or {hover=<color>@N%} -- while the mouse is over this label, it's drawn above other labels
// on a background of <color> (hex or named) at N% opacity.
public class HoverMetaVariable implements MetaVariable {
    private static final Pattern PATTERN = Pattern.compile("\\{hover(?:=#?(\\w+)@(\\d{1,3})%)?\\}", Pattern.CASE_INSENSITIVE);
    private static final Color DEFAULT_BACKGROUND = new Color(0, 0, 0, 150);

    @Inject
    private HoverMetaVariable() {
    }

    @Override
    public Pattern pattern() {
        return PATTERN;
    }

    @Override
    public void apply(Matcher matcher, MetaState state) {
        if (matcher.group(1) == null) {
            state.setHoverBackground(DEFAULT_BACKGROUND);
            return;
        }

        String value = matcher.group(1);
        String hex = value.matches("[0-9a-fA-F]{6}") ? value : NamedColors.HEX_BY_NAME.get(value.toLowerCase(Locale.ROOT));
        if (hex == null) {
            return;
        }

        int alpha = Math.min(100, Integer.parseInt(matcher.group(2))) * 255 / 100;
        Color color = new Color(Integer.parseInt(hex, 16));
        state.setHoverBackground(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
    }
}
