package com.groundmarkervariables.variables.support;

import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.StructComposition;

// Every collection log item, from the client's own cache. Built on first use (client thread only).
@Singleton
public class CollectionLog
{
	private static final int TABS_ENUM = 2102;
	private static final int TAB_PAGES_PARAM = 683;
	private static final int PAGE_NAME_PARAM = 689;
	private static final int PAGE_ITEMS_PARAM = 690;
	private static final String ALL_PETS_PAGE = "All Pets";

	private final Client client;

	private Set<Integer> items;
	private Set<Integer> pets;

	@Inject
	private CollectionLog(Client client)
	{
		this.client = client;
	}

	public boolean contains(int itemId)
	{
		load();
		return items.contains(itemId);
	}

	public boolean isPet(int itemId)
	{
		load();
		return pets.contains(itemId);
	}

	private void load()
	{
		if (items != null)
		{
			return;
		}

		Set<Integer> allItems = new HashSet<>();
		Set<Integer> allPets = new HashSet<>();
		for (int tabStruct : client.getEnum(TABS_ENUM).getIntVals())
		{
			int pagesEnum = client.getStructComposition(tabStruct).getIntValue(TAB_PAGES_PARAM);
			for (int pageStruct : client.getEnum(pagesEnum).getIntVals())
			{
				StructComposition page = client.getStructComposition(pageStruct);
				boolean petsPage = ALL_PETS_PAGE.equals(page.getStringValue(PAGE_NAME_PARAM));
				for (int itemId : client.getEnum(page.getIntValue(PAGE_ITEMS_PARAM)).getIntVals())
				{
					allItems.add(itemId);
					if (petsPage)
					{
						allPets.add(itemId);
					}
				}
			}
		}

		items = allItems;
		pets = allPets;
	}
}
