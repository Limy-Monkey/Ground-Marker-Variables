package com.groundmarkervariables.variables;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.slayer.SlayerPlugin;
import net.runelite.client.plugins.slayer.SlayerPluginService;

// {slayerTask} -> current task monster name. getInitialAmount() already includes a count-type
// Mortimer modifier (SlayerPlugin adds it before exposing the value), so we don't add it again
// -- but other modifier kinds (e.g. points) share the same value/negative varbits, so the
// displayed Modifier text shows any nonzero value, not just count-type ones.
class SlayerTaskLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{slayerTask\\}", Pattern.CASE_INSENSITIVE);
	private static final String DISABLED_TEXT = "N/A";
	private static final String DISABLED_RICH_TEXT = "Enable Slayer Plugin";
	private static final String NO_TASK_TEXT = "None";
	private static final int MORTIMER_SLAYER_MASTER = 10;

	private final Client client;
	private final SlayerPluginService slayerPluginService;
	private final PluginManager pluginManager;
	private final RichText richText;

	@Inject
	private SlayerTaskLabelVariable(Client client, SlayerPluginService slayerPluginService,
		PluginManager pluginManager, RichText richText)
	{
		this.client = client;
		this.slayerPluginService = slayerPluginService;
		this.pluginManager = pluginManager;
		this.richText = richText;
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		if (!isSlayerPluginRunning())
		{
			return DISABLED_TEXT;
		}

		String task = slayerPluginService.getTask();
		return task == null ? NO_TASK_TEXT : task;
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		if (!isSlayerPluginRunning())
		{
			return DISABLED_RICH_TEXT;
		}

		String task = slayerPluginService.getTask();
		if (task == null)
		{
			return "Slayer Task: " + richText.highlightValue(NO_TASK_TEXT);
		}

		int total = slayerPluginService.getInitialAmount();
		int remaining = slayerPluginService.getRemainingAmount();

		StringBuilder result = new StringBuilder("Slayer Task: ")
			.append(richText.highlightValue(task))
			.append(" (").append(remaining).append(" / ").append(total).append(")");

		String location = slayerPluginService.getTaskLocation();
		if (location != null && !location.isEmpty())
		{
			result.append(" Location: ").append(richText.highlightValue(location));
		}

		if (client.getVarbitValue(VarbitID.SLAYER_MASTER) == MORTIMER_SLAYER_MASTER)
		{
			String modifier = modifierText();
			if (modifier != null)
			{
				result.append(" Modifier: ").append(richText.highlightValue(modifier));
			}
		}

		return result.toString();
	}

	private String modifierText()
	{
		int value = client.getVarbitValue(VarbitID.SLAYER_MODIFIER_VALUE);
		if (value == 0)
		{
			return null;
		}

		boolean negative = client.getVarbitValue(VarbitID.SLAYER_MODIFIER_NEGATIVE) == 1;
		int signed = negative ? -value : value;
		String number = (signed > 0 ? "+" : "") + signed;

		SlayerModifierType type = SlayerModifierType.fromId(client.getVarbitValue(VarbitID.SLAYER_MODIFIER_ID));
		return type == null ? number : number + " " + type.displayName();
	}

	private boolean isSlayerPluginRunning()
	{
		return pluginManager.getPlugins().stream()
			.anyMatch(plugin -> plugin.getClass() == SlayerPlugin.class && pluginManager.isPluginActive(plugin));
	}
}
