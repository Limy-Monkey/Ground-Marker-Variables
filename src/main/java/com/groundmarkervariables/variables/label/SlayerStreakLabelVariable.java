package com.groundmarkervariables.variables.label;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

// {slayerStreak} -> current slayer task streak. Reads raw varbits, same as core SlayerPlugin's
// own counter tooltip -- no dependency on that plugin being enabled.
public class SlayerStreakLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{slayerStreak\\}", Pattern.CASE_INSENSITIVE);
	private static final int KRYSTILIA_SLAYER_MASTER = 7;
	private static final int MORTIMER_SLAYER_MASTER = 10;

	private final Client client;
	private final RichText richText;

	@Inject
	private SlayerStreakLabelVariable(Client client, RichText richText)
	{
		this.client = client;
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
		return String.valueOf(streak());
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		return "You've completed " + richText.highlightValue(streak() + " tasks")
			+ " in a row and currently have a total of "
			+ richText.highlightValue(client.getVarbitValue(VarbitID.SLAYER_POINTS) + " points") + ".";
	}

	private int streak()
	{
		switch (client.getVarbitValue(VarbitID.SLAYER_MASTER))
		{
			case KRYSTILIA_SLAYER_MASTER:
				return client.getVarbitValue(VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED);
			case MORTIMER_SLAYER_MASTER:
				return client.getVarpValue(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED);
			default:
				return client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED);
		}
	}
}
