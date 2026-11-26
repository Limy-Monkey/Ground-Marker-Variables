package com.groundmarkervariables.variables.label;

import com.groundmarkervariables.variables.Args;
import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.BossAliases;
import com.groundmarkervariables.variables.support.BossKillCountTracker;
import com.groundmarkervariables.variables.support.CollectionLog;
import com.groundmarkervariables.variables.support.LootTrackerRecords;
import com.groundmarkervariables.variables.support.RuneProfileCollectionLog;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;

// {log <page>[, missing][, noicons][, iconsOnly][, itemsPerLine=N][, maxItems=N]} -> how many items of that collection
// log page the player has, from the RuneProfile plugin (see RuneProfileCollectionLog). Rich Text: "<page>:
// obtained/total" (not with iconsOnly), then the obtained items' icons (or with missing, the rest), itemsPerLine to a
// line, at most maxItems.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class LogLabelVariable implements LabelVariable {
    private static final int DEFAULT_ITEMS_PER_LINE = 10;
    private static final String NOT_FOUND = "RuneProfile plugin not found";

    private final Client client;
    private final RuneProfileCollectionLog collectionLog;
    private final LootTrackerRecords lootTrackerRecords;
    private final ItemManager itemManager;
    private final BossKillCountTracker killCounts;
    private final RichText richText;
    private final CollectionLog pages;

    private final Args args = Args.of("log")
        .main(this::pageSuggestions, Args.BOSS_ICON)
        .flag("missing", "noicons", "iconsOnly")
        .number("itemsPerLine", "maxItems");

    @Override
    public Pattern pattern() {
        return args.pattern();
    }

    @Override
    public Args args() {
        return args;
    }

    // Just the obtained count, for math and comparisons.
    @Override
    public String resolvePlain(Matcher matcher) {
        RuneProfileCollectionLog.Entry entry = entry(matcher);
        RuneProfileCollectionLog.Page page = entry == null ? null : entry.page();
        return page == null ? null : String.valueOf(withLoot(page, matcher).obtained);
    }

    @Override
    public String resolveRich(Matcher matcher) {
        RuneProfileCollectionLog.Entry entry = entry(matcher);
        if (entry == null) {
            return null;
        }
        RuneProfileCollectionLog.Page page = entry.page();
        if (page == null) {
            return entry.error() != null ? entry.error() : entry.timedOut() ? NOT_FOUND : richText.animatedDots(client.getTickCount());
        }
        page = withLoot(page, matcher);

        Args.Options options = args.options(matcher);
        boolean iconsOnly = options.flag("iconsOnly");
        String text = iconsOnly ? "" : richText.labeled(page.name, page.obtained + "/" + page.items.size()) + kcText(page, matcher);
        if (options.flag("noicons")) {
            return text;
        }

        // Obtained items, or with missing the rest; without a title, no break before the first row.
        boolean missing = options.flag("missing");
        List<RuneProfileCollectionLog.Item> items = page.items.stream().filter(item -> (item.quantity > 0) != missing)
            .collect(Collectors.toList());
        return text + RichText.itemRows(items, item -> item.id, item -> item.quantity, options.number("itemsPerLine", DEFAULT_ITEMS_PER_LINE),
            options.number("maxItems", Integer.MAX_VALUE), !iconsOnly);
    }

    // " kc: N" from {kc}'s tracker, by the page's name or as written; empty if unknown.
    private String kcText(RuneProfileCollectionLog.Page page, Matcher matcher) {
        Integer kc = killCounts.getKc(BossAliases.resolve(page.name));
        if (kc == null) {
            kc = killCounts.getKc(BossAliases.resolve(args.main(matcher)));
        }
        return kc == null ? "" : " kc: " + richText.highlightValue(String.valueOf(kc));
    }

    // Add LootTracker data unknown by RuneProfile yet
    private RuneProfileCollectionLog.Page withLoot(RuneProfileCollectionLog.Page page, Matcher matcher) {
        String written = args.main(matcher);
        LootTrackerRecords.Record record = lootTrackerRecords.find(page.name);
        if (!record.found && !page.name.equalsIgnoreCase(written)) {
            record = lootTrackerRecords.find(written);
        }
        if (!record.found) {
            return page;
        }
        Map<Integer, Integer> unnoted = new HashMap<>();
        record.quantities.forEach((id, quantity) -> unnoted.merge(itemManager.canonicalize(id), quantity, Integer::sum));
        return page.withAtLeast(unnoted);
    }

    private RuneProfileCollectionLog.Entry entry(Matcher matcher) {
        String page = args.main(matcher);
        return page == null ? null : collectionLog.page(page);
    }

    private Set<String> pageSuggestions() {
        return pages.pageSuggestions();
    }
}
