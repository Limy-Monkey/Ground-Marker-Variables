package com.groundmarkervariables.variables.label;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.ConditionEvaluator;
import com.groundmarkervariables.variables.support.IconNames;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.QuantityFormatter;

// {price <item>}: RuneLite's price for the item (ItemManager#getItemPrice), a held one first.
@Singleton
public class PriceLabelVariable implements LabelVariable
{
	// Excludes ?:<>=! so a trailing comparator (e.g. "{price abyssal whip > 1m}") isn't part of the name.
	private static final Pattern PATTERN = Pattern.compile("\\{price\\s+([^{}?:<>=!]+?)\\}", Pattern.CASE_INSENSITIVE);

	private final Client client;
	private final ClientThread clientThread;
	private final ItemManager itemManager;
	private final IconNames iconNames;
	private final RichText richText;
	// Every tradeable item's name, for suggestions; built once (see tradeableNames()).
	private volatile List<String> tradeableNames;

	@Inject
	private PriceLabelVariable(Client client, ClientThread clientThread, ItemManager itemManager, IconNames iconNames, RichText richText)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.itemManager = itemManager;
		this.iconNames = iconNames;
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
		String name = iconNames.itemNamed(matcher.group(1).trim());
		Integer id = name == null ? null : iconNames.id(IconNames.Kind.ITEM, name);
		return id == null ? ConditionEvaluator.NULL : String.valueOf(itemManager.getItemPrice(id));
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		String name = iconNames.itemNamed(matcher.group(1).trim());
		Integer id = name == null ? null : iconNames.id(IconNames.Kind.ITEM, name);
		return id == null ? null : richText.labeled(name, QuantityFormatter.quantityToStackSize(itemManager.getItemPrice(id)));
	}

	// Every item name whose lowest ID is tradeable. Built once on the client thread, after the item names load; empty
	// until then.
	public List<String> tradeableNames()
	{
		List<String> names = tradeableNames;
		if (names != null)
		{
			return names;
		}
		if (!client.isClientThread())
		{
			clientThread.invoke(this::tradeableNames);
			return List.of();
		}

		List<String> all = iconNames.matches(IconNames.Kind.ITEM, "");
		if (all.isEmpty())
		{
			return List.of();
		}
		List<String> tradeable = new ArrayList<>();
		for (String name : all)
		{
			Integer id = iconNames.lowestId(IconNames.Kind.ITEM, name);
			if (id != null && client.getItemDefinition(id).isTradeable())
			{
				tradeable.add(name);
			}
		}
		tradeableNames = Collections.unmodifiableList(tradeable);
		return tradeableNames;
	}
}
