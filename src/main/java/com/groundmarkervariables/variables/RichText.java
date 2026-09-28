package com.groundmarkervariables.variables;

import com.groundmarkervariables.GroundMarkerVariablesConfig;
import java.awt.Color;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.util.ColorUtil;

// Shared Rich Text formatting. Colors are raw hex (via ColorUtil), not <col=NAME> aliases --
// those only expand on the raw label before LabelResolver runs (see CachedMarker), so a
// variable's own output would never get expanded.
@Singleton
class RichText
{
	private final GroundMarkerVariablesConfig config;

	@Inject
	private RichText(GroundMarkerVariablesConfig config)
	{
		this.config = config;
	}

	String labeled(String label, String value)
	{
		return label + ": " + highlightValue(value);
	}

	String highlightValue(String value)
	{
		return config.highlightValue() ? colored(config.highlightColor(), value) : value;
	}

	String booleanColored(boolean value, String text)
	{
		return colored(value ? config.booleanTrueColor() : config.booleanFalseColor(), text);
	}

	// Not config-driven, so package-private -- lets a caller force an explicit color (see
	// SixHourTimeRemainingLabelVariable/BooleanVariable's warning special case).
	static String colored(Color color, String text)
	{
		return "<col=" + ColorUtil.colorToHexCode(color) + ">" + text + "</col>";
	}
}
