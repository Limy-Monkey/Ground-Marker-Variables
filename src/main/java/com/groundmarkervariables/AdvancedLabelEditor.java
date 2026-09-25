package com.groundmarkervariables;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.FontID;
import net.runelite.api.FontTypeFace;
import net.runelite.api.Skill;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
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
// A scrollable list of recommended labels is shown below the separator — Current (the tile's
// own original label, always shown), Recent (the last few labels saved anywhere), and Nearby
// (other markers' labels within 150 tiles) — see recommendations(). Recent/Nearby only show
// while the input is still unedited (its value equals the tile's own original label); Current
// stays visible regardless, so it's always there to click back to. Section titles use Quill 8
// like the prompt; entries themselves are plain RuneScape 12 text, clickable to fill the
// input with that text.
class AdvancedLabelEditor extends ChatboxTextInput
{
	private static final int LINE_HEIGHT = 20;
	private static final int SEPARATOR_Y = 4 + (LINE_HEIGHT * 2);
	private static final int ROW_HEIGHT = 16;
	private static final int RESULTS_START_Y = SEPARATOR_Y + 8;
	private static final int VISIBLE_ROWS = 5;
	private static final int SCROLLBAR_WIDTH = 14;
	private static final int SCROLLBAR_ARROW_HEIGHT = 12;
	private static final int SCROLLBAR_TRACK_WIDTH = 4;
	private static final int SCROLLBAR_THUMB_MIN_HEIGHT = 6;
	private static final int HOVER_COLOR = 0x444444;
	private static final int AUTOCOMPLETE_COLOR = 0x444444;

	// The completable name portion of every variable — "lvl_"/"boost_" complete in full
	// (underscore included), since what follows them is a skill name — see completeSkill().
	private static final List<String> VARIABLE_NAMES = List.of(
		"rsn", "spellbook", "metronome", "weapon", "attackStyle",
		"lvl_", "boost_", "hasThralls", "hasAlchs", "hasFreeze", "hasEntangle", "hasItem", "miscellania"
	);

	// {lvl_<skill>} / {boost_<skill>} — once typing continues past either prefix, autocomplete
	// switches from variable names to skill names.
	private static final List<String> SKILL_PREFIXES = List.of("lvl_", "boost_");

	// {metronomeN} / {metronomeN_M} — once "metronome" itself is fully typed, the N/N_M
	// parameter completes from recent/nearby labels' own {metronome...} usage, same idea as
	// colors: no fixed candidate list, only genuine history.
	private static final String METRONOME_NAME = "metronome";
	private static final Pattern METRONOME_TOKEN_PATTERN = Pattern.compile("\\{metronome(\\d+(?:_\\d+)?)\\}", Pattern.CASE_INSENSITIVE);

	// <col=RRGGBB> — the literal tag itself completes like a variable name; once "col=" is
	// fully typed, the hex value completes from recent/nearby labels' own <col=> usage (there's
	// no fixed candidate list for colors the way Skill.values() is for skills).
	private static final String COLOR_TAG_PREFIX = "col=";
	private static final Pattern COLOR_TAG_PATTERN = Pattern.compile("<col=([0-9a-fA-F]{2,6})>", Pattern.CASE_INSENSITIVE);

	// </col> — reverts to the tile's own color (see GroundMarkerVariablesOverlay). A complete,
	// parameter-free literal, so it just completes in full the moment "<" is followed by "/".
	private static final String CLOSE_COLOR_TAG = "/col>";

	// One row in the recommendations list: either a section title (not selectable) or a
	// label suggestion (selectable — click fills the input with text). Only Recent entries
	// are removable (right-click "Remove"), since Current/Nearby aren't backed by the saved
	// recent-labels list.
	private static final class Row
	{
		private final String text;
		private final boolean title;
		private final boolean removable;

		private Row(String text, boolean title)
		{
			this(text, title, false);
		}

		private Row(String text, boolean title, boolean removable)
		{
			this.text = text;
			this.title = title;
			this.removable = removable;
		}
	}

	private final ChatboxPanelManager chatboxPanelManager;
	private final GroundMarkerVariablesConfig config;

	private String originalLabel = "";
	private List<String> recentLabels = Collections.emptyList();
	private List<String> nearbyLabels = Collections.emptyList();
	private Consumer<String> onRemoveRecent = label -> { };
	private int scrollOffset;

