package com.groundmarkervariables.variables.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ParamID;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.Text;

// <npc=Name[,head]> / <item=Name[@N%]> / <sprite=Name[@N%]>: names typed in a label, turned into the overlay's ID
// tags when the label is saved. A name matches any case. NPCs and items: nearby NPCs and held items win, then the
// lowest ID. Sprites: prayers, skills, spells and bosses, named as in game.
@Slf4j
@Singleton
public class IconNames
{
	public enum Kind
	{
		NPC("npc", "(,head)?"),
		ITEM("item", "(@\\d{1,3}%)?"),
		SPRITE("sprite", "(@\\d{1,3}%)?");

		public final String tag;
		private final Pattern pattern;

		Kind(String tag, String suffix)
		{
			this.tag = tag;
			this.pattern = Pattern.compile("<" + tag + "=(" + NAME_CHARS + "*[^<>{}=,@\\d\\s]" + NAME_CHARS + "*)" + suffix + ">",
				Pattern.CASE_INSENSITIVE);
		}
	}

	// Characters a name can't hold: they'd end or break the tag.
	private static final String NAME_CHARS = "[^<>{}=,@]";
	// Config archive groups holding every NPC / item definition.
	private static final int NPC_GROUP = 9;
	private static final int ITEM_GROUP = 10;
	// Spellbook index -> enum of the book's spell objects; their name and icon params.
	private static final int SPELLBOOKS = 1981;
	private static final int SPELL_ICON = 597;

	private static final Map<String, Integer> PRAYERS = new LinkedHashMap<>();

	static
	{
		PRAYERS.put("Thick Skin", SpriteID.Prayeron.THICK_SKIN);
		PRAYERS.put("Burst of Strength", SpriteID.Prayeron.BURST_OF_STRENGTH);
		PRAYERS.put("Clarity of Thought", SpriteID.Prayeron.CLARITY_OF_THOUGHT);
		PRAYERS.put("Sharp Eye", SpriteID.Prayeron.SHARP_EYE);
		PRAYERS.put("Mystic Will", SpriteID.Prayeron.MYSTIC_WILL);
		PRAYERS.put("Rock Skin", SpriteID.Prayeron.ROCK_SKIN);
		PRAYERS.put("Superhuman Strength", SpriteID.Prayeron.SUPERHUMAN_STRENGTH);
		PRAYERS.put("Improved Reflexes", SpriteID.Prayeron.IMPROVED_REFLEXES);
		PRAYERS.put("Rapid Restore", SpriteID.Prayeron.RAPID_RESTORE);
		PRAYERS.put("Rapid Heal", SpriteID.Prayeron.RAPID_HEAL);
		PRAYERS.put("Protect Item", SpriteID.Prayeron.PROTECT_ITEM);
		PRAYERS.put("Hawk Eye", SpriteID.Prayeron.HAWK_EYE);
		PRAYERS.put("Mystic Lore", SpriteID.Prayeron.MYSTIC_LORE);
		PRAYERS.put("Steel Skin", SpriteID.Prayeron.STEEL_SKIN);
		PRAYERS.put("Ultimate Strength", SpriteID.Prayeron.ULTIMATE_STRENGTH);
		PRAYERS.put("Incredible Reflexes", SpriteID.Prayeron.INCREDIBLE_REFLEXES);
		PRAYERS.put("Protect from Magic", SpriteID.Prayeron.PROTECT_FROM_MAGIC);
		PRAYERS.put("Protect from Missiles", SpriteID.Prayeron.PROTECT_FROM_MISSILES);
		PRAYERS.put("Protect from Melee", SpriteID.Prayeron.PROTECT_FROM_MELEE);
		PRAYERS.put("Eagle Eye", SpriteID.Prayeron.EAGLE_EYE);
		PRAYERS.put("Deadeye", SpriteID.Prayeron.DEADEYE);
		PRAYERS.put("Mystic Might", SpriteID.Prayeron.MYSTIC_MIGHT);
		PRAYERS.put("Mystic Vigour", SpriteID.Prayeron.MYSTIC_VIGOUR);
		PRAYERS.put("Retribution", SpriteID.Prayeron.RETRIBUTION);
		PRAYERS.put("Redemption", SpriteID.Prayeron.REDEMPTION);
		PRAYERS.put("Smite", SpriteID.Prayeron.SMITE);
		PRAYERS.put("Preserve", SpriteID.Prayeron.PRESERVE);
		PRAYERS.put("Chivalry", SpriteID.Prayeron.CHIVALRY);
		PRAYERS.put("Piety", SpriteID.Prayeron.PIETY);
		PRAYERS.put("Rigour", SpriteID.Prayeron.RIGOUR);
		PRAYERS.put("Augury", SpriteID.Prayeron.AUGURY);
		PRAYERS.put("Zeal", SpriteID.Prayeron.ZEAL);
	}

