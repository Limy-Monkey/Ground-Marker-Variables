package com.groundmarkervariables;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;

@ConfigGroup(GroundMarkerVariablesConfig.GROUP)
public interface GroundMarkerVariablesConfig extends Config
{
	String GROUP = "groundMarkerVariables";

	@ConfigItem(
		position = 1,
		keyName = "resetMetronomeHotkey",
		name = "Reset metronome",
		description = "Hotkey that resets every {metronome} tile's countdown to start from this tick."
	)
	default Keybind resetMetronomeHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		position = 2,
		keyName = "countDown",
		name = "Count Down",
		description = "Count {metronome} down from N to 1 instead of up from 1 to N."
	)
	default boolean countDown()
	{
		return false;
	}

	@ConfigSection(
		name = "Party Sync",
		description = "Sync {metronome} to a party member's tick count.",
		position = 3
	)
	String partySyncSection = "partySync";

	@ConfigItem(
		position = 1,
		keyName = "enablePartySync",
		name = "Party Sync",
		description = "Sync {metronome} to a party member's tick count.",
		section = partySyncSection
	)
	default boolean enablePartySync()
	{
		return false;
	}

	@ConfigItem(
		position = 2,
		keyName = "syncTarget",
		name = "Sync Target",
		description = "Display name of the party member to sync {metronome} to.\nThey need Ground Marker Variables installed and must be in the same party",
		section = partySyncSection
	)
	default String syncTarget()
	{
		return "";
	}
}