	// -1 when no shift+Left/Right selection is in progress; otherwise the fixed end of the
	// selection (where shift was first held down) — see keyPressed() for why this is needed.
	private int shiftSelectionAnchor = -1;

	// Lazily cached — only ever touched from update() (key-typing/click/scroll triggered,
	// all confirmed client-thread), never from mousePressed/mouseDragged.
	private FontTypeFace font;

	@Inject
	AdvancedLabelEditor(ChatboxPanelManager chatboxPanelManager, ClientThread clientThread, GroundMarkerVariablesConfig config)
	{
		super(chatboxPanelManager, clientThread);
		this.chatboxPanelManager = chatboxPanelManager;
		this.config = config;
		fontID(FontID.PLAIN_12);
	}

	// Called by the plugin before build() — see GroundMarkerVariablesPlugin#openLabelEditor.
	// onRemoveRecent is invoked (with the removed label) when the user right-clicks "Remove"
	// on a Recent entry, so the plugin can drop it from the saved recent-labels list.
	AdvancedLabelEditor recommendations(String originalLabel, List<String> recentLabels, List<String> nearbyLabels,
		Consumer<String> onRemoveRecent)
	{
		this.originalLabel = originalLabel == null ? "" : originalLabel;
		this.recentLabels = new ArrayList<>(recentLabels);
		this.nearbyLabels = nearbyLabels;
		this.onRemoveRecent = onRemoveRecent;
		return this;
	}

