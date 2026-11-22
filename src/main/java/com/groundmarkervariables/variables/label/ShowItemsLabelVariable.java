package com.groundmarkervariables.variables.label;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.Args;
import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.PartyMemberVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.IconNames;
import com.groundmarkervariables.variables.support.ItemQuantityFinder;
import com.groundmarkervariables.variables.support.ItemQuantityFinder.Match;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import net.runelite.client.party.PartyMember;

// {showItems <name>, <name>, ...} (or {showItem}) -> Plain: "true" if any listed name matches an item you hold (same
// scope as {hasItem}), else "false". Rich: the icon of each listed name's first matching item, in the listed order, or
// empty if none match. Every item is suggested for each name, carried ones first.
public class ShowItemsLabelVariable implements LabelVariable, PartyMemberVariable
{
	private final Client client;
	private final ItemManager itemManager;
	private final IconNames iconNames;
	// {showOneItem}: only the first.
	private final boolean onlyFirst;
	private final Args args;

	@Inject
	private ShowItemsLabelVariable(Client client, ItemManager itemManager, IconNames iconNames)
	{
		this(client, itemManager, iconNames, false);
	}

	private ShowItemsLabelVariable(Client client, ItemManager itemManager, IconNames iconNames, boolean onlyFirst)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.iconNames = iconNames;
		this.onlyFirst = onlyFirst;
		this.args = Args.of(onlyFirst ? "showOneItem" : "showItems").alias(onlyFirst ? "showOneItems" : "showItem")
			.main(this::itemNames, Args.ITEM_ICON)
			.extras(main -> itemNames(), Args.ITEM_ICON);
	}

	// {showOneItem <name>, <name>, ...} (or {showOneItems}): only the first held one's icon.
	public static class ShowOneItem extends ShowItemsLabelVariable
	{
		@Inject
		private ShowOneItem(Client client, ItemManager itemManager, IconNames iconNames)
		{
			super(client, itemManager, iconNames, true);
		}
	}

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
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		return String.valueOf(!found(matcher, this::findLocal).isEmpty());
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		return icons(found(matcher, this::findLocal));
	}

	@Override
	public String resolvePlain(Matcher matcher, PartyMember member)
	{
		return String.valueOf(!found(matcher, search -> ItemQuantityFinder.findAll(member, itemManager, search)).isEmpty());
	}

	@Override
	public String resolveRich(Matcher matcher, PartyMember member)
	{
		return icons(found(matcher, search -> ItemQuantityFinder.findAll(member, itemManager, search)));
	}

	@Override
	public String resolveRowRich(Matcher matcher, RichText richText)
	{
		return resolveRich(matcher);
	}

	// Every item's name, carried ones first (see IconNames#matches).
	private List<String> itemNames()
	{
		return iconNames.matches(IconNames.Kind.ITEM, "");
	}

	private List<Match> findLocal(String search)
	{
		return ItemQuantityFinder.findAll(client, itemManager, search);
	}

	private String icons(Set<Integer> ids)
	{
		StringBuilder icons = new StringBuilder();
		for (int id : ids)
		{
			icons.append("<item=").append(id).append('>');
			if (onlyFirst)
			{
				break;
			}
		}
		return icons.toString();
	}

	// Each listed name's first matching item id, in the listed order, without repeats.
	private Set<Integer> found(Matcher matcher, Function<String, List<Match>> find)
	{
		List<String> names = new ArrayList<>();
		names.add(args.main(matcher));
		names.addAll(args.options(matcher).extras());

		Set<Integer> ids = new LinkedHashSet<>();
		for (String name : names)
		{
			if (name == null)
			{
				continue;
			}
			List<Match> matches = find.apply(name.toLowerCase(Locale.ROOT));
			if (!matches.isEmpty())
			{
				ids.add(matches.get(0).id);
			}
		}
		return ids;
	}
}
