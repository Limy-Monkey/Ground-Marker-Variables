package com.groundmarkervariables.chatbox;

import com.groundmarkervariables.GroundMarkerVariablesPlugin;
import com.groundmarkervariables.chatbox.completion.Completion;
import com.groundmarkervariables.markers.GroundMarkerPointData;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import java.util.function.UnaryOperator;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.SpritePixels;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.FontID;
import net.runelite.api.FontTypeFace;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.game.chatbox.ChatboxInput;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.util.Text;

// A chatbox list (the Marker Manager, Marker Sets): the logo, title and typed search, then "x", "_" and the subclass's
// buttons right to left, then its rows. Escape clears the search, then closes; Backspace removes a character from it.
public abstract class SearchableChatbox extends ChatboxInput implements KeyListener
{
	protected static final int LINE_HEIGHT = 20;
	protected static final int ROW_HEIGHT = 16;
	protected static final int RESULTS_Y = 30;
	// Fixed, like AdvancedLabelEditor -- fills the chatbox down to where its own list ends.
	protected static final int VISIBLE_ROWS = 6;
	protected static final int DETAIL_COLOR = 0x555555;
	// Table columns start after the row's check box.
	protected static final int TABLE_X = 6 + ROW_HEIGHT + 4;
	private static final int BUTTON_FONT = FontID.PLAIN_11;
	private static final int TOP_BAR_GAP = 8;
	private static final int COLUMN_GAP = 6;
	// Beside the sorted column's heading.
	private static final String SORTED_UP = "25b2";
	private static final String SORTED_DOWN = "25bc";
	private static final int SORT_ARROW_SIZE = 8;

	protected final ChatboxPanelManager chatboxPanelManager;
	protected final ClientThread clientThread;
	protected final ChatboxWidgets widgets;
	protected final Client client;
	protected final GroundMarkerVariablesPlugin plugin;
	private final ChatMessageManager chatMessageManager;
	private final SuggestionIconOverlay iconOverlay;
	// This update's icons (the logo, check marks, ...) and tooltips, drawn by iconOverlay.
	protected final List<SuggestionIconOverlay.Placement> iconPlacements = new ArrayList<>();
	protected final List<SuggestionIconOverlay.Tip> tips = new ArrayList<>();
	protected int scrollOffset;
	protected volatile String query = "";
	// The table column the rows are sorted by, and which way; clicking a heading changes them.
	protected int sortColumn;
	protected boolean descending;
	// This update's row font, for fitting cells; the table's headings, and each column's width with its gap.
	protected FontTypeFace font;
	private String[] headings;
	private int[] widths;
	// This update's addSubmenu() choices, by widget and option.
	private final Map<Widget, Map<String, Map<String, Runnable>>> submenus = new HashMap<>();

	protected SearchableChatbox(ChatboxPanelManager chatboxPanelManager, ClientThread clientThread, ChatMessageManager chatMessageManager,
		SuggestionIconOverlay iconOverlay, ChatboxWidgets widgets, Client client, GroundMarkerVariablesPlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
		this.chatboxPanelManager = chatboxPanelManager;
		this.clientThread = clientThread;
		this.chatMessageManager = chatMessageManager;
		this.iconOverlay = iconOverlay;
		this.widgets = widgets;
	}

	// The title row's text before the search, e.g. "Marker Sets (2)".
	protected abstract String title();

	// Buttons right to left from rightX, after "x" and "_".
	protected abstract void buildTopBar(Widget container, FontTypeFace font, int rightX);

	protected abstract void buildRows(Widget container);

	protected void update()
	{
		Widget container = chatboxPanelManager.getContainerWidget();
		container.deleteAllChildren();
		iconPlacements.clear();
		tips.clear();
		submenus.clear();

		widgets.logo(container, iconPlacements, this);
		String search = query.isEmpty() ? " <col=808080>type to search</col>" : ": <col=000000>" + Text.escapeJagex(query) + "</col>";
		ChatboxWidgets.text(container, title() + search, FontID.QUILL_8, 0x800000, ChatboxWidgets.LOGO_RIGHT, 5, 260, LINE_HEIGHT);

		FontTypeFace font = ChatboxWidgets.font(container, BUTTON_FONT);
		int rightX = nextX(button(container, font, "x", "Close", "", true, TOP_BAR_GAP, chatboxPanelManager::close));
		rightX = nextX(button(container, font, "_", "Toggle font size", "", true, rightX, () ->
		{
			widgets.toggleFontSize();
			update();
		}));
		buildTopBar(container, font, rightX);

		ChatboxWidgets.separator(container, RESULTS_Y - 4);
		buildRows(container);
		iconOverlay.show(iconPlacements, tips);
	}

	@Override
	protected void close()
	{
		iconOverlay.clear();
	}

