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
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.function.UnaryOperator;
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
import net.runelite.client.game.chatbox.ChatboxInput;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.util.Text;

// A chatbox list (the Marker Manager, Marker Sets): the logo, title and typed search, then "x", "_" and the subclass's
// buttons right to left, then its rows. Escape clears the search, then closes; Backspace removes a character from it.
public abstract class SearchableChatbox extends ChatboxInput implements KeyListener {
	protected static final int LINE_HEIGHT = 20;
	protected static final int ROW_HEIGHT = 16;
	protected static final int RESULTS_Y = 30;
	// Fixed, like AdvancedLabelEditor -- fills the chatbox down to where its own list ends.
	protected static final int VISIBLE_ROWS = 6;
	protected static final int DETAIL_COLOR = 0x555555;
	// Table columns start after the row's check box.
	protected static final int TABLE_X = 6 + ROW_HEIGHT + 4;
	static final int BUTTON_FONT = FontID.PLAIN_11;
	static final int TOP_BAR_GAP = 8;
	// The recycling bin's :recycle@white: (see recycleBinButton), and a deleted thing's crossed check box there.
	private static final String RECYCLE = "267b@ffffff";
	private static final int RECYCLE_SIZE = 14;
	private static final int RECYCLED_CHECKBOX = SpriteID.OptionsRadioButtons.CROSS_RED_HOVER;
	private static final int COLUMN_GAP = 6;
	// Beside the sorted column's heading.
	private static final String SORTED_UP = "25b2";
	private static final String SORTED_DOWN = "25bc";
	private static final int SORT_ARROW_SIZE = 8;
	// A heading icon's size (see headingIcon).
	private static final int HEADING_ICON = 14;
	// longTip()'s width in pixels, and most lines.
	private static final int TIP_WIDTH = 300;
	private static final int TIP_LINES = 8;

	// Injected into the fields, so subclasses' constructors (Lombok's) take only their own.
	@Inject
	protected ChatboxPanelManager chatboxPanelManager;
	@Inject
	protected ClientThread clientThread;
	@Inject
	protected ChatboxWidgets widgets;
	@Inject
	protected Client client;
	@Inject
	protected GroundMarkerVariablesPlugin plugin;
	@Inject
	private SuggestionIconOverlay iconOverlay;
	// This update's icons (the logo, check marks, ...) and tooltips, drawn by iconOverlay.
	protected final List<SuggestionIconOverlay.Placement> iconPlacements = new ArrayList<>();
	protected final List<SuggestionIconOverlay.Tip> tips = new ArrayList<>();
	protected int scrollOffset;
	// Listing the recycling bin instead (see recycleBinButton).
	protected boolean recycleBin;
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

	// The title row's text before the search, e.g. "Marker Sets (2)".
	protected abstract String title();

	// What's in the recycling bin (0 for none, or none to show), for its title, icon and closing once empty.
	protected abstract int recycled();

	// Buttons right to left from rightX, after "x" and "_".
	protected abstract void buildTopBar(Widget container, FontTypeFace font, int rightX);

	protected abstract void buildRows(Widget container);

	protected void update() {
		leaveEmptyBin();
		Widget container = chatboxPanelManager.getContainerWidget();
		container.deleteAllChildren();
		iconPlacements.clear();
		tips.clear();
		submenus.clear();

		widgets.logo(container, iconPlacements, this);
		String search = query.isEmpty() ? " <col=808080>type to search</col>" : ": <col=000000>" + Text.escapeJagex(query) + "</col>";
		ChatboxWidgets.text(container, (recycleBin ? "Recycling Bin (" + recycled() + ")" : title()) + search, FontID.QUILL_8, 0x800000, ChatboxWidgets.LOGO_RIGHT, 5, 260, LINE_HEIGHT);

		FontTypeFace font = ChatboxWidgets.font(container, BUTTON_FONT);
		buildTopBar(container, font, closeAndFontButtons(container, font, chatboxPanelManager, widgets, this::update));

		ChatboxWidgets.separator(container, RESULTS_Y - 4);
		buildRows(container);
		iconOverlay.show(iconPlacements, tips);
	}

	@Override
	protected void close() {
		iconOverlay.clear();
	}

