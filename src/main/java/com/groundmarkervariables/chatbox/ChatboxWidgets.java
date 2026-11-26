package com.groundmarkervariables.chatbox;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import net.runelite.api.FontID;
import net.runelite.api.FontTypeFace;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import com.groundmarkervariables.GroundMarkerVariablesConfig;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.chatbox.ChatboxInput;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.Text;

// Widgets for chatbox panels, placed from the container's top and revalidated; set only non-layout properties after.
// Static, except logo(), which needs the chatboxes it opens.
public final class ChatboxWidgets {
	public static final int SCROLLBAR_WIDTH = 14;
	public static final int HOVER_COLOR = 0x444444;
	public static final int DISABLED_COLOR = 0x666666;
	private static final int SCROLLBAR_ARROW_HEIGHT = 12;
	private static final int SCROLLBAR_TRACK_WIDTH = 4;
	private static final int SCROLLBAR_THUMB_MIN_HEIGHT = 6;
	// Half the logo's pixel-art size, so its edges stay sharp.
	// The "_" toggle's smaller font (PLAIN_11), shared by every chatbox; persisted across restarts.
	private static final String SMALL_FONT_KEY = "advancedEditorSmallFont";
	private static final Rectangle LOGO_AREA = new Rectangle(4, 2, 24, 24);
	// The logo's last right-click option.
	private static final String DISCORD_URL = "https://discord.gg/ehyvCPpdSn";
	// Where the top row's other content can start, right of the logo.
	public static final int LOGO_RIGHT = LOGO_AREA.x + LOGO_AREA.width + 4;
	private static final BufferedImage LOGO = ImageUtil.loadImageResource(ChatboxWidgets.class, "gmv-logo.png");

	private final ChatboxPanelManager chatboxPanelManager;
	private final Provider<MarkerSetManager> markerSetManagerProvider;
	private final Provider<MarkerManager> markerManagerProvider;
	private final ConfigManager configManager;

	@Inject
	private ChatboxWidgets(ChatboxPanelManager chatboxPanelManager, Provider<MarkerSetManager> markerSetManagerProvider,
		Provider<MarkerManager> markerManagerProvider, ConfigManager configManager) {
		this.configManager = configManager;
		this.chatboxPanelManager = chatboxPanelManager;
		this.markerSetManagerProvider = markerSetManagerProvider;
		this.markerManagerProvider = markerManagerProvider;
	}

	// The chatboxes' text font: RuneScape 12, or PLAIN_11 once toggled by "_".
	public int fontId() {
		return smallFont() ? FontID.PLAIN_11 : FontID.PLAIN_12;
	}

	public boolean smallFont() {
		return Boolean.parseBoolean(configManager.getConfiguration(GroundMarkerVariablesConfig.GROUP, SMALL_FONT_KEY));
	}

	public void toggleFontSize() {
		configManager.setConfiguration(GroundMarkerVariablesConfig.GROUP, SMALL_FONT_KEY, !smallFont());
	}

	// The logo at the top-left, drawn by SuggestionIconOverlay once placements are shown: opens Marker Sets or the Marker
	// Manager, except current's own. Opening one closes current, unsaved.
	public void logo(Widget container, List<SuggestionIconOverlay.Placement> placements, ChatboxInput current) {
		placements.add(new SuggestionIconOverlay.Placement(container, LOGO_AREA, LOGO));
		Widget logo = child(container, WidgetType.GRAPHIC, LOGO_AREA.x, LOGO_AREA.y, LOGO_AREA.width, LOGO_AREA.height);
		logo.setHasListener(true);
		List<Runnable> opens = new ArrayList<>();
		if (!(current instanceof MarkerSetManager)) {
			logo.setAction(opens.size(), "Marker Sets");
			opens.add(() -> chatboxPanelManager.openInput(markerSetManagerProvider.get()));
		}
		if (!(current instanceof MarkerManager)) {
			logo.setAction(opens.size(), "Marker Manager");
			opens.add(() -> {
				MarkerManager manager = markerManagerProvider.get();
				manager.load();
				chatboxPanelManager.openInput(manager);
			});
		}
		logo.setAction(opens.size(), "Discord");
		opens.add(() -> LinkBrowser.browse(DISCORD_URL));
		logo.setOnOpListener((JavaScriptCallback) ev -> opens.get(ev.getOp() - 1).run());
	}

	public static Widget child(Widget container, int type, int x, int y, int width, int height) {
		return place(container.createChild(-1, type), WidgetPositionMode.ABSOLUTE_LEFT, x, y, width, height);
	}

	public static Widget rect(Widget container, int color, int x, int y, int width, int height) {
		Widget rect = child(container, WidgetType.RECTANGLE, x, y, width, height);
		rect.setFilled(true);
		rect.setTextColor(color);
		return rect;
	}

