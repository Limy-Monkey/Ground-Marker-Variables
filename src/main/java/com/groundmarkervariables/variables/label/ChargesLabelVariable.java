package com.groundmarkervariables.variables.label;

import com.groundmarkervariables.GroundMarkerVariablesConfig;
import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.ConditionEvaluator;
import com.groundmarkervariables.variables.support.Emojis;
import com.groundmarkervariables.variables.support.IconNames;
import com.groundmarkervariables.variables.support.ItemCharges;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;

// {charges <item>[, icon][, icons][, <stored item>]...}: an item's saved charges (see ItemCharges). Bare {charges}: whether
// Item Charges Improved is on.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ChargesLabelVariable implements LabelVariable
{
	// Excludes ?:<>=! so a trailing comparator (e.g. "{charges toxic blowpipe < 100}") isn't part of the name.
	private static final Pattern PATTERN = Pattern.compile("\\{charges(?:\\s+([^{}?:<>=!]+?))?\\s*\\}", Pattern.CASE_INSENSITIVE);
	private static final String IMPROVED_NAME = "Item Charges Improved";
	private static final String ICON = "icon";
	private static final String ICONS = "icons";
	private static final String UNLIMITED_PLAIN = "999999";
	private static final String INFINITY = ":math_infinity:";
	private static final String INFINITY_HIGHLIGHTED = ":math_infinity@highlight:";

	private final ItemCharges itemCharges;
	private final IconNames iconNames;
	private final GroundMarkerVariablesConfig config;
	private final Emojis emojis;
	private final RichText richText;

	// "<item>, icon, icons, <stored item>, ..." split up.
	private static final class Arguments
	{
		private final String item;
		private final boolean icon;
		private final boolean icons;
		private final List<String> only = new ArrayList<>();

		private Arguments(String text)
		{
			String[] parts = text.split(",");
			item = parts[0];
			boolean wantsIcon = false;
			boolean wantsIcons = false;
			for (int i = 1; i < parts.length; i++)
			{
				String part = parts[i].trim();
				if (part.equalsIgnoreCase(ICON))
				{
					wantsIcon = true;
				}
				else if (part.equalsIgnoreCase(ICONS))
				{
					wantsIcons = true;
				}
				else if (!part.isEmpty())
				{
					only.add(part);
				}
			}
			icon = wantsIcon;
			icons = wantsIcons;
		}
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		if (matcher.group(1) == null)
		{
			return String.valueOf(itemCharges.improvedActive());
		}
		Arguments arguments = new Arguments(matcher.group(1));
		if (arguments.icon || arguments.icons)
		{
			return iconsOf(arguments);
		}
		Integer charges = charges(arguments);
		if (charges == null)
		{
			return ConditionEvaluator.NULL;
		}
		return charges == ItemCharges.UNLIMITED_CHARGES ? UNLIMITED_PLAIN : String.valueOf(charges);
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		if (matcher.group(1) == null)
		{
			return richText.booleanColored(itemCharges.improvedActive(), IMPROVED_NAME);
		}
		Arguments arguments = new Arguments(matcher.group(1));
		if (arguments.icon || arguments.icons)
		{
			return iconsOf(arguments);
		}
		Integer charges = charges(arguments);
		if (charges == null)
		{
			return null;
		}
		String label = ItemCharges.readable(arguments.item) + " charges";
		// The symbol carries its own highlight.
		return charges == ItemCharges.UNLIMITED_CHARGES ? label + ": " + infinity() : richText.labeled(label, String.valueOf(charges));
	}

	private Integer charges(Arguments arguments)
	{
		return arguments.only.isEmpty() ? itemCharges.charges(arguments.item) : itemCharges.charges(arguments.item, arguments.only);
	}

	// The item's icon and charges (icon), then each stored item's icon and how many (icons); null if none apply.
	private String iconsOf(Arguments arguments)
	{
		List<String> parts = new ArrayList<>();
		if (arguments.icon)
		{
			Integer charges = charges(arguments);
			if (charges != null)
			{
				// No icon if no item has its name (e.g. "Quetzal whistle").
				String item = iconNames.itemNamed(arguments.item);
				String icon = item == null ? "" : iconNames.expand("<item=" + item + ">");
				parts.add(icon + "x" + (charges == ItemCharges.UNLIMITED_CHARGES ? infinity() : ItemCharges.shortened(charges)));
			}
		}
		String header = null;
		if (arguments.icons)
		{
			Map<Integer, Integer> stored = itemCharges.storedItems(arguments.item, arguments.only);
			if (stored != null)
			{
				stored.forEach((id, quantity) -> parts.add("<item=" + id + ">x" + ItemCharges.shortened(quantity)));
				// With icon, its own "<icon>x<charges>" says it.
				header = arguments.icon ? null : containerLine(arguments);
			}
		}
		if (parts.isEmpty())
		{
			return null;
		}
		String icons = String.join(" ", parts);
		return header == null ? icons : header + "\\n" + icons;
	}

	// "<Container>: <combined charges>", above its stored items; null if it has none.
	private String containerLine(Arguments arguments)
	{
		Integer charges = charges(arguments);
		if (charges == null)
		{
			return null;
		}
		String name = ItemCharges.readable(arguments.item);
		return charges == ItemCharges.UNLIMITED_CHARGES ? name + ": " + infinity() : richText.labeled(name, String.valueOf(charges));
	}

	// Highlighted only while values are.
	private String infinity()
	{
		return emojis.expand(config.highlightValue() ? INFINITY_HIGHLIGHTED : INFINITY);
	}
}
