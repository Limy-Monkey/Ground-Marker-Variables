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

	@ConfigItem(
		position = 3,
		keyName = "advancedLabelEditor",
		name = "Advanced Label Editor",
		description = "Replace Ground Markers' Label editor with our own. When off, Ground Markers' stock editor is used untouched."
	)
	default boolean advancedLabelEditor()
	{
		return false;
	}

	@ConfigSection(
		name = "Party Sync",
		description = "Sync {metronome} to a party member's tick count.",
		position = 4
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

	@ConfigSection(
		name = "Examples",
		description = "Quick reference for available variables and example labels — see the README for full details.",
		position = 5,
		closedByDefault = true
	)
	String examplesSection = "examples";

	// void return type + empty description: renders as a plain HTML label with no input
	// control (see ConfigPanel's type dispatch), used here purely as inline help text.
	@ConfigItem(
		position = 1,
		keyName = "variablesHelp",
		name = "<html><b>Variables:</b><blockquote style=\"margin-left: 10px\">"
			+ "- {rsn}<br>- {spellbook}<br>"
			+ "- {metronome&lt;N&gt;} / {metronome&lt;N&gt;_&lt;M&gt;}<br>"
			+ "- {weapon}<br>- {attackStyle}<br>"
			+ "- {lvl_&lt;skill&gt;}<br>- {boost_&lt;skill&gt;}<br>"
			+ "- {miscellania} (0-127)<br>"
			+ "- {hasThralls}<br>- {hasAlchs}<br>- {hasFreeze}<br>- {hasEntangle}<br>"
			+ "- {hasItem &lt;name&gt;}<br>"
			+ "- {&lt;cond1&gt; [&amp;&amp; / || &lt;cond2&gt;] ? A : B}"
			+ "</blockquote>",
		description = "",
		section = examplesSection
	)
	default void variablesHelp()
	{
	}

	@ConfigItem(
		position = 2,
		keyName = "examplesHelp",
		name = "<html><b>Examples:</b><blockquote style=\"margin-left: 10px\">"
			+ "- You are currently on the {spellbook} spellbook!<br>"
			+ "- {lvl_agility &lt; 87 ? Bring Summer Pie! : }<br>"
			+ "- {hasFreeze &amp;&amp; weapon == staff of the dead ? Gigachad : Noob}<br>"
			+ "- {metronome4}<br>"
			+ "- This text is &lt;col=teal&gt;teal!"
			+ "</blockquote>",
		description = "",
		section = examplesSection
	)
	default void examplesHelp()
	{
	}
}