	@Override
	public void keyPressed(KeyEvent ev)
	{
		if (!chatboxPanelManager.shouldTakeInput())
		{
			return;
		}
		if (ev.getKeyCode() == KeyEvent.VK_ESCAPE)
		{
			ev.consume();
			if (query.isEmpty())
			{
				chatboxPanelManager.close();
			}
			else
			{
				search(query -> "");
			}
		}
		else if (ev.getKeyCode() == KeyEvent.VK_BACK_SPACE && !query.isEmpty())
		{
			ev.consume();
			search(query -> query.isEmpty() ? query : query.substring(0, query.length() - 1));
		}
	}

	@Override
	public void keyTyped(KeyEvent ev)
	{
		char c = ev.getKeyChar();
		if (chatboxPanelManager.shouldTakeInput() && !Character.isISOControl(c) && !ev.isControlDown() && !ev.isAltDown())
		{
			ev.consume();
			search(query -> query + c);
		}
	}

	@Override
	public void keyReleased(KeyEvent ev)
	{
	}

	// Keys arrive on the AWT thread; the search changes, and widgets are built, on the client thread.
	private void search(UnaryOperator<String> change)
	{
		clientThread.invoke(() ->
		{
			query = change.apply(query);
			scrollOffset = 0;
			update();
		});
	}

	protected void scroll(int direction)
	{
		scrollOffset += direction;
		update();
	}

	// The x for the next button to the left of button.
	protected static int nextX(Widget button)
	{
		return button.getOriginalX() + button.getOriginalWidth() + TOP_BAR_GAP;
	}

	// A top bar button rightX from the right; target is its menu target while enabled.
	protected static Widget button(Widget container, FontTypeFace font, String text, String action, String target, boolean enabled,
		int rightX, Runnable onClick)
	{
		Widget button = ChatboxWidgets.button(container, text, BUTTON_FONT, rightX, 5, font.getTextWidth(text) + 4, LINE_HEIGHT, action,
			enabled ? onClick : null);
		if (enabled)
		{
			button.setName(target);
		}
		return button;
	}

	// The Settings menu's round check box (empty, or ticked green), at its own size (a smaller widget crops its bottom-right), centered in the row at y.
	protected Widget checkbox(Widget container, boolean checked, int x, int y)
	{
		int spriteId = checked ? SpriteID.OptionsRadioButtons.CHECK_GREEN : SpriteID.OptionsRadioButtons.EMPTY;
		SpritePixels[] sprites = client.getSprites(client.getIndexSprites(), spriteId, 0);
		int width = sprites == null ? ROW_HEIGHT : sprites[0].getMaxWidth();
		int height = sprites == null ? ROW_HEIGHT : sprites[0].getMaxHeight();
		Widget checkbox = ChatboxWidgets.child(container, WidgetType.GRAPHIC, x, y + (ROW_HEIGHT - height) / 2, width, height);
		checkbox.setSpriteId(spriteId);
		checkbox.setHasListener(true);
		return checkbox;
	}

	// The Advanced Label Editor on tile's label, given to save. Closing it comes back here (see reopen()), unless it opened
	// another chatbox (which leaves a current input).
	protected void editLabel(GroundMarkerPointData tile, Consumer<String> save)
	{
		plugin.editLabel(tile.worldPoint(), tile.getLabel(),
			tile.getColor(), save, () -> clientThread.invokeLater(() ->
			{
				if (chatboxPanelManager.getCurrentInput() == null)
				{
					reopen();
				}
			}));
	}

	// Redraws, unless another chatbox has replaced this one, e.g. after something it waited for.
	protected void updateIfOpen()
	{
		if (chatboxPanelManager.getCurrentInput() == this)
		{
			update();
		}
	}

	protected void reopen()
	{
		chatboxPanelManager.openInput(this);
	}

	// Sizes the table's columns: each but flex fits its heading with a sort arrow and sizedFor's values in the row font
	// (longer ones get "..."); flex takes the rest of the row, up to the scrollbar (the last column's gap is its margin). A
	// "" heading isn't shown, sized for or sortable.
	protected void columns(Widget container, String[] headings, List<List<String>> sizedFor, int flex)
	{
		this.headings = headings;
		font = ChatboxWidgets.font(container, widgets.fontId());
		FontTypeFace headingFont = ChatboxWidgets.font(container, FontID.QUILL_8);
		widths = new int[headings.length];
		int used = 0;
		for (int column = 0; column < headings.length; column++)
		{
			if (column != flex)
			{
				int width = headings[column].isEmpty() ? 0 : headingFont.getTextWidth(headings[column]) + 3 + SORT_ARROW_SIZE;
				for (String value : sizedFor.get(column))
				{
					width = Math.max(width, font.getTextWidth(value));
				}
				widths[column] = width + COLUMN_GAP;
				used += widths[column];
			}
		}
		widths[flex] = container.getWidth() - ChatboxWidgets.SCROLLBAR_WIDTH - TABLE_X - used;
	}

