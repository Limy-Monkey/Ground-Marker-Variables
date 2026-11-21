package com.groundmarkervariables.variables.label;

import com.groundmarkervariables.GroundMarkerVariablesConfig;
import com.groundmarkervariables.variables.Args;
import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.ConditionEvaluator;
import com.groundmarkervariables.variables.support.Emojis;
import com.groundmarkervariables.variables.support.IconNames;
import com.groundmarkervariables.variables.support.ItemCharges;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;

// {charges <item>[, icon][, icons][, <stored item>]...}: an item's saved charges (see ItemCharges). Bare {charges}: whether
// Item Charges Improved is on.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ChargesLabelVariable implements LabelVariable
{
	private static final String IMPROVED_NAME = "Item Charges Improved";
	private static final String UNLIMITED_PLAIN = "999999";
	private static final String INFINITY = ":math_infinity:";
	private static final String INFINITY_HIGHLIGHTED = ":math_infinity@highlight:";

	private final ItemCharges itemCharges;
	private final IconNames iconNames;
	private final GroundMarkerVariablesConfig config;
	private final Emojis emojis;
	private final RichText richText;
	// The names of what each container holds, by lowercase item (see storedNames()).
	private final Map<String, List<String>> storedByItem = new ConcurrentHashMap<>();

	private final Args args = Args.of("charges")
		.main(this::itemNames, Args.ITEM_ICON)
		.optional()
		.flag("icon")
		.flag("icons", item -> !storedNames(item).isEmpty())
		.extras(this::storedNames, Args.ITEM_ICON);

	@Override
	public Pattern pattern()
	{
		return args.pattern();
	}

	@Override
	public Args args()
	{
		return args;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		String item = args.main(matcher);
		if (item == null)
		{
			return String.valueOf(itemCharges.improvedActive());
		}
		Args.Options options = args.options(matcher);
		if (options.flag("icon") || options.flag("icons"))
		{
			return iconsOf(item, options);
		}
		Integer charges = charges(item, options);
		if (charges == null)
		{
			return ConditionEvaluator.NULL;
		}
		return charges == ItemCharges.UNLIMITED_CHARGES ? UNLIMITED_PLAIN : String.valueOf(charges);
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		String item = args.main(matcher);
		if (item == null)
		{
			return richText.booleanColored(itemCharges.improvedActive(), IMPROVED_NAME);
		}
		Args.Options options = args.options(matcher);
		if (options.flag("icon") || options.flag("icons"))
		{
			return iconsOf(item, options);
		}
		Integer charges = charges(item, options);
		if (charges == null)
		{
			return null;
		}
		String label = ItemCharges.readable(item) + " charges";
		// The symbol carries its own highlight.
		return charges == ItemCharges.UNLIMITED_CHARGES ? label + ": " + infinity() : richText.labeled(label, String.valueOf(charges));
	}

	// The items with saved charges, by their in-game names where one matches (see IconNames#itemNamed).
	private Collection<String> itemNames()
	{
		Set<String> names = new LinkedHashSet<>();
		for (String name : itemCharges.names())
		{
			String item = iconNames.itemNamed(name);
			names.add(item != null ? item : name);
		}
		return names;
	}

	// What item's container holds, for icons and the stored-item extras. Looked up where it can be (client thread),
	// else the last names seen.
	private List<String> storedNames(@Nullable String item)
	{
		if (item == null)
		{
			return List.of();
		}
		String key = item.toLowerCase(Locale.ROOT);
		List<String> stored = itemCharges.storedNames(item);
		if (stored != null)
		{
			storedByItem.put(key, stored);
			return stored;
		}
		return storedByItem.getOrDefault(key, List.of());
	}

	// Just the stored items given, if any.
	private Integer charges(String item, Args.Options options)
	{
		return options.extras().isEmpty() ? itemCharges.charges(item) : itemCharges.charges(item, options.extras());
	}

	// The item's icon and charges (icon), then each stored item's icon and how many (icons); null if none apply.
	private String iconsOf(String item, Args.Options options)
	{
		List<String> parts = new ArrayList<>();
		if (options.flag("icon"))
		{
			Integer charges = charges(item, options);
			if (charges != null)
			{
				// No icon if no item has its name (e.g. "Quetzal whistle").
				String named = iconNames.itemNamed(item);
				String icon = named == null ? "" : iconNames.expand("<item=" + named + ">");
				parts.add(icon + "x" + (charges == ItemCharges.UNLIMITED_CHARGES ? infinity() : ItemCharges.shortened(charges)));
			}
		}
		String header = null;
		if (options.flag("icons"))
		{
			Map<Integer, Integer> stored = itemCharges.storedItems(item, options.extras());
			if (stored != null)
			{
				stored.forEach((id, quantity) -> parts.add("<item=" + id + ">x" + ItemCharges.shortened(quantity)));
				// With icon, its own "<icon>x<charges>" says it.
				header = options.flag("icon") ? null : containerLine(item, options);
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
	private String containerLine(String item, Args.Options options)
	{
		Integer charges = charges(item, options);
		if (charges == null)
		{
			return null;
		}
		String name = ItemCharges.readable(item);
		return charges == ItemCharges.UNLIMITED_CHARGES ? name + ": " + infinity() : richText.labeled(name, String.valueOf(charges));
	}

	// Highlighted only while values are.
	private String infinity()
	{
		return emojis.expand(config.highlightValue() ? INFINITY_HIGHLIGHTED : INFINITY);
	}
}