	@Override
	public void keyPressed(KeyEvent ev) {
		if (!chatboxPanelManager.shouldTakeInput()) {
			return;
		}
		if (ev.getKeyCode() == KeyEvent.VK_ESCAPE) {
			ev.consume();
			if (query.isEmpty()) {
				chatboxPanelManager.close();
			} else {
				search(query -> "");
			}
		} else if (ev.getKeyCode() == KeyEvent.VK_BACK_SPACE && !query.isEmpty()) {
			ev.consume();
			search(query -> query.isEmpty() ? query : query.substring(0, query.length() - 1));
		}
	}

	@Override
	public void keyTyped(KeyEvent ev) {
		char c = ev.getKeyChar();
		if (chatboxPanelManager.shouldTakeInput() && !Character.isISOControl(c) && !ev.isControlDown() && !ev.isAltDown()) {
			ev.consume();
			search(query -> query + c);
		}
	}

	@Override
	public void keyReleased(KeyEvent ev) {
	}

	// Keys arrive on the AWT thread; the search changes, and widgets are built, on the client thread.
	private void search(UnaryOperator<String> change) {
		clientThread.invoke(() -> {
			query = change.apply(query);
			scrollOffset = 0;
			update();
		});
	}

	// text added to the search as more words, e.g. by a right-click Filter.
	protected void addToSearch(String text) {
		search(query -> query.isEmpty() ? text : query + " " + text);
	}

	protected void scroll(int direction) {
		scrollOffset += direction;
		update();
	}

	// Every chatbox's top bar starts at its right with "x" (Close) and "_" (Toggle font size, then redraw), in font (of
	// BUTTON_FONT). Returns the x for the next button.
	static int closeAndFontButtons(Widget container, FontTypeFace font, ChatboxPanelManager chatboxPanelManager, ChatboxWidgets widgets,
		Runnable redraw) {
		int rightX = nextX(button(container, font, "x", "Close", "", true, TOP_BAR_GAP, chatboxPanelManager::close));
		return nextX(button(container, font, "_", "Toggle font size", "", true, rightX, () -> {
			widgets.toggleFontSize();
			redraw.run();
		}));
	}

	// Out of the recycling bin once it's empty, e.g. after restoring its last.
	protected void leaveEmptyBin() {
		recycleBin &= recycled() > 0;
	}

	// While the recycling bin has something: its :recycle@white: at rightX, opening it (recycleBin) or going back; then
	// shown runs. Returns the x for the next button.
	protected int recycleBinButton(Widget container, int rightX, Runnable shown) {
		if (recycled() == 0) {
			return rightX;
		}
		Rectangle area = new Rectangle(container.getWidth() - rightX - RECYCLE_SIZE, 5 + (LINE_HEIGHT - RECYCLE_SIZE) / 2, RECYCLE_SIZE,
			RECYCLE_SIZE);
		iconPlacements.add(new SuggestionIconOverlay.Placement(container, area, Completion.Icon.emoji(RECYCLE)));
		Widget bin = ChatboxWidgets.child(container, WidgetType.GRAPHIC, area.x, area.y, area.width, area.height);
		addAction(bin, new ArrayList<>(), recycleBin ? "Back" : "Open recycling bin", () -> {
			recycleBin = !recycleBin;
			scrollOffset = 0;
			shown.run();
		});
		return rightX + RECYCLE_SIZE + TOP_BAR_GAP;
	}

	// Deleting from the recycling bin permanently: until it's been there 30 minutes (minutesLeft, see
	// RecyclingBin#minutesLeft), a notice under area of how long is left; then, once what (escaped, e.g. "this ground
	// marker") is confirmed, delete.
	protected void confirmPurge(long minutesLeft, Rectangle area, String what, Runnable delete) {
		if (minutesLeft > 0) {
			notice(area, "You must wait " + minutesLeft + (minutesLeft == 1 ? " minute" : " minutes") + " before deleting this permanently.");
			return;
		}
		new ConfirmChatbox(chatboxPanelManager, "Delete " + what + " permanently? It can't be restored.")
			.yes(() -> {
				delete.run();
				reopen();
			})
			.no(() -> chatboxPanelManager.openInput(this))
			.build();
	}

	// The x for the next button to the left of button.
	protected static int nextX(Widget button) {
		return button.getOriginalX() + button.getOriginalWidth() + TOP_BAR_GAP;
	}

	// A top bar button rightX from the right; target is its menu target while enabled.
	protected static Widget button(Widget container, FontTypeFace font, String text, String action, String target, boolean enabled,
		int rightX, Runnable onClick) {
		Widget button = ChatboxWidgets.button(container, text, BUTTON_FONT, rightX, 5, font.getTextWidth(text) + 4, LINE_HEIGHT, action,
			enabled ? onClick : null);
		if (enabled) {
			button.setName(target);
		}
		return button;
	}

