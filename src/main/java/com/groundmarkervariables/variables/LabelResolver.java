package com.groundmarkervariables.variables;

import com.groundmarkervariables.GroundMarkerVariablesConfig;
import com.groundmarkervariables.markers.PreparedLabel;
import com.groundmarkervariables.variables.label.BooleanVariable;
import com.groundmarkervariables.variables.label.ConditionalVariable;
import com.groundmarkervariables.variables.label.MathVariable;
import com.groundmarkervariables.variables.label.MetronomeLabelVariable;
import com.groundmarkervariables.variables.label.PartyLabelVariable;
import com.groundmarkervariables.variables.meta.GroundGuard;
import com.groundmarkervariables.variables.meta.TileMetaGuard;
import com.groundmarkervariables.variables.support.Emojis;
import com.groundmarkervariables.variables.support.NamedColors;
import com.groundmarkervariables.variables.support.PartyMemberContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

// Applies every registered LabelVariable to a marker's label. To add a new base variable
// (one usable directly and inside a {expr == value ? a : b} conditional): write a
// LabelVariable implementation and add it to VariableRegistry — nothing here needs to
// change. BooleanVariable and ConditionalVariable are listed separately since they depend on
// that same registry via ConditionEvaluator (see VariableRegistry's comment for why).
//
// MetronomeLabelVariable is listed separately too, deliberately LAST (after conditional)
// Per Plugin Hub review, metronomes inside of conditionals are too risky for abuse.
public class LabelResolver {
    private static final Pattern HAS_VARIABLE = Pattern.compile(".*\\{.*\\}.*", Pattern.DOTALL);
    private static final Pattern PARTY_START = Pattern.compile("\\{([\\^&*])?party(?=[\\s}])", Pattern.CASE_INSENSITIVE);
    private static final Pattern OPERATOR_START = Pattern.compile("^(?:==|!=|<|>|\\|\\||&&|\\?|[+\\-*/]\\s)");
    // {flag [condition]}...{/flag [condition]} (see extractFlags), a condition on it, what doesn't count as shown, and the flag.
    private static final String FLAG_TAG_CONDITION = "((?:(?=[\\s=!?&|<>])(?:[^{}]|\\{[^{}]*\\})*)?)";
    private static final Pattern FLAG_BLOCK = Pattern.compile("\\{flag" + FLAG_TAG_CONDITION + "\\}((?:(?!\\{flag[\\s=!?&|<>}]).)*?)\\{/flag" + FLAG_TAG_CONDITION + "\\}",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern FLAG_CONDITION = Pattern.compile("\\{([\\^&*]?)flag(?=\\s*(?:==|!=|\\?|&&|\\|\\|))", Pattern.CASE_INSENSITIVE);
    private static final Pattern HIDDEN = Pattern.compile("\\{[^{}]*\\}|</?col[^>]*>|\\\\[snt]|\\s");
    private static final String FLAG = ":triangular_flag_on_post:";
    private static final char PLACEHOLDER_START = '';
    private static final char PLACEHOLDER_END = '';

    private final List<LabelVariable> baseVariables;
    private final MathVariable math;
    private final BooleanVariable booleanVariable;
    private final ConditionalVariable conditional;
    private final MetronomeLabelVariable metronome;
    private final GroundMarkerVariablesConfig config;
    private final Emojis emojis;
    private final PartyMemberContext memberContext;
    private final PartyLabelVariable party;
    private boolean inParty;
    private boolean plainByDefault;

    // Built lazily per variable, then reused -- this instance is shared by every CachedMarker.
    private final Map<LabelVariable, Pattern> prefixedPatterns = new HashMap<>();

    @Inject
    private LabelResolver(VariableRegistry registry, MathVariable math, BooleanVariable booleanVariable, ConditionalVariable conditional,
                          MetronomeLabelVariable metronome, GroundMarkerVariablesConfig config, Emojis emojis, PartyMemberContext memberContext,
                          PartyLabelVariable party) {
        this.memberContext = memberContext;
        this.party = party;
        this.baseVariables = registry.all();
        this.math = math;
        this.booleanVariable = booleanVariable;
        this.conditional = conditional;
        this.metronome = metronome;
        this.config = config;
        this.emojis = emojis;
    }

    // The VariableRegistry variables, e.g. for their args().
    public List<LabelVariable> variables() {
        return baseVariables;
    }

    // A marker's own raw label: {col=} and :emoji: aliases expand first, so neither is mistaken
    // for {variable} / {cond ? a : b} syntax.
    public String resolveMarker(String label) {
        return resolveMarker(prepareMarker(label));
    }

    public String resolveMarker(PreparedLabel prepared) {
        return prepared.getGuarded() == null ? null : TileMetaGuard.restore(resolve(prepared.getGuarded()), prepared.getTileMeta());
    }

    // The work before resolving that depends only on the label, so a marker can keep it.
    public PreparedLabel prepareMarker(String label) {
        String expanded = emojis.expand(label == null ? null : NamedColors.expandColorAliases(label));
        if (expanded == null) {
            return new PreparedLabel(null, List.of());
        }

        List<String> tileMeta = new ArrayList<>();
        String guarded = TileMetaGuard.protect(GroundGuard.strip(expanded, this::isVariable), tileMeta, this::isVariable);
        return new PreparedLabel(guarded, tileMeta);
    }

    // text, braced, is a variable or math token.
    private boolean isVariable(String text) {
        String token = "{" + text + "}";
        return math.pattern().matcher(token).matches() || baseVariables.stream().anyMatch(variable -> variable.pattern().matcher(token).matches());
    }

    public String resolve(String label) {
        if (label == null || !HAS_VARIABLE.matcher(label).matches()) {
            return label;
        }

        List<String> parties = new ArrayList<>();
        // {partyCount} first, while its own tokens are still literal; then {party} rows (each resolved per member, {flag}s
        // too), then {flag} blocks, as their conditions need what they show.
        String resolved = extractFlags(extractParties(party.resolveCounts(label, this::resolvePlain), parties), parties);

        // Runs first, while a branch's own nested token is still literal -- otherwise its
        // resolved text (e.g. a colon in Rich Text) could corrupt the ?/: split. Also forces
        // Plain for its own <expr> internally (no ^/&/* support).
        resolved = conditional.resolveAll(resolved);

        // {a || {b ? c : d}}: ||/&& chains with nested tokens, which the pass below can't match.
        resolved = booleanVariable.resolveNested(resolved, richByDefault());

        // {expr cmp value} and {a || b} / {a && b}, with no ?: disjoint from conditional's own
        // ?-requiring pattern. Before the base variables, whose patterns (e.g. {hasItem ...})
        // would otherwise swallow a whole "a || b" as their own argument.
        resolved = applyWithMode(booleanVariable, resolved, true);

        resolved = applyWithMode(math, resolved, true);
        for (LabelVariable variable : baseVariables) {
            resolved = applyWithMode(variable, resolved);
        }

        // Metronome stays last, same as today — see the class comment on why.
        resolved = applyWithMode(metronome, resolved);

        return TileMetaGuard.putBack(resolved, parties, PLACEHOLDER_START, PLACEHOLDER_END);
    }

    // text resolved with Plain Text as the default, e.g. to test a condition.
    public String resolvePlain(String text) {
        boolean previous = plainByDefault;
        plainByDefault = true;
        try {
            return resolve(text);
        } finally {
            plainByDefault = previous;
        }
    }

    private boolean richByDefault() {
        return !plainByDefault && config.richTextByDefault();
    }

    // Renders each {party <template>} (which may nest tokens) and leaves a placeholder, so the passes
    // above leave its rows alone. A template starting with an operator is a comparison instead.
    private String extractParties(String text, List<String> parties) {
        Matcher start = PARTY_START.matcher(text);
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (start.find(i)) {
            int close = ConditionalVariable.matchingBrace(text, start.start());
            if (close < 0) {
                break;
            }

            String template = text.substring(start.end(), close).trim();
            if (OPERATOR_START.matcher(template).find()) {
                out.append(text, i, start.end());
                i = start.end();
                continue;
            }

            Character prefixChar = start.group(1) == null ? null : start.group(1).charAt(0);
            boolean rich = TextMode.forPrefix(prefixChar, richByDefault()) == TextMode.RICH;
            String value;
            try {
                value = renderParty(template, rich);
            } catch (RuntimeException e) {
                value = "ERROR";
            }

            out.append(text, i, start.start()).append(PLACEHOLDER_START).append(parties.size()).append(PLACEHOLDER_END);
            parties.add(value != null ? value : text.substring(start.start(), close + 1));
            i = close + 1;
        }
        return out.append(text.substring(i)).toString();
    }

    // {flag}...{/flag}: its content after a flag if it shows anything, else nothing; a placeholder (as {party}). A tag with a
    // condition ({flag ? <icon>}, replacing the flag; {/flag ? <icon>}) is on its block; {flag <condition>} elsewhere, on all.
    private String extractFlags(String text, List<String> parts) {
        Matcher block = FLAG_BLOCK.matcher(text);
        StringBuilder out = new StringBuilder();
        boolean any = false;
        while (block.find()) {
            String content = resolve(block.group(2));
            // Shown: anything left without tokens (e.g. {tip=...}), colors, \s \n \t and spaces.
            String left = content;
            for (String previous = null; !left.equals(previous); ) {
                previous = left;
                left = HIDDEN.matcher(left).replaceAll("");
            }
            boolean shown = !left.isEmpty();
            any |= shown;
            String open = block.group(1);
            parts.add(!shown ? "" : open.isBlank() ? emojis.expand(FLAG) + content : content);
            block.appendReplacement(out, Matcher.quoteReplacement((open.isBlank() ? "" : "{" + shown + open + "}") + PLACEHOLDER_START
                + (parts.size() - 1) + PLACEHOLDER_END + (block.group(3).isBlank() ? "" : "{" + shown + block.group(3) + "}")));
        }
        block.appendTail(out);
        boolean shown = any;
        return FLAG_CONDITION.matcher(out).replaceAll(match -> "{" + match.group(1) + shown);
    }

    // A {party} inside a {party} row is unavailable.
    private String renderParty(String template, boolean rich) {
        if (inParty) {
            return memberContext.unavailable(rich);
        }
        if (!rich) {
            return String.valueOf(party.size());
        }

        inParty = true;
        try {
            return party.render(template, this::resolve);
        } finally {
            inParty = false;
        }
    }

    // Matches this variable's token with an optional ^/&/* mode-override prefix right after
    // its opening brace, then re-matches the variable's own untouched pattern against the
    // prefix-stripped text so resolve() sees the exact match shape it always has.
    private String applyWithMode(LabelVariable variable, String text) {
        return applyWithMode(variable, text, false);
    }

    // composite: math/comparisons, whose own operands get the party-member check instead.
    private String applyWithMode(LabelVariable variable, String text, boolean composite) {
        Pattern prefixed = prefixedPatterns.computeIfAbsent(variable, LabelResolver::withOptionalModePrefix);
        Matcher matcher = prefixed.matcher(text);
        StringBuilder replaced = new StringBuilder();
        while (matcher.find()) {
            Character prefixChar = matcher.group(1) == null ? null : matcher.group(1).charAt(0);
            TextMode mode = TextMode.forPrefix(prefixChar, richByDefault());

            String bareToken = prefixChar == null ? matcher.group(0) : "{" + matcher.group(0).substring(2);
            Matcher bareMatcher = variable.pattern().matcher(bareToken);
            boolean matched = bareMatcher.matches();
            String value;
            try {
                // A token that can't resolve (e.g. a party member's data is missing) shows nothing.
                value = matched ? resolveFor(variable, bareMatcher, mode, composite) : null;
                if (value == null && matched) {
                    value = "";
                }
            } catch (RuntimeException e) {
                value = "";
            }
            if (value != null) {
                matcher.appendReplacement(replaced, Matcher.quoteReplacement(value));
            }
        }
        matcher.appendTail(replaced);
        return replaced.toString();
    }

    private String resolveFor(LabelVariable variable, Matcher matcher, TextMode mode, boolean composite) {
        return composite ? variable.resolve(matcher, mode) : memberContext.resolve(variable, matcher, mode == TextMode.RICH);
    }

    // Every LabelVariable pattern starts with the literal "\{" -- inserting the optional
    // prefix group right after it leaves the rest of the pattern, and its group numbering,
    // untouched.
    private static Pattern withOptionalModePrefix(LabelVariable variable) {
        String source = variable.pattern().pattern();
        return Pattern.compile("\\{(?:([\\^&*]))?" + source.substring(2), variable.pattern().flags());
    }
}
