package com.groundmarkervariables.variables;

import com.groundmarkervariables.GroundMarkerVariablesConfig;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;

// {autoRetaliate} (or its {autoRetal} alias) -> true if Auto Retaliate is on. Varp 172, 0 =
// on, nonzero = off — RuneLite hasn't named this one in VarPlayerID; confirmed against the
// Auto Retaliate Warning plugin (https://github.com/ste-h/auto-retaliate-warning), which
// reads it the same way.
class AutoRetaliateLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{autoRetal(?:iate)?\\}", Pattern.CASE_INSENSITIVE);
	private static final int AUTO_RETALIATE_VARP = 172;

	private final Client client;
	private final RichText richText;
	private final GroundMarkerVariablesConfig config;

	@Inject
	private AutoRetaliateLabelVariable(Client client, RichText richText, GroundMarkerVariablesConfig config)
	{
		this.client = client;
		this.richText = richText;
		this.config = config;
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		return String.valueOf(autoRetaliateOn());
	}

	// Invert Auto Retaliate config flips which state renders as Boolean True Color.
	@Override
	public String resolveRich(Matcher matcher)
	{
		boolean autoRetaliateOn = autoRetaliateOn();
		boolean colorAsTrue = config.invertAutoRetaliate() ? !autoRetaliateOn : autoRetaliateOn;
		return richText.booleanColored(colorAsTrue, "Auto Retaliate");
	}

	private boolean autoRetaliateOn()
	{
		return client.getVarpValue(AUTO_RETALIATE_VARP) == 0;
	}
}