	private static final class Index
	{
		// Display names, shortest first.
		final List<String> names;
		// Lowercase name -> ID.
		final Map<String, Integer> ids;

		Index(Map<String, String> names, Map<String, Integer> ids)
		{
			List<String> sorted = new ArrayList<>(names.values());
			sorted.sort(Comparator.comparingInt(String::length).thenComparing(String.CASE_INSENSITIVE_ORDER));
			this.names = Collections.unmodifiableList(sorted);
			this.ids = Collections.unmodifiableMap(ids);
		}
	}

	private final Client client;
	private final ClientThread clientThread;

	// Built on the client thread on first use; read from the editor's key thread too.
	private final Map<Kind, Index> indexes = new ConcurrentHashMap<>();
	// Lowercase names of nearby NPCs / held items, ranked first by complete().
	private volatile Set<String> nearbyNpcs = Set.of();
	private volatile Set<String> heldItems = Set.of();

	@Inject
	private IconNames(Client client, ClientThread clientThread)
	{
		this.client = client;
		this.clientThread = clientThread;
	}

	// Client thread. Takes note of nearby NPCs and held items for complete().
	public void refreshNearby()
	{
		Set<String> npcNames = new HashSet<>();
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			NPCComposition shown = npc.getTransformedComposition();
			String name = shown == null ? null : clean(shown.getName());
			if (name != null)
			{
				npcNames.add(name.toLowerCase(Locale.ROOT));
			}
		}

		Set<String> itemNames = new HashSet<>();
		for (Item item : heldItems())
		{
			String name = itemName(item.getId());
			if (name != null)
			{
				itemNames.add(name.toLowerCase(Locale.ROOT));
			}
		}