	// count rows, scrolling, under a fixed line if headed; addRow draws row i at y. The fixed line shows the headings,
	// unless fixedRow draws something there for the first row shown (e.g. the set it's in) at that y.
	protected void table(Widget container, boolean headed, int count, BiConsumer<Integer, Integer> addRow,
		@Nullable BiPredicate<Integer, Integer> fixedRow)
	{
		int top = headed ? RESULTS_Y + ROW_HEIGHT : RESULTS_Y;
		int visibleRows = headed ? VISIBLE_ROWS - 1 : VISIBLE_ROWS;
		int maxOffset = Math.max(0, count - visibleRows);
		scrollOffset = Math.max(0, Math.min(scrollOffset, maxOffset));
		if (headed && (fixedRow == null || count == 0 || !fixedRow.test(scrollOffset, RESULTS_Y)))
		{
			addHeadings(container, RESULTS_Y);
		}
		ChatboxWidgets.scrollCapture(container, top, visibleRows * ROW_HEIGHT, this::scroll);
		for (int i = 0; i < Math.min(visibleRows, count - scrollOffset); i++)
		{
			addRow.accept(scrollOffset + i, top + ROW_HEIGHT * i);
		}
		ChatboxWidgets.scrollbar(container, top, visibleRows * ROW_HEIGHT, scrollOffset, maxOffset, visibleRows, count, this::scroll);
	}

	// Each heading sorts by its column, or reverses the order if it's already sorted by it.
	private void addHeadings(Widget container, int y)
	{
		FontTypeFace headingFont = ChatboxWidgets.font(container, FontID.QUILL_8);
		for (int column = 0; column < headings.length; column++)
		{
			if (headings[column].isEmpty())
			{
				continue;
			}
			int sortBy = column;
			// The sorted column's arrow takes from its room.
			String text = fit(headingFont, headings[column], columnWidth(column) - (column == sortColumn ? SORT_ARROW_SIZE + 3 : 0));
			Widget heading = ChatboxWidgets.text(container, text, FontID.QUILL_8, 0x800000, columnX(column), y, columnWidth(column),
				ROW_HEIGHT);
			heading.setHasListener(true);
			heading.setAction(0, column != sortColumn ? "Sort by " + headings[column] : descending ? "Sort ascending" : "Sort descending");
			heading.setOnOpListener((JavaScriptCallback) ev ->
			{
				descending = sortBy == sortColumn && !descending;
				sortColumn = sortBy;
				update();
			});
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

	protected int columnX(int column)
	{
		int x = TABLE_X;
		for (int before = 0; before < column; before++)
		{
			x += widths[before];
		}
		return x;
	}

	protected int columnWidth(int column)
	{
		return widths[column] - COLUMN_GAP;
	}

	// text in column at y, escaped and cut to fit.
	protected Widget cell(Widget container, String text, int color, int column, int y)
	{
		return ChatboxWidgets.text(container, Text.escapeJagex(fit(text, columnWidth(column))), widgets.fontId(), color, columnX(column), y,
			columnWidth(column), ROW_HEIGHT);
	}

	// text in the row font, cut to fit width with "...".
	protected String fit(String text, int width)
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

	// widget's next right-click option; its op listener runs actions.get(op - 1).
	protected static void addAction(Widget widget, List<Runnable> actions, String name, Runnable action)
	{
		widget.setHasListener(true);
		widget.setAction(actions.size(), name);
		actions.add(action);
	}

	// widget's next right-click option, opening a submenu of choices (in order, e.g. Map > On World Map); itself does
	// nothing. See menuEntryAdded().
	protected void addSubmenu(Widget widget, List<Runnable> actions, String name, Map<String, Runnable> choices)
	{
		addAction(widget, actions, name, () -> { });
		submenus.computeIfAbsent(widget, key -> new HashMap<>()).put(name, choices);
	}

	// From the plugin's onMenuEntryAdded while this is open: makes an addSubmenu() option's entry its submenu.
	public void menuEntryAdded(MenuEntry entry)
	{
		String name = Text.removeTags(entry.getOption());
		Map<String, Runnable> choices = entry.getWidget() == null ? null : submenus.getOrDefault(entry.getWidget(), Map.of()).get(name);
		if (choices == null)
		{
			return;
		}
		// Its word as the target (e.g. "Map"), shown over its choices.
		entry.setOption("").setTarget(name);
		Menu submenu = entry.createSubMenu();
		// Each entry goes above the last, so the first choice is added last.
		List<String> names = new ArrayList<>(choices.keySet());
		Collections.reverse(names);
		names.forEach(choice -> submenu.createMenuEntry(-1).setOption(choice).setType(MenuAction.RUNELITE).onClick(e -> choices.get(choice).run()));
	}

	protected static Widget addText(Widget container, String text, int fontId, int color, int x, int y)
	{
		return ChatboxWidgets.rowText(container, text, fontId, color, x, y, ROW_HEIGHT, x + ChatboxWidgets.SCROLLBAR_WIDTH + 6);
	}

	// A tooltip under area (in the chatbox) for a few seconds, e.g. why an action can't be done.
	protected void notice(Rectangle area, String text)
	{
		iconOverlay.notice(new SuggestionIconOverlay.Tip(chatboxPanelManager.getContainerWidget(), area, text));
	}

	protected void sendChatMessage(String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(message)
			.build());
	}
}
