package com.groundmarkervariables;

import javax.annotation.Nullable;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.game.chatbox.ChatboxTextMenuInput;

// The import confirmation menu, whose "Yes" can also be right-clicked to overwrite existing markers.
class ImportConfirmInput extends ChatboxTextMenuInput
{
	private static final String YES = "Yes";
	private static final String OVERWRITE = "Yes, overwrite existing";
	// setAction(1, ...) fires as op 2.
	private static final int OVERWRITE_OP = 2;

	private final ChatboxPanelManager chatboxPanelManager;
	private Runnable yes;
	@Nullable
	private Runnable overwrite;

	ImportConfirmInput(ChatboxPanelManager chatboxPanelManager)
	{
		super(chatboxPanelManager);
		this.chatboxPanelManager = chatboxPanelManager;
	}

	// overwrite null for no right-click option.
	ImportConfirmInput yes(Runnable yes, @Nullable Runnable overwrite)
	{
		this.yes = yes;
		this.overwrite = overwrite;
		option(YES, yes);
		return this;
	}

	@Override
	protected void open()
	{
		super.open();
		if (overwrite == null)
		{
			return;
		}

		for (Widget option : chatboxPanelManager.getContainerWidget().getDynamicChildren())
		{
			if (YES.equals(option.getText()))
			{
				option.setAction(1, OVERWRITE);
				option.setOnOpListener((JavaScriptCallback) ev -> select(ev.getOp() == OVERWRITE_OP ? overwrite : yes));
			}
		}
	}

	// As the menu's own selection: closes it, then runs the choice.
	private void select(Runnable choice)
	{
		chatboxPanelManager.getContainerWidget().setOnKeyListener((Object[]) null);
		chatboxPanelManager.close();
		choice.run();
	}
}
