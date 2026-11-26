package com.groundmarkervariables.chatbox.completion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

// What the editor knows about each variable, from variables.json (the docs site's source too), keyed by its
// completable name (e.g. "lvl_"). An entry with variants (has<Spell>) stands for each of them.
@Slf4j
final class VariableCatalog {
    // Suggested after "{": every entry whose syntax starts with "{", in the file's order.
    static final List<String> NAMES = new ArrayList<>();
    // Not complete without more typed, e.g. "kc", "lvl_".
    static final Set<String> REQUIRING_ARGUMENT = new LinkedHashSet<>();
    // Taking "icon" right after the name.
    static final Set<String> WITH_ICON = new LinkedHashSet<>();
    // Other names a variant is also written as, e.g. hasFreezes.
    static final Map<String, List<String>> ALIASES = new HashMap<>();
    private static final Map<String, String> DESCRIPTIONS = new HashMap<>();
    // Each entry's parameters' "complete" words, in order.
    private static final Map<String, List<String>> COMPLETIONS = new HashMap<>();
    // Lowercase name of each variable typed after "{" -> what to show while typing it, see hints().
    private static final Map<String, Hint> HINTS = new HashMap<>();
    // A parameter that's one word, optionally "=<value>" or "=N" after it: the word (with its "=").
    private static final Pattern WORD_OPTION = Pattern.compile("([A-Za-z]+=?)(?:<[^>]*>|N)?");
    // Lowercase entry name -> lowercase completed word -> its parameter's description.
    private static final Map<String, Map<String, String>> COMPLETION_DESCRIPTIONS = new HashMap<>();

    static {
        try (Reader reader = new InputStreamReader(VariableCatalog.class.getResourceAsStream("variables.json"), StandardCharsets.UTF_8)) {
            for (Map.Entry<String, JsonElement> entry : new JsonParser().parse(reader).getAsJsonObject().entrySet()) {
                add(entry.getKey(), entry.getValue().getAsJsonObject());
            }
        } catch (Exception e) {
            log.warn("Couldn't load variables.json", e);
        }
    }

    private VariableCatalog() {
    }

    @Nullable
    static String description(String name) {
        return DESCRIPTIONS.get(name);
    }

    static List<String> completions(String name) {
        return COMPLETIONS.getOrDefault(name, Collections.emptyList());
    }

    // The description of the parameter word (a suggestion's label, e.g. " icon" or "itemsOnly") completes for
    // variable, any case; null if none.
    @Nullable
    static String completionDescription(String variable, String word) {
        Map<String, String> words = COMPLETION_DESCRIPTIONS.get(variable.trim().toLowerCase(Locale.ROOT));
        return words == null ? null : words.get(word.replaceFirst("^[\\s,]+", "").toLowerCase(Locale.ROOT));
    }

    // A variable's line ("{rsn}: Display name"), its freely typed parameters' ("name: description"), and its
    // "requiresValue" parameters' by the word the value follows ("@" -> "@<dir>: ...").
    private static final class Hint {
        // Its parameters follow its name directly (no space in its syntax), e.g. {highlightSW=<col>}, {lvl_<skill>}.
        private final boolean joined;
        @Nullable
        private final String summary;
        private final List<String> parameters;
        private final Map<String, String> valueWords;

        private Hint(boolean joined, @Nullable String summary, List<String> parameters, Map<String, String> valueWords) {
            this.joined = joined;
            this.summary = summary;
            this.parameters = parameters;
            this.valueWords = valueWords;
        }
    }

    // The variable's own line ("{rsn}: Display name") when partial (what follows "{") is exactly its name, else null.
    @Nullable
    static String summary(String partial) {
        Hint hint = HINTS.get(partial.toLowerCase(Locale.ROOT));
        return hint == null ? null : hint.summary;
    }

    // The free-typed parameters of the variable partial is giving arguments to ("showOneItem ", "highlightSW"); empty
    // if none, or if partial is just the name.
    static List<String> hints(String partial) {
        String lower = partial.toLowerCase(Locale.ROOT);
        String best = typedName(lower);
        return best == null || lower.equals(best) ? Collections.emptyList() : HINTS.get(best).parameters;
    }

    // The line of the "requiresValue" parameter whose value is being typed at the end of partial ("ground@",
    // "loot vorkath, items=6", "attackStyle weapon=Twisted"): after its word, with no "," or "}" since. Else null.
    @Nullable
    static String valueHint(String partial) {
        String lower = partial.toLowerCase(Locale.ROOT);
        String best = typedName(lower);
        if (best == null) {
            return null;
        }
        String line = null;
        int at = -1;
        for (Map.Entry<String, String> value : HINTS.get(best).valueWords.entrySet()) {
            int index = lower.lastIndexOf(value.getKey());
            if (index > at && lower.indexOf(',', index) < 0 && lower.indexOf('}', index) < 0) {
                at = index;
                line = value.getValue();
            }
        }
        return line;
    }

