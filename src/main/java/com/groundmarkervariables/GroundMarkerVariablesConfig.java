package com.groundmarkervariables;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;

@ConfigGroup(GroundMarkerVariablesConfig.GROUP)
public interface GroundMarkerVariablesConfig extends Config
{
	String GROUP = "groundMarkerVariables";

	@ConfigItem(
		keyName = "resetMetronomeHotkey",
		name = "Reset metronome",
		description = "Hotkey that resets every {metronome} tile's countdown to start from this tick."
	)
	default Keybind resetMetronomeHotkey()
	{
		return Keybind.NOT_SET;
	}
}