	// Left-aligned, vertically centered.
	public static Widget text(Widget container, String text, int fontId, int color, int x, int y, int width, int height) {
		return styled(child(container, WidgetType.TEXT, x, y, width, height), text, fontId, color, WidgetTextAlignment.LEFT);
	}

	// As text(), reaching to rightMargin px short of the container's right edge.
	public static Widget rowText(Widget container, String text, int fontId, int color, int x, int y, int height, int rightMargin) {
		Widget widget = container.createChild(-1, WidgetType.TEXT);
		widget.setWidthMode(WidgetSizeMode.MINUS);
		return styled(place(widget, WidgetPositionMode.ABSOLUTE_LEFT, x, y, rightMargin, height), text, fontId, color, WidgetTextAlignment.LEFT);
	}

	// Centered across the container's width.
	public static Widget centeredText(Widget container, String text, int fontId, int color, int y, int height) {
		Widget widget = container.createChild(-1, WidgetType.TEXT);
		widget.setWidthMode(WidgetSizeMode.MINUS);
		return styled(place(widget, WidgetPositionMode.ABSOLUTE_CENTER, 0, y, 0, height), text, fontId, color, WidgetTextAlignment.CENTER);
	}

	// Centered text rightX from the container's right; grayed out and inert without onClick.
	public static Widget button(Widget container, String text, int fontId, int rightX, int y, int width, int height, String action,
		@Nullable Runnable onClick) {
		Widget button = place(container.createChild(-1, WidgetType.TEXT), WidgetPositionMode.ABSOLUTE_RIGHT, rightX, y, width, height);
		styled(button, text, fontId, onClick == null ? DISABLED_COLOR : 0x000000, WidgetTextAlignment.CENTER);
		if (onClick != null) {
			button.setHasListener(true);
			button.setAction(0, action);
			button.setOnOpListener((JavaScriptCallback) ev -> onClick.run());
			hover(button, 0x000000);
		}
		return button;
	}

	public static void hover(Widget widget, int color) {
		hover(widget, color, widget);
	}

	// While over area, texts are HOVER_COLOR, then color again, e.g. a whole table row's.
	public static void hover(Widget area, int color, Widget... texts) {
		area.setOnMouseRepeatListener((JavaScriptCallback) ev -> Arrays.stream(texts).forEach(text -> text.setTextColor(HOVER_COLOR)));
		area.setOnMouseLeaveListener((JavaScriptCallback) ev -> Arrays.stream(texts).forEach(text -> text.setTextColor(color)));
	}

	// A line across the container, 8px in from each side.
	public static void separator(Widget container, int y) {
		Widget line = container.createChild(-1, WidgetType.LINE);
		line.setWidthMode(WidgetSizeMode.MINUS);
		place(line, WidgetPositionMode.ABSOLUTE_CENTER, 0, y, 16, 0);
	}

	public static FontTypeFace font(Widget container, int fontId) {
		Widget probe = container.createChild(-1, WidgetType.TEXT);
		probe.setFontId(fontId);
		return probe.getFont();
	}

	// text's lines of at most width px in font, split at <br> and between words (a longer word stays whole).
	public static List<String> wrap(FontTypeFace font, String text, int width) {
		List<String> lines = new ArrayList<>();
		for (String paragraph : text.split("<br>", -1)) {
			StringBuilder line = new StringBuilder();
			for (String word : paragraph.split(" ")) {
				if (line.length() > 0 && font.getTextWidth(line + " " + word) > width) {
					lines.add(line.toString());
					line.setLength(0);
				}
				line.append(line.length() > 0 ? " " : "").append(word);
			}
			lines.add(line.toString());
		}
		return lines;
	}

	// Every search and suggestion list's match: text holds each space-separated word of query, in any case and order
	// ("toa tob" matches "{kc toa} {kc tob}"); true for a blank query.
	public static boolean hasWords(String text, String query) {
		String lower = text.toLowerCase(Locale.ROOT);
		for (String word : query.toLowerCase(Locale.ROOT).split(" ")) {
			if (!lower.contains(word)) {
				return false;
			}
		}
		return true;
	}

	// text cut short with ".." to fit width px in font, as drawn escaped.
	public static String fit(FontTypeFace font, String text, int width) {
		if (font.getTextWidth(Text.escapeJagex(text)) <= width) {
			return text;
		}
		String cut = text;
		while (!cut.isEmpty() && font.getTextWidth(Text.escapeJagex(cut) + "..") > width) {
			cut = cut.substring(0, cut.length() - 1);
		}
		return cut.trim() + "..";
	}

