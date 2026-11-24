package com.groundmarkervariables.chatbox;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.Runnables;
import com.google.gson.JsonParseException;
import com.groundmarkervariables.GroundMarkerVariablesPlugin;
import com.groundmarkervariables.markers.DeletedMarkers;
import com.groundmarkervariables.markers.GroundMarkerPointData;
import com.groundmarkervariables.markers.ImportPreviewManager;
import com.groundmarkervariables.markers.MarkerStorage;
import com.groundmarkervariables.markers.MarkerSet;
import com.groundmarkervariables.markers.MarkerSets;
import com.groundmarkervariables.markers.minify.MarkerMinifier;
import com.groundmarkervariables.markers.minify.NewerFormatException;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.menus.WidgetMenuOption;
import net.runelite.client.util.Text;

// Mirrors core's GroundMarkerSharingManager — clipboard export/import and a "Clear" confirmation
// for every currently loaded ground marker, via the world map orb's right-click menu.
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class GroundMarkerVariablesSharingManager
{
	private static final WidgetMenuOption EXPORT_MARKERS_OPTION = new WidgetMenuOption("Export", "Ground Markers", InterfaceID.Orbs.WORLDMAP, InterfaceID.OrbsNomap.WORLDMAP);
	private static final WidgetMenuOption IMPORT_MARKERS_OPTION = new WidgetMenuOption("Import", "Ground Markers", InterfaceID.Orbs.WORLDMAP, InterfaceID.OrbsNomap.WORLDMAP);
	private static final WidgetMenuOption CLEAR_MARKERS_OPTION = new WidgetMenuOption("Clear", "Ground Markers", InterfaceID.Orbs.WORLDMAP, InterfaceID.OrbsNomap.WORLDMAP);

	private final GroundMarkerVariablesPlugin plugin;
	private final MarkerStorage markerStorage;
	private final Client client;
	private final ImportPreviewManager importPreviewManager;
	private final MenuManager menuManager;
	private final ChatMessageManager chatMessageManager;
	private final ChatboxPanelManager chatboxPanelManager;
	private final DeletedMarkers deletedMarkers;
	private final MarkerMinifier markerMinifier;
	private final MarkerSets markerSets;
	private final ClientThread clientThread;

	public void addImportExportMenuOptions()
	{
		menuManager.addManagedCustomMenu(EXPORT_MARKERS_OPTION, this::exportGroundMarkers);
		menuManager.addManagedCustomMenu(IMPORT_MARKERS_OPTION, this::promptForImport);
	}

	public void addClearMenuOption()
	{
		menuManager.addManagedCustomMenu(CLEAR_MARKERS_OPTION, this::promptForClear);
	}

	public void removeMenuOptions()
	{
		menuManager.removeManagedCustomMenu(EXPORT_MARKERS_OPTION);
		menuManager.removeManagedCustomMenu(IMPORT_MARKERS_OPTION);
		menuManager.removeManagedCustomMenu(CLEAR_MARKERS_OPTION);
	}

	// Also used by GroundMarkerPartySharingManager's "Share to Party".
	public List<GroundMarkerPointData> activePoints()
	{
		int[] regions = client.getMapRegions();
		if (regions == null)
		{
			return List.of();
		}

		return Arrays.stream(regions)
			.mapToObj(regionId -> markerStorage.getStoredPoints(regionId).stream())
			.flatMap(Function.identity())
			.collect(Collectors.toList());
	}

	// Also MarkerManager's "Copy"; plain ("Copy as plaintext") leaves labels uncompressed.
	public String exportText(List<GroundMarkerPointData> points, boolean plain)
	{
		return plain ? markerMinifier.exportPlain(points) : markerMinifier.export(points);
	}

	private void exportGroundMarkers(MenuEntry menuEntry)
	{
		List<GroundMarkerPointData> activePoints = activePoints();
		if (activePoints.isEmpty())
		{
			sendChatMessage("You have no ground markers to export.");
			return;
		}

		final String exportDump = markerMinifier.export(activePoints);

		log.debug("Exported ground markers: {}", exportDump);

		Toolkit.getDefaultToolkit()
			.getSystemClipboard()
			.setContents(new StringSelection(exportDump), null);
		sendChatMessage(activePoints.size() + " ground markers were copied to your clipboard.");
	}

	// A marker set is added as an Imported set, not as markers.
	private void promptForImport(MenuEntry menuEntry)
	{
		parseClipboard((set, text) -> confirmSetImport(set, text, Runnables.doNothing()), points ->
			confirmImport(points, " ground markers were imported from the clipboard.", Runnables.doNothing(), Runnables.doNothing(), true));
	}

	// The clipboard as a copied marker set (onSet, with its text), else as ground markers (onMarkers); if neither, a chat
	// message says why. Also Marker Sets' Import.
	public void parseClipboard(BiConsumer<MarkerSet, String> onSet, Consumer<List<GroundMarkerPointData>> onMarkers)
	{
		String text;
		try
		{
			text = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
		}
		catch (IOException | UnsupportedFlavorException e)
		{
			log.warn("error reading clipboard", e);
			sendChatMessage("Unable to read system clipboard.");
			return;
		}

		if (Strings.isNullOrEmpty(text))
		{
			text = "[]";
		}
		MarkerSet set = null;
		try
		{
			set = markerSets.parse(text);
		}
		catch (JsonParseException e)
		{
			// Not a set: maybe markers.
		}
		List<GroundMarkerPointData> markers = List.of();
		try
		{
			markers = set != null ? List.of() : markerMinifier.parse(text);
		}
		catch (JsonParseException e)
		{
			log.debug("Malformed JSON for clipboard import", e);
		}
		catch (NewerFormatException e)
		{
			sendChatMessage("These ground markers were exported by a newer version of Ground Marker Variables. Update the plugin to import them.");
			return;
		}
		catch (IllegalArgumentException e)
		{
			log.debug("Damaged gmv label in clipboard import", e);
			sendChatMessage("The ground markers with variables in your clipboard are damaged and can't be imported.");
			return;
		}

		if (set != null)
		{
			onSet.accept(set, text);
		}
		else if (markers.isEmpty())
		{
			sendChatMessage("You do not have a marker set or ground markers copied in your clipboard.");
		}
		else
		{
			onMarkers.accept(markers);
		}
	}

	// Previews the set's tiles and asks before adding it; one with the same title and author is replaced. Then after, either
	// way (also Marker Sets' Import).
	public void confirmSetImport(MarkerSet set, String text, Runnable after)
	{
		List<GroundMarkerPointData> tiles = new ArrayList<>();
		set.entries.forEach(entry -> tiles.addAll(entry.tiles));
		importPreviewManager.show(tiles);
		boolean replaces = markerSets.existing(set) != null;
		new ConfirmChatbox(chatboxPanelManager, "Import the marker set \"" + Text.escapeJagex(set.title) + "\" by "
				+ Text.escapeJagex(set.author) + " (" + tiles.size() + " tiles)?" + (replaces ? "<br>This replaces your copy of it." : ""))
			.yes(() ->
			{
				sendChatMessage(!markerSets.addShared(set, text) ? MarkerSets.TOO_LARGE
					: "The marker set \"" + set.title + "\" was " + (replaces ? "updated." : "imported."));
				after.run();
			})
			.no(after)
			.onClose(importPreviewManager::clear)
			.build();
	}

	// Previews importPoints and asks before importing them -- also MarkerManager's Undo.
	public void confirmImport(List<GroundMarkerPointData> importPoints, String importedMessage, Runnable onImported, Runnable onCancelled)
	{
		confirmImport(importPoints, importedMessage, onImported, onCancelled, false);
	}

	// asSet: also "Yes, as a marker set" (the clipboard import's).
	private void confirmImport(List<GroundMarkerPointData> importPoints, String importedMessage, Runnable onImported, Runnable onCancelled,
		boolean asSet)
	{
		List<GroundMarkerPointData> nonOverlapping = nonOverlapping(importPoints);
		importPreviewManager.show(nonOverlapping);
		String count = countWithOverlap(importPoints.size(), nonOverlapping.size());
		new ConfirmChatbox(chatboxPanelManager, "Are you sure you want to import " + count + " ground markers?")
			.yes(() ->
			{
				importGroundMarkers(importPoints);
				sendChatMessage(nonOverlapping.size() + importedMessage);
				onImported.run();
			})
			.option("Yes, and overwrite", nonOverlapping.size() == importPoints.size() ? null : () ->
			{
				overwriteGroundMarkers(importPoints);
				sendChatMessage(importPoints.size() + importedMessage);
				onImported.run();
			})
			.option("Yes, as a marker set", asSet ? () -> importAsSet(importPoints, chatboxPanelManager::close) : null)
			.no(onCancelled)
			.onClose(importPreviewManager::clear)
			.build();
	}

	// points as a new set by an unknown author (also Marker Sets' Import), asking for the title, which starts as (and blank
	// is) MarkerSets#defaultTitle's; then replaces the prompt (onDone returns false, as closing would close what it opens).
	public void importAsSet(List<GroundMarkerPointData> points, Runnable then)
	{
		chatboxPanelManager.openTextInput("Marker set title")
			.value(MarkerSets.defaultTitle(points))
			.onDone((Predicate<String>) title ->
			{
				clientThread.invoke(() ->
				{
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
	public static String countWithOverlap(int total, int nonOverlappingCount)
	{
		int overlapping = total - nonOverlappingCount;
		return overlapping > 0 ? total + " (-" + overlapping + " existing)" : String.valueOf(total);
	}

	// Candidates not already at an existing marker's location, per importGroundMarkers' own dedup.
	public List<GroundMarkerPointData> nonOverlapping(Collection<GroundMarkerPointData> candidates)
	{
		Map<Integer, List<GroundMarkerPointData>> regionGroupedPoints = candidates.stream()
			.collect(Collectors.groupingBy(GroundMarkerPointData::getRegionId));

		List<GroundMarkerPointData> result = new ArrayList<>();
		regionGroupedPoints.forEach((regionId, groupedPoints) ->
		{
			List<GroundMarkerPointData> existing = new ArrayList<>(markerStorage.getStoredPoints(regionId));
			for (GroundMarkerPointData point : groupedPoints)
			{
				if (!containsLocation(existing, point))
				{
					existing.add(point);
					result.add(point);
				}
			}
		});

		return result;
	}

	// Also used by GroundMarkerPartySharingManager's own "Import ... from <user>".
	public void importGroundMarkers(Collection<GroundMarkerPointData> importPoints)
	{
		log.debug("Importing {} points", importPoints.size());
		markerStorage.addPoints(importPoints);

		log.debug("Reloading points after import");
		plugin.loadPoints();
	}

	// Like importGroundMarkers, but replacing any existing marker at an imported one's location.
	private void overwriteGroundMarkers(Collection<GroundMarkerPointData> importPoints)
	{
		markerStorage.replacePoints(importPoints);
		plugin.loadPoints();
	}

	private static boolean containsLocation(Collection<GroundMarkerPointData> points, GroundMarkerPointData point)
	{
		for (GroundMarkerPointData existing : points)
		{
			if (existing.getRegionX() == point.getRegionX() && existing.getRegionY() == point.getRegionY()
				&& existing.getZ() == point.getZ())
			{
				return true;
			}
		}

		return false;
	}

	private void promptForClear(MenuEntry entry)
	{
		int[] regions = client.getMapRegions();
		if (regions == null)
		{
			return;
		}

		List<GroundMarkerPointData> activePoints = Arrays.stream(regions)
			.mapToObj(markerStorage::getStoredPoints)
			.flatMap(Collection::stream)
			.collect(Collectors.toList());

		if (activePoints.isEmpty())
		{
			sendChatMessage("You have no ground markers to clear.");
			return;
		}

		int numActivePoints = activePoints.size();
		new ConfirmChatbox(chatboxPanelManager, "Are you sure you want to clear the " + numActivePoints + " currently loaded ground markers?")
			.yes(() ->
			{
				deletedMarkers.addAll(activePoints);
				markerStorage.deletePoints(activePoints);

				plugin.loadPoints();
				sendChatMessage(numActivePoints + " ground marker"
					+ (numActivePoints == 1 ? " was cleared." : "s were cleared."));
			})
			.build();
	}

	private void sendChatMessage(final String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(message)
			.build());
	}
}
