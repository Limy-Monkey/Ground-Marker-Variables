package com.groundmarkervariables.variables.support;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.EnumID;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ParamID;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;

// Current attack style, plus each weapon's category and each category's last style seen this session.
@Singleton
public class WeaponStyleTracker
{
	private static final String NO_STYLE = "None";

	public static final class Style
	{
		public final String name;
		@Nullable
		public final WeaponAttackType type;

		private Style(String name, @Nullable WeaponAttackType type)
		{
			this.name = name;
			this.type = type;
		}
	}

	private final Client client;
	private final EventBus eventBus;

	private final TreeMap<String, Integer> categoryByWeapon = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	private final Map<Integer, Style> styleByCategory = new HashMap<>();

	private int trackedWeaponId = -1;
	private int trackedStyleIndex = -1;

	@Inject
	private WeaponStyleTracker(Client client, EventBus eventBus)
	{
		this.client = client;
		this.eventBus = eventBus;
	}

	public void startUp()
	{
		eventBus.register(this);
	}

	public void shutDown()
	{
		eventBus.unregister(this);
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		categoryByWeapon.clear();
		styleByCategory.clear();
		trackedWeaponId = -1;
		trackedStyleIndex = -1;
	}

	// Before the plugin's own onGameTick refreshes labels.
	@Subscribe(priority = 1)
	public void onGameTick(GameTick event)
	{
		int weaponId = equippedWeaponId();
		int styleIndex = attackStyleIndex();
		if (weaponId == trackedWeaponId && styleIndex == trackedStyleIndex)
		{
			return;
		}

		trackedWeaponId = weaponId;
		trackedStyleIndex = styleIndex;

		int category = weaponCategory();
		styleByCategory.put(category, current());
		if (weaponId > 0)
		{
			categoryByWeapon.put(client.getItemDefinition(weaponId).getName(), category);
		}
	}

	public Style current()
	{
		int category = weaponCategory();
		int styleIndex = attackStyleIndex();
		String name = buildAttackStyle(category, styleIndex);
		return new Style(name == null ? NO_STYLE : name, WeaponAttackTypes.forStyle(category, styleIndex));
	}

	// Equipped this session, alphabetical.
	public Set<String> weaponNames()
	{
		return Collections.unmodifiableSet(categoryByWeapon.keySet());
	}

	@Nullable
	public Style forWeapon(String search)
	{
		String weapon = weaponName(search);
		return weapon == null ? null : styleByCategory.get(categoryByWeapon.get(weapon));
	}

	// The equipped weapon's own name matching search, exact first, else substring.
	// Null if no match has been equipped.
	@Nullable
	public String weaponName(String search)
	{
		String trimmed = search.trim();
		if (categoryByWeapon.containsKey(trimmed))
		{
			return categoryByWeapon.ceilingKey(trimmed);
		}

		String needle = trimmed.toLowerCase(Locale.ROOT);
		for (String name : categoryByWeapon.keySet())
		{
			if (name.toLowerCase(Locale.ROOT).contains(needle))
			{
				return name;
			}
		}

		return null;
	}

	private int weaponCategory()
	{
		return client.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY);
	}

	private int equippedWeaponId()
	{
		ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		Item weapon = equipment == null ? null : equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		return weapon == null ? -1 : weapon.getId();
	}

	// Staff slot 4 covers Casting and Defensive Casting, split by AUTOCAST_DEFMODE.
	private int attackStyleIndex()
	{
		int styleIndex = client.getVarpValue(VarPlayerID.COM_MODE);
		if (styleIndex == 4)
		{
			styleIndex += client.getVarbitValue(VarbitID.AUTOCAST_DEFMODE);
		}

		return styleIndex;
	}

	private String buildAttackStyle(int weaponCategory, int attackStyleIndex)
	{
		String[] styleNames = weaponTypeStyleNames(weaponCategory);
		if (attackStyleIndex < 0 || attackStyleIndex >= styleNames.length)
		{
			return null;
		}

		return styleNames[attackStyleIndex];
	}

	// Mirrors core's AttackStylesPlugin, including its blue moon spear and partisan fallbacks.
	private String[] weaponTypeStyleNames(int weaponType)
	{
		if (weaponType == 3 || weaponType == 5 || weaponType == 19) // Bow, crossbow, thrown
		{
			return new String[] {"Accurate", "Rapid", null, "Longrange"};
		}

		if (weaponType == 7) // Chinchompas
		{
			return new String[] {"Short Fuse", "Medium Fuse", null, "Long Fuse"};
		}

		int weaponStyleEnumId = client.getEnum(EnumID.WEAPON_STYLES).getIntValue(weaponType);
		if (weaponStyleEnumId == -1)
		{
			if (weaponType == 22) // Blue moon spear
			{
				return new String[] {"Accurate", "Aggressive", null, "Defensive", "Casting", "Defensive Casting"};
			}

			if (weaponType == 30) // Partisan
			{
				return new String[] {"Accurate", "Aggressive", "Aggressive", "Defensive"};
			}

			return new String[0];
		}

		int[] weaponStyleStructs = client.getEnum(weaponStyleEnumId).getIntVals();
		String[] styleNames = new String[weaponStyleStructs.length];
		for (int i = 0; i < weaponStyleStructs.length; i++)
		{
			String name = client.getStructComposition(weaponStyleStructs[i]).getStringValue(ParamID.ATTACK_STYLE_NAME);
			if ("Other".equals(name))
			{
				continue;
			}

			styleNames[i] = (i == 5 && "Defensive".equals(name)) ? "Defensive Casting" : name;
		}

		return styleNames;
	}
}
