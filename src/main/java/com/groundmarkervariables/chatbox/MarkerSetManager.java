package com.groundmarkervariables.chatbox;

import com.google.gson.JsonParseException;
import com.groundmarkervariables.GroundMarkerVariablesPlugin;
import com.groundmarkervariables.chatbox.completion.Completion;
import com.groundmarkervariables.markers.GroundMarkerPointData;
import com.groundmarkervariables.markers.MarkerSet;
import com.groundmarkervariables.markers.MarkerSets;
import com.groundmarkervariables.markers.minify.MarkerMinifier;
import com.groundmarkervariables.markers.minify.NewerFormatException;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
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
import javax.annotation.Nullable;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.FontID;
import net.runelite.api.FontTypeFace;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.util.Text;

// Built-in, imported and own marker sets, opened from the chatboxes' logo. A set's checkbox switches it on and off; clicking its name shows its
// entries, each with its own checkbox. Right-click a set to copy or delete it.
public class MarkerSetManager extends SearchableChatbox
{
	private static final int DETAIL_COLOR = 0x555555;
	// Left of "_" while the last check failed.
	private static final String SATELLITE_ANTENNA = "1f4e1";
	private static final int ERROR_SIZE = 14;
	private static final String[] HEADINGS = {"Title", "Tiles", "Source", "Author", "Updated"};
	// Besides its heading and sort arrow, the widest value each column after Title is sized for; longer ones get "...".
	private static final List<List<String>> SIZED_FOR = List.of(List.of(), List.of("999/999"),
		List.of("Built-in", "Imported", "Own"), List.of("LimyMonkey12"), List.of());
	private static final int COLUMN_GAP = 6;
	private static final int TABLE_X = 6 + ROW_HEIGHT + 4;
	// Beside the sorted column's heading.
	private static final String SORTED_UP = "25b2";
	private static final String SORTED_DOWN = "25bc";
	private static final int SORT_ARROW_SIZE = 8;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
	// The row's entry for the status line (a check, a failure, or why the table is empty).
	private static final MarkerSet.Entry STATUS = new MarkerSet.Entry(null, List.of(), false, true, 0);

	private final MarkerSets markerSets;
	private final GroundMarkerVariablesSharingManager sharingManager;
	private final MarkerMinifier markerMinifier;

	// Sets showing their entries, by key.
	private final Set<String> expanded = new HashSet<>();
	// Picking one of the user's own sets (see pick()), not managing them.
	@Nullable
	private Consumer<MarkerSet> onPick;
	// The HEADINGS column sets are sorted by (Source at first), then title.
	private int sortColumn = 2;
	private boolean descending;
	// This update's row font, for fitting cells, and each column's width with its gap.
	private FontTypeFace font;
	private int[] widths;

	// A set, one of its entries, or the status line (entry == STATUS).
	private static final class Row
	{
		@Nullable
		private final MarkerSet set;
		@Nullable
		private final MarkerSet.Entry entry;

		private Row(@Nullable MarkerSet set, @Nullable MarkerSet.Entry entry)
		{
			this.set = set;
			this.entry = entry;
		}
	}

	@Inject
	MarkerSetManager(ChatboxPanelManager chatboxPanelManager, ChatMessageManager chatMessageManager, ClientThread clientThread,
		MarkerSets markerSets, SuggestionIconOverlay iconOverlay, ChatboxWidgets widgets, Client client,
		GroundMarkerVariablesSharingManager sharingManager, GroundMarkerVariablesPlugin plugin, MarkerMinifier markerMinifier)
	{
		super(chatboxPanelManager, clientThread, chatMessageManager, iconOverlay, widgets, client, plugin);
		this.markerMinifier = markerMinifier;
		this.sharingManager = sharingManager;
		this.markerSets = markerSets;
	}

	@Override
	protected void open()
	{
		markerSets.setListener(() ->
		{
			if (chatboxPanelManager.getCurrentInput() == this)
			{
				update();
			}
		});
		markerSets.checkForUpdates(false);
		update();
	}

