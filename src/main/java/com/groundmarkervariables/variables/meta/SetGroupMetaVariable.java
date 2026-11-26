package com.groundmarkervariables.variables.meta;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final Pattern PATTERN = Pattern.compile("\\{setGroup\\s+([^{}?:<>=!,]+?)\\s*(?:,([^{}]*))?\\}", Pattern.CASE_INSENSITIVE);
    private static final int MAX_OPTIONS = 10;
    private static final String DEFAULT_MARK = "*";
    private static final String TEMPORARY_MARK = "~";

    private final GroupState groupState;

    @Override
    public Pattern pattern() {
        return PATTERN;
    }

    @Override
    public void apply(Matcher matcher, MetaState state) {
        String name = matcher.group(1).trim();
        boolean temporary = name.startsWith(TEMPORARY_MARK);
        name = temporary ? name.substring(TEMPORARY_MARK.length()).trim() : name;
        groupState.seen(name, temporary);
        if (matcher.group(2) == null) {
            state.addGroupMenu(name, null);
            return;
        }

        List<String> options = options(matcher.group(2));
        groupState.declare(name, options, defaultOption(matcher.group(2)));
        state.addGroupMenu(name, options);
    }

    // The comma-separated options, without the default's *.
    private static List<String> options(String text) {
        List<String> options = new ArrayList<>();
        for (String option : text.split(",")) {
            option = unmarked(option.trim());
            if (!option.isEmpty() && options.size() < MAX_OPTIONS) {
                options.add(option);
            }
        }
        return options;
    }

    // The first option marked with *.
    @Nullable
    private static String defaultOption(String text) {
        for (String option : text.split(",")) {
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
