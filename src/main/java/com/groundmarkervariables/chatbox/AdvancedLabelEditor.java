package com.groundmarkervariables.chatbox;

import com.groundmarkervariables.GroundMarkerVariablesConfig;
import com.groundmarkervariables.chatbox.completion.Completion;
import com.groundmarkervariables.chatbox.completion.LabelCompleter;
import com.groundmarkervariables.variables.label.ConditionalVariable;
import com.groundmarkervariables.variables.support.NamedColors;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;
import net.runelite.api.FontID;
import net.runelite.api.FontTypeFace;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.game.chatbox.ChatboxTextInput;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.Text;
import okhttp3.HttpUrl;

// Our own version of Ground Markers' "Tile label" editor (see GroundMarkerVariablesConfig's
// "Advanced Label Editor" option) — a ChatboxTextInput subclass overriding update() to lay
// out its own widgets, the same base way GearScape's own chatbox search input and RuneLite's
// Grand Exchange search do: prompt, then buildEdit() for the input line, then a separator.
// The input line uses RuneScape 12 (FontID.PLAIN_12) instead of the default Quill font;
// the title above it stays Quill 8, matching Ground Markers' own "Tile label" prompt.
//
// A scrollable list is shown below the separator — Current (the tile's own original label), Recent (the last few
// labels saved anywhere) and Nearby (other markers' labels within 150 tiles), see recommendations(). Recent/Nearby
// only show while the input is still unedited; once edited, untitled suggestion rows list every way to complete the
// token at the cursor (see LabelCompleter) instead. Section titles use Quill 8 like the prompt; entries are clickable: a label
// fills the input, a suggestion completes the token like Tab.
public class AdvancedLabelEditor extends ChatboxTextInput
{
	private static final int LINE_HEIGHT = 20;
	private static final int ROW_HEIGHT = 16;
	// As core's Wiki plugin opens an item or NPC by id.
	private static final HttpUrl WIKI_LOOKUP = HttpUrl.get("https://oldschool.runescape.wiki/w/Special:Lookup");
	private static final int VISIBLE_ROWS = 5;
	// Edit rows shown; longer text wraps on and scrolls (see update()). Shrunk to COMPACT_EDIT_LINES by its button.
	private static final int MAX_EDIT_LINES = 5;
	private static final int COMPACT_EDIT_LINES = 2;
	private static final int SCROLLBAR_WIDTH = ChatboxWidgets.SCROLLBAR_WIDTH;
	private static final int AUTOCOMPLETE_COLOR = 0x444444;
	private static final int GHOST_BACKGROUND_COLOR = 0xFFFFFF;
	private static final int ROW_COLOR = 0x000000;
	// The suggestion Tab would insert.
	private static final int PREFERRED_SUGGESTION_COLOR = 0x0000A0;
	// A parameter's description, shown when nothing can be suggested.
	private static final int HINT_COLOR = 0x505050;
	// Suggestions per row, and each one's icon size and gap before its name.
	private static final int SUGGESTION_COLUMNS = 3;
	private static final int ICON_WIDTH = 18;
	private static final int ICON_HEIGHT = 16;
	private static final int ICON_GAP = 2;

	// Shrink/Undo/Redo/"_"/"x", right-aligned in the prompt row.
	private static final int TOP_BAR_GAP = 4;
	private static final int CLOSE_BUTTON_WIDTH = 12;
	private static final int FONT_TOGGLE_BUTTON_WIDTH = 12;
	private static final int UNDO_BUTTON_WIDTH = 12;
	private static final int SHRINK_BUTTON_WIDTH = 12;
	// Labels this long get a ⚠️ (U+26A0) in the prompt row's left, showing the count on hover.
	private static final int LONG_LABEL = 10_000;
	private static final String WARNING_EMOJI = "26a0";
	// For underlining only — matches <col=HEX|NAME>, {col=HEX|NAME}, </col> and {/col} (no groups means </col> or
	// {/col}, both reverting to the tile's own color).
	private static final Pattern COLOR_TAG_PATTERN_ANY = Pattern.compile(
		"<col=([0-9a-fA-F]{2,6}|" + String.join("|", NamedColors.HEX_BY_NAME.keySet()) + ")>"
			+ "|\\{col=([0-9a-fA-F]{2,6}|" + String.join("|", NamedColors.HEX_BY_NAME.keySet()) + ")\\}"
			+ "|</col>|\\{/col\\}",
		Pattern.CASE_INSENSITIVE);

	// Matches ChatboxTextInput's own BREAK_MATCHER exactly — see computeWrappedLines().
	private static final Pattern BREAK_MATCHER = Pattern.compile("[^a-zA-Z0-9']");

	// One row in the list: a section title (not selectable), a label (click fills the input; only Recent ones are
	// removable, since Current/Nearby aren't backed by the saved recent-labels list), up to SUGGESTION_COLUMNS
	// suggestions (click inserts), or a line of a parameter's description (not selectable).
	@RequiredArgsConstructor
	private static final class Row
	{
		private final String text;
		private final boolean title;
		private final boolean removable;
		@Nullable
		private final List<Completion.Option> suggestions;
		// The suggestion Tab would insert, if in this row.
		@Nullable
		private final Completion.Option preferred;
		private final boolean hint;

		private static Row title(String text)
		{
			return new Row(text, true, false, null, null, false);
		}

		private static Row label(String text, boolean removable)
		{
			return new Row(text, false, removable, null, null, false);
		}

		private static Row suggestions(List<Completion.Option> suggestions, @Nullable Completion.Option preferred)
		{
			return new Row("", false, false, suggestions, preferred, false);
		}

		private static Row hint(String text)
		{
			return new Row(text, false, false, null, null, true);
		}
	}

	// One wrapped row of edit text — mirrors ChatboxTextInput's own private Line class.
	@RequiredArgsConstructor
	private static final class WrappedLine
	{
		private final int start;
		private final int end;
		private final String text;
	}

