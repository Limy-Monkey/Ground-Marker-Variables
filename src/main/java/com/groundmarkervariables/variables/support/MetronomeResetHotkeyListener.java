package com.groundmarkervariables.variables.support;

import com.groundmarkervariables.GroundMarkerVariablesConfig;
import com.groundmarkervariables.variables.label.MetronomeLabelVariable;
import javax.inject.Inject;
import net.runelite.client.util.HotkeyListener;

// Offsets every {metronome} tile to start counting from the tick this hotkey is pressed.
public class MetronomeResetHotkeyListener extends HotkeyListener {
	private final MetronomeLabelVariable metronome;

	@Inject
	private MetronomeResetHotkeyListener(GroundMarkerVariablesConfig config, MetronomeLabelVariable metronome) {
		super(config::resetMetronomeHotkey);
		this.metronome = metronome;
	}

	@Override
	public void hotkeyPressed() {
		metronome.reset();
	}
}