	// Fixes a bug in ChatboxTextInput's own shift+Left/Right handling: once a selection
	// exists, its VK_LEFT/VK_RIGHT cases unconditionally collapse to cursorStart/cursorEnd
	// first (logic meant for a plain, non-shift arrow press), so a shift-selection just snaps
	// back to the same one-character span every time instead of growing. We track our own
	// anchor here and drive cursorAt() directly, bypassing that logic entirely for this case;
	// everything else (typing, non-shift arrows, etc.) still goes through the base class.
	// Also adds shift+Ctrl/Alt+Left/Right (either modifier) to extend by a whole word instead
	// of one character — the base class doesn't support this at all (Ctrl+anything other than
	// X/C/V/A is swallowed and does nothing, per its own keyPressed()).
	@Override
	public void keyPressed(KeyEvent ev)
	{
		if (!chatboxPanelManager.shouldTakeInput())
		{
			return;
		}

		int code = ev.getKeyCode();

		if (code == KeyEvent.VK_TAB)
		{
			String completion = pendingCompletion();
			if (completion != null && !completion.isEmpty())
			{
				ev.consume();
				value(getValue() + completion);
				cursorAt(getValue().length());
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

		if (!shift)
		{
			shiftSelectionAnchor = -1;
		}

		super.keyPressed(ev);
	}

	// Double-click selects the word under the cursor — the base class has no word-selection
	// at all. mousePressed() (super's) already moved the cursor to the click point by the
	// time mouseClicked() fires, so getCursorStart() is where the click landed.
	@Override
	public MouseEvent mouseClicked(MouseEvent mouseEvent)
	{
		super.mouseClicked(mouseEvent);

		if (mouseEvent.getButton() == MouseEvent.BUTTON1 && mouseEvent.getClickCount() >= 2)
		{
			selectWordAt(getCursorStart());
		}

		return mouseEvent;
	}

	private void selectWordAt(int pos)
	{
		String text = getValue();
		if (text.isEmpty())
		{
			return;
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

	// Renders the rest of a variable name as ghost text right after the caret — only while
	// the caret sits at the very end of the value (no support for autocompleting mid-text)
	// and inside an unclosed "{", with nothing typed since it but letters. The suggestion is
	// display-only: it's never inserted into the real value, so the real cursor/typing is
	// completely unaffected by it.
	private void buildAutocomplete(Widget container)
	{
		String completion = pendingCompletion();
		if (completion == null || completion.isEmpty())
		{
			return;
		}

		if (font == null)
		{
			Widget probe = container.createChild(-1, WidgetType.TEXT);
			probe.setFontId(FontID.PLAIN_12);
			font = probe.getFont();
		}

		String escapedCompletion = Text.escapeJagex(completion);
		int w = container.getWidth();
		int fullWidth = font.getTextWidth(Text.escapeJagex(getValue()));
		int ghostX = (w + fullWidth) / 2;

		Widget ghost = container.createChild(-1, WidgetType.TEXT);
		ghost.setText(escapedCompletion);
		ghost.setFontId(FontID.PLAIN_12);
		ghost.setTextColor(AUTOCOMPLETE_COLOR);
		ghost.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
		ghost.setOriginalX(ghostX);
		ghost.setOriginalWidth(font.getTextWidth(escapedCompletion));
		ghost.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		ghost.setOriginalY(5 + LINE_HEIGHT - 2);
		ghost.setOriginalHeight(LINE_HEIGHT);
		ghost.setXTextAlignment(WidgetTextAlignment.LEFT);
		ghost.setYTextAlignment(WidgetTextAlignment.CENTER);
		ghost.revalidate();
	}

	// Underlines any complete <col=RRGGBB> tag already in the label, in the color it names —
	// a purely visual editing aid; the tag itself still shows as literal escaped text here,
	// same as buildRecommendations() does elsewhere. Single-line pixel math, same trade-off
	// as buildAutocomplete().
	private void buildColorTagUnderlines(Widget container)
	{
		String text = getValue();
		Matcher matcher = COLOR_TAG_PATTERN.matcher(text);
		if (!matcher.find())
		{
			return;
		}

		if (font == null)
		{
			Widget probe = container.createChild(-1, WidgetType.TEXT);
			probe.setFontId(FontID.PLAIN_12);
			font = probe.getFont();
		}

		int w = container.getWidth();
		int fullWidth = font.getTextWidth(Text.escapeJagex(text));
		int textStartX = (w - fullWidth) / 2;

		matcher.reset();
		while (matcher.find())
		{
			int color;
			try
			{
				color = Integer.parseInt(matcher.group(1), 16);
			}
			catch (NumberFormatException e)
			{
				continue;
			}

			int startX = textStartX + font.getTextWidth(Text.escapeJagex(text.substring(0, matcher.start())));
			int endX = textStartX + font.getTextWidth(Text.escapeJagex(text.substring(0, matcher.end())));

			Widget underline = container.createChild(-1, WidgetType.RECTANGLE);
			underline.setFilled(true);
			underline.setTextColor(color);
			underline.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
			underline.setOriginalX(startX);
			underline.setOriginalWidth(Math.max(1, endX - startX));
			underline.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
			underline.setOriginalY(5 + LINE_HEIGHT + LINE_HEIGHT - 3);
			underline.setOriginalHeight(1);
			underline.revalidate();
		}
	}

	// null if autocomplete doesn't apply right now; otherwise the remaining letters that
	// would complete the variable name currently being typed. The single choke point for the
	// "Autocomplete" config toggle — disabling it here also disables the ghost display
	// (buildAutocomplete) and Tab-to-accept (keyPressed) for free, since both go through this.
	private String pendingCompletion()
	{
		if (!config.autocomplete())
		{
			return null;
		}

		String text = getValue();
		int cursor = getCursorStart();
		if (getCursorStart() != getCursorEnd() || cursor != text.length())
		{
			return null;
		}

		int open = -1;
		char opener = 0;
		for (int i = cursor - 1; i >= 0; i--)
		{
			char c = text.charAt(i);
			if (c == '}' || c == '>')
			{
				return null;
			}
			if (c == '{' || c == '<')
			{
				open = i;
				opener = c;
				break;
			}
		}

		if (open == -1)
		{
			return null;
		}

		String partial = text.substring(open + 1, cursor);

		if (opener == '<')
		{
			// "<" can start either an opening <col=RRGGBB> or a closing </col> — they diverge
			// at the very first character, so a leading "/" commits to the closing tag.
			if (!partial.isEmpty() && partial.charAt(0) == '/')
			{
				return completeCloseColorTag(partial);
			}

			// Unlike "{", an empty partial here still autocompletes — there's only one thing
			// "<" can start (a color tag), so there's no ambiguous guess being made.
			return completeColorTag(partial);
		}

		if (partial.isEmpty())
		{
			return null;
		}

		for (String prefix : SKILL_PREFIXES)
		{
			if (partial.length() >= prefix.length() && partial.regionMatches(true, 0, prefix, 0, prefix.length()))
			{
				return completeSkill(partial.substring(prefix.length()), prefix);
			}
		}

		if (partial.length() >= METRONOME_NAME.length() && partial.regionMatches(true, 0, METRONOME_NAME, 0, METRONOME_NAME.length()))
		{
			return completeMetronomeParams(partial.substring(METRONOME_NAME.length()));
		}

		for (int i = 0; i < partial.length(); i++)
		{
			if (!Character.isLetter(partial.charAt(i)))
			{
				return null;
			}
		}

		List<String> candidates = new ArrayList<>();
		for (String name : VARIABLE_NAMES)
		{
			if (name.length() > partial.length() && name.regionMatches(true, 0, partial, 0, partial.length()))
			{
				candidates.add(name);
			}
		}

		if (candidates.isEmpty())
		{
			return null;
		}

		String chosen = candidates.size() == 1 ? candidates.get(0) : resolveAmbiguousCandidate(candidates, "{");
		return chosen.substring(partial.length());
	}

	// Same idea as the general variable-name matching above, but against skill names — e.g.
	// typing "{lvl_a" completes from Agility/Attack, tie-broken the same recent/nearby way.
	private String completeSkill(String skillPartial, String prefix)
	{
		if (skillPartial.isEmpty())
		{
			return null;
		}

		List<String> candidates = new ArrayList<>();
		for (Skill skill : Skill.values())
		{
			String name = skill.getName().toLowerCase();
			if (name.length() > skillPartial.length() && name.regionMatches(true, 0, skillPartial, 0, skillPartial.length()))
			{
				candidates.add(name);
			}
		}

		if (candidates.isEmpty())
		{
			return null;
		}

		String chosen = candidates.size() == 1 ? candidates.get(0) : resolveAmbiguousCandidate(candidates, "{" + prefix);
		return chosen.substring(skillPartial.length());
	}

	// "<col=" completes literally, same as any variable name. Once it's fully typed, a standard
	// color name (see NamedColors) takes priority the same way skill names do — static
	// candidate list, ambiguity resolved from recent/nearby usage, falling back to the first
	// candidate. Only once no name matches does it fall back to a genuine hex value from
	// recent/nearby history (no fallback there — there's no "default color" to guess).
	private String completeColorTag(String partial)
	{
		if (partial.length() < COLOR_TAG_PREFIX.length())
		{
			return COLOR_TAG_PREFIX.regionMatches(true, 0, partial, 0, partial.length())
				? COLOR_TAG_PREFIX.substring(partial.length())
				: null;
		}

		if (!partial.regionMatches(true, 0, COLOR_TAG_PREFIX, 0, COLOR_TAG_PREFIX.length()))
		{
			return null;
		}

		String hexPartial = partial.substring(COLOR_TAG_PREFIX.length());

		List<String> namedCandidates = new ArrayList<>();
		for (String name : NamedColors.HEX_BY_NAME.keySet())
		{
			if (name.length() > hexPartial.length() && name.regionMatches(true, 0, hexPartial, 0, hexPartial.length()))
			{
				namedCandidates.add(name);
			}
		}

		if (!namedCandidates.isEmpty())
		{
			String chosen = namedCandidates.size() == 1 ? namedCandidates.get(0) : resolveAmbiguousCandidate(namedCandidates, "<" + COLOR_TAG_PREFIX);
			return chosen.substring(hexPartial.length()) + ">";
		}

		// Unlike skill names, an empty hex partial still autocompletes here — colors have no
		// large fixed candidate list to guess from, only actual recent/nearby usage, so
		// suggesting one only ever reflects something the user has genuinely typed before.
		String hex = findRecentColor(hexPartial);
		return hex == null ? null : hex.substring(hexPartial.length()) + ">";
	}

	// "</col>" completes literally in full — no hex value or history lookup needed.
	private String completeCloseColorTag(String partial)
	{
		if (partial.length() >= CLOSE_COLOR_TAG.length()
			|| !CLOSE_COLOR_TAG.regionMatches(true, 0, partial, 0, partial.length()))
		{
			return null;
		}

		return CLOSE_COLOR_TAG.substring(partial.length());
	}

	// paramPartial is whatever's been typed after "metronome" so far — digits, optionally
	// with one underscore (e.g. "4", "4_", "4_2"); an empty partial still autocompletes, same
	// reasoning as colors.
	private String completeMetronomeParams(String paramPartial)
	{
		for (int i = 0; i < paramPartial.length(); i++)
		{
			char c = paramPartial.charAt(i);
			if (!Character.isDigit(c) && c != '_')
			{
				return null;
			}
		}

		String param = findRecentMetronomeParam(paramPartial);
		return param == null ? null : param.substring(paramPartial.length());
	}

	private String findRecentMetronomeParam(String paramPartial)
	{
		for (String label : recentLabels)
		{
			String match = firstMatchingMetronomeParam(label, paramPartial);
			if (match != null)
			{
				return match;
			}
		}

		for (String label : nearbyLabels)
		{
			String match = firstMatchingMetronomeParam(label, paramPartial);
			if (match != null)
			{
				return match;
			}
		}

		return null;
	}

	private static String firstMatchingMetronomeParam(String label, String paramPartial)
	{
		Matcher matcher = METRONOME_TOKEN_PATTERN.matcher(label);
		while (matcher.find())
		{
			String param = matcher.group(1);
			if (param.length() > paramPartial.length() && param.regionMatches(true, 0, paramPartial, 0, paramPartial.length()))
			{
				return param;
			}
		}

		return null;
	}

	private String findRecentColor(String hexPartial)
	{
		for (String label : recentLabels)
		{
			String match = firstMatchingColor(label, hexPartial);
			if (match != null)
			{
				return match;
			}
		}

		for (String label : nearbyLabels)
		{
			String match = firstMatchingColor(label, hexPartial);
			if (match != null)
			{
				return match;
			}
		}

		return null;
	}

	private static String firstMatchingColor(String label, String hexPartial)
	{
		Matcher matcher = COLOR_TAG_PATTERN.matcher(label);
		while (matcher.find())
		{
			String hex = matcher.group(1);
			if (hex.length() > hexPartial.length() && hex.regionMatches(true, 0, hexPartial, 0, hexPartial.length()))
			{
				return hex;
			}
		}

		return null;
	}

	// The first candidate mentioned (as "tokenPrefix + candidate") by any recent/nearby label
	// (recent first), falling back to the first candidate in list order if none mention any.
	private String resolveAmbiguousCandidate(List<String> candidates, String tokenPrefix)
	{
		for (String label : recentLabels)
		{
			String match = firstMatchingCandidate(label, candidates, tokenPrefix);
			if (match != null)
			{
				return match;
			}
		}

		for (String label : nearbyLabels)
		{
			String match = firstMatchingCandidate(label, candidates, tokenPrefix);
			if (match != null)
			{
				return match;
			}
		}

		return candidates.get(0);
	}

	private static String firstMatchingCandidate(String label, List<String> candidates, String tokenPrefix)
	{
		String lower = label.toLowerCase();
		String lowerPrefix = tokenPrefix.toLowerCase();
		for (String candidate : candidates)
		{
			if (lower.contains(lowerPrefix + candidate.toLowerCase()))
			{
				return candidate;
			}
		}

		return null;
	}

	@Override
	protected void update()
	{
		Widget container = chatboxPanelManager.getContainerWidget();
		container.deleteAllChildren();

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

		buildEdit(0, 5 + LINE_HEIGHT, container.getWidth(), LINE_HEIGHT);
		buildAutocomplete(container);
		buildColorTagUnderlines(container);

		Widget separator = container.createChild(-1, WidgetType.LINE);
		separator.setXPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
		separator.setOriginalX(0);
		separator.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		separator.setOriginalY(SEPARATOR_Y);
		separator.setOriginalHeight(0);
		separator.setOriginalWidth(16);
		separator.setWidthMode(WidgetSizeMode.MINUS);
		separator.revalidate();

		buildRecommendations(container);
	}

	private List<Row> buildRows()
	{
		List<Row> rows = new ArrayList<>();
		boolean hasCurrent = !originalLabel.isEmpty();
		if (hasCurrent && config.showCurrent())
		{
			rows.add(new Row("Current", true));
			rows.add(new Row(originalLabel, false));
		}

		// Recent/Nearby only make sense as suggestions for what to type next — once the value
		// has diverged from the original, they no longer apply to what's being edited.
		if (getValue().equals(originalLabel))
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
					rows.add(new Row("Recent", true));
					for (String label : dedupedRecent)
					{
						rows.add(new Row(label, false, true));
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
					rows.add(new Row("Nearby", true));
					for (String label : dedupedNearby)
					{
						rows.add(new Row(label, false));
					}
				}
			}
		}

		return rows;
	}

	private void buildRecommendations(Widget container)
	{
		List<Row> rows = buildRows();
		if (rows.isEmpty())
		{
			return;
		}

		int maxOffset = Math.max(0, rows.size() - VISIBLE_ROWS + 1);
		scrollOffset = Math.max(0, Math.min(scrollOffset, maxOffset));

		// Invisible — purely captures mouse wheel input over the recommendations area.
		Widget scrollCapture = container.createChild(-1, WidgetType.TEXT);
		scrollCapture.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
		scrollCapture.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		scrollCapture.setOriginalX(0);
		scrollCapture.setOriginalY(RESULTS_START_Y);
		scrollCapture.setOriginalWidth(container.getWidth());
		scrollCapture.setOriginalHeight(VISIBLE_ROWS * ROW_HEIGHT);
		scrollCapture.setHasListener(true);
		scrollCapture.setNoScrollThrough(true);
		scrollCapture.setOnScrollWheelListener((JavaScriptCallback) ev -> scroll(ev.getMouseY()));
		scrollCapture.revalidate();

		int visibleCount = Math.min(VISIBLE_ROWS, rows.size() - scrollOffset);
		for (int i = 0; i < visibleCount; i++)
		{
			Row row = rows.get(scrollOffset + i);
			int y = RESULTS_START_Y + (ROW_HEIGHT * i);

			Widget text = container.createChild(-1, WidgetType.TEXT);
			// Escaped so a recommendation containing our own <col=> label syntax (or any other
			// Jagex markup) shows as literal text instead of actually being applied.
			text.setText(row.title ? row.text : Text.escapeJagex(row.text));
			text.setFontId(row.title ? FontID.QUILL_8 : FontID.PLAIN_12);
			text.setTextColor(row.title ? 0x800000 : 0x000000);
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

			if (!row.title)
			{
				String selected = row.text;
				text.setHasListener(true);
				text.setAction(0, "Select");
				if (row.removable)
				{
					text.setAction(1, "Remove");
				}
				text.setName(selected);
				text.setOnOpListener((JavaScriptCallback) ev ->
				{
					if (ev.getOp() == 2)
					{
						removeRecentEntry(selected);
					}
					else
					{
						selectRecommendation(selected);
					}
				});
				text.setOnMouseRepeatListener((JavaScriptCallback) ev -> text.setTextColor(HOVER_COLOR));
				text.setOnMouseLeaveListener((JavaScriptCallback) ev -> text.setTextColor(0x000000));
			}
		}

		buildScrollbar(container, rows.size(), maxOffset);
	}

	private void selectRecommendation(String text)
	{
		value(text);
	}

	private void removeRecentEntry(String label)
	{
		recentLabels.remove(label);
		onRemoveRecent.accept(label);
		update();
	}

	private void scroll(int direction)
	{
		int maxOffset = Math.max(0, buildRows().size() - VISIBLE_ROWS + 1);
		scrollOffset = Math.max(0, Math.min(scrollOffset + direction, maxOffset));
		update();
	}

	private void buildScrollbar(Widget container, int rowCount, int maxOffset)
	{
		if (maxOffset <= 0)
		{
			return;
		}

		int resultsHeight = VISIBLE_ROWS * ROW_HEIGHT;

		Widget upArrow = container.createChild(-1, WidgetType.TEXT);
		upArrow.setText("^");
		upArrow.setFontId(FontID.BOLD_12);
		upArrow.setTextColor(scrollOffset == 0 ? 0x888888 : 0x000000);
		upArrow.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		upArrow.setOriginalX(0);
		upArrow.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		upArrow.setOriginalY(RESULTS_START_Y);
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
		downArrow.setOriginalY(RESULTS_START_Y + resultsHeight - SCROLLBAR_ARROW_HEIGHT);
		downArrow.setOriginalWidth(SCROLLBAR_WIDTH);
		downArrow.setOriginalHeight(SCROLLBAR_ARROW_HEIGHT);
		downArrow.setXTextAlignment(WidgetTextAlignment.CENTER);
		downArrow.setYTextAlignment(WidgetTextAlignment.CENTER);
		downArrow.setHasListener(true);
		downArrow.setAction(0, "Scroll down");
		downArrow.setOnOpListener((JavaScriptCallback) ev -> scroll(1));
		downArrow.revalidate();

		int trackY = RESULTS_START_Y + SCROLLBAR_ARROW_HEIGHT;
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

		int thumbHeight = Math.max(SCROLLBAR_THUMB_MIN_HEIGHT, trackHeight * VISIBLE_ROWS / rowCount);
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