	// The Settings menu's round check box (empty, or ticked green), at its own size (a smaller widget crops its bottom-right), centered in the row at y.
	// In the recycling bin, crossed instead.
	protected Widget checkbox(Widget container, boolean checked, int x, int y) {
		int spriteId = recycleBin ? RECYCLED_CHECKBOX : checked ? SpriteID.OptionsRadioButtons.CHECK_GREEN : SpriteID.OptionsRadioButtons.EMPTY;
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
	protected void editLabel(GroundMarkerPointData tile, Consumer<String> save) {
		plugin.editLabel(tile.worldPoint(), tile.getLabel(),
			tile.getColor(), save, () -> clientThread.invokeLater(() -> {
				if (chatboxPanelManager.getCurrentInput() == null) {
					reopen();
				}
			}));
	}

	// Redraws, unless another chatbox has replaced this one, e.g. after something it waited for.
	protected void updateIfOpen() {
		if (chatboxPanelManager.getCurrentInput() == this) {
			update();
		}
	}

	protected void reopen() {
		chatboxPanelManager.openInput(this);
	}

	// Sizes the table's columns: each but flex fits its heading with a sort arrow and sizedFor's values in the row font
	// (longer ones get ".."); flex takes the rest of the row, up to the scrollbar (the last column's gap is its margin). A
	// "" heading isn't shown, sized for or sortable.
	protected void columns(Widget container, String[] headings, List<List<String>> sizedFor, int flex) {
		this.headings = headings;
		font = ChatboxWidgets.font(container, widgets.fontId());
		FontTypeFace headingFont = ChatboxWidgets.font(container, FontID.QUILL_8);
		widths = new int[headings.length];
		int used = 0;
		for (int column = 0; column < headings.length; column++) {
			if (column != flex) {
				// An icon heading has the sort arrow's room only while sorted by.
				int arrow = headingIcon(column) == null || column == sortColumn ? 3 + SORT_ARROW_SIZE : 0;
				int heading = headingIcon(column) != null ? HEADING_ICON : headingFont.getTextWidth(headings[column]);
				int width = headings[column].isEmpty() ? 0 : heading + arrow;
				for (String value : sizedFor.get(column)) {
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
		@Nullable BiPredicate<Integer, Integer> fixedRow) {
		int top = headed ? RESULTS_Y + ROW_HEIGHT : RESULTS_Y;
		int visibleRows = headed ? VISIBLE_ROWS - 1 : VISIBLE_ROWS;
		scrollOffset = ChatboxWidgets.scrollList(container, top, ROW_HEIGHT, visibleRows, count, Math.max(0, count - visibleRows), scrollOffset,
			this::scroll);
		if (headed && (fixedRow == null || count == 0 || !fixedRow.test(scrollOffset, RESULTS_Y))) {
			addHeadings(container, RESULTS_Y);
		}
		for (int i = 0; i < Math.min(visibleRows, count - scrollOffset); i++) {
			addRow.accept(scrollOffset + i, top + ROW_HEIGHT * i);
		}
	}

	// A heading drawn as this icon rather than its text, which still names it ("Sort by <heading>"); null for its text.
	@Nullable
	protected Supplier<Completion.Icon> headingIcon(int column) {
		return null;
	}

	// Each heading sorts by its column, or reverses the order if it's already sorted by it.
	private void addHeadings(Widget container, int y) {
		FontTypeFace headingFont = ChatboxWidgets.font(container, FontID.QUILL_8);
		for (int column = 0; column < headings.length; column++) {
			if (headings[column].isEmpty()) {
				continue;
			}
			int sortBy = column;
			Supplier<Completion.Icon> icon = headingIcon(column);
			// The sorted column's arrow takes from its room.
			String text = icon != null ? ""
				: ChatboxWidgets.fit(headingFont, headings[column], columnWidth(column) - (column == sortColumn ? SORT_ARROW_SIZE + 3 : 0));
			if (icon != null) {
				rowIcon(container, columnX(column), y, HEADING_ICON, icon);
			}
			Widget heading = ChatboxWidgets.text(container, text, FontID.QUILL_8, 0x800000, columnX(column), y, columnWidth(column),
				ROW_HEIGHT);
			heading.setHasListener(true);
			heading.setAction(0, column != sortColumn ? "Sort by " + headings[column] : descending ? "Sort ascending" : "Sort descending");
			heading.setOnOpListener((JavaScriptCallback) ev -> {
				descending = sortBy == sortColumn && !descending;
				sortColumn = sortBy;
				update();
			});
			ChatboxWidgets.hover(heading, 0x800000);
			if (column == sortColumn) {
				Completion.Icon arrow = Completion.Icon.emoji(descending ? SORTED_DOWN : SORTED_UP);
				rowIcon(container, columnX(column) + (icon != null ? HEADING_ICON : headingFont.getTextWidth(text)) + 3, y, SORT_ARROW_SIZE,
					() -> arrow);
			}
		}
	}

	// A tooltip of long text, e.g. a label: tags removed, its lines (\n) wrapped to TIP_WIDTH px, at most TIP_LINES.
	protected String longTip(String text) {
		List<String> lines = new ArrayList<>();
		for (String line : Text.removeTags(text).split("\\\\n")) {
			lines.addAll(ChatboxWidgets.wrap(font, line, TIP_WIDTH));
		}
		return String.join("</br>", lines.subList(0, Math.min(lines.size(), TIP_LINES))) + (lines.size() > TIP_LINES ? "</br>.." : "");
	}

	// column's cell in the row at y.
	protected Rectangle cellArea(int column, int y) {
		return new Rectangle(columnX(column), y, columnWidth(column), ROW_HEIGHT);
	}

	// icon, size px square, at x in the row at y (centred down it); its area.
	protected Rectangle rowIcon(Widget container, int x, int y, int size, Supplier<Completion.Icon> icon) {
		Rectangle area = new Rectangle(x, y + (ROW_HEIGHT - size) / 2, size, size);
		iconPlacements.add(new SuggestionIconOverlay.Placement(container, area, icon));
		return area;
	}

	protected int columnX(int column) {
		int x = TABLE_X;
		for (int before = 0; before < column; before++) {
			x += widths[before];
		}
		return x;
	}

	protected int columnWidth(int column) {
		return widths[column] - COLUMN_GAP;
	}

	// text in column at y, escaped and cut to fit.
	protected Widget cell(Widget container, String text, int color, int column, int y) {
		return ChatboxWidgets.text(container, Text.escapeJagex(fit(text, columnWidth(column))), widgets.fontId(), color, columnX(column), y,
			columnWidth(column), ROW_HEIGHT);
	}

	// text in the row font, cut to fit width with "..".
	protected String fit(String text, int width) {
		return ChatboxWidgets.fit(font, text, width);
	}

	// widget's next right-click option, of actions (one list per widget), each run by its option.
	static void addAction(Widget widget, List<Runnable> actions, String name, Runnable action) {
		widget.setHasListener(true);
		widget.setAction(actions.size(), name);
		actions.add(action);
		widget.setOnOpListener((JavaScriptCallback) ev -> actions.get(ev.getOp() - 1).run());
	}

	// widget's next right-click option, opening a submenu of choices (in order, e.g. Map > On World Map); itself does
	// nothing. See menuEntryAdded().
	protected void addSubmenu(Widget widget, List<Runnable> actions, String name, Map<String, Runnable> choices) {
		addAction(widget, actions, name, () -> { });
		submenus.computeIfAbsent(widget, key -> new HashMap<>()).put(name, choices);
	}

	// From the plugin's onMenuEntryAdded while this is open: makes an addSubmenu() option's entry its submenu.
	public void menuEntryAdded(MenuEntry entry) {
		String name = Text.removeTags(entry.getOption());
		Map<String, Runnable> choices = entry.getWidget() == null ? null : submenus.getOrDefault(entry.getWidget(), Map.of()).get(name);
		if (choices == null) {
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

	protected static Widget addText(Widget container, String text, int fontId, int color, int x, int y) {
		return ChatboxWidgets.rowText(container, text, fontId, color, x, y, ROW_HEIGHT, x + ChatboxWidgets.SCROLLBAR_WIDTH + 6);
	}

	// A tooltip under area (in the chatbox) for a few seconds, e.g. why an action can't be done.
	protected void notice(Rectangle area, String text) {
		iconOverlay.notice(new SuggestionIconOverlay.Tip(chatboxPanelManager.getContainerWidget(), area, text));
	}

	protected void sendChatMessage(String message) {
		plugin.sendChatMessage(message);
	}
}
