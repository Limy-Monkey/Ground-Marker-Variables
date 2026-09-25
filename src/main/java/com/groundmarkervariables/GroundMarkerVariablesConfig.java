package com.groundmarkervariables;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup(GroundMarkerVariablesConfig.GROUP)
public interface GroundMarkerVariablesConfig extends Config
{
	String GROUP = "groundMarkerVariables";

	// Recreates core Ground Markers' own config, since this plugin fully replaces it (see the
	// migration plan) — values are copied from core's config the very first time this plugin
	// ever starts up (see GroundMarkerVariablesPlugin#migrateConfigFromCore), so existing users
	// keep their current settings.
	@ConfigSection(
		name = "Ground Markers",
		description = "Tile marker appearance settings, migrated from RuneLite's core Ground Markers plugin.",
		position = 1
	)
	String groundMarkersSection = "groundMarkers";

	@ConfigItem(
		position = 1,
		keyName = "borderWidth",
		name = "Border width",
		description = "Width of the marked tile border.",
		section = groundMarkersSection
	)
	default double borderWidth()
	{
		return 2;
	}

	@ConfigItem(
		position = 2,
		keyName = "drawOnMinimap",
		name = "Draw tiles on minimap",
		description = "Configures whether marked tiles should be drawn on minimap.",
		section = groundMarkersSection
	)
	default boolean drawTileOnMinimap()
	{
		return false;
	}

	@Range(
		max = 255
	)
	@ConfigItem(
		position = 3,
		keyName = "fillOpacity",
		name = "Fill opacity",
		description = "Opacity of the tile fill color.",
		section = groundMarkersSection
	)
	default int fillOpacity()
	{
		return 50;
	}

	@ConfigItem(
		position = 4,
		keyName = "showImportExport",
		name = "Show import/export/clear options",
		description = "Show the Import, Export, and Clear options on the world map orb right-click menu.",
		section = groundMarkersSection
	)
	default boolean showImportExport()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		position = 5,
		keyName = "markerColor",
		name = "Tile color",
		description = "The default color for marked tiles.",
		section = groundMarkersSection
	)
	default Color markerColor()
	{
		return Color.YELLOW;
	}

	@ConfigSection(
		name = "Metronome",
		description = "Settings for {metronome} tiles.",
		position = 2
	)
	String metronomeSection = "metronome";

	@ConfigItem(
		position = 1,
		keyName = "resetMetronomeHotkey",
		name = "Reset metronome",
		description = "Hotkey that resets every {metronome} tile's countdown to start from this tick.",
		section = metronomeSection
	)
	default Keybind resetMetronomeHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		position = 2,
		keyName = "countDown",
		name = "Count Down",
		description = "Count {metronome} down from N to 1 instead of up from 1 to N.",
		section = metronomeSection
	)
	default boolean countDown()
	{
		return false;
	}

	@ConfigItem(
		position = 3,
		keyName = "highlightFinalTick",
		name = "Highlight Final Tick",
		description = "Color {metronome} on its final tick before it repeats.",
		section = metronomeSection
	)
	default boolean highlightFinalTick()
	{
		return false;
	}

	@ConfigItem(
		position = 4,
		keyName = "finalTickColor",
		name = "Final Tick Color",
		description = "Color to use on {metronome}'s final tick when Highlight Final Tick is on.",
		section = metronomeSection
	)
	default Color finalTickColor()
	{
		return Color.CYAN;
	}

	@ConfigSection(
		name = "Party Sync",
		description = "Sync {metronome} to a party member's tick count.",
		position = 3,
		closedByDefault = true
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
		name = "Advanced Editor",
		description = "Settings for the Advanced Label Editor.",
		position = 4,
		closedByDefault = true
	)
	String advancedEditorSection = "advancedEditor";

	@ConfigItem(
		position = 1,
		keyName = "useAdvancedLabelEditor",
		name = "Use Advanced Label Editor",
		description = "Replace the plain Tile label prompt with the Advanced Label Editor.",
		section = advancedEditorSection
	)
	default boolean useAdvancedLabelEditor()
	{
		return true;
	}

	@ConfigItem(
		position = 2,
		keyName = "showCurrent",
		name = "Show Current",
		description = "Show the \"Current\" section of the Advanced Label Editor's recommendations.",
		section = advancedEditorSection
	)
	default boolean showCurrent()
	{
		return true;
	}

	@ConfigItem(
		position = 3,
		keyName = "showRecent",
		name = "Show Recent",
		description = "Show the \"Recent\" section of the Advanced Label Editor's recommendations.",
		section = advancedEditorSection
	)
	default boolean showRecent()
	{
		return true;
	}

	@ConfigItem(
		position = 4,
		keyName = "showNearby",
		name = "Show Nearby",
		description = "Show the \"Nearby\" section of the Advanced Label Editor's recommendations.",
		section = advancedEditorSection
	)
	default boolean showNearby()
	{
		return true;
	}

	@ConfigItem(
		position = 5,
		keyName = "autocomplete",
		name = "Autocomplete",
		description = "Autocomplete variable names, skills, colors, and metronome parameters while typing in the Advanced Label Editor.",
		section = advancedEditorSection
	)
	default boolean autocomplete()
	{
		return true;
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
			+ "- {rsn}<br>- {time} / {time24}<br>- {spellbook}<br>"
			+ "- {metronome&lt;N&gt;} / {metronome&lt;N&gt;_&lt;M&gt; - X}<br>"
			+ "- {weapon}<br>- {equip_&lt;slot&gt;}<br>- {attackStyle}<br>"
			+ "- {lvl_&lt;skill&gt;}<br>- {boost_&lt;skill&gt;}<br>"
			+ "- {miscellania} (0-127)<br>- {questPoints}<br>- {kc &lt;boss&gt;}<br>"
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
			+ "- {time &gt; 10pm ? Go to bed : One more raid!}<br>"
			+ "- This text is {col=cyan}cyan!"
			+ "</blockquote>",
		description = "",
		section = examplesSection
	)
	default void examplesHelp()
	{
	}
}