	private final ChatboxPanelManager chatboxPanelManager;
	private final GroundMarkerVariablesConfig config;
	private final ConfigManager configManager;
	private final LabelCompleter completer;
	private final SuggestionIconOverlay iconOverlay;
	private final ChatboxWidgets widgets;
	// This update's suggestion icons and tooltips, for iconOverlay.
	private final List<SuggestionIconOverlay.Placement> iconPlacements = new ArrayList<>();
	private final List<SuggestionIconOverlay.Tip> tips = new ArrayList<>();

	private String originalLabel = "";
	private List<String> recentLabels = Collections.emptyList();
	private List<String> nearbyLabels = Collections.emptyList();
	private Consumer<String> onRemoveRecent = label -> { };
	private Color tileColor = Color.YELLOW;
	private int scrollOffset;
	// List rows that fit under the edit text, up to VISIBLE_ROWS; fewer as it wraps, none with MAX_EDIT_LINES shown.
	private int visibleRows = VISIBLE_ROWS;
	// A list row's text width, for wrapping parameter descriptions; set each update.
	private int rowTextWidth = Integer.MAX_VALUE;
	// The value the list was last scrolled for: a new value starts it back at the top.
	private String scrolledValue = "";

	// The edit text's wrapped lines, those shown (its window), the first shown, and its x span; set each update.
	private int editLines = 1;
	private int editWindow = 1;
	private int editScroll;
	private int editX;
	private int editWidth;
	// Shrunk to COMPACT_EDIT_LINES rows by its button.
	private boolean compact;
	// The value and caret the window last followed: it scrolls to the caret only when they change.
	private String followedValue;
	private int followedCaret = -1;
	// The shown rows' canvas y span, for mousePressed() (input thread).
	private volatile int editTopCanvasY;
	private volatile int editBottomCanvasY;

	// -1 when no shift+Left/Right selection is in progress; otherwise the fixed end of the
	// selection (where shift was first held down) — see keyPressed() for why this is needed.
	private int shiftSelectionAnchor = -1;

	// One edit: at position, removed became inserted; and the selection before and after.
	@RequiredArgsConstructor
	private static final class Change
	{
		private final int position;
		private final String removed;
		private final String inserted;
		private final int startBefore;
		private final int endBefore;
		private final int caretAfter;
	}

	// One Ctrl+Z step: the edits made without a STEP_PAUSE_MS gap, in order.
	private static final class Step
	{
		private final List<Change> changes = new ArrayList<>();
	}

	private static final int MAX_UNDO_STEPS = 100;
	// Edits closer together than this undo together.
	private static final long STEP_PAUSE_MS = 3000;
	private final Deque<Step> undoSteps = new ArrayDeque<>();
	private final Deque<Step> redoSteps = new ArrayDeque<>();
	// The text and selection as last recorded (see recordHistory()), when it last changed, and whether the newest
	// undo step may still take edits.
	@Nullable
	private String recordedValue;
	private int recordedStart;
	private int recordedEnd;
	private long lastEditAt;
	private boolean stepOpen;

	// Lazily cached — only ever touched from update() (key-typing/click/scroll triggered,
	// all confirmed client-thread), never from mousePressed/mouseDragged. Cleared by
	// toggleFontSize() to force a re-probe against the new font.
	private FontTypeFace font;

	@Inject
	AdvancedLabelEditor(ChatboxPanelManager chatboxPanelManager, ClientThread clientThread, GroundMarkerVariablesConfig config,
		ConfigManager configManager, LabelCompleter completer, SuggestionIconOverlay iconOverlay, ChatboxWidgets widgets)
	{
		super(chatboxPanelManager, clientThread);
		this.widgets = widgets;
		this.chatboxPanelManager = chatboxPanelManager;
		this.config = config;
		this.configManager = configManager;
		this.completer = completer;
		this.iconOverlay = iconOverlay;
		fontID(FontID.PLAIN_12);
		// Unlimited: every line wraps, and update() shows a window of them.
		lines(0);
	}

	@Override
	protected void close()
	{
		super.close();
		iconOverlay.clear();
	}

	// PLAIN_11 once toggled via the "_" button, else the default PLAIN_12.
	private int editorFontId()
	{
		return widgets.fontId();
	}

	private boolean smallFont()
	{
		return widgets.smallFont();
	}

	// Called by the plugin before build() — see GroundMarkerVariablesPlugin#openLabelEditor.
	// onRemoveRecent handles right-click "Remove" on a Recent entry. tileColor is used to
	// underline </col> — see buildColorTagUnderlines().
	public AdvancedLabelEditor recommendations(String originalLabel, List<String> recentLabels, List<String> nearbyLabels,
		Consumer<String> onRemoveRecent, Color tileColor)
	{
		this.originalLabel = originalLabel == null ? "" : originalLabel;
		this.recentLabels = new ArrayList<>(recentLabels);
		this.nearbyLabels = nearbyLabels;
		this.onRemoveRecent = onRemoveRecent;
		this.tileColor = tileColor;
		completer.labels(this.recentLabels, this.nearbyLabels);
		return this;
	}

	// What completes the token at the cursor, or null (e.g. with text selected).
	@Nullable
	private Completion completion()
	{
		return getCursorStart() != getCursorEnd() ? null : completer.complete(getValue(), getCursorStart());
	}

	// The preferred completion's text: the ghost, and what Tab inserts. Null if none.
	@Nullable
	private String pendingCompletion()
	{
		Completion completion = completion();
		String insert = completion == null ? null : completion.preferredInsert();
		return insert == null || insert.isEmpty() ? null : insert;
	}

	private void insertAtCursor(String insert)
	{
		insertAtCursor(insert, 0);
	}

	// insert in place of the replace characters before the cursor.
	private void insertAtCursor(String insert, int replace)
	{
		String before = getValue().substring(0, Math.max(0, getCursorStart() - replace));
		value(before + insert + getValue().substring(getCursorEnd()));
		cursorAt(before.length() + insert.length());
	}