    // The longest variable name lower (lowercase, what follows "{") is giving arguments to, or null.
    @Nullable
    private static String typedName(String lower) {
        String best = null;
        for (Map.Entry<String, Hint> entry : HINTS.entrySet()) {
            String name = entry.getKey();
            boolean matches = entry.getValue().joined ? lower.startsWith(name)
                : lower.equals(name) || lower.startsWith(name + " ") || lower.startsWith(name + ",");
            if (matches && (best == null || name.length() > best.length())) {
                best = name;
            }
        }
        return best;
    }

    private static void add(String key, JsonObject entry) {
        putDescription(key, entry);
        List<String> completions = new ArrayList<>();
        List<String> hints = new ArrayList<>();
        Map<String, String> valueWords = new HashMap<>();
        if (entry.has("parameters")) {
            for (JsonElement element : entry.getAsJsonArray("parameters")) {
                JsonObject parameter = element.getAsJsonObject();
                String name = parameter.get("name").getAsString();
                // Described when suggested: its "complete" words (a word, or {word, description} with its own), or a lone
                // word option, e.g. icon, hideWorld, weapon=.
                String description = parameter.has("description") ? parameter.get("description").getAsString() : null;
                Map<String, String> byWord = COMPLETION_DESCRIPTIONS.computeIfAbsent(key.toLowerCase(Locale.ROOT), k -> new HashMap<>());
                Matcher word = WORD_OPTION.matcher(name);
                // Its line shows while its value is typed after its word (its "complete" words, or its lone word).
                if (parameter.has("requiresValue") && parameter.get("requiresValue").getAsBoolean() && description != null) {
                    List<String> valueWordsOf = new ArrayList<>();
                    if (parameter.has("complete")) {
                        parameter.getAsJsonArray("complete").forEach(option -> valueWordsOf.add(option.isJsonObject()
                            ? option.getAsJsonObject().get("word").getAsString() : option.getAsString()));
                    } else if (word.matches()) {
                        valueWordsOf.add(word.group(1));
                    }
                    valueWordsOf.forEach(w -> valueWords.put(w.toLowerCase(Locale.ROOT), name + ": " + description));
                }
                if (parameter.has("complete")) {
                    for (JsonElement option : parameter.getAsJsonArray("complete")) {
                        JsonObject own = option.isJsonObject() ? option.getAsJsonObject() : null;
                        String completed = own != null ? own.get("word").getAsString() : option.getAsString();
                        String completedDescription = own != null && own.has("description") ? own.get("description").getAsString() : description;
                        completions.add(completed);
                        if (completedDescription != null) {
                            byWord.put(completed.toLowerCase(Locale.ROOT), completedDescription);
                        }
                    }
                    continue;
                }
                // Each of "a / b" too.
                for (String part : name.split(" / ")) {
                    Matcher partWord = WORD_OPTION.matcher(part);
                    if (partWord.matches() && description != null) {
                        byWord.put(partWord.group(1).toLowerCase(Locale.ROOT), description);
                    }
                }
                if (description != null && !name.equals("icon") && !name.equals("...") && !name.startsWith("{/")) {
                    hints.add(name + ": " + description);
                }
            }
        }
        COMPLETIONS.put(key, completions);

        String syntax = entry.get("syntax").getAsString();
        if (!syntax.startsWith("{")) {
            return;
        }
        boolean joined = !syntax.startsWith("{" + key + " ");
        boolean requiresArgument = entry.has("requiresArgument") && entry.get("requiresArgument").getAsBoolean();
        boolean icon = entry.has("icon") && "yes".equals(entry.get("icon").getAsString());
        List<String> names = new ArrayList<>();
        if (entry.has("variants")) {
            for (JsonElement element : entry.getAsJsonArray("variants")) {
                JsonObject variant = element.getAsJsonObject();
                String name = variant.get("name").getAsString();
                names.add(name);
                putDescription(name, variant);
                // Its parameters are the entry's.
                COMPLETION_DESCRIPTIONS.put(name.toLowerCase(Locale.ROOT), COMPLETION_DESCRIPTIONS.get(key.toLowerCase(Locale.ROOT)));
                if (variant.has("aliases")) {
                    ALIASES.put(name, strings(variant.getAsJsonArray("aliases")));
                }
            }
        } else {
            names.add(key);
        }

        for (String name : names) {
            NAMES.add(name);
            // A variant's line is its own name and description.
            String description = DESCRIPTIONS.get(name);
            String line = entry.has("variants") ? "{" + name + "}" : syntax;
            HINTS.put(name.toLowerCase(Locale.ROOT), new Hint(joined,
                description == null ? null : line + ": " + description.replace("<br>", "; "), hints, valueWords));
            if (requiresArgument) {
                REQUIRING_ARGUMENT.add(name);
            } else if (icon) {
                WITH_ICON.add(name);
            }
        }
    }

    private static void putDescription(String name, JsonObject entry) {
        if (entry.has("description")) {
            DESCRIPTIONS.put(name, entry.get("description").getAsString());
        }
    }

    private static List<String> strings(JsonArray array) {
        List<String> out = new ArrayList<>(array.size());
        array.forEach(element -> out.add(element.getAsString()));
        return out;
    }
}
