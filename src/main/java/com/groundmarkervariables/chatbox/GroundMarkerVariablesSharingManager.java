package com.groundmarkervariables.chatbox;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.Runnables;
import com.google.gson.JsonParseException;
import com.groundmarkervariables.GroundMarkerVariablesPlugin;
import com.groundmarkervariables.markers.DeletedMarkers;
import com.groundmarkervariables.markers.Gists;
import com.groundmarkervariables.markers.GroundMarkerPointData;
import com.groundmarkervariables.markers.ImportPreviewManager;
import com.groundmarkervariables.markers.MarkerStorage;
import com.groundmarkervariables.markers.MarkerSet;
import com.groundmarkervariables.markers.MarkerSets;
import com.groundmarkervariables.markers.minify.MarkerMinifier;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.menus.WidgetMenuOption;
import net.runelite.client.util.Text;

// Mirrors core's GroundMarkerSharingManager — clipboard export/import and a "Clear" confirmation
// for every currently loaded ground marker, via the world map orb's right-click menu.
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class GroundMarkerVariablesSharingManager {
    // The website's page turning older versions' compressed copies back into text.
    private static final String CONVERT_PAGE = "https://limy-monkey.github.io/Ground-Marker-Variables/convert/";
    private static final WidgetMenuOption EXPORT_MARKERS_OPTION = new WidgetMenuOption("Export", "Ground Markers", InterfaceID.Orbs.WORLDMAP, InterfaceID.OrbsNomap.WORLDMAP);
    private static final WidgetMenuOption IMPORT_MARKERS_OPTION = new WidgetMenuOption("Import", "Ground Markers", InterfaceID.Orbs.WORLDMAP, InterfaceID.OrbsNomap.WORLDMAP);
    private static final WidgetMenuOption CLEAR_MARKERS_OPTION = new WidgetMenuOption("Clear", "Ground Markers", InterfaceID.Orbs.WORLDMAP, InterfaceID.OrbsNomap.WORLDMAP);

    private final GroundMarkerVariablesPlugin plugin;
    private final MarkerStorage markerStorage;
    private final Client client;
    private final ImportPreviewManager importPreviewManager;
    private final MenuManager menuManager;
    private final ChatboxPanelManager chatboxPanelManager;
    private final DeletedMarkers deletedMarkers;
    private final MarkerMinifier markerMinifier;
    private final MarkerSets markerSets;
    private final ClientThread clientThread;
    private final Gists gists;
    private final Provider<MarkerSetManager> markerSetManagerProvider;

    public void addImportExportMenuOptions() {
        menuManager.addManagedCustomMenu(EXPORT_MARKERS_OPTION, this::exportGroundMarkers);
        menuManager.addManagedCustomMenu(IMPORT_MARKERS_OPTION, menuEntry -> importClipboard(Runnables.doNothing()));
    }

    public void addClearMenuOption() {
        menuManager.addManagedCustomMenu(CLEAR_MARKERS_OPTION, this::promptForClear);
    }

    public void removeMenuOptions() {
        menuManager.removeManagedCustomMenu(EXPORT_MARKERS_OPTION);
        menuManager.removeManagedCustomMenu(IMPORT_MARKERS_OPTION);
        menuManager.removeManagedCustomMenu(CLEAR_MARKERS_OPTION);
    }

    public List<GroundMarkerPointData> activePoints() {
        int[] regions = client.getMapRegions();
        if (regions == null) {
            return List.of();
        }

        return Arrays.stream(regions).mapToObj(markerStorage::getStoredPoints).flatMap(Collection::stream).collect(Collectors.toList());
    }

    private void exportGroundMarkers(MenuEntry menuEntry) {
        List<GroundMarkerPointData> activePoints = activePoints();
        if (activePoints.isEmpty()) {
            sendChatMessage("You have no ground markers to export.");
            return;
        }

        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(markerMinifier.exportGmvPlain(activePoints)), null);
        sendChatMessage(activePoints.size() + " ground markers were copied to your clipboard.");
    }

    // The clipboard's marker set (added as an Imported set, not as markers) or ground markers, each asked about first; then
    // after, imported or not. Also the Advanced Label Editor's Ctrl+V.
    public void importClipboard(Runnable after) {
        parseClipboard((set, text) -> confirmSetImport(set, text, after), points ->
            confirmImport(points, " ground markers were imported from the clipboard.", after, after, true));
    }

    // The clipboard as a copied marker set (onSet, with its text), else as ground markers (onMarkers); if neither, a chat
    // message says why. Also Marker Sets' Import.
    public void parseClipboard(BiConsumer<MarkerSet, String> onSet, Consumer<List<GroundMarkerPointData>> onMarkers) {
        String text;
        try {
            text = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
        } catch (IOException | UnsupportedFlavorException e) {
            log.warn("error reading clipboard", e);
            sendChatMessage("Unable to read system clipboard.");
            return;
        }

        if (Strings.isNullOrEmpty(text)) {
            text = "[]";
        }
        // Just a gist link: its set, from GitHub.
        String gistId = Gists.linkId(text);
        if (gistId != null) {
            gists.run(() -> {
                Map<MarkerSet, String> linked = markerSets.download(gists.list("gists/" + gistId));
                clientThread.invoke(() -> {
                    if (linked.isEmpty()) {
                        sendChatMessage("That link doesn't have a marker set.");
                    }
                    linked.forEach(onSet);
                });
            }, () -> sendChatMessage("Couldn't get that marker set from GitHub."));
            return;
        }
        MarkerSet set = null;
        try {
            set = markerSets.parse(text);
        } catch (JsonParseException e) {
            // Not a set: maybe markers.
        }
        List<GroundMarkerPointData> markers = List.of();
        try {
            markers = set != null ? List.of() : markerMinifier.parse(text);
        } catch (JsonParseException e) {
            log.debug("Malformed JSON for clipboard import", e);
        } catch (IllegalArgumentException e) {
            sendChatMessage("These ground markers were copied by an older version of Ground Marker Variables. Convert them at "
                + CONVERT_PAGE + ", then import them.");
            return;
        }

        if (set != null) {
            onSet.accept(set, text);
        } else if (markers.isEmpty()) {
            sendChatMessage("You do not have a marker set or ground markers copied in your clipboard.");
        } else {
            onMarkers.accept(markers);
        }
    }

    // The ground markers copied in the clipboard, or a copied marker set's tiles, quietly: empty if none, unreadable or damaged.
    public List<GroundMarkerPointData> clipboardMarkers() {
        try {
            String text = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            MarkerSet set = Strings.isNullOrEmpty(text) ? null : markerSets.parse(text);
            return set != null ? set.tiles()
                : Strings.isNullOrEmpty(text) ? List.of() : markerMinifier.parse(text);
        } catch (Exception e) {
            return List.of();
        }
    }

    // Previews the set's tiles and asks before adding it; one with the same title and author is replaced. Then after, either
    // way (also Marker Sets' Import).
    public void confirmSetImport(MarkerSet set, String text, Runnable after) {
        List<GroundMarkerPointData> tiles = set.tiles();
        importPreviewManager.show(tiles);
        boolean replaces = markerSets.existing(set) != null;
        new ConfirmChatbox(chatboxPanelManager, "Import the marker set \"" + Text.escapeJagex(set.title) + "\" by "
                + Text.escapeJagex(set.author) + " (" + tiles.size() + " tiles)?" + (replaces ? "<br>This replaces your copy of it." : ""))
            .yes(() -> {
                boolean added = markerSets.addShared(set, text);
                sendChatMessage(!added ? MarkerSets.TOO_LARGE : "The marker set \"" + set.title + "\" was " + (replaces ? "updated." : "imported."));
                if (!added || set.gist == null) {
                    after.run();
                    return;
                }
                // From a gist: its author's others.
                new ConfirmChatbox(chatboxPanelManager, "Browse " + Text.escapeJagex(set.author) + "'s other published Marker Sets?")
                    .yes(() -> chatboxPanelManager.openInput(markerSetManagerProvider.get().browse(set.gist.owner)))
                    .no(after)
                    .build();
            })
            .no(after)
            .onClose(importPreviewManager::clear)
            .build();
    }

    // Previews importPoints and asks before importing them -- also MarkerManager's Undo.
    public void confirmImport(List<GroundMarkerPointData> importPoints, String importedMessage, Runnable onImported, Runnable onCancelled) {
        confirmImport(importPoints, importedMessage, onImported, onCancelled, false);
    }

    // asSet: also "Yes, as a marker set" (the clipboard import's).
    private void confirmImport(List<GroundMarkerPointData> importPoints, String importedMessage, Runnable onImported, Runnable onCancelled,
        boolean asSet) {
        List<GroundMarkerPointData> nonOverlapping = nonOverlapping(importPoints);
        importPreviewManager.show(nonOverlapping);
        String count = countWithOverlap(importPoints.size(), nonOverlapping.size());
        new ConfirmChatbox(chatboxPanelManager, "Are you sure you want to import " + count + " ground markers?")
            .yes(() -> {
                importGroundMarkers(importPoints);
                sendChatMessage(nonOverlapping.size() + importedMessage);
                onImported.run();
            })
            .option("Yes, and overwrite", nonOverlapping.size() == importPoints.size() ? null : () -> {
                overwriteGroundMarkers(importPoints);
                sendChatMessage(importPoints.size() + importedMessage);
                onImported.run();
            })
            .option("Yes, as a marker set", asSet ? () -> importAsSet(importPoints, () -> {
                chatboxPanelManager.close();
                onImported.run();
            }) : null)
            .no(onCancelled)
            .onClose(importPreviewManager::clear)
            .build();
    }

    // points as a new set by an unknown author (also Marker Sets' Import), asking for the title, which starts as (and blank
    // is) MarkerSets#defaultTitle's; then replaces the prompt (onDone returns false, as closing would close what it opens).
    public void importAsSet(List<GroundMarkerPointData> points, Runnable then) {
        chatboxPanelManager.openTextInput("Marker set title")
            .value(MarkerSets.defaultTitle(points))
            .onDone((Predicate<String>) title -> {
                clientThread.invoke(() -> {
                    MarkerSet set = markerSets.createFrom(title, MarkerSet.UNKNOWN, points);
                    sendChatMessage(set == null ? MarkerSets.TOO_LARGE
                        : points.size() + " ground markers were imported as the marker set \"" + set.title + "\".");
                    then.run();
                });
                return false;
            })
            .build();
    }

    // "X" or "X (-Y existing)" -- see nonOverlapping().
    public static String countWithOverlap(int total, int nonOverlappingCount) {
        int overlapping = total - nonOverlappingCount;
        return overlapping > 0 ? total + " (-" + overlapping + " existing)" : String.valueOf(total);
    }

    // Candidates not already at an existing marker's location, per importGroundMarkers' own dedup.
    public List<GroundMarkerPointData> nonOverlapping(Collection<GroundMarkerPointData> candidates) {
        Set<WorldPoint> taken = new HashSet<>();
        candidates.stream().map(GroundMarkerPointData::getRegionId).distinct()
            .forEach(regionId -> markerStorage.getStoredPoints(regionId).forEach(point -> taken.add(point.worldPoint())));
        return candidates.stream().filter(point -> taken.add(point.worldPoint())).collect(Collectors.toList());
    }

    public void importGroundMarkers(Collection<GroundMarkerPointData> importPoints) {
        markerStorage.addPoints(importPoints);
        plugin.loadPoints();
    }

    // Like importGroundMarkers, but replacing any existing marker at an imported one's location (into the recycling bin).
    private void overwriteGroundMarkers(Collection<GroundMarkerPointData> importPoints) {
        deletedMarkers.addAll(importPoints.stream().map(point -> markerStorage.findStoredPoint(point.worldPoint()))
            .filter(existing -> existing != null).collect(Collectors.toList()));
        markerStorage.replacePoints(importPoints);
        plugin.loadPoints();
    }

    private void promptForClear(MenuEntry entry) {
        List<GroundMarkerPointData> activePoints = activePoints();
        if (activePoints.isEmpty()) {
            sendChatMessage("You have no ground markers to clear.");
            return;
        }

        int numActivePoints = activePoints.size();
        new ConfirmChatbox(chatboxPanelManager, "Are you sure you want to clear the " + numActivePoints + " currently loaded ground markers?")
            .yes(() -> {
                deletedMarkers.addAll(activePoints);
                markerStorage.deletePoints(activePoints);

                plugin.loadPoints();
                sendChatMessage(numActivePoints + " ground marker"
                    + (numActivePoints == 1 ? " was cleared." : "s were cleared."));
            })
            .build();
    }

    private void sendChatMessage(String message) {
        plugin.sendChatMessage(message);
    }
}
