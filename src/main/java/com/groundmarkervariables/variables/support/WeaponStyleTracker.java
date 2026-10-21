package com.groundmarkervariables.variables.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
import net.runelite.api.gameval.DBTableID;
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
	// Autocast spell index -> spell obj.
	private static final int AUTOCAST_SPELLS = 1986;

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
	private final Map<Integer, String[]> tabNamesByCategory = new HashMap<>();

	private int trackedWeaponId = -1;
	private int trackedStyleIndex = -1;
	private int trackedAutocastSpell = -1;
	@Nullable
	private String lastAutocast;

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
		trackedAutocastSpell = -1;
		lastAutocast = null;
	}

	// Before the plugin's own onGameTick refreshes labels.
	@Subscribe(priority = 1)
	public void onGameTick(GameTick event)
	{
		int weaponId = equippedWeaponId();
		int styleIndex = attackStyleIndex();
		int autocastSpell = client.getVarbitValue(VarbitID.AUTOCAST_SPELL);
		if (weaponId == trackedWeaponId && styleIndex == trackedStyleIndex && autocastSpell == trackedAutocastSpell)
		{
			return;
		}

		trackedWeaponId = weaponId;
		trackedStyleIndex = styleIndex;
		trackedAutocastSpell = autocastSpell;

		int category = weaponCategory();
		if (buildAttackStyle(category, 4) != null)
		{
			lastAutocast = styleIndex >= 4 ? autocastName(styleIndex == 5) : null;
		}

		styleByCategory.put(category, current());
		if (weaponId > 0)
		{
			categoryByWeapon.put(client.getItemDefinition(weaponId).getName(), category);
		}
	}

	// Melee styles keep their style name (Accurate, Aggressive...). Autocast is the spell's name,
	// anything else the combat tab's button name, e.g. Rapid or Longrange.
	public Style current()
	{
		int category = weaponCategory();
		int styleIndex = attackStyleIndex();
		WeaponAttackType type = WeaponAttackTypes.forStyle(category, styleIndex);
		String name = styleIndex >= 4 ? autocastName(styleIndex == 5)
			: type == WeaponAttackType.STAB || type == WeaponAttackType.SLASH || type == WeaponAttackType.CRUSH ? null
			: combatTabName(category, styleIndex);
		if (name == null)
		{
			name = buildAttackStyle(category, styleIndex);
		}
		return new Style(name == null ? NO_STYLE : name, type);
	}

	@Nullable
	private String autocastName(boolean defensive)
	{
		String name = autocastSpell();
		return name == null ? null : defensive ? name + " (def)" : name;
	}

	// e.g. "Fire Surge" or "Fire Surge (def)". Null if not autocasting.
	@Nullable
	public String lastAutocast()
	{
		return lastAutocast;
	}

	@Nullable
	private String autocastSpell()
	{
		int spell = client.getEnum(AUTOCAST_SPELLS).getIntValue(client.getVarbitValue(VarbitID.AUTOCAST_SPELL));
		String name = spell <= 0 ? null : client.getItemDefinition(spell).getStringValue(ParamID.SPELL_NAME);
		return name == null || name.isEmpty() ? null : name;
	}

	@Nullable
	private String combatTabName(int category, int styleIndex)
	{
		String[] names = tabNamesByCategory.computeIfAbsent(category, this::loadCombatTabNames);
		return styleIndex >= 0 && styleIndex < names.length ? names[styleIndex] : null;
	}

	// Button names by style slot, from the combat tab's own DB table.
	private String[] loadCombatTabNames(int category)
	{
		String[] names = new String[6];
		List<Integer> rows = client.getDBRowsByValue(DBTableID.CombatInterfaceWeaponCategory.ID, DBTableID.CombatInterfaceWeaponCategory.COL_ID, 0, category);
		for (int i = 0; !rows.isEmpty() && i < names.length; i++)
		{
			Object[] button = client.getDBTableField(rows.get(0), DBTableID.CombatInterfaceWeaponCategory.COL_BUTTON, i);
			if (button == null || button.length < 2)
			{
				break;
			}

			int slot = (Integer) button[0];
			if (slot >= 0 && slot < names.length)
			{
				names[slot] = (String) button[1];
			}
		}
		return names;
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

	// A seen weapon's name matching search: the worn weapon first, then inventory items, then
	// any other. Exact before substring within each. Null if no match has been equipped.
	@Nullable
	public String weaponName(String search)
	{
		String needle = search.trim();
		String match = bestMatch(itemNames(client.getItemContainer(InventoryID.WORN), EquipmentInventorySlot.WEAPON.getSlotIdx()), needle);
		if (match == null)
		{
			match = bestMatch(itemNames(client.getItemContainer(InventoryID.INV), -1), needle);
		}
		return match != null ? match : bestMatch(categoryByWeapon.keySet(), needle);
	}

	// Only names with a saved state, returned in their saved casing.
	@Nullable
	private String bestMatch(Collection<String> names, String needle)
	{
		String substring = null;
		for (String name : names)
		{
			String saved = categoryByWeapon.containsKey(name) ? categoryByWeapon.ceilingKey(name) : null;
			if (saved == null)
			{
				continue;
			}

			if (saved.equalsIgnoreCase(needle))
			{
				return saved;
			}

			if (substring == null && saved.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT)))
			{
				substring = saved;
			}
		}
		return substring;
	}

	// All item names in container, or just slot's when slot >= 0.
	private List<String> itemNames(@Nullable ItemContainer container, int slot)
	{
		List<String> names = new ArrayList<>();
		Item[] items = container == null ? new Item[0] : container.getItems();
		for (int i = 0; i < items.length; i++)
		{
			if ((slot < 0 || i == slot) && items[i].getId() > 0)
			{
				names.add(client.getItemDefinition(items[i].getId()).getName());
			}
		}
		return names;
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
		if (weaponType == 36) // Obligator
		{
			return new String[] {"5t Accurate", "5t Aggressive", "3t Aggressive"};
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