	@Override
	protected void close()
	{
		markerSets.setListener(null);
		super.close();
	}

	// Lists only own sets, each clicked to pick it, e.g. to add markers to.
	public MarkerSetManager pick(Consumer<MarkerSet> onPick)
	{
		this.onPick = onPick;
		return this;
	}

	@Override
	protected String title()
	{
		return onPick != null ? "Add to which set?" : "Marker Sets (" + markerSets.sets().size() + ")";
	}

	// Import, and the failed update's mark left of it.
	@Override
	protected void buildTopBar(Widget container, FontTypeFace font, int rightX)
	{
		rightX = nextX(button(container, font, "Import", "Import a marker set or ground markers from your clipboard", "", true, rightX,
			this::importClipboard));
		if (markerSets.failed())
		{
			Rectangle area = new Rectangle(container.getWidth() - rightX - ERROR_SIZE + 4, 5 + (LINE_HEIGHT - ERROR_SIZE) / 2, ERROR_SIZE,
				ERROR_SIZE);
			iconPlacements.add(new SuggestionIconOverlay.Placement(container, area, Completion.Icon.emoji(SATELLITE_ANTENNA)));
			tips.add(new SuggestionIconOverlay.Tip(container, area, "Couldn't update built-in sets"));
		}
	}

	// Each set in the chosen order, with its entries when expanded or searched for.
	private List<Row> rows()
	{
		List<Row> rows = new ArrayList<>();
		List<MarkerSet> sets = new ArrayList<>(markerSets.sets());
		sets.sort(order());
		for (MarkerSet set : sets)
		{
			if (onPick != null && set.kind != MarkerSet.Kind.OWN)
			{
				continue;
			}
			boolean setMatches = matches(set.title) || matches(set.author) || matches(set.kind.title);
			List<MarkerSet.Entry> entries = new ArrayList<>();
			for (MarkerSet.Entry entry : set.entries)
			{
				if (setMatches || matches(entry.name()))
				{
					entries.add(entry);
				}
			}
			if (!setMatches && entries.isEmpty())
			{
				continue;
			}
			rows.add(new Row(set, null));
			if (expanded.contains(set.key()) || !query.isEmpty() && !setMatches)
			{
				entries.forEach(entry -> rows.add(new Row(set, entry)));
			}
		}
		return rows;
	}

	private boolean matches(String text)
	{
		return query.isEmpty() || text.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
	}

	@Override
	protected void buildRows(Widget container)
	{
		List<Row> rows = rows();
		String status = markerSets.status();
		boolean table = !rows.isEmpty();
		if (!table)
		{
			status = !query.isEmpty() ? "No marker sets match \"" + query + "\"."
				: status != null ? status : "No marker sets yet.";
		}
		if (status != null)
		{
			rows.add(new Row(null, STATUS));
		}
		font = ChatboxWidgets.font(container, widgets.fontId());
		widths = columnWidths(container);

		// The headings stay put above the rows, which scroll.
		int top = RESULTS_Y;
		int visibleRows = VISIBLE_ROWS;
		if (table)
		{
			addHeadings(container, top);
			top += ROW_HEIGHT;
			visibleRows--;
		}
		int maxOffset = Math.max(0, rows.size() - visibleRows);
		scrollOffset = Math.max(0, Math.min(scrollOffset, maxOffset));
		ChatboxWidgets.scrollCapture(container, top, visibleRows * ROW_HEIGHT, this::scroll);

		int visibleCount = Math.min(visibleRows, rows.size() - scrollOffset);
		for (int i = 0; i < visibleCount; i++)
		{
			Row row = rows.get(scrollOffset + i);
			int y = top + ROW_HEIGHT * i;
			if (row.entry == STATUS)
			{
				addText(container, Text.escapeJagex(status), widgets.fontId(), DETAIL_COLOR, 6, y);
			}
			else if (row.entry == null)
			{
				addSet(container, row.set, y);
			}
			else
			{
				addEntry(container, row.set, row.entry, y);
			}
		}

		ChatboxWidgets.scrollbar(container, top, visibleRows * ROW_HEIGHT, scrollOffset, maxOffset, visibleRows, rows.size(), this::scroll);
	}

