package com.groundmarkervariables.chatbox;

import com.groundmarkervariables.GroundMarkerVariablesConfig;
import com.groundmarkervariables.chatbox.completion.Completion;
import com.groundmarkervariables.chatbox.completion.LabelCompleter;
import com.groundmarkervariables.variables.support.NamedColors;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import net.runelite.api.FontID;
import net.runelite.api.FontTypeFace;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.game.chatbox.ChatboxTextInput;
import net.runelite.client.util.Text;

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
	private static final int VISIBLE_ROWS = 5;
	// Caps ChatboxTextInput's own word-wrap (see lines() in the constructor) — also mirrored
	// by computeWrappedLines(), since the wrapped row count isn't otherwise exposed.
	private static final int MAX_EDIT_LINES = 5;
	private static final int SCROLLBAR_WIDTH = 14;
	private static final int SCROLLBAR_ARROW_HEIGHT = 12;
	private static final int SCROLLBAR_TRACK_WIDTH = 4;
	private static final int SCROLLBAR_THUMB_MIN_HEIGHT = 6;
	private static final int HOVER_COLOR = 0x444444;
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

	// Undo/"_"/"x", right-aligned in the prompt row.
	private static final int TOP_BAR_GAP = 4;
	private static final int CLOSE_BUTTON_WIDTH = 12;
	private static final int FONT_TOGGLE_BUTTON_WIDTH = 12;
	private static final int UNDO_BUTTON_WIDTH = 12;

	// For underlining only — matches <col=HEX|NAME>, {col=HEX|NAME}, </col> and {/col} (no groups means </col> or
	// {/col}, both reverting to the tile's own color).
	private static final Pattern COLOR_TAG_PATTERN_ANY = Pattern.compile(
		"<col=([0-9a-fA-F]{2,6}|" + String.join("|", NamedColors.HEX_BY_NAME.keySet()) + ")>"
			+ "|\\{col=([0-9a-fA-F]{2,6}|" + String.join("|", NamedColors.HEX_BY_NAME.keySet()) + ")\\}"
			+ "|</col>|\\{/col\\}",
		Pattern.CASE_INSENSITIVE);

	// Matches ChatboxTextInput's own BREAK_MATCHER exactly — see computeWrappedLines().
	private static final Pattern BREAK_MATCHER = Pattern.compile("[^a-zA-Z0-9']");

	// Every complete {...} token, up to one level of nesting — used by selectTokenAt().
	private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{(?:[^{}]|\\{[^{}]*\\})*\\}");

	// One row in the list: a section title (not selectable), a label (click fills the input; only Recent ones are
	// removable, since Current/Nearby aren't backed by the saved recent-labels list), up to SUGGESTION_COLUMNS
	// suggestions (click inserts), or a line of a parameter's description (not selectable).
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

		private Row(String text, boolean title, boolean removable, @Nullable List<Completion.Option> suggestions,
			@Nullable Completion.Option preferred, boolean hint)
		{
			this.text = text;
			this.title = title;
			this.removable = removable;
			this.suggestions = suggestions;
			this.preferred = preferred;
			this.hint = hint;
		}

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
	private static final class WrappedLine
	{
		private final int start;
		private final int end;
		private final String text;

		private WrappedLine(int start, int end, String text)
		{
			this.start = start;
			this.end = end;
			this.text = text;
		}
	}

	private final ChatboxPanelManager chatboxPanelManager;
	private final GroundMarkerVariablesConfig config;
	private final ConfigManager configManager;
	private final LabelCompleter completer;
	private final SuggestionIconOverlay iconOverlay;
	// This update's suggestion icons and tooltips, for iconOverlay.
	private final List<SuggestionIconOverlay.Placement> iconPlacements = new ArrayList<>();
	private final List<SuggestionIconOverlay.Tip> tips = new ArrayList<>();

	private String originalLabel = "";
	private List<String> recentLabels = Collections.emptyList();
	private List<String> nearbyLabels = Collections.emptyList();
	private Consumer<String> onRemoveRecent = label -> { };
	private Color tileColor = Color.YELLOW;
	private int scrollOffset;
	// List rows that fit under the edit text, up to VISIBLE_ROWS; fewer as it wraps, none at MAX_EDIT_LINES.
	private int visibleRows = VISIBLE_ROWS;
	// A list row's text width, for wrapping parameter descriptions; set each update.
	private int rowTextWidth = Integer.MAX_VALUE;
	// The value the list was last scrolled for: a new value starts it back at the top.
	private String scrolledValue = "";

	// -1 when no shift+Left/Right selection is in progress; otherwise the fixed end of the
	// selection (where shift was first held down) — see keyPressed() for why this is needed.
	private int shiftSelectionAnchor = -1;

	// Lazily cached — only ever touched from update() (key-typing/click/scroll triggered,
	// all confirmed client-thread), never from mousePressed/mouseDragged. Cleared by
	// toggleFontSize() to force a re-probe against the new font.
	private FontTypeFace font;

	@Inject
	AdvancedLabelEditor(ChatboxPanelManager chatboxPanelManager, ClientThread clientThread, GroundMarkerVariablesConfig config,
		ConfigManager configManager, LabelCompleter completer, SuggestionIconOverlay iconOverlay)
	{
		super(chatboxPanelManager, clientThread);
		this.chatboxPanelManager = chatboxPanelManager;
		this.config = config;
		this.configManager = configManager;
		this.completer = completer;
		this.iconOverlay = iconOverlay;
		fontID(FontID.PLAIN_12);
		lines(MAX_EDIT_LINES);
	}

	@Override
	protected void close()
	{
		super.close();
		iconOverlay.clear();
	}

	// Persisted (not RS-profile-scoped, like recentLabels) so the "_" toggle survives restarts.
	private static final String SMALL_FONT_KEY = "advancedEditorSmallFont";

	// PLAIN_11 once toggled via the "_" button, else the default PLAIN_12.
	private int editorFontId()
	{
		return smallFont() ? FontID.PLAIN_11 : FontID.PLAIN_12;
	}

	private boolean smallFont()
	{
		return Boolean.parseBoolean(configManager.getConfiguration(GroundMarkerVariablesConfig.GROUP, SMALL_FONT_KEY));
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

	// Falls back to selecting the entire label if pos isn't inside a {...} token.
	private void selectTokenAt(int pos)
	{
		Matcher matcher = TOKEN_PATTERN.matcher(getValue());
		while (matcher.find())
		{
			if (pos >= matcher.start() && pos <= matcher.end())
			{
				shiftSelectionAnchor = -1;
				cursorAt(matcher.start(), matcher.end());
				return;
			}
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
		WrappedLine line = wrappedLines.get(lineIndex);
		int localCursor = cursor - line.start;

		String escapedCompletion = Text.escapeJagex(completion);
		int w = container.getWidth();
		int lineFullWidth = font.getTextWidth(Text.escapeJagex(line.text));
		int lineStartX = (w - lineFullWidth) / 2;
		int ghostX = lineStartX + font.getTextWidth(Text.escapeJagex(line.text.substring(0, localCursor)));
		int ghostWidth = font.getTextWidth(escapedCompletion);
		int ghostY = editRowY(lineIndex) + ghostYOffset();

		// No native rounded-rect widget exists, so this is a plain filled backing behind the
		// ghost text — same technique buildEdit() itself uses for its selection highlight. Only
		// needed when there's real text after the cursor on this row to stay legible over.
		if (localCursor < line.text.length())
		{
			Widget ghostBackground = container.createChild(-1, WidgetType.RECTANGLE);
			ghostBackground.setFilled(true);
			ghostBackground.setTextColor(GHOST_BACKGROUND_COLOR);
			ghostBackground.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
			ghostBackground.setOriginalX(ghostX - 1);
			ghostBackground.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
			ghostBackground.setOriginalY(ghostY);
			ghostBackground.setOriginalWidth(ghostWidth + 2);
			ghostBackground.setOriginalHeight(LINE_HEIGHT);
			ghostBackground.revalidate();
		}

		Widget ghost = container.createChild(-1, WidgetType.TEXT);
		ghost.setText(escapedCompletion);
		ghost.setFontId(editorFontId());
		ghost.setTextColor(AUTOCOMPLETE_COLOR);
		ghost.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
		ghost.setOriginalX(ghostX);
		ghost.setOriginalWidth(ghostWidth);
		ghost.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		ghost.setOriginalY(ghostY);
		ghost.setOriginalHeight(LINE_HEIGHT);
		ghost.setXTextAlignment(WidgetTextAlignment.LEFT);
		ghost.setYTextAlignment(WidgetTextAlignment.CENTER);
		ghost.revalidate();
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

		int w = container.getWidth();

		matcher.reset();
		while (matcher.find())
		{
			int lineIndex = findLineIndex(wrappedLines, matcher.start());
			WrappedLine line = wrappedLines.get(lineIndex);
			if (matcher.end() - 1 > line.end)
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
			int lineStartX = (w - lineFullWidth) / 2;
			int localStart = matcher.start() - line.start;
			int localEnd = matcher.end() - line.start;
			int startX = lineStartX + font.getTextWidth(Text.escapeJagex(line.text.substring(0, localStart)));
			int endX = lineStartX + font.getTextWidth(Text.escapeJagex(line.text.substring(0, localEnd)));

			Widget underline = container.createChild(-1, WidgetType.RECTANGLE);
			underline.setFilled(true);
			underline.setTextColor(color);
			underline.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
			underline.setOriginalX(startX);
			underline.setOriginalWidth(Math.max(1, endX - startX));
			underline.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
			underline.setOriginalY(editRowY(lineIndex) + LINE_HEIGHT - 3);
			underline.setOriginalHeight(1);
			underline.revalidate();
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

			if (result.size() < MAX_EDIT_LINES - 1)
			{
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
		Widget container = chatboxPanelManager.getContainerWidget();
		container.deleteAllChildren();
		iconPlacements.clear();
		tips.clear();

		Widget promptWidget = container.createChild(-1, WidgetType.TEXT);
		promptWidget.setText(getPrompt());
		promptWidget.setTextColor(0x800000);
		promptWidget.setFontId(FontID.QUILL_8);
		promptWidget.setXPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
		promptWidget.setOriginalX(0);
		promptWidget.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		promptWidget.setOriginalY(5);
		promptWidget.setOriginalHeight(LINE_HEIGHT);
		promptWidget.setXTextAlignment(WidgetTextAlignment.CENTER);
		promptWidget.setYTextAlignment(WidgetTextAlignment.CENTER);
		promptWidget.setWidthMode(WidgetSizeMode.MINUS);
		promptWidget.revalidate();

		buildTopBar(container);

		if (font == null)
		{
			Widget probe = container.createChild(-1, WidgetType.TEXT);
			probe.setFontId(editorFontId());
			font = probe.getFont();
		}

		List<WrappedLine> wrappedLines = computeWrappedLines(getValue(), container.getWidth());

		fontID(editorFontId());
		buildEdit(0, 5 + LINE_HEIGHT, container.getWidth(), LINE_HEIGHT);
		buildAutocomplete(container, wrappedLines);
		buildColorTagUnderlines(container, wrappedLines);

		int separatorY = editRowY(wrappedLines.size()) - 1;
		int resultsStartY = separatorY + 8;
		// The list ends where all VISIBLE_ROWS end under one edit line.
		int listBottom = editRowY(1) + 7 + VISIBLE_ROWS * ROW_HEIGHT;
		visibleRows = Math.max(0, Math.min(VISIBLE_ROWS, (listBottom - resultsStartY) / ROW_HEIGHT));
		if (visibleRows == 0)
		{
			iconOverlay.show(iconPlacements, tips);
			return;
		}

		Widget separator = container.createChild(-1, WidgetType.LINE);
		separator.setXPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
		separator.setOriginalX(0);
		separator.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		separator.setOriginalY(separatorY);
		separator.setOriginalHeight(0);
		separator.setOriginalWidth(16);
		separator.setWidthMode(WidgetSizeMode.MINUS);
		separator.revalidate();

		// As a label row's text: from x 13 to the scrollbar.
		rowTextWidth = container.getWidth() - 13 - SCROLLBAR_WIDTH - 6;
		buildRecommendations(container, resultsStartY);
		iconOverlay.show(iconPlacements, tips);
	}

	// Undo (grayed out/unclickable once the value matches originalLabel), "_" (font size
	// toggle) and "x" (close without saving), right-aligned in the prompt row.
	private void buildTopBar(Widget container)
	{
		int rightX = TOP_BAR_GAP;

		Widget close = createTopBarButton(container, "x", rightX, CLOSE_BUTTON_WIDTH);
		close.setHasListener(true);
		close.setAction(0, "Close");
		close.setOnOpListener((JavaScriptCallback) ev -> chatboxPanelManager.close());
		addHoverHighlight(close, 0x000000);
		close.revalidate();
		rightX += CLOSE_BUTTON_WIDTH + TOP_BAR_GAP;

		Widget fontToggle = createTopBarButton(container, "_", rightX, FONT_TOGGLE_BUTTON_WIDTH);
		fontToggle.setHasListener(true);
		fontToggle.setAction(0, "Toggle font size");
		fontToggle.setOnOpListener((JavaScriptCallback) ev -> toggleFontSize());
		addHoverHighlight(fontToggle, 0x000000);
		fontToggle.revalidate();
		rightX += FONT_TOGGLE_BUTTON_WIDTH + TOP_BAR_GAP;

		boolean canUndo = !getValue().equals(originalLabel);
		// Escaped — a lone "<" is parsed as the start of an inline tag and never drawn otherwise.
		Widget undo = createTopBarButton(container, Text.escapeJagex("<"), rightX, UNDO_BUTTON_WIDTH);
		undo.setTextColor(canUndo ? 0x000000 : 0x888888);
		if (canUndo)
		{
			undo.setHasListener(true);
			undo.setAction(0, "Undo");
			undo.setOnOpListener((JavaScriptCallback) ev -> undo());
			addHoverHighlight(undo, 0x000000);
		}
		undo.revalidate();
	}

	private void addHoverHighlight(Widget widget, int normalColor)
	{
		widget.setOnMouseRepeatListener((JavaScriptCallback) ev -> widget.setTextColor(HOVER_COLOR));
		widget.setOnMouseLeaveListener((JavaScriptCallback) ev -> widget.setTextColor(normalColor));
	}

	private Widget createTopBarButton(Widget container, String text, int rightX, int width)
	{
		Widget button = container.createChild(-1, WidgetType.TEXT);
		button.setText(text);
		button.setFontId(FontID.BOLD_12);
		button.setTextColor(0x000000);
		button.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		button.setOriginalX(rightX);
		button.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		button.setOriginalY(5);
		button.setOriginalWidth(width);
		button.setOriginalHeight(LINE_HEIGHT);
		button.setXTextAlignment(WidgetTextAlignment.CENTER);
		button.setYTextAlignment(WidgetTextAlignment.CENTER);
		return button;
	}

	private void undo()
	{
		value(originalLabel);
		update();
	}

	private void toggleFontSize()
	{
		configManager.setConfiguration(GroundMarkerVariablesConfig.GROUP, SMALL_FONT_KEY, !smallFont());
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

	// Every listed completion of the token at the cursor, SUGGESTION_COLUMNS to a row, the one Tab would insert marked.
	// With none, what the variable being typed takes there, from its parameters' descriptions.
	private void addSuggestions(List<Row> rows)
	{
		Completion completion = completion();
		if (completion == null || !completion.hasListed())
		{
			if (getCursorStart() == getCursorEnd())
			{
				for (String hint : completer.parameterHints(getValue(), getCursorStart()))
				{
					wrap(hint, rowTextWidth).forEach(line -> rows.add(Row.hint(line)));
				}
			}
			return;
		}

		Completion.Option preferred = completion.preferred < 0 ? null : completion.options.get(completion.preferred);
		List<Completion.Option> listed = new ArrayList<>();
		for (Completion.Option option : completion.options)
		{
			if (option.label != null)
			{
				listed.add(option);
			}
		}

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

		// Invisible — purely captures mouse wheel input over the recommendations area.
		Widget scrollCapture = container.createChild(-1, WidgetType.TEXT);
		scrollCapture.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
		scrollCapture.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		scrollCapture.setOriginalX(0);
		scrollCapture.setOriginalY(resultsStartY);
		scrollCapture.setOriginalWidth(container.getWidth());
		scrollCapture.setOriginalHeight(visibleRows * ROW_HEIGHT);
		scrollCapture.setHasListener(true);
		scrollCapture.setNoScrollThrough(true);
		scrollCapture.setOnScrollWheelListener((JavaScriptCallback) ev -> scroll(ev.getMouseY()));
		scrollCapture.revalidate();

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

			Widget text = container.createChild(-1, WidgetType.TEXT);
			// Escaped so a recommendation containing our own <col=> label syntax (or any other
			// Jagex markup) shows as literal text instead of actually being applied.
			text.setText(row.title ? row.text : Text.escapeJagex(row.text));
			text.setFontId(row.title ? FontID.QUILL_8 : editorFontId());
			text.setTextColor(color);
			text.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
			text.setOriginalX(row.title ? 6 : 13);
			text.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
			text.setOriginalY(y);
			text.setOriginalHeight(ROW_HEIGHT);
			text.setOriginalWidth(SCROLLBAR_WIDTH + 6);
			text.setWidthMode(WidgetSizeMode.MINUS);
			text.setXTextAlignment(WidgetTextAlignment.LEFT);
			text.setYTextAlignment(WidgetTextAlignment.CENTER);
			text.revalidate();

			if (!row.title && !row.hint)
			{
				addRowActions(text, row, color);
			}
		}

		buildScrollbar(container, rows.size(), maxOffset, resultsStartY);
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
		addHoverHighlight(text, color);
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
				Widget placeholder = container.createChild(-1, WidgetType.GRAPHIC);
				placeholder.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
				placeholder.setOriginalX(x);
				placeholder.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
				placeholder.setOriginalY(y);
				placeholder.setOriginalWidth(ICON_WIDTH);
				placeholder.setOriginalHeight(ICON_HEIGHT);
				addInsertAction(placeholder, option);
				placeholder.revalidate();
				iconPlacements.add(new SuggestionIconOverlay.Placement(container, new Rectangle(x, y, ICON_WIDTH, ICON_HEIGHT), icon));
			}

			if (option.description != null)
			{
				tips.add(new SuggestionIconOverlay.Tip(container, new Rectangle(x, y, columnWidth, ROW_HEIGHT), option.description));
			}

			int color = option == row.preferred ? PREFERRED_SUGGESTION_COLOR : ROW_COLOR;
			int textWidth = x + columnWidth - textX - 2;
			Widget text = container.createChild(-1, WidgetType.TEXT);
			text.setText(Text.escapeJagex(fit(option.label, textWidth)));
			text.setFontId(editorFontId());
			text.setTextColor(color);
			text.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
			text.setOriginalX(textX);
			text.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
			text.setOriginalY(y);
			text.setOriginalWidth(textWidth);
			text.setOriginalHeight(ROW_HEIGHT);
			text.setXTextAlignment(WidgetTextAlignment.LEFT);
			text.setYTextAlignment(WidgetTextAlignment.CENTER);
			addInsertAction(text, option);
			addHoverHighlight(text, color);
			text.revalidate();
		}
	}

	// "Insert": completes the token at the cursor with option. Its full name shows on hover.
	private void addInsertAction(Widget widget, Completion.Option option)
	{
		widget.setHasListener(true);
		widget.setName(NamedColors.expandColorAliases(option.label));
		widget.setAction(0, "Insert");
		widget.setOnOpListener((JavaScriptCallback) ev -> insertAtCursor(option.insert, option.replace));
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

	private void buildScrollbar(Widget container, int rowCount, int maxOffset, int resultsStartY)
	{
		int resultsHeight = visibleRows * ROW_HEIGHT;
		// Needs room for both arrows and a thumb; the wheel still scrolls without it.
		if (maxOffset <= 0 || resultsHeight < SCROLLBAR_ARROW_HEIGHT * 2 + SCROLLBAR_THUMB_MIN_HEIGHT)
		{
			return;
		}

		Widget upArrow = container.createChild(-1, WidgetType.TEXT);
		upArrow.setText("^");
		upArrow.setFontId(FontID.BOLD_12);
		upArrow.setTextColor(scrollOffset == 0 ? 0x888888 : 0x000000);
		upArrow.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		upArrow.setOriginalX(0);
		upArrow.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		upArrow.setOriginalY(resultsStartY);
		upArrow.setOriginalWidth(SCROLLBAR_WIDTH);
		upArrow.setOriginalHeight(SCROLLBAR_ARROW_HEIGHT);
		upArrow.setXTextAlignment(WidgetTextAlignment.CENTER);
		upArrow.setYTextAlignment(WidgetTextAlignment.CENTER);
		upArrow.setHasListener(true);
		upArrow.setAction(0, "Scroll up");
		upArrow.setOnOpListener((JavaScriptCallback) ev -> scroll(-1));
		upArrow.revalidate();

		Widget downArrow = container.createChild(-1, WidgetType.TEXT);
		downArrow.setText("v");
		downArrow.setFontId(FontID.BOLD_12);
		downArrow.setTextColor(scrollOffset >= maxOffset ? 0x888888 : 0x000000);
		downArrow.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		downArrow.setOriginalX(0);
		downArrow.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		downArrow.setOriginalY(resultsStartY + resultsHeight - SCROLLBAR_ARROW_HEIGHT);
		downArrow.setOriginalWidth(SCROLLBAR_WIDTH);
		downArrow.setOriginalHeight(SCROLLBAR_ARROW_HEIGHT);
		downArrow.setXTextAlignment(WidgetTextAlignment.CENTER);
		downArrow.setYTextAlignment(WidgetTextAlignment.CENTER);
		downArrow.setHasListener(true);
		downArrow.setAction(0, "Scroll down");
		downArrow.setOnOpListener((JavaScriptCallback) ev -> scroll(1));
		downArrow.revalidate();

		int trackY = resultsStartY + SCROLLBAR_ARROW_HEIGHT;
		int trackHeight = resultsHeight - (SCROLLBAR_ARROW_HEIGHT * 2);
		int trackX = (SCROLLBAR_WIDTH - SCROLLBAR_TRACK_WIDTH) / 2;

		Widget track = container.createChild(-1, WidgetType.RECTANGLE);
		track.setFilled(true);
		track.setOpacity(120);
		track.setTextColor(0x000000);
		track.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		track.setOriginalX(trackX);
		track.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		track.setOriginalY(trackY);
		track.setOriginalWidth(SCROLLBAR_TRACK_WIDTH);
		track.setOriginalHeight(trackHeight);
		track.revalidate();

		int thumbHeight = Math.max(SCROLLBAR_THUMB_MIN_HEIGHT, trackHeight * visibleRows / rowCount);
		int thumbY = trackY + (trackHeight - thumbHeight) * scrollOffset / maxOffset;

		Widget thumb = container.createChild(-1, WidgetType.RECTANGLE);
		thumb.setFilled(true);
		thumb.setTextColor(0x800000);
		thumb.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		thumb.setOriginalX(trackX);
		thumb.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		thumb.setOriginalY(thumbY);
		thumb.setOriginalWidth(SCROLLBAR_TRACK_WIDTH);
		thumb.setOriginalHeight(thumbHeight);
		thumb.revalidate();
	}
}
