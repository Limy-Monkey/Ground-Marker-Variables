package com.groundmarkervariables.variables.meta;

import com.groundmarkervariables.variables.Args;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// A {name=value} (or bare {name}) token that configures how a marker's tile renders, rather
// than substituting into the visible label text -- e.g. {tileCol=red}. Always stripped from
// the label before normal LabelVariable resolution runs (see MetaVariableResolver).
public interface MetaVariable {
    // A joined value: anything but braces and spaces (one it can't use drops the token, see MetaVariableResolver).
    String VALUE = "[^{}\\s]+?";

    // How it's written, which its pattern follows.
    Args args();

    default Pattern pattern() {
        return args().pattern();
    }

    // Applies this token's effect onto state, reading its value with args().
    void apply(Matcher matcher, MetaState state);
}
