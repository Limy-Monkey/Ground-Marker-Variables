package com.groundmarkervariables.variables;

import com.groundmarkervariables.GroundMarkerVariablesConfig;
import java.awt.Color;
import java.util.List;
import java.util.function.ToIntFunction;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.QuantityFormatter;

// Shared Rich Text formatting. Colors are raw hex (via ColorUtil), not <col=NAME> aliases --
// those only expand on the raw label before LabelResolver runs (see CachedMarker), so a
// variable's own output would never get expanded.
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class RichText
{
	private static final int DOT_COUNT = 3;

	private final GroundMarkerVariablesConfig config;

	public String labeled(String label, String value)
	{
		return label + ": " + highlightValue(value);
	}

	public String highlightValue(String value)
	{
		return config.highlightValue() ? colored(config.highlightColor(), value) : value;
	}

	// Loading placeholder: one highlighted dot cycling each tick, e.g. ".<col>.</col>.".
	public String animatedDots(int tickCount)
	{
		StringBuilder dots = new StringBuilder();
		for (int i = 0; i < DOT_COUNT; i++)
		{
			dots.append(i == tickCount % DOT_COUNT ? highlightValue(".") : ".");
		}
		return dots.toString();
	}

	public String booleanColored(boolean value, String text)
	{
		return colored(value ? config.booleanTrueColor() : config.booleanFalseColor(), text);
	}

	// Item icons as "<item=ID>xN" (N as an item stack shows it, and only over 1), spaced, perLine to a row, at most max: rows
	// split by a line break, and the first row too if afterTitle. {loot}'s, {log}'s and {charges}'.
	public static <T> String itemRows(List<T> items, ToIntFunction<T> id, ToIntFunction<T> quantity, int perLine, int max,
		boolean afterTitle)
	{
		perLine = Math.max(1, perLine);
		StringBuilder sb = new StringBuilder();
		for (int shown = 0; shown < Math.min(max, items.size()); shown++)
		{
			if (shown % perLine != 0)
			{
				sb.append(' ');
			}
			else if (shown > 0 || afterTitle)
			{
				sb.append("\\n");
			}
			T item = items.get(shown);
			sb.append("<item=").append(id.applyAsInt(item)).append('>');
			if (quantity.applyAsInt(item) > 1)
			{
				sb.append('x').append(QuantityFormatter.quantityToStackSize(quantity.applyAsInt(item)));
			}
		}
		return sb.toString();
	}

	// Not config-driven -- lets a caller force an explicit color (see
	// SixHourTimeRemainingLabelVariable/BooleanVariable's warning special case).
	public static String colored(Color color, String text)
	{
		return "<col=" + ColorUtil.colorToHexCode(color) + ">" + text + "</col>";
	}
}
