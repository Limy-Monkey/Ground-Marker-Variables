package com.groundmarkervariables.variables.meta;

import com.groundmarkervariables.variables.Args;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;

import com.groundmarkervariables.variables.support.GroupState;

// {setGroup <name>, <option>, <option>, ...} -- declares <name>'s options, picked from the
// shift+right-click Tile Variables menu of this tile, in this order. See {group <name>}. A leading *
// marks the default, e.g. *Any. Without options, only adds the menu, for options declared by another tile.
// A leading ~ on the name, e.g. ~Role, unpicks the group once you leave its {setGroup} tiles.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SetGroupMetaVariable implements MetaVariable {
    // Its options are its extras, after commas.
    private static final Args ARGS = Args.of("setGroup").main(List::of, Args.NO_ICON).extras(name -> List.of(), Args.NO_ICON);
    private static final int MAX_OPTIONS = 10;
    private static final String DEFAULT_MARK = "*";
    private static final String TEMPORARY_MARK = "~";

    private final GroupState groupState;

    @Override
    public Args args() {
        return ARGS;
    }

    @Override
    public void apply(Matcher matcher, MetaState state) {
        String name = ARGS.main(matcher);
        boolean temporary = name.startsWith(TEMPORARY_MARK);
        name = temporary ? name.substring(TEMPORARY_MARK.length()).trim() : name;
        groupState.seen(name, temporary);
        List<String> given = ARGS.options(matcher).extras();
        if (given.isEmpty()) {
            state.addGroupMenu(name, null);
            return;
        }

        List<String> options = options(given);
        groupState.declare(name, options, defaultOption(given));
        state.addGroupMenu(name, options);
    }

    // The options, without the default's *.
    private static List<String> options(List<String> given) {
        List<String> options = new ArrayList<>();
        for (String option : given) {
            option = unmarked(option.trim());
            if (!option.isEmpty() && options.size() < MAX_OPTIONS) {
                options.add(option);
            }
        }
        return options;
    }

    // The first option marked with *.
    @Nullable
    private static String defaultOption(List<String> given) {
        for (String option : given) {
            option = option.trim();
            if (option.startsWith(DEFAULT_MARK) && !unmarked(option).isEmpty()) {
                return unmarked(option);
            }
        }
        return null;
    }

    private static String unmarked(String option) {
        return option.startsWith(DEFAULT_MARK) ? option.substring(DEFAULT_MARK.length()).trim() : option;
    }
}
