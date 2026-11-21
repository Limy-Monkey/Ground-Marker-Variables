package com.groundmarkervariables.chatbox;

import java.util.function.IntConsumer;
import javax.annotation.Nullable;
import net.runelite.api.FontID;
import net.runelite.api.FontTypeFace;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;

// Widgets for chatbox panels, placed from the container's top and revalidated; set only non-layout properties after.
public final class ChatboxWidgets
{
	public static final int SCROLLBAR_WIDTH = 14;
	public static final int HOVER_COLOR = 0x444444;
	public static final int DISABLED_COLOR = 0x888888;
	private static final int SCROLLBAR_ARROW_HEIGHT = 12;
	private static final int SCROLLBAR_TRACK_WIDTH = 4;
	private static final int SCROLLBAR_THUMB_MIN_HEIGHT = 6;

	private ChatboxWidgets()
	{
	}

	public static Widget child(Widget container, int type, int x, int y, int width, int height)
	{
		return place(container.createChild(-1, type), WidgetPositionMode.ABSOLUTE_LEFT, x, y, width, height);
	}

	public static Widget rect(Widget container, int color, int x, int y, int width, int height)
	{
		Widget rect = child(container, WidgetType.RECTANGLE, x, y, width, height);
		rect.setFilled(true);
		rect.setTextColor(color);
		return rect;
	}

	// Left-aligned, vertically centered.
	public static Widget text(Widget container, String text, int fontId, int color, int x, int y, int width, int height)
	{
		return styled(child(container, WidgetType.TEXT, x, y, width, height), text, fontId, color, WidgetTextAlignment.LEFT);
	}

	// As text(), reaching to rightMargin px short of the container's right edge.
	public static Widget rowText(Widget container, String text, int fontId, int color, int x, int y, int height, int rightMargin)
	{
		Widget widget = container.createChild(-1, WidgetType.TEXT);
		widget.setWidthMode(WidgetSizeMode.MINUS);
		return styled(place(widget, WidgetPositionMode.ABSOLUTE_LEFT, x, y, rightMargin, height), text, fontId, color, WidgetTextAlignment.LEFT);
	}

	// Centered across the container's width.
	public static Widget centeredText(Widget container, String text, int fontId, int color, int y, int height)
	{
		Widget widget = container.createChild(-1, WidgetType.TEXT);
		widget.setWidthMode(WidgetSizeMode.MINUS);
		return styled(place(widget, WidgetPositionMode.ABSOLUTE_CENTER, 0, y, 0, height), text, fontId, color, WidgetTextAlignment.CENTER);
	}

	// Centered text rightX from the container's right; grayed out and inert without onClick.
	public static Widget button(Widget container, String text, int fontId, int rightX, int y, int width, int height, String action,
		@Nullable Runnable onClick)
	{
		Widget button = place(container.createChild(-1, WidgetType.TEXT), WidgetPositionMode.ABSOLUTE_RIGHT, rightX, y, width, height);
		styled(button, text, fontId, onClick == null ? DISABLED_COLOR : 0x000000, WidgetTextAlignment.CENTER);
		if (onClick != null)
		{
			button.setHasListener(true);
			button.setAction(0, action);
			button.setOnOpListener((JavaScriptCallback) ev -> onClick.run());
			hover(button, 0x000000);
		}
		return button;
	}

	public static void hover(Widget widget, int color)
	{
		widget.setOnMouseRepeatListener((JavaScriptCallback) ev -> widget.setTextColor(HOVER_COLOR));
		widget.setOnMouseLeaveListener((JavaScriptCallback) ev -> widget.setTextColor(color));
	}

	// A line across the container, 8px in from each side.
	public static void separator(Widget container, int y)
	{
		Widget line = container.createChild(-1, WidgetType.LINE);
		line.setWidthMode(WidgetSizeMode.MINUS);
		place(line, WidgetPositionMode.ABSOLUTE_CENTER, 0, y, 16, 0);
	}

	public static FontTypeFace font(Widget container, int fontId)
	{
		Widget probe = container.createChild(-1, WidgetType.TEXT);
		probe.setFontId(fontId);
		return probe.getFont();
	}

	// Invisible: the mouse wheel over the container's full width from y scrolls by its direction.
	public static void scrollCapture(Widget container, int y, int height, IntConsumer scroll)
	{
		Widget capture = child(container, WidgetType.TEXT, 0, y, container.getWidth(), height);
		capture.setHasListener(true);
		capture.setNoScrollThrough(true);
		capture.setOnScrollWheelListener((JavaScriptCallback) ev -> scroll.accept(ev.getMouseY()));
	}

	// At the right of top..top+height, showing visible of count rows from offset (up to maxOffset); the arrows call
	// scroll(-1) / scroll(1).
	public static void scrollbar(Widget container, int top, int height, int offset, int maxOffset, int visible, int count,
		IntConsumer scroll)
	{
		// Needs room for both arrows and a thumb; the wheel still scrolls without it.
		if (maxOffset <= 0 || height < SCROLLBAR_ARROW_HEIGHT * 2 + SCROLLBAR_THUMB_MIN_HEIGHT)
		{
			return;
		}

		arrow(container, "^", "Scroll up", top, offset > 0, () -> scroll.accept(-1));
		arrow(container, "v", "Scroll down", top + height - SCROLLBAR_ARROW_HEIGHT, offset < maxOffset, () -> scroll.accept(1));

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

	// Clickable even when grayed out at the end; scrolling clamps.
	private static void arrow(Widget container, String text, String action, int y, boolean enabled, Runnable onClick)
	{
		Widget arrow = place(container.createChild(-1, WidgetType.TEXT), WidgetPositionMode.ABSOLUTE_RIGHT, 0, y, SCROLLBAR_WIDTH,
			SCROLLBAR_ARROW_HEIGHT);
		styled(arrow, text, FontID.BOLD_12, enabled ? 0x000000 : DISABLED_COLOR, WidgetTextAlignment.CENTER);
		arrow.setHasListener(true);
		arrow.setAction(0, action);
		arrow.setOnOpListener((JavaScriptCallback) ev -> onClick.run());
	}

	private static Widget place(Widget widget, int xMode, int x, int y, int width, int height)
	{
		widget.setXPositionMode(xMode);
		widget.setOriginalX(x);
		widget.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		widget.setOriginalY(y);
		widget.setOriginalWidth(width);
		widget.setOriginalHeight(height);
		widget.revalidate();
		return widget;
	}

	private static Widget styled(Widget widget, String text, int fontId, int color, int xAlign)
	{
		widget.setText(text);
		widget.setFontId(fontId);
		widget.setTextColor(color);
		widget.setXTextAlignment(xAlign);
		widget.setYTextAlignment(WidgetTextAlignment.CENTER);
		return widget;
	}
}
