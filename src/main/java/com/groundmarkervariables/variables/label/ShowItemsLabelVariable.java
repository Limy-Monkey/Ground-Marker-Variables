package com.groundmarkervariables.variables.label;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.PartyMemberVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.ItemQuantityFinder;
import com.groundmarkervariables.variables.support.ItemQuantityFinder.Match;
import java.util.function.Function;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import net.runelite.client.party.PartyMember;

// {showItems <name>, <name>, ...} (or {showItem}) -> Plain: "true" if any listed name matches an
// item you hold (same scope as {hasItem}), else "false". Rich: the icon of each listed name's first
// matching item, in the listed order, or empty if none match. {showOneItem(s)} shows only the first.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ShowItemsLabelVariable implements LabelVariable, PartyMemberVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{show(One)?Items?\\s+([^{}?:<>=!]+?)\\s*\\}", Pattern.CASE_INSENSITIVE);

	private final Client client;
	private final ItemManager itemManager;

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		return String.valueOf(!found(matcher.group(2), this::findLocal).isEmpty());
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		return icons(found(matcher.group(2), this::findLocal), matcher.group(1) != null);
	}

	@Override
	public String resolvePlain(Matcher matcher, PartyMember member)
	{
		return String.valueOf(!found(matcher.group(2), search -> ItemQuantityFinder.findAll(member, itemManager, search)).isEmpty());
	}

	@Override
	public String resolveRich(Matcher matcher, PartyMember member)
	{
		return icons(found(matcher.group(2), search -> ItemQuantityFinder.findAll(member, itemManager, search)), matcher.group(1) != null);
	}

	@Override
	public String resolveRowRich(Matcher matcher, RichText richText)
	{
		return resolveRich(matcher);
	}

	private List<Match> findLocal(String search)
	{
		return ItemQuantityFinder.findAll(client, itemManager, search);
	}

	private static String icons(Set<Integer> ids, boolean onlyFirst)
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
	private static Set<Integer> found(String names, Function<String, List<Match>> find)
	{
		Set<Integer> ids = new LinkedHashSet<>();
		for (String name : names.split(","))
		{
			String search = name.trim().toLowerCase(Locale.ROOT);
			if (search.isEmpty())
			{
				continue;
			}

			List<Match> matches = find.apply(search);
			if (!matches.isEmpty())
			{
				ids.add(matches.get(0).id);
			}
		}
		return ids;
	}
}
