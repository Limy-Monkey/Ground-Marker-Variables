package com.groundmarkervariables.variables.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.StructComposition;
import net.runelite.client.callback.ClientThread;

// Every collection log page and item, from the client's own cache. Built on first use (client thread only).
@Singleton
public class CollectionLog
{
	private static final int TABS_ENUM = 2102;
	private static final int TAB_PAGES_PARAM = 683;
	private static final int PAGE_NAME_PARAM = 689;
	private static final int PAGE_ITEMS_PARAM = 690;
	private static final String ALL_PETS_PAGE = "All Pets";

	private final Client client;
	private final ClientThread clientThread;

	// Set last, once the rest is built.
	private volatile Set<Integer> items;
	private Set<Integer> pets;
	private List<String> pageNames;

	@Inject
	private CollectionLog(Client client, ClientThread clientThread)
	{
		this.client = client;
		this.clientThread = clientThread;
	}

	// Every page's name, in the log's order. Off the client thread before the first build: empty, the build queued.
	public List<String> pageNames()
	{
		if (items == null && !client.isClientThread())
		{
			clientThread.invoke(this::load);
			return List.of();
		}
		load();
		return pageNames;
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
		List<String> names = new ArrayList<>();
		for (int tabStruct : client.getEnum(TABS_ENUM).getIntVals())
		{
			int pagesEnum = client.getStructComposition(tabStruct).getIntValue(TAB_PAGES_PARAM);
			for (int pageStruct : client.getEnum(pagesEnum).getIntVals())
			{
				StructComposition page = client.getStructComposition(pageStruct);
				String name = page.getStringValue(PAGE_NAME_PARAM);
				names.add(name);
				boolean petsPage = ALL_PETS_PAGE.equals(name);
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

		pets = allPets;
		pageNames = Collections.unmodifiableList(names);
		items = allItems;
	}
}
