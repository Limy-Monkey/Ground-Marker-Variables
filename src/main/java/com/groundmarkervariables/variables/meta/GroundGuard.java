package com.groundmarkervariables.variables.meta;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.groundmarkervariables.variables.label.ConditionalVariable;

// {ground[@dir]} and {/ground} only apply outside any token but a condition on {group} alone (as TileMetaGuard), around
// content with no conditional (?, || or &&) but one on {group} alone. Others are removed before resolving, leaving their
// content as normal label text.
public final class GroundGuard {
    private static final Pattern TAG = GroundIcons.TAG;

    private GroundGuard() {
    }

    // isVariable: as TileMetaGuard.protect.
    public static String strip(String label, Predicate<String> isVariable) {
        if (label == null || !TAG.matcher(label).find()) {
            return label;
        }

        // Tags as {start, end, open}, split by whether they're outside every token (or {group}-only conditions).
        List<int[]> topLevel = new ArrayList<>();
        List<int[]> removed = new ArrayList<>();
        Matcher tag = TAG.matcher(label);
        int depth = 0;
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            if (c == '{') {
                tag.region(i, label.length());
                if (tag.lookingAt()) {
                    int[] found = {i, tag.end(), label.charAt(i + 1) == '/' ? 0 : 1};
                    (depth == 0 || TileMetaGuard.allowed(label, i, isVariable) ? topLevel : removed).add(found);
                    i = tag.end() - 1;
                    continue;
                }
                depth++;
            } else if (c == '}' && depth > 0) {
                depth--;
            }
        }

        // A block runs from an open tag to the next close (or the end), as GroundIcons reads it.
        for (int k = 0; k < topLevel.size(); k++) {
            if (topLevel.get(k)[2] != 1) {
                continue;
            }
            int last = k;
            while (last < topLevel.size() - 1 && topLevel.get(last)[2] != 0) {
                last++;
            }
            int end = topLevel.get(last)[2] == 0 ? topLevel.get(last)[0] : label.length();
            if (hasConditional(label, topLevel.get(k)[1], end, isVariable)) {
                removed.addAll(topLevel.subList(k, last + 1));
            }
            k = last;
        }

        if (removed.isEmpty()) {
            return label;
        }
        removed.sort((a, b) -> Integer.compare(a[0], b[0]));
        StringBuilder out = new StringBuilder();
        int last = 0;
        for (int[] range : removed) {
            out.append(label, last, range[0]);
            last = range[1];
        }
        return out.append(label.substring(last)).toString();
    }

    // A {<condition> ? <branches>} or {a || b} / {a && b} token not on {group} alone, at any depth, between from and to.
    private static boolean hasConditional(String label, int from, int to, Predicate<String> isVariable) {
        for (int i = from; i < to; i++) {
            if (label.charAt(i) != '{') {
                continue;
            }
            int close = ConditionalVariable.matchingBrace(label, i);
            if (close < 0) {
                continue;
            }
            String inner = label.substring(i + 1, close);
            int question = TileMetaGuard.topLevelQuestion(inner);
            boolean conditional = question > 0 && !inner.substring(0, question).trim().isEmpty() && !inner.substring(question + 1).trim().isEmpty();
            if (conditional ? !TileMetaGuard.groupOnly(inner.substring(0, question), isVariable)
                : (hasTopLevel(inner, "||") || hasTopLevel(inner, "&&")) && !TileMetaGuard.groupOnly(inner, isVariable)) {
                return true;
            }
        }
        return false;
    }

    // needle outside any nested {...}.
    private static boolean hasTopLevel(String text, String needle) {
        return ConditionalVariable.topLevel(text, 0, needle) >= 0;
    }
}
