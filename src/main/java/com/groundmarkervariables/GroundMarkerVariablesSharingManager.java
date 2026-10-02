package com.groundmarkervariables;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.Runnables;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.menus.WidgetMenuOption;

// Mirrors core's GroundMarkerSharingManager — clipboard export/import and a "Clear" confirmation
// for every currently loaded ground marker, via the world map orb's right-click menu.
@Slf4j
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
	private final Gson gson;

	@Inject
	private GroundMarkerVariablesSharingManager(GroundMarkerVariablesPlugin plugin, MarkerStorage markerStorage, Client client,
		ImportPreviewManager importPreviewManager, MenuManager menuManager, ChatMessageManager chatMessageManager,
		ChatboxPanelManager chatboxPanelManager, Gson gson)
	{
		this.plugin = plugin;
		this.markerStorage = markerStorage;
		this.client = client;
		this.importPreviewManager = importPreviewManager;
		this.menuManager = menuManager;
		this.chatMessageManager = chatMessageManager;
		this.chatboxPanelManager = chatboxPanelManager;
		this.gson = gson;
	}

	void addImportExportMenuOptions()
	{
		menuManager.addManagedCustomMenu(EXPORT_MARKERS_OPTION, this::exportGroundMarkers);
		menuManager.addManagedCustomMenu(IMPORT_MARKERS_OPTION, this::promptForImport);
	}

	void addClearMenuOption()
	{
		menuManager.addManagedCustomMenu(CLEAR_MARKERS_OPTION, this::promptForClear);
	}

	void removeMenuOptions()
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

	private void exportGroundMarkers(MenuEntry menuEntry)
	{
		List<GroundMarkerPointData> activePoints = activePoints();
		if (activePoints.isEmpty())
		{
			sendChatMessage("You have no ground markers to export.");
			return;
		}

		final String exportDump = gson.toJson(activePoints);

		log.debug("Exported ground markers: {}", exportDump);

		Toolkit.getDefaultToolkit()
			.getSystemClipboard()
			.setContents(new StringSelection(exportDump), null);
		sendChatMessage(activePoints.size() + " ground markers were copied to your clipboard.");
	}

	private void promptForImport(MenuEntry menuEntry)
	{
		final String clipboardText;
		try
		{
			clipboardText = Toolkit.getDefaultToolkit()
				.getSystemClipboard()
				.getData(DataFlavor.stringFlavor)
				.toString();
		}
		catch (IOException | UnsupportedFlavorException ex)
		{
			sendChatMessage("Unable to read system clipboard.");
			log.warn("error reading clipboard", ex);
			return;
		}

		log.debug("Clipboard contents: {}", clipboardText);
		if (Strings.isNullOrEmpty(clipboardText))
		{
			sendChatMessage("You do not have any ground markers copied in your clipboard.");
			return;
		}

		List<GroundMarkerPointData> importPoints;
		try
		{
			// CHECKSTYLE:OFF
			importPoints = gson.fromJson(clipboardText, new TypeToken<List<GroundMarkerPointData>>(){}.getType());
			// CHECKSTYLE:ON
		}
		catch (JsonSyntaxException e)
		{
			log.debug("Malformed JSON for clipboard import", e);
			sendChatMessage("You do not have any ground markers copied in your clipboard.");
			return;
		}

		if (importPoints == null || importPoints.isEmpty())
		{
			sendChatMessage("You do not have any ground markers copied in your clipboard.");
			return;
		}

		List<GroundMarkerPointData> nonOverlapping = nonOverlapping(importPoints);
		importPreviewManager.show(nonOverlapping);
		String count = countWithOverlap(importPoints.size(), nonOverlapping.size());
		chatboxPanelManager.openTextMenuInput("Are you sure you want to import " + count + " ground markers?")
			.option("Yes", () ->
			{
				importGroundMarkers(importPoints);
				sendChatMessage(importPoints.size() + " ground markers were imported from the clipboard.");
			})
			.option("No", Runnables.doNothing())
			.onClose(importPreviewManager::clear)
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
		chatboxPanelManager.openTextMenuInput("Are you sure you want to clear the<br>" + numActivePoints + " currently loaded ground markers?")
			.option("Yes", () ->
			{
				markerStorage.deletePoints(activePoints);

				plugin.loadPoints();
				sendChatMessage(numActivePoints + " ground marker"
					+ (numActivePoints == 1 ? " was cleared." : "s were cleared."));
			})
			.option("No", Runnables.doNothing())
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