	// By sortColumn, reversed if descending (unknown dates first going up), then title.
	private Comparator<MarkerSet> order()
	{
		Comparator<MarkerSet> byTitle = Comparator.comparing(set -> set.title, String.CASE_INSENSITIVE_ORDER);
		Comparator<MarkerSet> byColumn;
		switch (sortColumn)
		{
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

	// Each heading sorts by its column, or reverses the order if it's already sorted by it.
	private void addHeadings(Widget container, int y)
	{
		FontTypeFace headingFont = ChatboxWidgets.font(container, FontID.QUILL_8);
		for (int column = 0; column < HEADINGS.length; column++)
		{
			int sortBy = column;
			// The sorted column's arrow takes from its room.
			String text = fit(headingFont, HEADINGS[column], columnWidth(column) - (column == sortColumn ? SORT_ARROW_SIZE + 3 : 0));
			Widget heading = ChatboxWidgets.text(container, text, FontID.QUILL_8, 0x800000, columnX(column), y, columnWidth(column),
				ROW_HEIGHT);
			heading.setHasListener(true);
			heading.setAction(0, column != sortColumn ? "Sort by " + HEADINGS[column] : descending ? "Sort ascending" : "Sort descending");
			heading.setOnOpListener((JavaScriptCallback) ev -> sortBy(sortBy));
			ChatboxWidgets.hover(heading, 0x800000);
			if (column == sortColumn)
			{
				Rectangle area = new Rectangle(columnX(column) + headingFont.getTextWidth(text) + 3,
					y + (ROW_HEIGHT - SORT_ARROW_SIZE) / 2, SORT_ARROW_SIZE, SORT_ARROW_SIZE);
				iconPlacements.add(new SuggestionIconOverlay.Placement(container, area,
					Completion.Icon.emoji(descending ? SORTED_DOWN : SORTED_UP)));
			}
		}
	}

	// Tiles in the set's switched-on entries, whether or not the set itself is on.
	private int enabledTiles(MarkerSet set)
	{
		return set.entries.stream().filter(entry -> markerSets.enabled(set, entry)).mapToInt(entry -> entry.tiles.size()).sum();
	}

	private void sortBy(int column)
	{
		descending = column == sortColumn && !descending;
		sortColumn = column;
		update();
	}

	// Title, tiles, source, author and updated; the title cell is the one clicked to expand, copy or delete.
	private void addSet(Widget container, MarkerSet set, int y)
	{
		boolean enabled = markerSets.enabled(set);
		Widget checkbox = checkbox(container, enabled, 6, y);
		checkbox.setAction(0, enabled ? "Disable" : "Enable");
		checkbox.setOnOpListener((JavaScriptCallback) ev -> markerSets.setEnabled(set, !enabled));

		String[] cells = {set.title, enabledTiles(set) + "/" + set.tileCount(), set.kind.title, set.author,
			set.updated == null ? "Unknown" : DATE.format(set.updated)};
		Widget label = null;
		for (int column = 0; column < cells.length; column++)
		{
			Widget cell = ChatboxWidgets.text(container, Text.escapeJagex(fit(cells[column], columnWidth(column))), widgets.fontId(),
				column == 0 ? 0x000000 : DETAIL_COLOR, columnX(column), y, columnWidth(column), ROW_HEIGHT);
			label = label == null ? cell : label;
		}
		List<Runnable> actions = new ArrayList<>();
		if (onPick != null)
		{
			addAction(label, actions, "Choose", () -> onPick.accept(set));
		}
		addAction(label, actions, expanded.contains(set.key()) ? "Compact set" : "Expand set", () -> toggleExpanded(set));
		addAction(label, actions, "Copy", () -> copy(set));
		addAction(label, actions, "Export", () -> exportTiles(set));
		if (set.kind != MarkerSet.Kind.OWN)
		{
			addAction(label, actions, "Clone", () -> cloneSet(set));
		}
		if (set.kind != MarkerSet.Kind.BUILT_IN)
		{
			addAction(label, actions, "Delete", () -> confirmDelete(set));
		}
		if (set.kind == MarkerSet.Kind.OWN)
		{
			addAction(label, actions, "Set title", () -> askTitle(set));
			addAction(label, actions, "Dissolve set", () -> confirmDissolve(set));
		}
		// Own sets: anon, or claimed by the logged-in name (an Unknown set's history noting it was imported).
		Player player = client.getLocalPlayer();
		String name = player != null ? player.getName() : null;
		boolean unknown = MarkerSet.UNKNOWN.equals(set.author);
		if (set.kind == MarkerSet.Kind.OWN && !MarkerSet.ANON.equals(set.author) && !unknown)
		{
			addAction(label, actions, "Anonymize", () -> markerSets.setAuthor(set, MarkerSet.ANON));
		}
		else if (set.kind == MarkerSet.Kind.OWN && name != null)
		{
			addAction(label, actions, "Claim Authorship", () ->
			{
				if (unknown)
				{
					markerSets.claim(set, name);
				}
				else
				{
					markerSets.setAuthor(set, name);
				}
			});
		}
		label.setOnOpListener((JavaScriptCallback) ev -> actions.get(ev.getOp() - 1).run());
		ChatboxWidgets.hover(label, 0x000000);
	}

	// widget's next right-click option.
	private static void addAction(Widget widget, List<Runnable> actions, String name, Runnable action)
	{
		widget.setHasListener(true);
		widget.setAction(actions.size(), name);
		actions.add(action);
	}

	private void addEntry(Widget container, MarkerSet set, MarkerSet.Entry entry, int y)
	{
		boolean enabled = markerSets.enabled(set, entry);
		int x = columnX(0);
		Widget checkbox = checkbox(container, enabled, x, y);
		int textX = x + checkbox.getOriginalWidth() + 4;
		int textWidth = columnX(1) - textX - 6;
		int color = markerSets.enabled(set) ? 0x000000 : ChatboxWidgets.DISABLED_COLOR;
		Widget label = ChatboxWidgets.text(container, Text.escapeJagex(fit(entry.name(), textWidth)), widgets.fontId(), color, textX, y,
			textWidth, ROW_HEIGHT);
		ChatboxWidgets.text(container, String.valueOf(entry.tiles.size()), widgets.fontId(), DETAIL_COLOR, columnX(1), y, columnWidth(1),
			ROW_HEIGHT);
		for (Widget clickable : List.of(checkbox, label))
		{
			List<Runnable> actions = new ArrayList<>();
			addAction(clickable, actions, enabled ? "Disable" : "Enable", () -> markerSets.setEnabled(set, entry, !enabled));
			if (set.kind == MarkerSet.Kind.OWN && !entry.group)
			{
				addAction(clickable, actions, "Edit", () -> editTile(set, entry));
			}
			if (set.kind == MarkerSet.Kind.OWN)
			{
				addAction(clickable, actions, "Set title", () -> askTitle(set, entry));
				addAction(clickable, actions, "Delete", () -> confirmDelete(set, entry));
			}
			clickable.setOnOpListener((JavaScriptCallback) ev -> actions.get(ev.getOp() - 1).run());
		}
		ChatboxWidgets.hover(label, color);
	}

	// Columns' left edges: title after the set's check box, then tiles, source, author and updated.
	private int columnX(int column)
	{
		int x = TABLE_X;
		for (int before = 0; before < column; before++)
		{
			x += widths[before];
		}
		return x;
	}

	private int columnWidth(int column)
	{
		return widths[column] - COLUMN_GAP;
	}

	// Each column after Title fits its heading with a sort arrow and SIZED_FOR (and any date, for Updated) in the row
	// font; Title takes the rest of the row, up to the scrollbar (the last column's gap is its margin).
	private int[] columnWidths(Widget container)
	{
		FontTypeFace headingFont = ChatboxWidgets.font(container, FontID.QUILL_8);
		int[] columns = new int[HEADINGS.length];
		int used = 0;
		for (int column = 1; column < HEADINGS.length; column++)
		{
			List<String> values = new ArrayList<>(SIZED_FOR.get(column));
			if (column == HEADINGS.length - 1)
			{
				for (int month = 1; month <= 12; month++)
				{
					values.add(DATE.format(LocalDate.of(2026, month, 28)));
				}
			}
			int width = headingFont.getTextWidth(HEADINGS[column]) + 3 + SORT_ARROW_SIZE;
			for (String value : values)
			{
				width = Math.max(width, font.getTextWidth(value));
			}
			columns[column] = width + COLUMN_GAP;
			used += columns[column];
		}
		columns[0] = container.getWidth() - ChatboxWidgets.SCROLLBAR_WIDTH - TABLE_X - used;
		return columns;
	}

	// text in the row font, cut to fit width with "...".
	private String fit(String text, int width)
	{
		return fit(font, text, width);
	}

	private static String fit(FontTypeFace font, String text, int width)
	{
		if (font.getTextWidth(Text.escapeJagex(text)) <= width)
		{
			return text;
		}
		String cut = text;
		while (!cut.isEmpty() && font.getTextWidth(Text.escapeJagex(cut) + "...") > width)
		{
			cut = cut.substring(0, cut.length() - 1);
		}
		return cut.trim() + "...";
	}

	private void toggleExpanded(MarkerSet set)
	{
		if (!expanded.remove(set.key()))
		{
			expanded.add(set.key());
		}
		update();
	}

	private void copy(MarkerSet set)
	{
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(markerSets.export(set)), null);
		sendChatMessage("The marker set \"" + set.title + "\" was copied to your clipboard.");
	}

	// A copied marker set, as the orb's Import; else copied ground markers, named and saved as the user's own set by Unknown,
	// same-label markers grouped (as Marker Manager's Set).
	private void importClipboard()
	{
		String text;
		try
		{
			text = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
		}
		catch (IOException | UnsupportedFlavorException e)
		{
			sendChatMessage("Unable to read system clipboard.");
			return;
		}

		MarkerSet set;
		List<GroundMarkerPointData> markers;
		try
		{
			set = markerSets.parse(text);
			markers = set != null ? List.of() : markerMinifier.parse(text);
		}
		catch (JsonParseException | IllegalArgumentException | NewerFormatException e)
		{
			set = null;
			markers = List.of();
		}
		if (set != null)
		{
			sharingManager.confirmSetImport(set, text, () -> chatboxPanelManager.openInput(this));
			return;
		}
		if (markers.isEmpty())
		{
			sendChatMessage("You do not have a marker set or ground markers copied in your clipboard.");
			return;
		}

		Map<String, List<GroundMarkerPointData>> byLabel = new LinkedHashMap<>();
		markers.forEach(marker -> byLabel.computeIfAbsent(marker.getLabel() == null ? "" : marker.getLabel(), label -> new ArrayList<>())
			.add(marker));
		List<MarkerSet.Entry> entries = new ArrayList<>();
		byLabel.values().forEach(tiles -> entries.add(new MarkerSet.Entry(null, tiles, tiles.size() > 1, true, entries.size())));
		int count = markers.size();
		chatboxPanelManager.openTextInput("Name the marker set for these " + count + " ground markers")
			.onDone((Predicate<String>) title ->
			{
				clientThread.invoke(() ->
				{
					if (!title.isBlank())
					{
						markerSets.create(title.trim(), MarkerSet.UNKNOWN, entries);
						sendChatMessage(count + " ground markers were imported as the marker set \"" + title.trim() + "\".");
					}
					chatboxPanelManager.openInput(this);
				});
				return false;
			})
			.build();
	}

	// Just its tiles, as ground markers: a label with variables goes in "gmv", as written.
	private void exportTiles(MarkerSet set)
	{
		List<GroundMarkerPointData> tiles = new ArrayList<>();
		set.entries.forEach(entry -> tiles.addAll(entry.tiles));
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(markerMinifier.exportGmvPlain(tiles)), null);
		sendChatMessage(tiles.size() + " ground markers from the marker set \"" + set.title + "\" were copied to your clipboard.");
	}