	// Fixes a bug in ChatboxTextInput's own shift+Left/Right handling: once a selection
	// exists, its VK_LEFT/VK_RIGHT cases unconditionally collapse to cursorStart/cursorEnd
	// first (logic meant for a plain, non-shift arrow press), so a shift-selection just snaps
	// back to the same one-character span every time instead of growing. We track our own
	// anchor here and drive cursorAt() directly, bypassing that logic entirely for this case;
	// everything else (typing, non-shift arrows, etc.) still goes through the base class.
	// Also adds Ctrl/Alt+Left/Right (plain or with shift, either modifier) to move/extend by a
	// whole word instead of one character, and Ctrl/Alt+Backspace to delete back to the
	// previous word boundary — the base class doesn't support any of this (Ctrl+anything other
	// than X/C/V/A is swallowed and does nothing, per its own keyPressed()).
	// Ctrl+Space accepts autocomplete like Tab -- its key-typed ' ' must not also insert a space.
	@Override
	public void keyTyped(KeyEvent ev)
	{
		if (ev.isControlDown() && ev.getKeyChar() == ' ')
		{
			ev.consume();
			return;
		}

		super.keyTyped(ev);
	}

	private static boolean isCtrlSpace(KeyEvent ev)
	{
		return ev.getKeyCode() == KeyEvent.VK_SPACE && ev.isControlDown();
	}

	@Override
	public void keyPressed(KeyEvent ev)
	{
		if (!chatboxPanelManager.shouldTakeInput())
		{
			return;
		}

		int code = ev.getKeyCode();

		if (ev.isControlDown() && (code == KeyEvent.VK_Z || code == KeyEvent.VK_Y))
		{
			ev.consume();
			if (code == KeyEvent.VK_Z && !ev.isShiftDown())
			{
				undoStep();
			}
			else
			{
				redoStep();
			}
			return;
		}

		if (code == KeyEvent.VK_TAB || isCtrlSpace(ev))
		{
			String completion = pendingCompletion();
			if (completion != null)
			{
				ev.consume();
				insertAtCursor(completion);
				return;
			}
		}

		boolean shift = ev.isShiftDown();
		boolean wordJump = ev.isControlDown() || ev.isAltDown();

		if (shift && (code == KeyEvent.VK_LEFT || code == KeyEvent.VK_RIGHT))
		{
			ev.consume();

			int caret;
			if (shiftSelectionAnchor == -1)
			{
				if (getCursorStart() == getCursorEnd())
				{
					shiftSelectionAnchor = getCursorStart();
					caret = shiftSelectionAnchor;
				}
				else
				{
					// An existing (mouse-drag or double-click) selection: anchor at the edge
					// opposite the direction being extended, caret at the edge being moved —
					// not just "caret = anchor", which would extend from the wrong edge.
					if (code == KeyEvent.VK_LEFT)
					{
						shiftSelectionAnchor = getCursorEnd();
						caret = getCursorStart();
					}
					else
					{
						shiftSelectionAnchor = getCursorStart();
						caret = getCursorEnd();
					}
				}
			}
			else
			{
				caret = shiftSelectionAnchor == getCursorStart() ? getCursorEnd() : getCursorStart();
			}

			if (wordJump)
			{
				caret = code == KeyEvent.VK_LEFT
					? previousWordBoundary(getValue(), caret)
					: nextWordBoundary(getValue(), caret);
			}
			else
			{
				caret += code == KeyEvent.VK_LEFT ? -1 : 1;
			}

			cursorAt(shiftSelectionAnchor, caret);
			return;
		}

		// Ctrl/Alt+Left/Right with no shift -- move by a whole word instead of one character.
		// Collapses from whichever edge is being moved toward, same as a plain arrow would.
		if (!shift && wordJump && (code == KeyEvent.VK_LEFT || code == KeyEvent.VK_RIGHT))
		{
			shiftSelectionAnchor = -1;
			cursorAt(code == KeyEvent.VK_LEFT
				? previousWordBoundary(getValue(), getCursorStart())
				: nextWordBoundary(getValue(), getCursorEnd()));
			return;
		}

		// Ctrl/Alt+Backspace -- delete the selection if one exists, otherwise delete back to
		// the previous word boundary.
		if (wordJump && code == KeyEvent.VK_BACK_SPACE)
		{
			ev.consume();
			shiftSelectionAnchor = -1;
			String text = getValue();
			int start = getCursorStart();
			int end = getCursorEnd();
			if (start == end)
			{
				start = previousWordBoundary(text, start);
			}
			value(text.substring(0, start) + text.substring(end));
			cursorAt(start);
			return;
		}

		if (!shift)
		{
			shiftSelectionAnchor = -1;
		}

		super.keyPressed(ev);
	}

	// Double-click selects the word under the cursor, triple-click the whole {...} token
	// (braces included), quadruple-click (or more) the entire label — the base class has none
	// of these.
	@Override
	public MouseEvent mouseClicked(MouseEvent mouseEvent)
	{
		super.mouseClicked(mouseEvent);

		if (mouseEvent.getButton() == MouseEvent.BUTTON1)
		{
			int clicks = mouseEvent.getClickCount();
			if (clicks >= 4)
			{
				selectAll();
			}
			else if (clicks == 3)
			{
				selectTokenAt(getCursorStart());
			}
			else if (clicks == 2)
			{
				selectWordAt(getCursorStart());
			}
		}

		return mouseEvent;
	}

	// The outermost complete {...} token around pos, at any nesting depth (an unclosed "{" is skipped); the entire
	// label if none. A brace scan, not a regex: any token length works.
	private void selectTokenAt(int pos)
	{
		String text = getValue();
		int i = 0;
		while (i < text.length())
		{
			if (text.charAt(i) != '{')
			{
				i++;
				continue;
			}
			int close = ConditionalVariable.matchingBrace(text, i);
			if (close < 0)
			{
				i++;
				continue;
			}
			if (pos >= i && pos <= close + 1)
			{
				shiftSelectionAnchor = -1;
				cursorAt(i, close + 1);
				return;
			}
			i = close + 1;
		}

		selectAll();
	}

