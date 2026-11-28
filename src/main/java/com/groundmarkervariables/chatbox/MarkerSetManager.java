package com.groundmarkervariables.chatbox;

import com.groundmarkervariables.chatbox.completion.Completion;
import com.groundmarkervariables.chatbox.completion.LabelCompleter;
import com.groundmarkervariables.markers.Gists;
import com.groundmarkervariables.markers.GroundMarkerPointData;
import com.groundmarkervariables.markers.MarkerSet;
import com.groundmarkervariables.markers.MarkerSets;
import com.groundmarkervariables.markers.MarkerStorage;
import com.groundmarkervariables.markers.minify.MarkerMinifier;
import com.groundmarkervariables.variables.support.LatestData;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.FontTypeFace;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.Text;

// GitHub (see Gists), imported and own marker sets, opened from the chatboxes' logo; also a GitHub user's published sets (see
// browse()). A set's checkbox switches it on and off; clicking its name shows its
// entries, each with its own checkbox. Right-click a set to copy or delete it.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class MarkerSetManager extends SearchableChatbox {
    // Left of Import while the last check failed.
    private static final String SATELLITE_ANTENNA = "1f4e1";
    private static final int ICON_SIZE = 14;
    // Shared only while any listed set is one you published.
    private static final String[] HEADINGS = {"Title", "Tiles", "Source", "Author", "Updated", "Shared"};
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
    // Updated: within the last 364 days, without the year; older, just the month and year.
    private static final DateTimeFormatter RECENT = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter OLD = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    // The widest value each column after Title (which takes the rest) is sized for: Updated, any month's date; Shared, its icon.
    private static final List<List<String>> SIZED_FOR = List.of(List.of(), List.of("999/999"), List.of("Unsubscribed", "Published", "Imported"),
        List.of("LimyMonkey12"), IntStream.rangeClosed(1, 12).mapToObj(month -> LocalDate.of(2026, month, 28))
            .flatMap(date -> Stream.of(RECENT.format(date), OLD.format(date)))
            .collect(Collectors.toList()), List.of());
    // Shared's heading, and a published set's icon there: changed since it was shared, else shared by link only (else
    // MarkerManager.YES); or deleted on GitHub.
    private static final String SHARED_HEADING = "<sprite=People (Clan)>";
    private static final String DRIFTED = ":chains:";
    private static final String PRIVATE = ":detective:";
    private static final String DELETED = ":wastebasket:";
    private static final int SHARED_ICON = 10;
    // Each column's order: Title, Tiles, Source, Author, Updated (unknown dates first going up); Shared's is in order().
    private static final List<Comparator<MarkerSet>> BY_COLUMN = List.of(
        Comparator.comparing(set -> set.title, String.CASE_INSENSITIVE_ORDER),
        Comparator.comparingInt(MarkerSet::tileCount),
        Comparator.comparing(set -> set.kind),
        Comparator.comparing(set -> set.author, String.CASE_INSENSITIVE_ORDER),
        Comparator.comparing(set -> set.updated, Comparator.nullsFirst(Comparator.naturalOrder())));
    // The row's entry for the status line (a check, a failure, or why the table is empty).
    private static final MarkerSet.Entry STATUS = new MarkerSet.Entry(null, null, List.of(), false, true, 0);

    private final MarkerSets markerSets;
    private final MarkerStorage markerStorage;
    private final GroundMarkerVariablesSharingManager sharingManager;
    private final MarkerMinifier markerMinifier;
    private final Gists gists;
    private final LatestData latestData;
    // Resolves the Shared column's icons by name.
    private final LabelCompleter completer;
    // Show on map's pins: the last set shown, until closing.
    private final WorldMapPins mapPins;
    // Show tiles: a set's tiles in the Marker Manager.
    private final Provider<MarkerManager> markerManagerProvider;

    // Sets showing their entries, by key.
    private final Set<String> expanded = new HashSet<>();
    // Picking one of the user's own sets (see pick()), not managing them.
    @Nullable
    private Consumer<MarkerSet> onPick;
    // Ground markers are in the clipboard, for your own sets' Edit > Paste; checked each update.
    private boolean clipboardHasMarkers;
    private boolean showShared;
    // Browsing a GitHub user's published sets (see browse()): whose, their sets with their text once got, and the status
    // line until then.
    @Nullable
    private String browsing;
    @Nullable
    private Map<MarkerSet, String> browsed;
    @Nullable
    private String browseStatus;

    // A set, one of its entries, or the status line (entry == STATUS).
    @RequiredArgsConstructor
    private static final class Row {
        @Nullable
        private final MarkerSet set;
        @Nullable
        private final MarkerSet.Entry entry;
    }

    // By Source at first, then title.
    {
        sortColumn = 2;
    }

    @Override
    protected void open() {
        markerSets.setListener(this::updateIfOpen);
        markerSets.checkForUpdates(false);
        update();
    }

    @Override
    protected void close() {
        markerSets.setListener(null);
        mapPins.clear();
        super.close();
    }

    // The pinned set's tiles, searching for tile's.
    @Override
    public void pinClicked(WorldPoint tile) {
        if (mapPins.getPinnedKey() instanceof MarkerSet) {
            markerManagerProvider.get().show((MarkerSet) mapPins.getPinnedKey(), tileSearch(tile));
        }
    }

    // Opened searching for query, e.g. a set's title and an entry's name (see MarkerManager#findInSets).
    public MarkerSetManager search(String query) {
        this.query = query;
        return this;
    }

    // owner's (a GitHub name) published sets from GitHub; Sets in the crumbs goes back.
    public MarkerSetManager browse(String owner) {
        return browse(Gists.displayName(owner), List.of(owner));
    }

    // owners' published sets, titled title in the crumbs.
    private MarkerSetManager browse(String title, List<String> owners) {
        browsing = title;
        browsed = null;
        browseStatus = "Getting Marker Sets from GitHub...";
        scrollOffset = 0;
        gists.run(() -> {
            Map<MarkerSet, String> found = new LinkedHashMap<>();
            for (String owner : owners) {
                found.putAll(markerSets.download(gists.list(owner)));
            }
            clientThread.invoke(() -> {
                browsed = found;
                browseStatus = found.isEmpty() ? "No published Marker Sets yet." : null;
                updateIfOpen();
            });
        }, () -> clientThread.invoke(() -> {
            browseStatus = gists.getLogin() == null ? "You are being rate-limited by GitHub. Try again later or sign in to GitHub"
                : "There is an issue with your GitHub auth. Please try again or relog.";
            updateIfOpen();
        }));
        updateIfOpen();
        return this;
    }

    @Override
    protected List<Crumb> crumbs() {
        return browsing == null ? super.crumbs() : List.of(new Crumb("Sets", "Back to Marker Sets", this::leaveBrowse),
            new Crumb(browsing + " (" + (browsed == null ? 0 : browsed.size()) + ")", null, null));
    }

    private void leaveBrowse() {
        browsing = null;
        scrollOffset = 0;
        update();
    }

    // Lists only own sets, each clicked to pick it, e.g. to add markers to.
    public MarkerSetManager pick(Consumer<MarkerSet> onPick) {
        this.onPick = onPick;
        return this;
    }

    @Override
    protected String title() {
        return onPick != null ? "Add to which set?" : recycleBin ? "Sets" : "Marker Sets (" + markerSets.listed().size() + ")";
    }

    @Override
    protected Supplier<Completion.Icon> headingIcon(int column) {
        return column == 5 ? () -> completer.icon(SHARED_HEADING) : null;
    }

    // None while picking or browsing.
    @Override
    protected int recycled() {
        return onPick != null || browsing != null ? 0 : markerSets.bin().size();
    }

    // The recycling bin's :recycle: while it has sets (or is open), Browse, Import, and the failed update's mark left of it.
    @Override
    protected void buildTopBar(Widget container, FontTypeFace font, int rightX) {
        rightX = recycleBinButton(container, rightX, this::update);
        // Browse: every GMV partner's sets; your own author page (signed in); a partner's, a subscribed set's author's or anyone's
        // sets; sign in or out.
        String login = gists.getLogin();
        Runnable partnerPage = () -> browse("Partners", Gists.partners("partnersPage"));
        // While browsing, back to the sets first.
        Runnable first = browsing != null ? this::leaveBrowse : partnerPage;
        Widget browseButton = button(container, font, "Browse", browsing != null ? "Back to sets" : "Browse Partners' Sets", "", true, rightX,
            first);
        List<Runnable> browseActions = new ArrayList<>(List.of(first));
        if (browsing != null) {
            addAction(browseButton, browseActions, "Browse Partners' Sets", partnerPage);
        }
        if (login != null) {
            addAction(browseButton, browseActions, "Browse my Author page", () -> browse(login));
        }
        Map<String, Runnable> others = new LinkedHashMap<>();
        Set<String> authors = new LinkedHashSet<>(Gists.partners("othersMenu"));
        markerSets.sets().stream().filter(set -> set.kind == MarkerSet.Kind.GIST).forEach(set -> authors.add(set.gist.owner));
        authors.stream().filter(name -> !Gists.blocked(name)).forEach(name -> others.put(Gists.displayName(name), () -> browse(name)));
        others.put("Other...", this::askBrowse);
        addSubmenu(browseButton, browseActions, "Browse Others' Sets", others);
        addAction(browseButton, browseActions, login == null ? "Sign in to GitHub" : "Sign out of GitHub (" + login + ")", login == null ? this::signIn
            : () -> {
                gists.signOut();
                update();
            });
        rightX = nextX(browseButton);
        // Right-click: check GitHub for set updates now, not waiting for the next check, and fetch LatestData's files again.
        Widget importButton = button(container, font, "Import", "Import from clipboard", "", true, rightX, this::importClipboard);
        addAction(importButton, new ArrayList<>(List.of(this::importClipboard)), "Check for updates", () -> {
            markerSets.checkForUpdates(true);
            latestData.update();
        });
        rightX = nextX(importButton);
        if (markerSets.failed()) {
            Rectangle area = new Rectangle(container.getWidth() - rightX - ICON_SIZE + 4, 5 + (LINE_HEIGHT - ICON_SIZE) / 2, ICON_SIZE,
                ICON_SIZE);
            iconPlacements.add(new SuggestionIconOverlay.Placement(container, area, Completion.Icon.emoji(SATELLITE_ANTENNA)));
            tips.add(new SuggestionIconOverlay.Tip(container, area, "Couldn't check GitHub for set updates"));
        }
    }

    // Each set in the chosen order, with its entries when expanded or searched for.
    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        List<MarkerSet> sets = browsing != null ? new ArrayList<>(browsed == null ? Set.of() : browsed.keySet())
            : recycleBin ? markerSets.bin().values().stream().map(recycled -> recycled.set).collect(Collectors.toList()) : markerSets.listed();
        sets.sort(order());
        for (MarkerSet set : sets) {
            if (onPick != null && set.kind != MarkerSet.Kind.OWN) {
                continue;
            }
            // A tile word only matches entries (see matches).
            boolean setMatches = matches(set.title, List.of()) || matches(set.author, List.of()) || matches(set.kind.title, List.of());
            List<MarkerSet.Entry> entries = new ArrayList<>();
            for (MarkerSet.Entry entry : set.entries) {
                // Its words may be split between the set's title and the entry's, e.g. "tob lobby".
                if (setMatches || matches(set.title + "\n" + entry.name(),
                    entry.tiles.stream().map(GroundMarkerPointData::worldPoint).collect(Collectors.toList()))) {
                    entries.add(entry);
                }
            }
            if (!setMatches && entries.isEmpty()) {
                continue;
            }
            rows.add(new Row(set, null));
            if (expanded.contains(set.key()) || !query.isEmpty() && !setMatches) {
                entries.forEach(entry -> rows.add(new Row(set, entry)));
            }
        }
        return rows;
    }

    // text holds the search's words, but tile words, which tiles must match (see tileMatch).
    private boolean matches(String text, List<WorldPoint> tiles) {
        StringBuilder words = new StringBuilder();
        for (String word : normalized(query).split(" ")) {
            Boolean tile = tileMatch(word, tiles);
            if (tile == null) {
                words.append(word).append(' ');
            } else if (!tile) {
                return false;
            }
        }
        return ChatboxWidgets.hasWords(text, words.toString().trim());
    }

    @Override
    protected void buildRows(Widget container) {
        clipboardHasMarkers = !recycleBin && !sharingManager.clipboardMarkers().isEmpty();
        List<Row> rows = rows();
        showShared = rows.stream().anyMatch(row -> mine(row.set));
        String status = browsing != null ? browseStatus : markerSets.status();
        boolean table = !rows.isEmpty();
        if (!table) {
            status = !query.isEmpty() ? "No marker sets match \"" + query + "\"."
                : status != null ? status : "No marker sets yet.";
        }
        if (status != null) {
            rows.add(new Row(null, STATUS));
        }
        columns(container, Arrays.copyOf(HEADINGS, showShared ? 6 : 5), SIZED_FOR, 0);
        String shown = status;
        table(container, table, rows.size(), (i, y) -> {
            Row row = rows.get(i);
            if (row.entry == STATUS) {
                addText(container, Text.escapeJagex(shown), widgets.fontId(), DETAIL_COLOR, 6, y);
            } else if (row.entry == null) {
                addSet(container, row.set, y);
            } else {
                addEntry(container, row.set, row.entry, y);
            }
        }, (i, y) -> {
            // Scrolled into a set's entries: its row stays on the fixed line, in place of the headings.
            Row row = rows.get(i);
            if (row.entry == null || row.entry == STATUS) {
                return false;
            }
            addSet(container, row.set, y);
            return true;
        });
    }

    // By sortColumn, reversed if descending, then title. Shared: yours published first, shared longest ago first.
    private Comparator<MarkerSet> order() {
        Comparator<MarkerSet> byColumn = sortColumn < BY_COLUMN.size() ? BY_COLUMN.get(sortColumn)
            : Comparator.comparing(set -> mine(set) ? set.gist.shared : null, Comparator.nullsLast(Comparator.naturalOrder()));
        return (descending ? byColumn.reversed() : byColumn).thenComparing(BY_COLUMN.get(0));
    }

    // Tiles in the set's switched-on entries, whether or not the set itself is on.
    private int enabledTiles(MarkerSet set) {
        return set.entries.stream().filter(entry -> markerSets.enabled(set, entry)).mapToInt(entry -> entry.tiles.size()).sum();
    }

    // Title, tiles, source, author and updated; the title cell is the one clicked to expand, copy or delete. In the
    // recycling bin, the crossed check box restores it, and the title cell restores, expands or deletes it for good.
    private void addSet(Widget container, MarkerSet set, int y) {
        // Browsing: checked once subscribed, which its check box toggles.
        boolean added = browsing != null && markerSets.existing(set) != null;
        boolean enabled = browsing != null ? added : markerSets.enabled(set);
        Widget checkbox = checkbox(container, enabled, 6, y);
        String subscribe = added ? "Unsubscribe" : "Subscribe";
        Runnable toggle = added ? () -> confirmDelete(markerSets.existing(set)) : () -> {
            sendChatMessage(markerSets.addShared(set, browsed.get(set)) ? "You subscribed to \"" + set.title + "\"." : MarkerSets.TOO_LARGE);
            update();
        };
        addAction(checkbox, new ArrayList<>(), browsing != null ? subscribe : recycleBin ? "Restore" : enabled ? "Disable" : "Enable",
            browsing != null ? toggle : recycleBin ? () -> restore(set) : () -> markerSets.setEnabled(set, !enabled));

        String[] cells = {set.title, enabledTiles(set) + "/" + set.tileCount(), source(set), set.author,
            set.updated == null ? set.kind == MarkerSet.Kind.PLUGIN ? "" : "Unknown"
                : (set.updated.isAfter(LocalDate.now().minusDays(365)) ? RECENT : OLD).format(set.updated)};
        Widget label = null;
        for (int column = 0; column < cells.length; column++) {
            Widget cell = cell(container, cells[column], column == 0 ? 0x000000 : DETAIL_COLOR, column, y);
            label = label == null ? cell : label;
        }
        describe(container, set.description, cellArea(0, y));
        // Yours, published: shared as it is, changed since, or deleted on GitHub.
        MarkerSet.Gist gist = set.gist;
        boolean published = published(set);
        if (mine(set)) {
            boolean drifted = set.version > gist.sharedVersion;
            String shared = DATE.format(LocalDate.parse(gist.shared));
            tips.add(new SuggestionIconOverlay.Tip(container, rowIcon(container, columnX(5), y, SHARED_ICON, () -> completer.icon(gist.gone
                ? DELETED : drifted ? DRIFTED : gist.secret ? PRIVATE : MarkerManager.YES)), gist.gone
                ? "Deleted from GitHub</br>Publish it again to share it"
                : (gist.secret ? "Shared privately</br>" : "Shared publicly</br>") + (drifted ? "Contains unpublished changes since " : "") + shared));
        }
        if (cells[2].equals("Unsubscribed")) {
            tips.add(new SuggestionIconOverlay.Tip(container, cellArea(2, y),
                "Its author deleted it from their published sets. Your copy is kept, but no longer updates."));
        }
        List<Runnable> actions = new ArrayList<>();
        if (onPick != null) {
            addAction(label, actions, "Choose", () -> onPick.accept(set));
        }
        addAction(label, actions, expanded.contains(set.key()) ? "Compact set" : "Expand set", () -> toggleExpanded(set));
        if (browsing != null) {
            addAction(label, actions, subscribe, toggle);
        }
        if (recycleBin) {
            Rectangle titleArea = cellArea(0, y);
            addAction(label, actions, "Restore", () -> restore(set));
            addAction(label, actions, "Delete", () -> confirmPurge(markerSets.bin().minutesLeft(set.key()), titleArea,
                "the marker set \"" + Text.escapeJagex(set.title) + "\"", () -> {
                    markerSets.bin().remove(set.key());
                    sendChatMessage("The marker set \"" + set.title + "\" was deleted permanently.");
                }));
            ChatboxWidgets.hover(label, 0x000000);
            return;
        }
        // Link > Open / Copy: a browsed or subscribed set's, or yours once published.
        Map<String, Runnable> link = new LinkedHashMap<>();
        if (browsing != null || set.kind == MarkerSet.Kind.GIST || published) {
            link.put("Open", () -> LinkBrowser.browse(gist.link()));
            link.put("Copy", () -> clip(gist.link(), "The link to \"" + set.title + "\" was copied to your clipboard."));
        }
        if (browsing != null) {
            addShowOnMap(label, actions, set, set.tiles(), cellArea(0, y));
            addSubmenu(label, actions, "Link", link);
            if (gist.owner.equalsIgnoreCase(gists.getLogin())) {
                addAction(label, actions, "Unpublish", () -> unpublish(set));
            }
            ChatboxWidgets.hover(label, 0x000000);
            return;
        }
        // Then Edit, Manage, Map, Publish and Delete (each only where it applies).
        if (set.kind == MarkerSet.Kind.OWN) {
            // Yours can be given any author; anyone else's (a clone, anon, Unknown) claimed by the logged-in name.
            Player player = client.getLocalPlayer();
            String name = player != null ? player.getName() : null;
            Map<String, Runnable> edit = new LinkedHashMap<>();
            edit.put("Set title", () -> askTitle(set, this::reopen));
            edit.put("Set description", () -> askDescription(set.description, text -> markerSets.setDescription(set, text)));
            if (clipboardHasMarkers) {
                edit.put("Paste", () -> paste(set));
            }
            // Blank for anonymous; a name keeps the old author in its history, as claiming does.
            edit.put("Set author", () -> askText("Author (blank for anonymous)", MarkerSet.ANON.equals(set.author) ? "" : set.author, author -> {
                if (author.isBlank()) {
                    markerSets.setAuthor(set, MarkerSet.ANON);
                } else {
                    markerSets.claim(set, author.trim());
                }
                reopen();
            }));
            if (name != null && !name.equalsIgnoreCase(set.author)) {
                edit.put("Claim Authorship", () -> markerSets.claim(set, name));
            }
            addSubmenu(label, actions, "Edit", edit);
        }
        Map<String, Runnable> manage = new LinkedHashMap<>();
        manage.put("Copy", () -> copy(set));
        manage.put("Export", () -> exportTiles(set));
        manage.put("Clone", () -> cloneSet(set));
        if (onPick == null) {
            manage.put("Show tiles", () -> markerManagerProvider.get().show(set, ""));
        }
        addSubmenu(label, actions, "Manage", manage);
        addShowOnMap(label, actions, set, set.tiles(), cellArea(0, y));
        addSubmenu(label, actions, "Link", link);
        if (set.kind == MarkerSet.Kind.GIST) {
            addAction(label, actions, "View author's other sets", () -> browse(gist.owner));
        }
        // Yours: Publish to GitHub, or once published (see published()), Publish Updates and Unpublish.
        if (set.kind == MarkerSet.Kind.OWN && onPick == null) {
            Map<String, Runnable> publish = new LinkedHashMap<>();
            publish.put(published ? "Publish Updates" : "Publish to GitHub", () -> publish(set));
            if (published) {
                publish.put("Unpublish", () -> unpublish(set));
            }
            addSubmenu(label, actions, "Publish", publish);
        }
        // Last, Delete > Dissolve / Delete; a subscribed one's Unsubscribe (Delete once gone). A plugin's group is its to clear.
        if (set.kind == MarkerSet.Kind.SHARED || set.kind == MarkerSet.Kind.OWN) {
            Map<String, Runnable> delete = new LinkedHashMap<>();
            delete.put("Dissolve", () -> confirmDissolve(set));
            delete.put("Delete", () -> confirmDelete(set));
            addSubmenu(label, actions, "Delete", delete);
        } else if (set.kind == MarkerSet.Kind.GIST) {
            addAction(label, actions, gist.gone ? "Delete" : "Unsubscribe", () -> confirmDelete(set));
        }
        ChatboxWidgets.hover(label, 0x000000);
    }

    // Map (see WorldMapPins#mapChoices): all set's tiles pinned (each named by its entry) or on RuneLite.net, the open
    // world map moved to tiles.
    private void addShowOnMap(Widget widget, List<Runnable> actions, MarkerSet set, List<GroundMarkerPointData> tiles, Rectangle labelArea) {
        addSubmenu(widget, actions, "Map", mapPins.mapChoices(tiles, titledTiles(set), () -> mapPins.pin(set), text -> notice(labelArea, text),
            this::updateIfOpen));
    }

    // set's tiles, each labeled by its entry's title if it has one, for RuneLite.net.
    private static List<GroundMarkerPointData> titledTiles(MarkerSet set) {
        List<GroundMarkerPointData> tiles = new ArrayList<>();
        set.entries.forEach(entry -> entry.tiles.forEach(tile -> tiles.add(entry.title == null ? tile : tile.withLabel(entry.title))));
        return tiles;
    }

    private void addEntry(Widget container, MarkerSet set, MarkerSet.Entry entry, int y) {
        boolean enabled = markerSets.enabled(set, entry);
        int x = columnX(0);
        Widget checkbox = checkbox(container, enabled, x, y);
        int textX = x + checkbox.getOriginalWidth() + 4;
        int textWidth = columnX(1) - textX - 6;
        int color = markerSets.enabled(set) ? 0x000000 : ChatboxWidgets.DISABLED_COLOR;
        Widget label = ChatboxWidgets.text(container, Text.escapeJagex(fit(entry.name(), textWidth)), widgets.fontId(), color, textX, y,
            textWidth, ROW_HEIGHT);
        describe(container, entry.description, new Rectangle(textX, y, textWidth, ROW_HEIGHT));
        cell(container, String.valueOf(entry.tiles.size()), DETAIL_COLOR, 1, y);
        // A recycled or browsed set's entries are only listed.
        for (Widget clickable : recycleBin || browsing != null ? List.<Widget>of() : List.of(checkbox, label)) {
            List<Runnable> actions = new ArrayList<>();
            addAction(clickable, actions, enabled ? "Disable" : "Enable", () -> markerSets.setEnabled(set, entry, !enabled));
            addShowOnMap(clickable, actions, set, entry.tiles, new Rectangle(textX, y, textWidth, ROW_HEIGHT));
            Runnable copy = () -> copyTiles(entry.tiles, "\"" + entry.name() + "\" in the marker set \"" + set.title + "\"");
            // Your own: Edit > Label (one tile's: a group's would become that tile), Copy, Set Title, Set Description,
            // Delete. Others' have just Copy.
            if (set.kind != MarkerSet.Kind.OWN) {
                addAction(clickable, actions, "Copy", copy);
            } else {
                Map<String, Runnable> edit = new LinkedHashMap<>();
                if (!entry.group) {
                    edit.put("Label", () -> editTile(set, entry));
                }
                edit.put("Copy", copy);
                edit.put("Set Title", () -> askTitle(set, entry, entry.tiles, this::reopen));
                edit.put("Set Description", () -> askDescription(entry.description, text -> markerSets.setDescription(set, entry, text)));
                edit.put("Delete", () -> confirmDelete(set, entry));
                addSubmenu(clickable, actions, "Edit", edit);
                addSubmenu(clickable, actions, "Move", moves(set, entry, this::updateIfOpen));
            }
        }
        ChatboxWidgets.hover(label, color);
    }

    // A Move submenu's To top / Up / Down / To bottom (those that move it) for an own set's entry: its place in the set's
    // order, as listed and drawn (later entries win shared tiles); then runs then. Also the Marker Manager's set view.
    Map<String, Runnable> moves(MarkerSet set, MarkerSet.Entry entry, Runnable then) {
        int at = set.entries.indexOf(entry), last = set.entries.size() - 1;
        Map<String, Runnable> move = new LinkedHashMap<>();
        for (int to : new int[]{0, at - 1, at + 1, last}) {
            if (to >= 0 && to <= last && to != at) {
                move.putIfAbsent(to == 0 ? "To top" : to == last ? "To bottom" : to < at ? "Up" : "Down", () -> {
                    markerSets.moveEntry(set, entry, to);
                    then.run();
                });
            }
        }
        return move;
    }

    private void toggleExpanded(MarkerSet set) {
        if (!expanded.remove(set.key())) {
            expanded.add(set.key());
        }
        update();
    }

    private void copy(MarkerSet set) {
        clip(markerSets.export(set), "The marker set \"" + set.title + "\" was copied to your clipboard.");
    }

    // A GIST set's Subscribed, or Unsubscribed once its author deleted it; while browsing, Published (Private: only you see
    // it, signed in) until added.
    private String source(MarkerSet set) {
        return browsing != null ? (markerSets.existing(set) != null ? "Subscribed" : set.gist.secret ? "Private" : "Published")
            : set.kind == MarkerSet.Kind.GIST && set.gist.gone ? "Unsubscribed" : set.kind.title;
    }

    private void askBrowse() {
        askText("Whose Marker Sets? (their GitHub name)", "",
            name -> chatboxPanelManager.openInput(name.strip().matches("[\\w-]+") ? browse(Gists.login(name.strip())) : this));
    }

    // Yours and published, on the GitHub account you're signed in to (or signed out). Published on another account, it's
    // treated as unpublished: publishing it again moves it to this account, leaving the old copy (unpublish it from that
    // account's Author page).
    private boolean mine(MarkerSet set) {
        String login = gists.getLogin();
        return set.kind == MarkerSet.Kind.OWN && set.gist != null && (login == null || login.equalsIgnoreCase(set.gist.owner));
    }

    // mine, and not since deleted on GitHub (publishing it again makes a new one).
    private boolean published(MarkerSet set) {
        return mine(set) && !set.gist.gone;
    }

    // Published, asking first whether it goes on your public list (else it's shared by its link only); updated if it is.
    // Signs in to GitHub first if needed.
    private void publish(MarkerSet set) {
        if (gists.getLogin() == null) {
            signIn();
        } else if (published(set)) {
            upload(set, set.gist.secret);
        } else {
            new ConfirmChatbox(chatboxPanelManager, "Publish \"" + Text.escapeJagex(set.title) + "\" publicly via GitHub Gist<br>This will be public to anyone on the internet.")
                .option("Public", () -> upload(set, false))
                .option("Private (Link only)", () -> upload(set, true))
                .option("Cancel", this::reopen)
                .build();
        }
    }

    private void upload(MarkerSet set, boolean secret) {
        String text = markerSets.export(set);
        gists.run(() -> {
            MarkerSet.Gist gist = gists.save(published(set) ? set.gist : null, set.title, text, secret);
            clientThread.invoke(() -> {
                markerSets.setGist(set, gist.withSharedVersion(set.version).withShared(LocalDate.now().toString()));
                clip(gist.link(), "\"" + set.title + "\" was shared on GitHub, and its link copied to your clipboard.");
            });
        }, () -> sendChatMessage("Couldn't share \"" + set.title + "\" on GitHub."));
        reopen();
    }

    private void unpublish(MarkerSet set) {
        new ConfirmChatbox(chatboxPanelManager, "Unpublish \"" + Text.escapeJagex(set.title) + "\"?"
                + "<br>Subscribers keep the set, but no longer receive updates")
            .yes(() -> {
                unpublish(set, () -> {
                    if (browsed != null) {
                        browsed.remove(set);
                    }
                    updateIfOpen();
                });
                reopen();
            })
            .no(this::reopen)
            .build();
    }

    // Its gist deleted (subscribers keep their copies, no longer updated), then then runs; not if that fails. set may be
    // yours, or one on your Author page.
    private void unpublish(MarkerSet set, Runnable then) {
        gists.run(() -> {
            gists.delete(set.gist);
            clientThread.invoke(() -> {
                markerSets.unpublished(set.gist.id);
                then.run();
            });
        }, () -> sendChatMessage("Couldn't unpublish \"" + set.title + "\" from GitHub."));
    }

    // GitHub's device flow: the code to enter there, copied as it opens.
    private void signIn() {
        gists.signIn((page, code) -> clientThread.invoke(() -> new ConfirmChatbox(chatboxPanelManager,
                "To share marker sets, sign in with GitHub.<br>Use code " + code + " in the browser."
                    + "<br>Warning: Shared marker sets will be published under this GitHub account")
                .option("Copy the code and open GitHub", () -> {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code), null);
                    LinkBrowser.browse(page);
                })
                .option("Cancel", this::reopen)
                .build()),
            login -> sendChatMessage("Signed in to GitHub as " + login + "."), () -> sendChatMessage("Couldn't start signing in to GitHub."));
    }

    // A copied marker set, as the orb's Import; else copied ground markers, named and saved as the user's own set by Unknown,
    // same-label markers grouped (as Marker Manager's Set).
    private void importClipboard() {
        sharingManager.parseClipboard((set, text) -> sharingManager.confirmSetImport(set, text, this::reopen),
            markers -> sharingManager.importAsSet(markers, this::reopen));
    }

    // Just its tiles, as ground markers: a label with variables goes in "gmv", as written.
    private void exportTiles(MarkerSet set) {
        copyTiles(set.tiles(), "the marker set \"" + set.title + "\"");
    }

    // The clipboard's ground markers (read again, in case it changed) added to set, an entry per label as Marker Manager's Set.
    // Any at a tile already in it: asks whether to skip those, replace the set's, or cancel.
    private void paste(MarkerSet set) {
        List<GroundMarkerPointData> markers = sharingManager.clipboardMarkers();
        if (markers.isEmpty()) {
            sendChatMessage("You do not have ground markers copied in your clipboard.");
            return;
        }
        long duplicates = MarkerSets.duplicates(set, markers);
        if (duplicates == 0) {
            paste(set, markers, false, 0);
            return;
        }
        new ConfirmChatbox(chatboxPanelManager, duplicates + " of the " + markers.size() + " ground markers are on tiles already in the marker set \""
                + Text.escapeJagex(set.title) + "\".")
            .option("Skip", () -> paste(set, markers, false, duplicates))
            .option("Replace", () -> paste(set, markers, true, duplicates))
            .option("Cancel", this::reopen)
            .build();
    }

    // replace: the set's tiles where markers go are replaced; else duplicates of markers are skipped.
    private void paste(MarkerSet set, List<GroundMarkerPointData> markers, boolean replace, long duplicates) {
        long pasted = replace ? markers.size() : markers.size() - duplicates;
        sendChatMessage(markerSets.paste(set, markers, replace) == null ? MarkerSets.TOO_LARGE
            : pasted + (pasted == 1 ? " ground marker was" : " ground markers were") + " pasted into the marker set \"" + set.title + "\""
                + (duplicates == 0 ? "." : replace ? ", replacing " + duplicates + "." : ", skipping " + duplicates + "."));
        reopen();
    }

    // tiles as ground markers (see exportTiles), from (e.g. "the marker set "TFA"") for the chat message.
    private void copyTiles(List<GroundMarkerPointData> tiles, String from) {
        clip(markerMinifier.exportGmvPlain(tiles), tiles.size() + (tiles.size() == 1 ? " ground marker" : " ground markers") + " from " + from
            + (tiles.size() == 1 ? " was" : " were") + " copied to your clipboard.");
    }

    // Your own copy, still by its author until you claim it.
    private void cloneSet(MarkerSet set) {
        MarkerSet clone = markerSets.cloneSet(set);
        sendChatMessage(clone == null ? MarkerSets.TOO_LARGE
            : "The marker set \"" + clone.title + "\" was made from \"" + set.title + "\" by " + set.author + ".");
    }

    // A published one may be unpublished too, first (a failed unpublish keeps it).
    private void confirmDelete(MarkerSet set) {
        Runnable delete = () -> {
            markerSets.delete(Objects.requireNonNullElse(markerSets.existing(set), set));
            sendChatMessage("The marker set \"" + set.title + "\" was moved to the recycling bin, where it can be restored until you close the client.");
            updateIfOpen();
        };
        boolean published = published(set);
        new ConfirmChatbox(chatboxPanelManager, "Are you sure you want to delete the marker set \"" + Text.escapeJagex(set.title) + "\"?"
                + (published ? "<br>It's published. Subscribers keep the set either way." : ""))
            .option("Delete and unpublish", published ? () -> {
                unpublish(set, delete);
                reopen();
            } : null)
            .option(published ? "Delete, keep it published" : "Yes", () -> {
                delete.run();
                reopen();
            })
            .option(published ? "Cancel" : "No", this::reopen)
            .build();
    }

    // Out of the recycling bin; back to the marker sets once it's empty. A dissolved set first asks whether to keep the
    // saved markers dissolving made from its tiles.
    private void restore(MarkerSet set) {
        MarkerSets.Recycled recycled = markerSets.bin().get(set.key());
        if (recycled == null || !recycled.dissolved) {
            restore(set, false);
            return;
        }
        int tiles = set.tileCount();
        new ConfirmChatbox(chatboxPanelManager, "\"" + Text.escapeJagex(set.title) + "\" was dissolved into " + tiles
                + (tiles == 1 ? " ground marker" : " ground markers") + ". Should those markers be kept?")
            .option("Restore set, remove the markers", () -> restore(set, true))
            .option("Restore set, keep the markers", () -> restore(set, false))
            .option("Cancel", this::reopen)
            .build();
    }

    // removeMarkers: the saved markers at its tiles are removed again, fully undoing a dissolve.
    private void restore(MarkerSet set, boolean removeMarkers) {
        if (removeMarkers) {
            markerStorage.deletePoints(set.tiles());
            plugin.loadPoints();
        }
        markerSets.restore(set);
        sendChatMessage("The marker set \"" + set.title + "\" was restored" + (removeMarkers ? ", and its ground markers removed." : "."));
        reopen();
    }

    // Every tile back into saved markers (previewed, keeping or overwriting markers already there, as on import), then
    // the set goes to the recycling bin, where restoring it can undo this (see restore()).
    private void confirmDissolve(MarkerSet set) {
        sharingManager.confirmImport(set.tiles()," ground markers were restored from the marker set \"" + set.title + "\".", () -> {
            markerSets.delete(set, true);
            reopen();
        }, this::reopen);
    }

    private void editTile(MarkerSet set, MarkerSet.Entry entry) {
        editLabel(entry.tiles.get(0), label -> markerSets.setLabel(set, entry.tiles, label));
    }

    // description, if any, as a tooltip over area (see longTip).
    private void describe(Widget container, @Nullable String description, Rectangle area) {
        if (description != null) {
            tips.add(new SuggestionIconOverlay.Tip(container, area, longTip(description)));
        }
    }

    // A set's or entry's description, starting as current; blank for none (see MarkerSet#description for its limits).
    private void askDescription(@Nullable String current, Consumer<String> save) {
        askText("Description (blank for none)", current == null ? "" : current, text -> {
            save.accept(text);
            reopen();
        });
    }

    // Then runs then, e.g. reopening the chatbox it came from.
    void askTitle(MarkerSet set, Runnable then) {
        askText("Marker set title", set.title, title -> {
            if (!title.isBlank()) {
                markerSets.setTitle(set, title);
            }
            then.run();
        });
    }

    // Blank removes the title, so the entry shows its label again; another entry's merges it into that one. Just tiles of
    // it, if not all: those split off (see MarkerSets#setTitle).
    void askTitle(MarkerSet set, MarkerSet.Entry entry, List<GroundMarkerPointData> tiles, Runnable then) {
        askText("Title (blank for none)", entry.title == null ? "" : entry.title, title -> {
            if (!markerSets.setTitle(set, entry, tiles, title)) {
                String what = tiles.containsAll(entry.tiles) ? "\"" + entry.name() + "\" was"
                    : tiles.size() + (tiles.size() == 1 ? " tile" : " tiles") + " of \"" + entry.name() + (tiles.size() == 1 ? "\" was" : "\" were");
                sendChatMessage(what + " merged into \"" + title.trim() + "\" in the marker set \"" + set.title + "\".");
            }
            then.run();
        });
    }

    private void confirmDelete(MarkerSet set, MarkerSet.Entry entry) {
        int tiles = entry.tiles.size();
        new ConfirmChatbox(chatboxPanelManager, "Are you sure you want to delete \"" + Text.escapeJagex(entry.name()) + "\" ("
                + tiles + (tiles == 1 ? " tile" : " tiles") + ") from the marker set \"" + Text.escapeJagex(set.title) + "\"?")
            .yes(() -> {
                markerSets.deleteTiles(set, entry.tiles);
                sendChatMessage("\"" + entry.name() + "\" was deleted from the marker set \"" + set.title
                    + "\" and moved to its recycling bin (Manage > Show tiles), where it can be restored until you close the client.");
                reopen();
            })
            .no(this::reopen)
            .build();
    }
}