	// Your own copy, by the logged-in name (else anon).
	private void cloneSet(MarkerSet set)
	{
		Player player = client.getLocalPlayer();
		MarkerSet clone = markerSets.cloneSet(set, player != null && player.getName() != null ? player.getName() : MarkerSet.ANON);
		sendChatMessage("The marker set \"" + clone.title + "\" was made from \"" + set.title + "\" by " + set.author + ".");
	}

	private void confirmDelete(MarkerSet set)
	{
		chatboxPanelManager.openTextMenuInput("Are you sure you want to delete the marker set \"" + Text.escapeJagex(set.title) + "\"?")
			.option("Yes", () ->
			{
				markerSets.delete(set);
				sendChatMessage("The marker set \"" + set.title + "\" was deleted.");
				chatboxPanelManager.openInput(this);
			})
			.option("No", () -> chatboxPanelManager.openInput(this))
			.build();
	}

	// Every tile back into saved markers (previewed, keeping or overwriting markers already there, as on import), then
	// the set is deleted.
	private void confirmDissolve(MarkerSet set)
	{
		List<GroundMarkerPointData> tiles = new ArrayList<>();
		set.entries.forEach(entry -> tiles.addAll(entry.tiles));
		sharingManager.confirmImport(tiles, " ground markers were restored from the marker set \"" + set.title + "\".", () ->
		{
			markerSets.delete(set);
			chatboxPanelManager.openInput(this);
		}, () -> chatboxPanelManager.openInput(this));
	}