	private void selectAll()
	{
		shiftSelectionAnchor = -1;
		cursorAt(0, getValue().length());
	}

	private void selectWordAt(int pos)
	{
		String text = getValue();
		if (text.isEmpty())
		{
			return;
		}

		// The literal "\n" line-break escape is its own word, not split across the "\" (not
		// alphanumeric) and "n" (alphanumeric) categories below.
		for (int start = Math.max(0, pos - 2); start <= Math.min(pos, text.length() - 2); start++)
		{
			if (text.startsWith("\\n", start))
			{
				shiftSelectionAnchor = -1;
				cursorAt(start, start + 2);
				return;
			}
		}

		// pos may be at the very end (no character there) — fall back to the last
		// character's category so double-clicking past the last word still selects it.
		boolean alphanumeric = Character.isLetterOrDigit(text.charAt(Math.min(pos, text.length() - 1)));

		int start = pos;
		while (start > 0 && Character.isLetterOrDigit(text.charAt(start - 1)) == alphanumeric)
		{
			start--;
		}

		int end = pos;
		while (end < text.length() && Character.isLetterOrDigit(text.charAt(end)) == alphanumeric)
		{
			end++;
		}

		shiftSelectionAnchor = -1;
		cursorAt(start, end);
	}

	// A "word" is a single run of one category — alphanumeric, or not — starting from
	// whichever character sits adjacent to pos in the direction of travel. E.g. in
	// "hasFreeze && weapon", moving left from the end stops after "weapon" (alphanumeric
	// run), then after " && " (one non-alphanumeric run: space, &, &, space together),
	// then at the very start (the rest of "hasFreeze").
	private static int previousWordBoundary(String value, int pos)
	{
		if (pos <= 0)
		{
			return pos;
		}

		boolean alphanumeric = Character.isLetterOrDigit(value.charAt(pos - 1));
		int i = pos;
		while (i > 0 && Character.isLetterOrDigit(value.charAt(i - 1)) == alphanumeric)
		{
			i--;
		}
		return i;
	}

	private static int nextWordBoundary(String value, int pos)
	{
		int len = value.length();
		if (pos >= len)
		{
			return pos;
		}

		boolean alphanumeric = Character.isLetterOrDigit(value.charAt(pos));
		int i = pos;
		while (i < len && Character.isLetterOrDigit(value.charAt(i)) == alphanumeric)
		{
			i++;
		}
		return i;
	}

	// Absolute Y of the top of wrapped edit row `lineIndex` (0-based).
	private static int editRowY(int lineIndex)
	{
		return 5 + LINE_HEIGHT + (LINE_HEIGHT * lineIndex);
	}

	// Whether wrapped line lineIndex is in the shown window.
	private boolean shown(int lineIndex)
	{
		return lineIndex >= editScroll && lineIndex < editScroll + editWindow;
	}

	// Scrolls the window to the caret's line when the value or caret changed since last time, else keeps it in range.
	private void followCaret(List<WrappedLine> wrappedLines)
	{
		int caret = getCursorEnd();
		if (!getValue().equals(followedValue) || caret != followedCaret)
		{
			followedValue = getValue();
			followedCaret = caret;
			int line = findLineIndex(wrappedLines, caret);
			if (line < editScroll)
			{
				editScroll = line;
			}
			else if (line >= editScroll + editWindow)
			{
				editScroll = line - editWindow + 1;
			}
		}
		editScroll = Math.max(0, Math.min(editScroll, editLines - editWindow));
	}

	// Hides buildEdit()'s widgets (from index from on) outside the shown rows.
	private void hideOutsideWindow(Widget container, int from)
	{
		Widget[] children = container.getDynamicChildren();
		for (int i = from; i < children.length; i++)
		{
			int y = children[i].getOriginalY();
			if (y < editRowY(0) || y >= editRowY(editWindow))
			{
				children[i].setHidden(true);
			}
		}
	}

	// With more lines than shown: the wheel over the edit rows scrolls them, and a scrollbar shows where they are.
	private void buildEditScrolling(Widget container)
	{
		int maxScroll = editLines - editWindow;
		if (maxScroll <= 0)
		{
			return;
		}

		ChatboxWidgets.scrollCapture(container, editRowY(0), editWindow * LINE_HEIGHT, this::scrollEdit);
		ChatboxWidgets.scrollbar(container, editRowY(0), editWindow * LINE_HEIGHT, editScroll, maxScroll, editWindow, editLines,
			this::scrollEdit);
	}

	private void scrollEdit(int direction)
	{
		editScroll = Math.max(0, Math.min(editScroll + direction, editLines - editWindow));
		update();
	}

	// The shrink button: COMPACT_EDIT_LINES rows, or back to MAX_EDIT_LINES, showing the caret's line.
	private void toggleCompact()
	{
		compact = !compact;
		followedCaret = -1;
		update();
	}

	// Core's click area covers every wrapped line, hidden ones too (over the top bar and the list): presses outside
	// the shown rows aren't for the text.
	@Override
	public MouseEvent mousePressed(MouseEvent mouseEvent)
	{
		if (mouseEvent.getY() < editTopCanvasY || mouseEvent.getY() >= editBottomCanvasY)
		{
			return mouseEvent;
		}
		return super.mousePressed(mouseEvent);
	}