		nearbyNpcs = npcNames;
		heldItems = itemNames;
	}

	// The best name starting with partial (any case): an exact match, then nearby NPCs / held items, then the
	// shortest. Null if none, or while the names are still loading.
	@Nullable
	public String complete(Kind kind, String partial)
	{
		Index index = index(kind);
		if (index == null || partial.isEmpty())
		{
			return null;
		}

		String lower = partial.toLowerCase(Locale.ROOT);
		Set<String> preferred = kind == Kind.NPC ? nearbyNpcs : kind == Kind.ITEM ? heldItems : Set.of();
		String shortest = null;
		String nearby = null;
		// Shortest first, so an exact match comes before any longer name.
		for (String name : index.names)
		{
			String key = name.toLowerCase(Locale.ROOT);
			if (!key.startsWith(lower))
			{
				continue;
			}
			if (key.equals(lower))
			{
				return name;
			}
			if (nearby == null && preferred.contains(key))
			{
				nearby = name;
			}
			if (shortest == null)
			{
				shortest = name;
			}
		}
		return nearby != null ? nearby : shortest;
	}

	// Client thread. "<npc=Verzik Vitur>" -> "<npc=8369>", "<item=Abyssal whip@50%>" -> "<item=4151@50%>",
	// "<sprite=Piety>" -> "<sprite=946>". Unknown names are left as-is.
	public String expand(String label)
	{
		if (label == null || label.indexOf('<') < 0)
		{
			return label;
		}

		for (Kind kind : Kind.values())
		{
			label = replace(label, kind);
		}
		return label;
	}

	private String replace(String label, Kind kind)
	{
		Matcher matcher = kind.pattern.matcher(label);
		StringBuilder sb = new StringBuilder();
		while (matcher.find())
		{
			Integer id = id(kind, matcher.group(1).trim());
			String suffix = matcher.group(2) == null ? "" : matcher.group(2).toLowerCase(Locale.ROOT);
			String replacement = id == null ? matcher.group() : "<" + kind.tag + "=" + id + suffix + ">";
			matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	@Nullable
	private Integer id(Kind kind, String name)
	{
		if (kind == Kind.NPC)
		{
			for (NPC nearby : client.getTopLevelWorldView().npcs())
			{
				NPCComposition shown = nearby.getTransformedComposition();
				if (shown != null && name.equalsIgnoreCase(clean(shown.getName())))
				{
					return shown.getId();
				}
			}
		}
		else if (kind == Kind.ITEM)
		{
			for (Item item : heldItems())
			{
				if (name.equalsIgnoreCase(itemName(item.getId())))
				{
					return item.getId();
				}
			}
		}

		Index index = index(kind);
		return index == null ? null : index.ids.get(name.toLowerCase(Locale.ROOT));
	}

	private List<Item> heldItems()
	{
		List<Item> held = new ArrayList<>();
		for (int id : new int[]{InventoryID.INV, InventoryID.WORN})
		{
			ItemContainer container = client.getItemContainer(id);
			if (container != null)
			{
				for (Item item : container.getItems())
				{
					if (item.getId() >= 0)
					{
						held.add(item);
					}
				}
			}
		}
		return held;
	}

	// Built on the client thread; elsewhere, null until a queued build has run.
	@Nullable
	private Index index(Kind kind)
	{
		Index index = indexes.get(kind);
		if (index != null)
		{
			return index;
		}
		if (!client.isClientThread())
		{
			clientThread.invoke(() -> index(kind));
			return null;
		}

		Map<String, String> names = new HashMap<>();
		Map<String, Integer> ids = new HashMap<>();
		if (kind == Kind.SPRITE)
		{
			buildSprites(names, ids);
		}
		else
		{
			buildDefinitions(kind == Kind.NPC, names, ids);
		}
		index = new Index(names, ids);
		indexes.put(kind, index);
		return index;
	}

	// Every named NPC / item, keeping the lowest ID per name.
	private void buildDefinitions(boolean npc, Map<String, String> names, Map<String, Integer> ids)
	{
		int[] fileIds = client.getIndexConfig().getFileIds(npc ? NPC_GROUP : ITEM_GROUP);
		for (int id : fileIds == null ? new int[0] : fileIds)
		{
			String name = npc ? npcName(id) : itemName(id);
			if (name != null)
			{
				String key = name.toLowerCase(Locale.ROOT);
				names.putIfAbsent(key, name);
				ids.merge(key, id, Math::min);
			}
		}
	}

	// Prayers, skills, spells, then bosses: the first to take a name keeps it.
	private void buildSprites(Map<String, String> names, Map<String, Integer> ids)
	{
		PRAYERS.forEach((name, id) -> addSprite(names, ids, name, id));
		addTableSprites(names, ids, DBTableID.HiscoresSkillInfo.ID, DBTableID.HiscoresSkillInfo.COL_SKILLNAME,
			DBTableID.HiscoresSkillInfo.COL_SKILLICON);

		try
		{
			for (int book : client.getEnum(SPELLBOOKS).getIntVals())
			{
				EnumComposition spells = client.getEnum(book);
				for (int spell : spells.getIntVals())
				{
					ItemComposition definition = client.getItemDefinition(spell);
					addSprite(names, ids, definition.getStringValue(ParamID.SPELL_NAME), definition.getIntValue(SPELL_ICON));
				}
			}
		}
		catch (RuntimeException e)
		{
			log.warn("Couldn't read spell icons", e);
		}

		addTableSprites(names, ids, DBTableID.HiscoresBossesInfo.ID, DBTableID.HiscoresBossesInfo.COL_BOSSNAME,
			DBTableID.HiscoresBossesInfo.COL_BOSSICON);
	}

	private void addTableSprites(Map<String, String> names, Map<String, Integer> ids, int table, int nameColumn, int iconColumn)
	{
		try
		{
			for (int row : client.getDBTableRows(table))
			{
				Object[] name = client.getDBTableField(row, nameColumn, 0);
				Object[] icon = client.getDBTableField(row, iconColumn, 0);
				if (name.length > 0 && icon.length > 0 && name[0] instanceof String && icon[0] instanceof Integer)
				{
					addSprite(names, ids, (String) name[0], (Integer) icon[0]);
				}
			}
		}
		catch (RuntimeException e)
		{
			log.warn("Couldn't read sprite names from table {}", table, e);
		}
	}

	private static void addSprite(Map<String, String> names, Map<String, Integer> ids, @Nullable String rawName, int id)
	{
		String name = clean(rawName);
		if (name != null && id > 0)
		{
			String key = name.toLowerCase(Locale.ROOT);
			if (names.putIfAbsent(key, name) == null)
			{
				ids.put(key, id);
			}
		}
	}

	// Only NPCs with a model of their own.
	@Nullable
	private String npcName(int id)
	{
		NPCComposition npc = client.getNpcDefinition(id);
		return npc == null || npc.getModels() == null ? null : clean(npc.getName());
	}

	// Not noted items or bank placeholders.
	@Nullable
	private String itemName(int id)
	{
		ItemComposition item = client.getItemDefinition(id);
		return item == null || item.getNote() != -1 || item.getPlaceholderTemplateId() != -1 ? null : clean(item.getName());
	}

	// Null for unnamed definitions and names the tags can't hold.
	@Nullable
	private static String clean(@Nullable String name)
	{
		if (name == null)
		{
			return null;
		}
		name = Text.removeTags(name).trim();
		return name.isEmpty() || name.equalsIgnoreCase("null") || !name.matches(NAME_CHARS + "+") ? null : name;
	}
}