	private void editTile(MarkerSet set, MarkerSet.Entry entry)
	{
		editLabel(entry.tiles.get(0), label -> markerSets.setLabel(set, entry, label));
	}

	// A set needs a title, so blank leaves it as it was.
	private void askTitle(MarkerSet set)
	{
		chatboxPanelManager.openTextInput("Marker set title")
			.value(set.title)
			.onDone((Predicate<String>) title ->
			{
				clientThread.invoke(() ->
				{
					if (!title.isBlank())
					{
						markerSets.setTitle(set, title);
					}
					chatboxPanelManager.openInput(this);
				});
				return false;
			})
			.build();
	}

	// Blank removes the title, so the entry shows its label again. onDone returns false: reopening this replaces the
	// prompt, which closing would close instead.
	private void askTitle(MarkerSet set, MarkerSet.Entry entry)
	{
		chatboxPanelManager.openTextInput("Title (blank for none)")
			.value(entry.title == null ? "" : entry.title)
			.onDone((Predicate<String>) title ->
			{
				clientThread.invoke(() ->
				{
					if (!markerSets.setTitle(set, entry, title))
					{
						sendChatMessage("Another entry in \"" + set.title + "\" is already titled \"" + title.trim() + "\".");
					}
					chatboxPanelManager.openInput(this);
				});
				return false;
			})
			.build();
	}

	private void confirmDelete(MarkerSet set, MarkerSet.Entry entry)
	{
		int tiles = entry.tiles.size();
		chatboxPanelManager.openTextMenuInput("Are you sure you want to delete \"" + Text.escapeJagex(entry.name()) + "\" ("
				+ tiles + (tiles == 1 ? " tile" : " tiles") + ") from the marker set \"" + Text.escapeJagex(set.title) + "\"?")
			.option("Yes", () ->
			{
				markerSets.remove(set, entry);
				sendChatMessage("\"" + entry.name() + "\" was deleted from the marker set \"" + set.title + "\".");
				chatboxPanelManager.openInput(this);
			})
			.option("No", () -> chatboxPanelManager.openInput(this))
			.build();
	}
}