	// Renders the rest of a variable name as ghost text right after the caret — display-only,
	// never inserted into the real value. Caret may be mid-value now, so its row/X come from
	// findLineIndex the same way buildColorTagUnderlines locates a match.
	private void buildAutocomplete(Widget container, List<WrappedLine> wrappedLines)
	{
		String completion = pendingCompletion();
		if (completion == null)
		{
			return;
		}

		int cursor = getCursorStart();
		int lineIndex = findLineIndex(wrappedLines, cursor);
		if (!shown(lineIndex))
		{
			return;
		}
		WrappedLine line = wrappedLines.get(lineIndex);
		int localCursor = cursor - line.start;

		String escapedCompletion = Text.escapeJagex(completion);
		int lineFullWidth = font.getTextWidth(Text.escapeJagex(line.text));
		int lineStartX = editX + (editWidth - lineFullWidth) / 2;
		int ghostX = lineStartX + font.getTextWidth(Text.escapeJagex(line.text.substring(0, localCursor)));
		int ghostWidth = font.getTextWidth(escapedCompletion);
		int ghostY = editRowY(lineIndex - editScroll) + ghostYOffset();

		// No native rounded-rect widget exists, so this is a plain filled backing behind the
		// ghost text — same technique buildEdit() itself uses for its selection highlight. Only
		// needed when there's real text after the cursor on this row to stay legible over.
		if (localCursor < line.text.length())
		{
			ChatboxWidgets.rect(container, GHOST_BACKGROUND_COLOR, ghostX - 1, ghostY, ghostWidth + 2, LINE_HEIGHT);
		}

		ChatboxWidgets.text(container, escapedCompletion, editorFontId(), AUTOCOMPLETE_COLOR, ghostX, ghostY, ghostWidth, LINE_HEIGHT);
	}

	// Empirically tuned — PLAIN_11 needs 2px more than PLAIN_12.
	private int ghostYOffset()
	{
		return smallFont() ? -4 : -2;
	}

