package com.groundmarkervariables.variables.meta;

import com.groundmarkervariables.variables.Args;
import java.util.List;
import java.util.regex.Matcher;
import javax.inject.Inject;

// {borderWidth=<width>} -- this tile's own outline width, same units as the Border width config,
// capped at MAX_WIDTH.
public class BorderWidthMetaVariable implements MetaVariable {
    static final Args ARGS = Args.of("borderWidth=").joined(VALUE).main(List::of, Args.NO_ICON);
    private static final float MAX_WIDTH = 10f;

    @Inject
    private BorderWidthMetaVariable() {
    }

    @Override
    public Args args() {
        return ARGS;
    }

    @Override
    public void apply(Matcher matcher, MetaState state) {
        state.setBorderWidth(Math.max(0, Math.min(MAX_WIDTH, Float.parseFloat(ARGS.main(matcher)))));
    }
}