	// A list of visible rows of height from top, of count, scrolled to offset kept within 0 to maxOffset (returned): the
	// wheel over it and a scrollbar call scroll. Its rows go on after, over the wheel's capture.
	public static int scrollList(Widget container, int top, int height, int visible, int count, int maxOffset, int offset,
		IntConsumer scroll) {
		offset = Math.max(0, Math.min(offset, maxOffset));
		scrollCapture(container, top, visible * height, scroll);
		scrollbar(container, top, visible * height, offset, maxOffset, visible, count, scroll);
		return offset;
	}

	// Invisible: the mouse wheel over the container's full width from y scrolls by its direction.
	public static void scrollCapture(Widget container, int y, int height, IntConsumer scroll) {
		Widget capture = child(container, WidgetType.TEXT, 0, y, container.getWidth(), height);
		capture.setHasListener(true);
		capture.setNoScrollThrough(true);
		capture.setOnScrollWheelListener((JavaScriptCallback) ev -> scroll.accept(ev.getMouseY()));
	}

	// At the right of top..top+height, showing visible of count rows from offset (up to maxOffset); the arrows call
	// scroll(-1) / scroll(1), or right-clicked, scroll to the top / bottom.
	public static void scrollbar(Widget container, int top, int height, int offset, int maxOffset, int visible, int count,
		IntConsumer scroll) {
		// Needs room for both arrows and a thumb; the wheel still scrolls without it.
		if (maxOffset <= 0 || height < SCROLLBAR_ARROW_HEIGHT * 2 + SCROLLBAR_THUMB_MIN_HEIGHT) {
			return;
		}

		int from = offset;
		arrow(container, "^", "Scroll up", "Scroll to top", top, offset > 0, () -> scroll.accept(-1), () -> scroll.accept(-from));
		arrow(container, "v", "Scroll down", "Scroll to bottom", top + height - SCROLLBAR_ARROW_HEIGHT, offset < maxOffset,
			() -> scroll.accept(1), () -> scroll.accept(maxOffset - from));

		int trackY = top + SCROLLBAR_ARROW_HEIGHT;
		int trackHeight = height - SCROLLBAR_ARROW_HEIGHT * 2;
		int trackX = (SCROLLBAR_WIDTH - SCROLLBAR_TRACK_WIDTH) / 2;
		Widget track = place(container.createChild(-1, WidgetType.RECTANGLE), WidgetPositionMode.ABSOLUTE_RIGHT, trackX, trackY,
			SCROLLBAR_TRACK_WIDTH, trackHeight);
		track.setFilled(true);
		track.setOpacity(120);
		track.setTextColor(0x000000);

		int thumbHeight = Math.max(SCROLLBAR_THUMB_MIN_HEIGHT, trackHeight * visible / count);
		int thumbY = trackY + (trackHeight - thumbHeight) * offset / maxOffset;
		Widget thumb = place(container.createChild(-1, WidgetType.RECTANGLE), WidgetPositionMode.ABSOLUTE_RIGHT, trackX, thumbY,
			SCROLLBAR_TRACK_WIDTH, thumbHeight);
		thumb.setFilled(true);
		thumb.setTextColor(0x800000);
	}

	// Clickable even when grayed out at the end; scrolling clamps. jump is its right-click option, e.g. to the top.
	private static void arrow(Widget container, String text, String action, String jumpAction, int y, boolean enabled, Runnable onClick,
		Runnable jump) {
		Widget arrow = place(container.createChild(-1, WidgetType.TEXT), WidgetPositionMode.ABSOLUTE_RIGHT, 0, y, SCROLLBAR_WIDTH,
			SCROLLBAR_ARROW_HEIGHT);
		styled(arrow, text, FontID.BOLD_12, enabled ? 0x000000 : DISABLED_COLOR, WidgetTextAlignment.CENTER);
		arrow.setHasListener(true);
		arrow.setAction(0, action);
		arrow.setAction(1, jumpAction);
		arrow.setOnOpListener((JavaScriptCallback) ev -> (ev.getOp() == 2 ? jump : onClick).run());
	}

	private static Widget place(Widget widget, int xMode, int x, int y, int width, int height) {
		widget.setXPositionMode(xMode);
		widget.setOriginalX(x);
		widget.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		widget.setOriginalY(y);
		widget.setOriginalWidth(width);
		widget.setOriginalHeight(height);
		widget.revalidate();
		return widget;
	}

	private static Widget styled(Widget widget, String text, int fontId, int color, int xAlign) {
		widget.setText(text);
		widget.setFontId(fontId);
		widget.setTextColor(color);
		widget.setXTextAlignment(xAlign);
		widget.setYTextAlignment(WidgetTextAlignment.CENTER);
		return widget;
	}
}
