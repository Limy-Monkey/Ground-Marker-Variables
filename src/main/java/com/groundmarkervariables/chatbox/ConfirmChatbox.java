package com.groundmarkervariables.chatbox;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import net.runelite.api.FontID;
import net.runelite.api.FontTypeFace;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.chatbox.ChatboxInput;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.input.KeyListener;

// A question in the chatbox: its title wrapped to fit (<br> still breaks), then its choices, picked by click or 1, 2...;
// Esc cancels. A choice can have more on right-click, e.g. "Yes, overwrite existing". A lone choice gets a "No" that does
// nothing.
@RequiredArgsConstructor
public class ConfirmChatbox extends ChatboxInput implements KeyListener {
	private static final int TITLE_COLOR = 0x800000;
	private static final int LINE_HEIGHT = 15;
	private static final int MARGIN = 8;
	private static final int OPTION_HEIGHT = 24;
	private static final int MAX_STEP = 30;

	private final ChatboxPanelManager chatboxPanelManager;
	private final String title;
	private final List<Choice> choices = new ArrayList<>();
	@Nullable
	private Runnable onClose;

	@RequiredArgsConstructor
	private static final class Choice {
		private final String text;
		private final Runnable action;
		// Right-click only.
		private final List<Choice> more = new ArrayList<>();
	}

	// None if action is null.
	public ConfirmChatbox option(String text, @Nullable Runnable action) {
		if (action != null) {
			choices.add(new Choice(text, action));
		}
		return this;
	}

	public ConfirmChatbox yes(Runnable action) {
		return option("Yes", action);
	}

	public ConfirmChatbox no(Runnable action) {
		return option("No", action);
	}

	// Another way to pick the last choice, on right-click; none if action is null.
	public ConfirmChatbox or(String text, @Nullable Runnable action) {
		if (action != null) {
			choices.get(choices.size() - 1).more.add(new Choice(text, action));
		}
		return this;
	}

	// However it closes; before a choice's action runs.
	public ConfirmChatbox onClose(Runnable onClose) {
		this.onClose = onClose;
		return this;
	}

	public void build() {
		if (choices.size() == 1) {
			no(() -> { });
		}
		chatboxPanelManager.openInput(this);
	}

	@Override
	protected void open() {
		Widget container = chatboxPanelManager.getContainerWidget();
		FontTypeFace font = ChatboxWidgets.font(container, FontID.QUILL_8);
		List<String> lines = ChatboxWidgets.wrap(font, title, container.getWidth() - 4 * MARGIN);
		Widget prompt = ChatboxWidgets.centeredText(container, String.join("<br>", lines), FontID.QUILL_8, TITLE_COLOR, MARGIN,
			lines.size() * LINE_HEIGHT);
		prompt.setLineHeight(LINE_HEIGHT);

		// As RuneLite's menu: the choices share the space left, at most MAX_STEP apart, centered in it.
		int y = MARGIN + lines.size() * LINE_HEIGHT + 6;
		int space = container.getHeight() - y - MARGIN;
		int step = Math.min(space / choices.size(), MAX_STEP);
		y += (space - step * choices.size()) / 2;
		for (Choice choice : choices) {
			Widget option = ChatboxWidgets.centeredText(container, choice.text, FontID.QUILL_8, 0, y, OPTION_HEIGHT);
			option.setAction(0, "Continue");
			for (int i = 0; i < choice.more.size(); i++) {
				option.setAction(i + 1, choice.more.get(i).text);
			}
			// Op 1 is "Continue", op 2 the first of more.
			option.setOnOpListener((JavaScriptCallback) ev -> pick(ev.getOp() <= 1 ? choice : choice.more.get(ev.getOp() - 2)));
			ChatboxWidgets.hover(option, 0);
			option.setHasListener(true);
			y += step;
		}
	}

	// As RuneLite's menu: closes, then runs the choice.
	private void pick(Choice choice) {
		chatboxPanelManager.getContainerWidget().setOnKeyListener((Object[]) null);
		chatboxPanelManager.close();
		choice.action.run();
	}

	@Override
	protected void close() {
		if (onClose != null) {
			onClose.run();
		}
	}

	@Override
	public void keyTyped(KeyEvent e) {
		if (!chatboxPanelManager.shouldTakeInput()) {
			return;
		}
		int n = e.getKeyChar() - '1';
		if (e.getKeyChar() == KeyEvent.VK_ESCAPE) {
			chatboxPanelManager.close();
			e.consume();
		} else if (n >= 0 && n < choices.size()) {
			pick(choices.get(n));
			e.consume();
		}
	}

	@Override
	public void keyPressed(KeyEvent e) {
		if (chatboxPanelManager.shouldTakeInput() && e.getKeyCode() == KeyEvent.VK_ESCAPE) {
			e.consume();
		}
	}

	@Override
	public void keyReleased(KeyEvent e) {
	}
}
