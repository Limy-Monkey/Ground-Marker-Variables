package com.groundmarkervariables.chatbox;

import com.groundmarkervariables.GroundMarkerVariablesPlugin;
import com.groundmarkervariables.markers.GroundMarkerPointData;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.SpritePixels;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.FontID;
import net.runelite.api.FontTypeFace;
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
	private static final int BUTTON_FONT = FontID.PLAIN_11;
	private static final int TOP_BAR_GAP = 8;

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
		plugin.editLabel(WorldPoint.fromRegion(tile.getRegionId(), tile.getRegionX(), tile.getRegionY(), tile.getZ()), tile.getLabel(),
			tile.getColor(), save, () -> clientThread.invokeLater(() ->
			{
				if (chatboxPanelManager.getCurrentInput() == null)
				{
					reopen();
				}
			}));
	}

	protected void reopen()
	{
		chatboxPanelManager.openInput(this);
	}

	protected static Widget addText(Widget container, String text, int fontId, int color, int x, int y)
	{
		return ChatboxWidgets.rowText(container, text, fontId, color, x, y, ROW_HEIGHT, x + ChatboxWidgets.SCROLLBAR_WIDTH + 6);
	}

	protected void sendChatMessage(String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(message)
			.build());
	}
}
