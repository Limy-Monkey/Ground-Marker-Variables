package com.groundmarkervariables;

import com.groundmarkervariables.variables.MetronomeLabelVariable;
import javax.inject.Inject;
import net.runelite.client.util.HotkeyListener;

// Offsets every {metronome} tile to start counting rom the tick this hotkey is pressed.
class MetronomeResetHotkeyListener extends HotkeyListener
{
	private final MetronomeLabelVariable metronome;

	@Inject
	private MetronomeResetHotkeyListener(GroundMarkerVariablesConfig config, MetronomeLabelVariable metronome)
	{
		super(config::resetMetronomeHotkey);
		this.metronome = metronome;
	}

	@Override
	public void hotkeyPressed()
	{
		metronome.offset();
	}
}