	// Underlines any complete <col=RRGGBB> or <col=name> tag in the label, in the color it
	// names. A tag split across a wrapped line break (rare) is skipped rather than drawn wrong.
	private void buildColorTagUnderlines(Widget container, List<WrappedLine> wrappedLines)
	{
		String text = getValue();
		Matcher matcher = COLOR_TAG_PATTERN_ANY.matcher(text);
		if (!matcher.find())
		{
			return;
		}

		matcher.reset();
		while (matcher.find())
		{
			int lineIndex = findLineIndex(wrappedLines, matcher.start());
			WrappedLine line = wrappedLines.get(lineIndex);
			if (matcher.end() - 1 > line.end || !shown(lineIndex))
			{
				continue;
			}

			String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);

			int color;
			if (value == null)
			{
				// </col> — the tile's own current color, always fully opaque.
				color = tileColor.getRGB() & 0xFFFFFF;
			}
			else
			{
				String hex = NamedColors.HEX_BY_NAME.getOrDefault(value.toLowerCase(), value);
				try
				{
					color = Integer.parseInt(hex, 16);
				}
				catch (NumberFormatException e)
				{
					continue;
				}
			}

			int lineFullWidth = font.getTextWidth(Text.escapeJagex(line.text));
			int lineStartX = editX + (editWidth - lineFullWidth) / 2;
			int localStart = matcher.start() - line.start;
			int localEnd = matcher.end() - line.start;
			int startX = lineStartX + font.getTextWidth(Text.escapeJagex(line.text.substring(0, localStart)));
			int endX = lineStartX + font.getTextWidth(Text.escapeJagex(line.text.substring(0, localEnd)));

			ChatboxWidgets.rect(container, color, startX, editRowY(lineIndex - editScroll) + LINE_HEIGHT - 3, Math.max(1, endX - startX), 1);
		}
	}

	// Mirrors ChatboxTextInput#buildEdit()'s own word-wrap algorithm, since it's computed
	// internally there and never exposed — needed for our own separator/ghost/underline layout.
	private List<WrappedLine> computeWrappedLines(String value, int w)
	{
		List<WrappedLine> result = new ArrayList<>();
		if (value.isEmpty())
		{
			result.add(new WrappedLine(0, 0, ""));
			return result;
		}

		int breakIndex = -1;
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < value.length(); i++)
		{
			int count = i - sb.length();
			char c = value.charAt(i);
			sb.append(c);
			if (BREAK_MATCHER.matcher(String.valueOf(c)).matches())
			{
				breakIndex = sb.length();
			}

			if (i == value.length() - 1)
			{
				result.add(new WrappedLine(count, count + sb.length() - 1, sb.toString()));
				break;
			}

			// Raw, matching core's own buildEdit() exactly — a raw <col=...> substring measures
			// as near-zero-width Jagex markup there too, so a mismatch here (e.g. escaping)
			// would make our predicted wrap points diverge from what's actually rendered.
			if (font.getTextWidth(sb.toString() + value.charAt(i + 1)) < w)
			{
				continue;
			}

			if (breakIndex > 1)
			{
				String str = sb.substring(0, breakIndex);
				result.add(new WrappedLine(count, count + str.length() - 1, str));
				sb.replace(0, breakIndex, "");
				breakIndex = -1;
				continue;
			}

			result.add(new WrappedLine(count, count + sb.length() - 1, sb.toString()));
			sb.replace(0, sb.length(), "");
		}

		return result;
	}

	private static int findLineIndex(List<WrappedLine> lines, int charIndex)
	{
		for (int i = 0; i < lines.size(); i++)
		{
			WrappedLine line = lines.get(i);
			if (charIndex >= line.start && charIndex <= line.end)
			{
				return i;
			}
		}

		return lines.size() - 1;
	}

	@Override
	protected void update()
	{
		recordHistory();
		Widget container = chatboxPanelManager.getContainerWidget();
		container.deleteAllChildren();
		iconPlacements.clear();
		tips.clear();

		ChatboxWidgets.centeredText(container, getPrompt(), FontID.QUILL_8, 0x800000, 5, LINE_HEIGHT);

		if (font == null)
		{
			font = ChatboxWidgets.font(container, editorFontId());
		}

		// Every line wraps; a window of them shows. When it can scroll, a scrollbar takes the right margin, and the
		// centered text gives up both.
		List<WrappedLine> wrappedLines = computeWrappedLines(getValue(), container.getWidth());
		int window = Math.min(wrappedLines.size(), compact ? COMPACT_EDIT_LINES : MAX_EDIT_LINES);
		editX = wrappedLines.size() > window ? SCROLLBAR_WIDTH : 0;
		editWidth = container.getWidth() - 2 * editX;
		if (editX > 0)
		{
			wrappedLines = computeWrappedLines(getValue(), editWidth);
		}
		editLines = wrappedLines.size();
		editWindow = window;
		followCaret(wrappedLines);

		buildTopBar(container);
		widgets.logo(container, iconPlacements, this);
		buildLengthWarning(container);

		fontID(editorFontId());
		int before = container.getDynamicChildren().length;
		buildEdit(editX, editRowY(-editScroll), editWidth, LINE_HEIGHT);
		hideOutsideWindow(container, before);
		buildAutocomplete(container, wrappedLines);
		buildColorTagUnderlines(container, wrappedLines);
		buildEditScrolling(container);

		int canvasY = container.getCanvasLocation().getY();
		editTopCanvasY = canvasY + editRowY(0);
		editBottomCanvasY = canvasY + editRowY(window);

		int separatorY = editRowY(window) - 1;
		int resultsStartY = separatorY + 8;
		// The list ends where all VISIBLE_ROWS end under one edit line.
		int listBottom = editRowY(1) + 7 + VISIBLE_ROWS * ROW_HEIGHT;
		visibleRows = Math.max(0, Math.min(VISIBLE_ROWS, (listBottom - resultsStartY) / ROW_HEIGHT));
		if (visibleRows == 0)
		{
			iconOverlay.show(iconPlacements, tips);
			return;
		}

		ChatboxWidgets.separator(container, separatorY);

		// As a label row's text: from x 13 to the scrollbar.
		rowTextWidth = container.getWidth() - 13 - SCROLLBAR_WIDTH - 6;
		buildRecommendations(container, resultsStartY);
		iconOverlay.show(iconPlacements, tips);
	}

	// "<" undo (grayed out with nothing to undo), ">" redo (only while there's something to redo), "_" (font size
	// toggle) and "x" (close without saving), right-aligned in the prompt row.
	private void buildTopBar(Widget container)
	{
		int rightX = TOP_BAR_GAP;
		rightX = addTopBarButton(container, "x", rightX, CLOSE_BUTTON_WIDTH, "Close", chatboxPanelManager::close);
		rightX = addTopBarButton(container, "_", rightX, FONT_TOGGLE_BUTTON_WIDTH, "Toggle font size", this::toggleFontSize);

		// ">" only while there's something to redo: a new edit clears it.
		if (!redoSteps.isEmpty())
		{
			rightX = addTopBarButton(container, Text.escapeJagex(">"), rightX, UNDO_BUTTON_WIDTH, "Redo", this::redoStep);
		}

		// Escaped — a lone "<" is parsed as the start of an inline tag and never drawn otherwise.
		rightX = addTopBarButton(container, Text.escapeJagex("<"), rightX, UNDO_BUTTON_WIDTH, "Undo",
			undoSteps.isEmpty() ? null : this::undoStep);

		// From 3 wrapped lines: "-" shrinks the text to COMPACT_EDIT_LINES rows, "+" brings back MAX_EDIT_LINES.
		if (editLines >= 3)
		{
			addTopBarButton(container, compact ? "+" : "-", rightX, SHRINK_BUTTON_WIDTH, compact ? "Expand" : "Shrink", this::toggleCompact);
		}
	}

	// ⚠️ right of the logo once the label reaches LONG_LABEL characters, its tooltip the count used.
	private void buildLengthWarning(Widget container)
	{
		int length = getValue().length();
		if (length < LONG_LABEL)
		{
			return;
		}
		Rectangle area = new Rectangle(ChatboxWidgets.LOGO_RIGHT, 5 + (LINE_HEIGHT - ICON_HEIGHT) / 2, ICON_HEIGHT, ICON_HEIGHT);
		iconPlacements.add(new SuggestionIconOverlay.Placement(container, area, Completion.Icon.emoji(WARNING_EMOJI)));
		tips.add(new SuggestionIconOverlay.Tip(container, area, String.format("%,d characters used", length)));
	}

	// Returns the next button's rightX.
	private int addTopBarButton(Widget container, String text, int rightX, int width, String action, @Nullable Runnable onClick)
	{
		ChatboxWidgets.button(container, text, FontID.BOLD_12, rightX, 5, width, LINE_HEIGHT, action, onClick);
		return rightX + width + TOP_BAR_GAP;
	}

	// Every edit ends in update(), so it's recorded here as just what changed: added to the newest undo step if it's
	// within STEP_PAUSE_MS of the last edit, else starting a new one.
	private void recordHistory()
	{
		String value = getValue();
		int start = getCursorStart();
		int end = getCursorEnd();
		String before = recordedValue;
		int startBefore = recordedStart;
		int endBefore = recordedEnd;
		recordedValue = value;
		recordedStart = start;
		recordedEnd = end;
		if (before == null || before.equals(value))
		{
			return;
		}

		// The changed middle: what's left after the shared start and end.
		int prefix = 0;
		int max = Math.min(before.length(), value.length());
		while (prefix < max && before.charAt(prefix) == value.charAt(prefix))
		{
			prefix++;
		}
		int suffix = 0;
		while (suffix < max - prefix && before.charAt(before.length() - 1 - suffix) == value.charAt(value.length() - 1 - suffix))
		{
			suffix++;
		}
		Change change = new Change(prefix, before.substring(prefix, before.length() - suffix),
			value.substring(prefix, value.length() - suffix), startBefore, endBefore, end);

		long time = System.currentTimeMillis();
		if (!stepOpen || undoSteps.isEmpty() || time - lastEditAt >= STEP_PAUSE_MS)
		{
			undoSteps.push(new Step());
			if (undoSteps.size() > MAX_UNDO_STEPS)
			{
				undoSteps.removeLast();
			}
		}
		undoSteps.peek().changes.add(change);
		redoSteps.clear();
		stepOpen = true;
		lastEditAt = time;
	}

	// Each of the step's edits reversed, last first; the selection back to before its first.
	private void undoStep()
	{
		Step step = undoSteps.poll();
		if (step != null)
		{
			String value = getValue();
			for (int i = step.changes.size() - 1; i >= 0; i--)
			{
				Change change = step.changes.get(i);
				value = value.substring(0, change.position) + change.removed + value.substring(change.position + change.inserted.length());
			}
			Change first = step.changes.get(0);
			apply(value, first.startBefore, first.endBefore);
			redoSteps.push(step);
		}
	}

	// The step's edits again, in order; the caret where its last left it.
	private void redoStep()
	{
		Step step = redoSteps.poll();
		if (step != null)
		{
			String value = getValue();
			for (Change change : step.changes)
			{
				value = value.substring(0, change.position) + change.inserted + value.substring(change.position + change.removed.length());
			}
			int caret = step.changes.get(step.changes.size() - 1).caretAfter;
			apply(value, caret, caret);
			undoSteps.push(step);
		}
	}

	// Recorded first, so undoing or redoing isn't itself recorded as an edit. The next edit starts a new step.
	private void apply(String value, int start, int end)
	{
		recordedValue = value;
		recordedStart = start;
		recordedEnd = end;
		stepOpen = false;
		shiftSelectionAnchor = -1;
		value(value);
		cursorAt(start, end);
	}

	private void toggleFontSize()
	{
		widgets.toggleFontSize();
		font = null;
		update();
	}

	private List<Row> buildRows()
	{
		List<Row> rows = new ArrayList<>();
		boolean hasCurrent = !originalLabel.isEmpty();
		boolean edited = !getValue().equals(originalLabel);

		// Once edited, Recent/Nearby no longer apply; what can complete the token at the cursor does.
		if (edited && config.showSuggestions())
		{
			addSuggestions(rows);
		}

		if (hasCurrent && config.showCurrent())
		{
			rows.add(Row.title("Current"));
			rows.add(Row.label(originalLabel, false));
		}

		if (!edited)
		{
			// Skip anything already shown as Current, so the same label isn't listed twice.
			// Left empty when Show Recent is off, so Nearby's own dedup below naturally excludes
			// nothing from Recent (there's nothing to exclude if Recent isn't shown at all).
			List<String> dedupedRecent = new ArrayList<>();
			if (config.showRecent())
			{
				for (String label : recentLabels)
				{
					if (!hasCurrent || !label.equals(originalLabel))
					{
						dedupedRecent.add(label);
					}
				}

				if (!dedupedRecent.isEmpty())
				{
					rows.add(Row.title("Recent"));
					for (String label : dedupedRecent)
					{
						rows.add(Row.label(label, true));
					}
				}
			}

			// Skip anything already shown as Current or Recent.
			if (config.showNearby())
			{
				List<String> dedupedNearby = new ArrayList<>();
				for (String label : nearbyLabels)
				{
					if ((!hasCurrent || !label.equals(originalLabel)) && !dedupedRecent.contains(label))
					{
						dedupedNearby.add(label);
					}
				}

				if (!dedupedNearby.isEmpty())
				{
					rows.add(Row.title("Nearby"));
					for (String label : dedupedNearby)
					{
						rows.add(Row.label(label, false));
					}
				}
			}
		}

		return rows;
	}

	// A variable's exact name first gets its own line, or a required value being typed its parameter's. Then every
	// listed completion of the token at the cursor, SUGGESTION_COLUMNS to a row, the one Tab would insert marked; with
	// none, what the variable takes there.
	private void addSuggestions(List<Row> rows)
	{
		boolean caret = getCursorStart() == getCursorEnd();
		if (caret)
		{
			String summary = completer.variableSummary(getValue(), getCursorStart());
			String top = summary != null ? summary : completer.valueHint(getValue(), getCursorStart());
			if (top != null)
			{
				addHint(rows, top);
			}
		}

		Completion completion = completion();
		if (completion == null || !completion.hasListed())
		{
			if (caret)
			{
				completer.parameterHints(getValue(), getCursorStart()).forEach(hint -> addHint(rows, hint));
			}
			return;
		}

		Completion.Option preferred = completion.preferred < 0 ? null : completion.options.get(completion.preferred);
		List<Completion.Option> listed = new ArrayList<>();
		for (Completion.Option option : completion.options)
		{
			if (option == Completion.BREAK)
			{
				addSuggestionRows(rows, listed, preferred);
				rows.add(Row.hint(""));
				listed = new ArrayList<>();
			}
			else if (option.label != null)
			{
				listed.add(option);
			}
		}
		addSuggestionRows(rows, listed, preferred);
	}

	private static void addSuggestionRows(List<Row> rows, List<Completion.Option> listed, @Nullable Completion.Option preferred)
	{
		for (int i = 0; i < listed.size(); i += SUGGESTION_COLUMNS)
		{
			List<Completion.Option> row = listed.subList(i, Math.min(i + SUGGESTION_COLUMNS, listed.size()));
			rows.add(Row.suggestions(row, row.contains(preferred) ? preferred : null));
		}
	}

	private void buildRecommendations(Widget container, int resultsStartY)
	{
		List<Row> rows = buildRows();
		if (rows.isEmpty())
		{
			return;
		}

		if (!getValue().equals(scrolledValue))
		{
			scrolledValue = getValue();
			scrollOffset = 0;
		}
		int maxOffset = Math.max(0, rows.size() - visibleRows + 1);
		scrollOffset = Math.max(0, Math.min(scrollOffset, maxOffset));

		ChatboxWidgets.scrollCapture(container, resultsStartY, visibleRows * ROW_HEIGHT, this::scroll);

		int visibleCount = Math.min(visibleRows, rows.size() - scrollOffset);
		for (int i = 0; i < visibleCount; i++)
		{
			Row row = rows.get(scrollOffset + i);
			int y = resultsStartY + (ROW_HEIGHT * i);
			if (row.suggestions != null)
			{
				buildSuggestionRow(container, row, y);
				continue;
			}
			int color = row.title ? 0x800000 : row.hint ? HINT_COLOR : ROW_COLOR;

			// Escaped so a recommendation containing our own <col=> label syntax (or any other
			// Jagex markup) shows as literal text instead of actually being applied.
			Widget text = ChatboxWidgets.rowText(container, row.title ? row.text : Text.escapeJagex(row.text),
				row.title ? FontID.QUILL_8 : editorFontId(), color, row.title ? 6 : 13, y, ROW_HEIGHT, SCROLLBAR_WIDTH + 6);

			if (!row.title && !row.hint)
			{
				addRowActions(text, row, color);
			}
		}

		ChatboxWidgets.scrollbar(container, resultsStartY, visibleRows * ROW_HEIGHT, scrollOffset, maxOffset, visibleRows, rows.size(),
			this::scroll);
	}

	// A label fills the input; a Recent one can be removed.
	private void addRowActions(Widget text, Row row, int color)
	{
		text.setHasListener(true);
		// Expanded so the tooltip's <col=> handling gets real hex, not a named color.
		text.setName(NamedColors.expandColorAliases(row.text));
		String selected = row.text;
		text.setAction(0, "Select");
		if (row.removable)
		{
			text.setAction(1, "Remove");
		}
		text.setOnOpListener((JavaScriptCallback) ev ->
		{
			if (ev.getOp() == 2)
			{
				removeRecentEntry(selected);
			}
			else
			{
				value(selected);
			}
		});
		ChatboxWidgets.hover(text, color);
	}

	// Each suggestion in its column: its icon if it has one, then its name cut to fit. Clicking either inserts it.
	private void buildSuggestionRow(Widget container, Row row, int y)
	{
		int left = 13;
		int columnWidth = (container.getWidth() - left - SCROLLBAR_WIDTH - 6) / SUGGESTION_COLUMNS;
		for (int column = 0; column < row.suggestions.size(); column++)
		{
			Completion.Option option = row.suggestions.get(column);
			int x = left + column * columnWidth;
			// After the icon's space whether or not there's an icon, so names line up.
			int textX = x + ICON_WIDTH + ICON_GAP;
			Completion.Icon icon = option.icon == null ? null : completer.icon(option.icon);
			if (icon != null)
			{
				// Empty: SuggestionIconOverlay draws the icon over it, smoothly scaled.
				addInsertAction(ChatboxWidgets.child(container, WidgetType.GRAPHIC, x, y, ICON_WIDTH, ICON_HEIGHT), option, icon);
				iconPlacements.add(new SuggestionIconOverlay.Placement(container, new Rectangle(x, y, ICON_WIDTH, ICON_HEIGHT), icon));
			}

			if (option.description != null)
			{
				tips.add(new SuggestionIconOverlay.Tip(container, new Rectangle(x, y, columnWidth, ROW_HEIGHT), option.description));
			}

			int color = option == row.preferred ? PREFERRED_SUGGESTION_COLOR : ROW_COLOR;
			int textWidth = x + columnWidth - textX - 2;
			Widget text = ChatboxWidgets.text(container, Text.escapeJagex(fit(option.label, textWidth)), editorFontId(), color, textX, y,
				textWidth, ROW_HEIGHT);
			addInsertAction(text, option, icon);
			ChatboxWidgets.hover(text, color);
		}
	}

	// "Insert": completes the token at the cursor with option. Its full name shows on hover. A named item or NPC
	// also gets "Wiki".
	private void addInsertAction(Widget widget, Completion.Option option, @Nullable Completion.Icon icon)
	{
		widget.setHasListener(true);
		widget.setName(NamedColors.expandColorAliases(option.label));
		widget.setAction(0, "Insert");
		boolean named = option.icon != null && (option.icon.startsWith("<item=") || option.icon.startsWith("<npc="));
		String wikiType = icon == null || !named ? null : icon.kind == Completion.Icon.Kind.ITEM ? "item"
			: icon.kind == Completion.Icon.Kind.NPC || icon.kind == Completion.Icon.Kind.NPC_HEAD ? "npc" : null;
		if (wikiType != null)
		{
			widget.setAction(1, "Wiki");
		}
		widget.setOnOpListener((JavaScriptCallback) ev ->
		{
			if (wikiType != null && ev.getOp() == 2)
			{
				LinkBrowser.browse(WIKI_LOOKUP.newBuilder()
					.addQueryParameter("type", wikiType)
					.addQueryParameter("id", icon.id)
					.build().toString());
			}
			else
			{
				insertAtCursor(option.insert, option.replace);
			}
		});
	}

	// hint as rows, wrapped to the list's width.
	private void addHint(List<Row> rows, String hint)
	{
		wrap(hint, rowTextWidth).forEach(line -> rows.add(Row.hint(line)));
	}

	// text split at spaces into lines of at most width px in the editor font (a longer word stays whole).
	private List<String> wrap(String text, int width)
	{
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" "))
		{
			String next = line.length() == 0 ? word : line + " " + word;
			if (line.length() > 0 && font != null && font.getTextWidth(Text.escapeJagex(next)) > width)
			{
				lines.add(line.toString());
				line = new StringBuilder(word);
			}
			else
			{
				line = new StringBuilder(next);
			}
		}
		lines.add(line.toString());
		return lines;
	}

	// text cut short with ".." to fit width px in the editor font.
	private String fit(String text, int width)
	{
		if (font.getTextWidth(Text.escapeJagex(text)) <= width)
		{
			return text;
		}
		String cut = text;
		while (!cut.isEmpty() && font.getTextWidth(Text.escapeJagex(cut + "..")) > width)
		{
			cut = cut.substring(0, cut.length() - 1);
		}
		return cut + "..";
	}

	private void removeRecentEntry(String label)
	{
		recentLabels.remove(label);
		onRemoveRecent.accept(label);
		update();
	}

	private void scroll(int direction)
	{
		int maxOffset = Math.max(0, buildRows().size() - visibleRows + 1);
		scrollOffset = Math.max(0, Math.min(scrollOffset + direction, maxOffset));
		update();
	}
}
