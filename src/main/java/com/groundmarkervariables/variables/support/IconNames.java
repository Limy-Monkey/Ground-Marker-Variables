package com.groundmarkervariables.variables.support;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.groundmarkervariables.chatbox.ChatboxWidgets;
import com.groundmarkervariables.variables.label.SpellVariables;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
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
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.Text;

// <npc=Name[,head]> / <item=Name[@N%]> / <sprite=Name[@N%]>: names typed in a label, turned into the overlay's ID
// tags when the label is saved. A name matches any case. NPCs and items: nearby NPCs and held items win, then the
// lowest ID. Sprites: "Name (Category)", from the game's prayers, skills, spells and bosses, then sprite-names.json.
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class IconNames {
	public enum Kind {
		NPC("npc", "(,head)?"),
		ITEM("item", "(@\\d{1,3}%)?"),
		SPRITE("sprite", "(@\\d{1,3}%)?");

		public final String tag;
		private final Pattern pattern;
		// The tag with an ID, for collapse().
		private final Pattern idPattern;

		Kind(String tag, String suffix) {
			this.tag = tag;
			this.pattern = Pattern.compile("<" + tag + "=(" + NAME_CHARS + "*[^<>{}=,@\\d\\s]" + NAME_CHARS + "*)" + suffix + ">",
				Pattern.CASE_INSENSITIVE);
			this.idPattern = Pattern.compile("<" + tag + "=(\\d{1,9})" + suffix + ">", Pattern.CASE_INSENSITIVE);
		}
	}

	// Characters a name can't hold: they'd end or break the tag.
	private static final String NAME_CHARS = "[^<>{}=,@]";
	// Config archive groups holding every NPC / item definition.
	private static final int NPC_GROUP = 9;
	private static final int ITEM_GROUP = 10;
	// Definitions read per client tick by a background build (see loaded()).
	private static final int BUILD_SLICE = 2000;

	// A prayer object's (see buildSprites()) name, prayer-book icon, and the prayer it leads to (e.g. Eagle Eye to Deadeye).
	private static final int PRAYER_NAME = 1752;
	private static final int PRAYER_ICON = 1757;
	private static final int PRAYER_NEXT = 1760;

	private static final class Index {
		// Display names, shortest first.
		final List<String> names;
		// Lowercase name -> ID.
		final Map<String, Integer> ids;
		// NPCs: lowercase names whose ID has a chathead model, for <npc=Name,head>.
		final Set<String> heads;

		Index(Map<String, String> names, Map<String, Integer> ids, Set<String> heads) {
			List<String> sorted = new ArrayList<>(names.values());
			sorted.sort(Comparator.comparingInt(String::length).thenComparing(String.CASE_INSENSITIVE_ORDER));
			this.names = Collections.unmodifiableList(sorted);
			this.ids = Collections.unmodifiableMap(ids);
			this.heads = Collections.unmodifiableSet(heads);
		}
	}

	private final Client client;
	private final ClientThread clientThread;
	private final SpellVariables spellVariables;

	// Built on the client thread on first use; read from the editor's key thread too.
	private final Map<Kind, Index> indexes = new ConcurrentHashMap<>();
	// Kinds being built in the background, and what to run once one is done (see whenLoaded()).
	private final Set<Kind> building = ConcurrentHashMap.newKeySet();
	private final Set<Runnable> onLoaded = ConcurrentHashMap.newKeySet();
	// Lowercase names of nearby NPCs / held items, ranked first by complete().
	private volatile Set<String> nearbyNpcs = Set.of();
	private volatile Set<String> heldItems = Set.of();
	// See itemNamed(). Built on first use once the item names have loaded.
	private volatile Map<String, String> looseItemNames;
	// See bossSprite(). Client thread only.
	private Map<String, Integer> bossSprites;
	// Sprite ID -> name, for collapse(). Client thread only.
	private Map<Integer, String> spriteNames;
	// sprite-names.json: the bundled copy until updateSprites() replaces it.
	private volatile JsonObject spriteJson = bundledSprites();

	// A newer sprite-names.json (see LatestData). The sprites are rebuilt on next use.
	public void updateSprites(String json) {
		spriteJson = new JsonParser().parse(json).getAsJsonObject();
		clientThread.invoke(() -> {
			indexes.remove(Kind.SPRITE);
			spriteNames = null;
		});
	}

	// Searches listed first after a bare "<sprite=", each with its sprite: every sprite name ends in its "(Category)".
	public Map<String, Integer> spriteCategories() {
		return jsonSprites(spriteJson, "categories");
	}

	// Client thread. Takes note of nearby NPCs and held items for complete().
	public void refreshNearby() {
		Set<String> npcNames = new HashSet<>();
		for (NPC npc : client.getTopLevelWorldView().npcs()) {
			NPCComposition shown = npc.getTransformedComposition();
			String name = shown == null ? null : clean(shown.getName());
			if (name != null) {
				npcNames.add(name.toLowerCase(Locale.ROOT));
			}
		}

		Set<String> itemNames = new HashSet<>();
		for (Item item : heldItems()) {
			String name = itemName(item.getId());
			if (name != null) {
				itemNames.add(name.toLowerCase(Locale.ROOT));
			}
		}

		nearbyNpcs = npcNames;
		heldItems = itemNames;
	}

	// The best name starting with partial (any case): an exact match, then nearby NPCs / held items, then the
	// shortest. Null if none, or while the names are still loading.
	@Nullable
	public String complete(Kind kind, String partial) {
		Index index = index(kind);
		if (index == null || partial.isEmpty()) {
			return null;
		}

		String lower = partial.toLowerCase(Locale.ROOT);
		Set<String> preferred = kind == Kind.NPC ? nearbyNpcs : kind == Kind.ITEM ? heldItems : Set.of();
		String shortest = null;
		String nearby = null;
		// Shortest first, so an exact match comes before any longer name.
		for (String name : index.names) {
			String key = name.toLowerCase(Locale.ROOT);
			if (!key.startsWith(lower)) {
				continue;
			}
			if (key.equals(lower)) {
				return name;
			}
			if (nearby == null && preferred.contains(key)) {
				nearby = name;
			}
			if (shortest == null) {
				shortest = name;
			}
		}
		return nearby != null ? nearby : shortest;
	}

	// The item named name, ignoring case and apostrophes ("Alchemists amulet" -> "Alchemist's amulet"); the shortest
	// if several. Null if none, or while the names are still loading.
	@Nullable
	public String itemNamed(String name) {
		Map<String, String> byLoose = looseItemNames;
		if (byLoose == null) {
			Index index = index(Kind.ITEM);
			if (index == null) {
				return null;
			}
			byLoose = new HashMap<>();
			for (String item : index.names) {
				byLoose.putIfAbsent(loose(item), item);
			}
			looseItemNames = byLoose;
		}
		return byLoose.get(loose(name));
	}

	private static String loose(String name) {
		return name.trim().toLowerCase(Locale.ROOT).replace("'", "");
	}

	// Whether the NPC named name (any case) has a chathead; false while the names are still loading.
	public boolean hasHead(String name) {
		Index index = loaded(Kind.NPC);
		return index != null && index.heads.contains(name.trim().toLowerCase(Locale.ROOT));
	}

	// Client thread. A boss's hiscores icon by the names {kc} uses, which differ from the hiscores table's ("Gauntlet"
	// for "The Gauntlet", "Obor" for "Obor chests opened", ...); null if none.
	@Nullable
	public Integer bossSprite(String name) {
		if (bossSprites == null) {
			bossSprites = loadBossSprites();
		}
		return bossSprites.get(bossKey(name));
	}

	// Each row by its whole name; one with a colon also by the part before it, unless a row has that name already.
	private Map<String, Integer> loadBossSprites() {
		Map<String, Integer> sprites = new HashMap<>();
		Map<String, Integer> beforeColon = new HashMap<>();
		tableRows(DBTableID.HiscoresBossesInfo.ID, DBTableID.HiscoresBossesInfo.COL_BOSSNAME, DBTableID.HiscoresBossesInfo.COL_BOSSICON,
			(name, icon) -> {
				sprites.putIfAbsent(bossKey(name), icon);
				int colon = name.indexOf(':');
				if (colon > 0) {
					beforeColon.putIfAbsent(bossKey(name.substring(0, colon)), icon);
				}
			});
		beforeColon.forEach(sprites::putIfAbsent);
		return sprites;
	}

	// Lowercase, without a leading "The ", colons, or a trailing "chest(s) (opened)".
	private static String bossKey(String name) {
		String key = Text.removeTags(name).toLowerCase(Locale.ROOT).trim();
		if (key.startsWith("the ")) {
			key = key.substring(4);
		}
		return key.replace(":", "").replaceAll("\\s+chests?(\\s+opened)?$", "").replaceAll("\\s+", " ").trim();
	}

	// Every name starting with partial (any case; all of them if it's empty), in complete()'s order: an exact match,
	// then nearby NPCs / held items, then the rest shortest first; then names only containing it, shortest first. Empty
	// if none, or while the names are still loading.
	public List<String> matches(Kind kind, String partial) {
		Index index = loaded(kind);
		if (index == null) {
			return List.of();
		}

		String lower = partial.toLowerCase(Locale.ROOT);
		Set<String> preferred = kind == Kind.NPC ? nearbyNpcs : kind == Kind.ITEM ? heldItems : Set.of();
		List<String> exact = new ArrayList<>();
		List<String> nearby = new ArrayList<>();
		List<String> rest = new ArrayList<>();
		List<String> contained = new ArrayList<>();
		for (String name : index.names) {
			String key = name.toLowerCase(Locale.ROOT);
			if (!key.startsWith(lower)) {
				if (ChatboxWidgets.hasWords(key, lower)) {
					contained.add(name);
				}
				continue;
			}
			if (key.equals(lower)) {
				exact.add(name);
			} else if (preferred.contains(key)) {
				nearby.add(name);
			} else {
				rest.add(name);
			}
		}
		List<String> ranked = new ArrayList<>(exact);
		ranked.addAll(nearby);
		ranked.addAll(rest);
		ranked.addAll(contained);
		return ranked;
	}

	// Client thread. expand() reversed, for showing a label in the editor: "<item=4151@50%>" -> "<item=Abyssal whip@50%>".
	// IDs without a name are left as-is.
	public String collapse(String label) {
		if (label == null || label.indexOf('<') < 0) {
			return label;
		}

		for (Kind kind : Kind.values()) {
			Matcher matcher = kind.idPattern.matcher(label);
			StringBuilder sb = new StringBuilder();
			while (matcher.find()) {
				String name = name(kind, Integer.parseInt(matcher.group(1)));
				String tag = name == null ? matcher.group() : "<" + kind.tag + "=" + name + (matcher.group(2) == null ? "" : matcher.group(2)) + ">";
				matcher.appendReplacement(sb, Matcher.quoteReplacement(tag));
			}
			matcher.appendTail(sb);
			label = sb.toString();
		}
		return label;
	}

	// Client thread. An NPC's, item's or sprite's name by ID, or null.
	@Nullable
	private String name(Kind kind, int id) {
		if (kind == Kind.NPC) {
			return npcName(id);
		}
		if (kind == Kind.ITEM) {
			return itemName(id);
		}
		if (spriteNames == null) {
			Index index = index(Kind.SPRITE);
			if (index == null) {
				return null;
			}
			// Shortest name first, so it's the one kept.
			spriteNames = new HashMap<>();
			for (String name : index.names) {
				spriteNames.putIfAbsent(index.ids.get(name.toLowerCase(Locale.ROOT)), name);
			}
		}
		return spriteNames.get(id);
	}

	// Client thread. "<npc=Verzik Vitur>" -> "<npc=8369>", "<item=Abyssal whip@50%>" -> "<item=4151@50%>",
	// "<sprite=Piety (Prayer)>" -> "<sprite=946>". Unknown names are left as-is.
	public String expand(String label) {
		if (label == null || label.indexOf('<') < 0) {
			return label;
		}

		for (Kind kind : Kind.values()) {
			label = replace(label, kind);
		}
		return label;
	}

	private String replace(String label, Kind kind) {
		Matcher matcher = kind.pattern.matcher(label);
		StringBuilder sb = new StringBuilder();
		while (matcher.find()) {
			Integer id = id(kind, matcher.group(1).trim());
			String suffix = matcher.group(2) == null ? "" : matcher.group(2).toLowerCase(Locale.ROOT);
			String replacement = id == null ? matcher.group() : "<" + kind.tag + "=" + id + suffix + ">";
			matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	@Nullable
	public Integer id(Kind kind, String name) {
		if (kind == Kind.NPC) {
			for (NPC nearby : client.getTopLevelWorldView().npcs()) {
				NPCComposition shown = nearby.getTransformedComposition();
				if (shown != null && name.equalsIgnoreCase(clean(shown.getName()))) {
					return shown.getId();
				}
			}
		} else if (kind == Kind.ITEM) {
			for (Item item : heldItems()) {
				if (name.equalsIgnoreCase(itemName(item.getId()))) {
					return item.getId();
				}
			}
		}

		Index index = index(kind);
		return index == null ? null : index.ids.get(name.toLowerCase(Locale.ROOT));
	}

	private List<Item> heldItems() {
		List<Item> held = new ArrayList<>();
		for (int id : new int[]{InventoryID.INV, InventoryID.WORN}) {
			ItemContainer container = client.getItemContainer(id);
			if (container != null) {
				for (Item item : container.getItems()) {
					if (item.getId() >= 0) {
						held.add(item);
					}
				}
			}
		}
		return held;
	}

	// On the client thread, built now if not yet (saving and drawing labels need it); elsewhere as loaded().
	@Nullable
	private Index index(Kind kind) {
		Index index = loaded(kind);
		if (index == null && client.isClientThread()) {
			new Build(kind).step(Integer.MAX_VALUE);
			index = indexes.get(kind);
		}
		return index;
	}

	// Never blocks: the index if built, else null, building it in the background (a slice per client tick).
	@Nullable
	private Index loaded(Kind kind) {
		Index index = indexes.get(kind);
		if (index == null && building.add(kind)) {
			clientThread.invoke(new Build(kind));
		}
		return index;
	}

	// Whether kind's names have loaded; if not, they start loading in the background.
	public boolean ready(Kind kind) {
		return loaded(kind) != null;
	}

	// If names are loading in the background, runs run once, on the client thread, the tick after the next is done.
	public void whenLoaded(Runnable run) {
		if (!building.isEmpty()) {
			onLoaded.add(run);
		}
	}

	// A kind's index, built in slices on the client thread: in the background, one per tick until done. Every named
	// NPC / item, keeping the lowest ID per name; or the sprites, at once.
	@RequiredArgsConstructor
	private final class Build implements BooleanSupplier {
		private final Kind kind;
		private final Map<String, String> names = new HashMap<>();
		private final Map<String, Integer> ids = new HashMap<>();
		private final Set<String> heads = new HashSet<>();
		private int[] fileIds;
		private int next;

		@Override
		public boolean getAsBoolean() {
			return indexes.containsKey(kind) ? done() : step(BUILD_SLICE);
		}

		// Up to slice more definitions; true once the index is stored.
		private boolean step(int slice) {
			if (kind == Kind.SPRITE) {
				buildSprites(names, ids);
			} else {
				if (fileIds == null) {
					int[] all = client.getIndexConfig().getFileIds(kind == Kind.NPC ? NPC_GROUP : ITEM_GROUP);
					fileIds = all == null ? new int[0] : all;
				}
				for (int end = (int) Math.min(fileIds.length, (long) next + slice); next < end; next++) {
					add(fileIds[next]);
				}
				if (next < fileIds.length) {
					return false;
				}
			}
			indexes.put(kind, new Index(names, ids, heads));
			return done();
		}

		// An NPC's head counts if the ID kept for its name has one.
		private void add(int id) {
			NPCComposition npc = kind == Kind.NPC ? client.getNpcDefinition(id) : null;
			String name = npc == null ? (kind == Kind.NPC ? null : itemName(id)) : npc.getModels() == null ? null : clean(npc.getName());
			if (name == null) {
				return;
			}
			String key = name.toLowerCase(Locale.ROOT);
			names.putIfAbsent(key, name);
			Integer kept = ids.get(key);
			if (kept == null || id < kept) {
				ids.put(key, id);
				heads.remove(key);
				if (npc != null && npc.getChatheadModels() != null && npc.getChatheadModels().length > 0) {
					heads.add(key);
				}
			}
		}

		private boolean done() {
			if (building.remove(kind)) {
				// Not mid-way through whatever needed the names now (e.g. drawing a label).
				onLoaded.forEach(clientThread::invokeLater);
				onLoaded.clear();
			}
			return true;
		}
	}

	// Prayers, skills, spells, bosses from the game, then sprite-names.json; each named "Name (Category)", e.g.
	// "Wind Surge (Spell)". The first to take a name keeps it.
	private void buildSprites(Map<String, String> names, Map<String, Integer> ids) {
		try {
			// The prayer book's prayers, then those they lead to (Deadeye, Mystic Vigour, Zeal).
			List<Integer> prayers = new ArrayList<>();
			for (int prayer : client.getEnum(EnumID.PRAYERS_NORMAL).getIntVals()) {
				prayers.add(prayer);
			}
			for (int i = 0; i < prayers.size(); i++) {
				ItemComposition prayer = client.getItemDefinition(prayers.get(i));
				addSprite(names, ids, prayer.getStringValue(PRAYER_NAME) + " (Prayer)", prayer.getIntValue(PRAYER_ICON));
				int next = prayer.getIntValue(PRAYER_NEXT);
				if (next > 0 && !prayers.contains(next)) {
					prayers.add(next);
				}
			}
			spellVariables.forEachSpellSprite((name, sprite) -> addSprite(names, ids, name + " (Spell)", sprite));
		} catch (RuntimeException e) {
			log.warn("Couldn't read prayer or spell icons", e);
		}
		tableRows(DBTableID.HiscoresSkillInfo.ID, DBTableID.HiscoresSkillInfo.COL_SKILLNAME, DBTableID.HiscoresSkillInfo.COL_SKILLICON,
			(name, icon) -> addSprite(names, ids, name + " (Skill)", icon));
		tableRows(DBTableID.HiscoresBossesInfo.ID, DBTableID.HiscoresBossesInfo.COL_BOSSNAME, DBTableID.HiscoresBossesInfo.COL_BOSSICON,
			(name, icon) -> addSprite(names, ids, name + " (Hiscore)", icon));
		jsonSprites(spriteJson, "sprites").forEach((name, id) -> addSprite(names, ids, name, id));
	}

	// The bundled sprite-names.json; empty if unreadable.
	private static JsonObject bundledSprites() {
		try (Reader reader = new InputStreamReader(IconNames.class.getResourceAsStream("sprite-names.json"), StandardCharsets.UTF_8)) {
			return new JsonParser().parse(reader).getAsJsonObject();
		} catch (Exception e) {
			log.warn("Couldn't load sprite names", e);
			return new JsonObject();
		}
	}

	// A name -> sprite ID map from json, in order: "sprites", RuneLite's SpriteID constants as "Name (Category)", or
	// "categories". Empty if missing.
	private static Map<String, Integer> jsonSprites(JsonObject json, String key) {
		Map<String, Integer> sprites = new LinkedHashMap<>();
		JsonObject section = json.getAsJsonObject(key);
		if (section != null) {
			for (Map.Entry<String, JsonElement> sprite : section.entrySet()) {
				sprites.put(sprite.getKey(), sprite.getValue().getAsInt());
			}
		}
		return sprites;
	}

	// Each of a game table's rows with a name and an icon, e.g. a hiscores skill's or boss's.
	private void tableRows(int table, int nameColumn, int iconColumn, BiConsumer<String, Integer> row) {
		try {
			for (int id : client.getDBTableRows(table)) {
				Object[] name = client.getDBTableField(id, nameColumn, 0);
				Object[] icon = client.getDBTableField(id, iconColumn, 0);
				if (name.length > 0 && icon.length > 0 && name[0] instanceof String && icon[0] instanceof Integer) {
					row.accept((String) name[0], (Integer) icon[0]);
				}
			}
		} catch (RuntimeException e) {
			log.warn("Couldn't read table {}", table, e);
		}
	}

	private static void addSprite(Map<String, String> names, Map<String, Integer> ids, @Nullable String rawName, int id) {
		String name = clean(rawName);
		if (name != null && id > 0) {
			String key = name.toLowerCase(Locale.ROOT);
			if (names.putIfAbsent(key, name) == null) {
				ids.put(key, id);
			}
		}
	}

	// Only NPCs with a model of their own.
	@Nullable
	private String npcName(int id) {
		NPCComposition npc = client.getNpcDefinition(id);
		return npc == null || npc.getModels() == null ? null : clean(npc.getName());
	}

	// Not noted items or bank placeholders.
	@Nullable
	private String itemName(int id) {
		ItemComposition item = client.getItemDefinition(id);
		return item == null || item.getNote() != -1 || item.getPlaceholderTemplateId() != -1 ? null : clean(item.getName());
	}

	// Null for unnamed definitions and names the tags can't hold.
	@Nullable
	private static String clean(@Nullable String name) {
		if (name == null) {
			return null;
		}
		name = Text.removeTags(name).trim();
		return name.isEmpty() || name.equalsIgnoreCase("null") || !name.matches(NAME_CHARS + "+") ? null : name;
	}
}
