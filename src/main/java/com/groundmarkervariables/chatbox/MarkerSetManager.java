package com.groundmarkervariables.chatbox;

import com.groundmarkervariables.chatbox.completion.Completion;
import com.groundmarkervariables.markers.GroundMarkerPointData;
import com.groundmarkervariables.markers.MarkerSet;
import com.groundmarkervariables.markers.MarkerSets;
import com.groundmarkervariables.markers.MarkerStorage;
import com.groundmarkervariables.markers.minify.MarkerMinifier;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.FontTypeFace;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

// Built-in, imported and own marker sets, opened from the chatboxes' logo. A set's checkbox switches it on and off; clicking its name shows its
// entries, each with its own checkbox. Right-click a set to copy or delete it.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class MarkerSetManager extends SearchableChatbox {
    // Left of Import while the last check failed.
    private static final String SATELLITE_ANTENNA = "1f4e1";
    private static final int ICON_SIZE = 14;
    private static final String[] HEADINGS = {"Title", "Tiles", "Source", "Author", "Updated"};
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
    // The widest value each column after Title (which takes the rest) is sized for: Updated, any month's date.
    private static final List<List<String>> SIZED_FOR = List.of(List.of(), List.of("999/999"), List.of("Built-in", "Imported", "Own", "Plugin"),
        List.of("LimyMonkey12"), IntStream.rangeClosed(1, 12).mapToObj(month -> DATE.format(LocalDate.of(2026, month, 28)))
            .collect(Collectors.toList()));
    // The row's entry for the status line (a check, a failure, or why the table is empty).
    private static final MarkerSet.Entry STATUS = new MarkerSet.Entry(null, null, List.of(), false, true, 0);

    private final MarkerSets markerSets;
    private final MarkerStorage markerStorage;
    private final GroundMarkerVariablesSharingManager sharingManager;
    private final MarkerMinifier markerMinifier;
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

    // Lists only own sets, each clicked to pick it, e.g. to add markers to.
    public MarkerSetManager pick(Consumer<MarkerSet> onPick) {
        this.onPick = onPick;
        return this;
    }

    @Override
    protected String title() {
        return onPick != null ? "Add to which set?" : recycleBin ? "Sets" : "Marker Sets (" + markerSets.listed().size() + ")";
    }

    // None while picking a set.
    @Override
    protected int recycled() {
        return onPick != null ? 0 : markerSets.bin().size();
    }

    // The recycling bin's :recycle: while it has sets (or is open), Import, and the failed update's mark left of it.
    @Override
    protected void buildTopBar(Widget container, FontTypeFace font, int rightX) {
        rightX = recycleBinButton(container, rightX, this::update);
        // Right-click: check GitHub for built-in set updates now, not waiting for the next check.
        Widget importButton = button(container, font, "Import", "Import from clipboard", "", true, rightX, this::importClipboard);
        addAction(importButton, new ArrayList<>(List.of(this::importClipboard)), "Check for updates", () -> markerSets.checkForUpdates(true));
        rightX = nextX(importButton);
        if (markerSets.failed()) {
            Rectangle area = new Rectangle(container.getWidth() - rightX - ICON_SIZE + 4, 5 + (LINE_HEIGHT - ICON_SIZE) / 2, ICON_SIZE,
                ICON_SIZE);
            iconPlacements.add(new SuggestionIconOverlay.Placement(container, area, Completion.Icon.emoji(SATELLITE_ANTENNA)));
            tips.add(new SuggestionIconOverlay.Tip(container, area, "Couldn't update built-in sets"));
        }
    }

    // Each set in the chosen order, with its entries when expanded or searched for.
    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        List<MarkerSet> sets = recycleBin ? markerSets.bin().values().stream().map(recycled -> recycled.set).collect(Collectors.toList())
            : markerSets.listed();
        sets.sort(order());
        for (MarkerSet set : sets) {
            if (onPick != null && set.kind != MarkerSet.Kind.OWN) {
                continue;
            }
            boolean setMatches = matches(set.title) || matches(set.author) || matches(set.kind.title);
            List<MarkerSet.Entry> entries = new ArrayList<>();
            for (MarkerSet.Entry entry : set.entries) {
                // Its words may be split between the set's title and the entry's, e.g. "tob lobby".
                if (setMatches || matches(set.title + "\n" + entry.name())) {
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

    private boolean matches(String text) {
        return ChatboxWidgets.hasWords(text, query);
    }

    @Override
    protected void buildRows(Widget container) {
        clipboardHasMarkers = !recycleBin && !sharingManager.clipboardMarkers().isEmpty();
        List<Row> rows = rows();
        String status = markerSets.status();
        boolean table = !rows.isEmpty();
        if (!table) {
            status = !query.isEmpty() ? "No marker sets match \"" + query + "\"."
                : status != null ? status : "No marker sets yet.";
        }
        if (status != null) {
            rows.add(new Row(null, STATUS));
        }
        columns(container, HEADINGS, SIZED_FOR, 0);
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

    // By sortColumn, reversed if descending (unknown dates first going up), then title.
    private Comparator<MarkerSet> order() {
        Comparator<MarkerSet> byTitle = Comparator.comparing(set -> set.title, String.CASE_INSENSITIVE_ORDER);
        Comparator<MarkerSet> byColumn;
        switch (sortColumn) {
            case 1:
                byColumn = Comparator.comparingInt(MarkerSet::tileCount);
                break;
            case 2:
                byColumn = Comparator.comparing(set -> set.kind);
                break;
            case 3:
                byColumn = Comparator.comparing(set -> set.author, String.CASE_INSENSITIVE_ORDER);
                break;
            case 4:
                byColumn = Comparator.comparing(set -> set.updated, Comparator.nullsFirst(Comparator.naturalOrder()));
                break;
            default:
                byColumn = byTitle;
        }
        return (descending ? byColumn.reversed() : byColumn).thenComparing(byTitle);
    }

    // Tiles in the set's switched-on entries, whether or not the set itself is on.
    private int enabledTiles(MarkerSet set) {
        return set.entries.stream().filter(entry -> markerSets.enabled(set, entry)).mapToInt(entry -> entry.tiles.size()).sum();
    }

    // Title, tiles, source, author and updated; the title cell is the one clicked to expand, copy or delete. In the
    // recycling bin, the crossed check box restores it, and the title cell restores, expands or deletes it for good.
    private void addSet(Widget container, MarkerSet set, int y) {
        boolean enabled = markerSets.enabled(set);
        Widget checkbox = checkbox(container, enabled, 6, y);
        addAction(checkbox, new ArrayList<>(), recycleBin ? "Restore" : enabled ? "Disable" : "Enable",
            recycleBin ? () -> restore(set) : () -> markerSets.setEnabled(set, !enabled));

        String[] cells = {set.title, enabledTiles(set) + "/" + set.tileCount(), set.kind.title, set.author,
            set.updated != null ? DATE.format(set.updated) : set.kind == MarkerSet.Kind.PLUGIN ? "" : "Unknown"};
        Widget label = null;
        for (int column = 0; column < cells.length; column++) {
            Widget cell = cell(container, cells[column], column == 0 ? 0x000000 : DETAIL_COLOR, column, y);
            label = label == null ? cell : label;
        }
        describe(container, set.description, cellArea(0, y));
        List<Runnable> actions = new ArrayList<>();
        if (onPick != null) {
            addAction(label, actions, "Choose", () -> onPick.accept(set));
        }
        addAction(label, actions, expanded.contains(set.key()) ? "Compact set" : "Expand set", () -> toggleExpanded(set));
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
        addShowOnMap(label, actions, set, tiles(set), cellArea(0, y));
        Map<String, Runnable> manage = new LinkedHashMap<>();
        manage.put("Copy", () -> copy(set));
        manage.put("Export", () -> exportTiles(set));
        manage.put("Clone", () -> cloneSet(set));
        if (onPick == null) {
            manage.put("Show tiles", () -> markerManagerProvider.get().show(set, ""));
        }
        addSubmenu(label, actions, "Manage", manage);
        if (set.kind == MarkerSet.Kind.OWN) {
            // Yours can be made anon; anyone else's (a clone, anon, Unknown) claimed by the logged-in name.
            Player player = client.getLocalPlayer();
            String name = player != null ? player.getName() : null;
            Map<String, Runnable> edit = new LinkedHashMap<>();
            edit.put("Set title", () -> askTitle(set, this::reopen));
            edit.put("Set description", () -> askDescription(set.description, text -> markerSets.setDescription(set, text)));
            if (clipboardHasMarkers) {
                edit.put("Paste", () -> paste(set));
            }
            if (name != null && name.equalsIgnoreCase(set.author)) {
                edit.put("Anonymize", () -> markerSets.setAuthor(set, MarkerSet.ANON));
            } else if (name != null) {
                edit.put("Claim Authorship", () -> markerSets.claim(set, name));
            }
            addSubmenu(label, actions, "Edit", edit);
        }
        // Last, Dissolve then Delete. A plugin's group is its to clear.
        if (set.kind == MarkerSet.Kind.SHARED || set.kind == MarkerSet.Kind.OWN) {
            addAction(label, actions, "Dissolve set", () -> confirmDissolve(set));
            addAction(label, actions, "Delete", () -> confirmDelete(set));
        }
        ChatboxWidgets.hover(label, 0x000000);
    }

    // Map (see WorldMapPins#mapChoices): all set's tiles pinned (each named by its entry) or on RuneLite.net, the open
    // world map moved to tiles.
    private void addShowOnMap(Widget widget, List<Runnable> actions, MarkerSet set, List<GroundMarkerPointData> tiles, Rectangle labelArea) {
        addSubmenu(widget, actions, "Map", mapPins.mapChoices(tiles, titledTiles(set), () -> mapPins.pin(set), text -> notice(labelArea, text),
            this::updateIfOpen));
    }

    private static List<GroundMarkerPointData> tiles(MarkerSet set) {
        List<GroundMarkerPointData> tiles = new ArrayList<>();
        set.entries.forEach(entry -> tiles.addAll(entry.tiles));
        return tiles;
    }

    // tiles(set), each labeled by its entry's title if it has one, for RuneLite.net.
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
        // A recycled set's entries are only listed.
        for (Widget clickable : recycleBin ? List.<Widget>of() : List.of(checkbox, label)) {
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
            }
        }
        ChatboxWidgets.hover(label, color);
    }

    private void toggleExpanded(MarkerSet set) {
        if (!expanded.remove(set.key())) {
            expanded.add(set.key());
        }
        update();
    }

    private void copy(MarkerSet set) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(markerSets.export(set)), null);
        sendChatMessage("The marker set \"" + set.title + "\" was copied to your clipboard.");
    }

    // A copied marker set, as the orb's Import; else copied ground markers, named and saved as the user's own set by Unknown,
    // same-label markers grouped (as Marker Manager's Set).
    private void importClipboard() {
        sharingManager.parseClipboard((set, text) -> sharingManager.confirmSetImport(set, text, this::reopen),
            markers -> sharingManager.importAsSet(markers, this::reopen));
    }

    // Just its tiles, as ground markers: a label with variables goes in "gmv", as written.
    private void exportTiles(MarkerSet set) {
        copyTiles(tiles(set), "the marker set \"" + set.title + "\"");
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
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(markerMinifier.exportGmvPlain(tiles)), null);
        sendChatMessage(tiles.size() + (tiles.size() == 1 ? " ground marker" : " ground markers") + " from " + from
            + (tiles.size() == 1 ? " was" : " were") + " copied to your clipboard.");
    }

    // Your own copy, still by its author until you claim it.
    private void cloneSet(MarkerSet set) {
        MarkerSet clone = markerSets.cloneSet(set);
        sendChatMessage(clone == null ? MarkerSets.TOO_LARGE
            : "The marker set \"" + clone.title + "\" was made from \"" + set.title + "\" by " + set.author + ".");
    }

    private void confirmDelete(MarkerSet set) {
        new ConfirmChatbox(chatboxPanelManager, "Are you sure you want to delete the marker set \"" + Text.escapeJagex(set.title) + "\"?")
            .yes(() -> {
                markerSets.delete(set);
                sendChatMessage("The marker set \"" + set.title + "\" was moved to the recycling bin, where it can be restored until you close the client.");
                reopen();
            })
            .no(this::reopen)
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
            markerStorage.deletePoints(tiles(set));
            plugin.loadPoints();
        }
        markerSets.restore(set);
        sendChatMessage("The marker set \"" + set.title + "\" was restored" + (removeMarkers ? ", and its ground markers removed." : "."));
        reopen();
    }

    // Every tile back into saved markers (previewed, keeping or overwriting markers already there, as on import), then
    // the set goes to the recycling bin, where restoring it can undo this (see restore()).
    private void confirmDissolve(MarkerSet set) {
        sharingManager.confirmImport(tiles(set)," ground markers were restored from the marker set \"" + set.title + "\".", () -> {
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
        chatboxPanelManager.openTextInput("Description (blank for none)")
            .value(current == null ? "" : current)
            .onDone((Predicate<String>) text -> {
                clientThread.invoke(() -> {
                    save.accept(text);
                    reopen();
                });
                return false;
            })
            .build();
    }

    // Then runs then, e.g. reopening the chatbox it came from.
    void askTitle(MarkerSet set, Runnable then) {
        chatboxPanelManager.openTextInput("Marker set title")
            .value(set.title)
            .onDone((Predicate<String>) title -> {
                clientThread.invoke(() -> {
                    if (!title.isBlank()) {
                        markerSets.setTitle(set, title);
                    }
                    then.run();
                });
                return false;
            })
            .build();
    }

    // Blank removes the title, so the entry shows its label again; another entry's merges it into that one. onDone returns false: reopening this replaces the
    // prompt, which closing would close instead.
    // Just tiles of it, if not all: those split off (see MarkerSets#setTitle).
    void askTitle(MarkerSet set, MarkerSet.Entry entry, List<GroundMarkerPointData> tiles, Runnable then) {
        chatboxPanelManager.openTextInput("Title (blank for none)")
            .value(entry.title == null ? "" : entry.title)
            .onDone((Predicate<String>) title -> {
                clientThread.invoke(() -> {
                    if (!markerSets.setTitle(set, entry, tiles, title)) {
                        String what = tiles.containsAll(entry.tiles) ? "\"" + entry.name() + "\" was"
                            : tiles.size() + (tiles.size() == 1 ? " tile" : " tiles") + " of \"" + entry.name() + (tiles.size() == 1 ? "\" was" : "\" were");
                        sendChatMessage(what + " merged into \"" + title.trim() + "\" in the marker set \"" + set.title + "\".");
                    }
                    then.run();
                });
                return false;
            })
            .build();
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
