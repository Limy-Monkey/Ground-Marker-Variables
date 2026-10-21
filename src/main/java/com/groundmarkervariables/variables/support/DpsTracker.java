package com.groundmarkervariables.variables.support;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import com.groundmarkervariables.GroundMarkerPointData;
import com.groundmarkervariables.MarkerStorage;
import com.groundmarkervariables.variables.LabelResolver;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Hitsplat;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

// Damage for {dps}, kept per tile and only counted while that tile's label is resolving (i.e.
// you're near it). A tile resets once it hasn't resolved for a couple of ticks.
@Slf4j
@Singleton
public class DpsTracker
{
	public enum Source
	{
		WEAPON, THRALL, VENGEANCE, RECOIL
	}

	// {dps}, {^dps total}, {dps report ...} etc.
	public static final Pattern DPS_TOKEN = Pattern.compile("\\{[\\^&*]?dps(?:\\s[^{}]*)?\\}", Pattern.CASE_INSENSITIVE);
	private static final int ACTIVE_TICKS = 2;
	// 30 seconds with no damage from you or to the NPCs you've hit.
	private static final int IDLE_TICKS = 50;
	// Stats for a tile not resolved in this long are dropped.
	private static final int FORGET_TICKS = 100;
	private static final double SECONDS_PER_TICK = 0.6;
	private static final int THRALL_ATTACK_TICKS = 4;
	private static final int THRALL_MAX_HIT = 3;
	private static final int MAGIC_SPEC_LAND_TICKS = 4;
	private static final int MAX_HIT_DELAY_TICKS = 6;
	// No attack counted yet. Unarmed is -1.
	private static final int NO_WEAPON = Integer.MIN_VALUE;
	private static final Skill[] COMBAT_SKILLS = {
		Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.HITPOINTS};
	private static final int POWERED_STAFF_CATEGORY = 24;
	private static final int POWERED_WAND_CATEGORY = 29;
	// Thrust (slots 0 and 1) is 5 ticks, Smash 3.
	private static final int OBLIGATOR_CATEGORY = 36;
	private static final int OBLIGATOR_SMASH_SLOT = 2;

	private static final class Hit
	{
		private final int npc;
		private final int amount;
		private Source source = Source.WEAPON;

		private Hit(int npc, int amount)
		{
			this.npc = npc;
			this.amount = amount;
		}
	}

	// One {dps} tile's numbers.
	public final class Stats
	{
		// NPC index -> {my damage, others' damage}
		private final Map<Integer, int[]> damageByNpc = new HashMap<>();
		private final int[] damageBySource = new int[Source.values().length];
		// {tick, damage, weapon or NO_WEAPON} for each of your hits, for recentDps().
		private final List<int[]> history = new ArrayList<>();
		private int lastActiveTick = -1;
		private int firstHitTick = -1;
		// Tick you died or went idle at, or -1.
		private int pausedTick = -1;
		private int lastDamageTick = -1;
		private int myDamage;
		private int takenDamage;
		private int lastAttackTick = -1;
		private int lastAttackWeapon = NO_WEAPON;
		// Weapon item id (-1 unarmed) -> {damage, theory ticks, hits, hits that did damage, attacks}, in the
		// order first used.
		private final Map<Integer, int[]> theoryByWeapon = new LinkedHashMap<>();
		private int thrallTicks;

		private boolean isActive(int tick)
		{
			return lastActiveTick >= 0 && tick - lastActiveTick <= ACTIVE_TICKS;
		}

		// attack is this tick's new attack, or null if none.
		private void apply(List<Hit> hits, List<Integer> taken, int tick, @Nullable Attack attack)
		{
			// Merged hits (e.g. claws) belong to the last attack's weapon.
			int weapon = attack != null ? attack.weapon : lastAttackWeapon;
			// Paused: only your own weapon hit starts a fresh run.
			if (pausedTick >= 0)
			{
				if (hits.stream().noneMatch(hit -> hit.source == Source.WEAPON))
				{
					return;
				}
				reset();
			}

			for (int damage : taken)
			{
				takenDamage += damage;
			}

			if (hits.isEmpty())
			{
				return;
			}

			for (Hit hit : hits)
			{
				damageByNpc.computeIfAbsent(hit.npc, k -> new int[2])[0] += hit.amount;
				damageBySource[hit.source.ordinal()] += hit.amount;
				myDamage += hit.amount;
				history.add(new int[] {tick, hit.amount, hit.source == Source.WEAPON ? weapon : NO_WEAPON});
				if (hit.source == Source.WEAPON)
				{
					if (weapon != NO_WEAPON)
					{
						int[] totals = theoryByWeapon.computeIfAbsent(weapon, k -> new int[5]);
						totals[0] += hit.amount;
						totals[2]++;
						totals[3] += hit.amount > 0 ? 1 : 0;
					}
				}
				else if (hit.source == Source.THRALL)
				{
					thrallTicks += THRALL_ATTACK_TICKS;
				}
			}
			if (firstHitTick < 0)
			{
				firstHitTick = tick;
			}
			lastDamageTick = tick;

			if (attack != null)
			{
				// Only correct an attack this tile counted itself.
				int[] previous = theoryByWeapon.get(lastAttackWeapon);
				if (previous != null && lastAttackTick >= 0 && lastAttackTick == attack.previousTick)
				{
					previous[1] += attack.previousCorrection;
				}
				int[] totals = theoryByWeapon.computeIfAbsent(attack.weapon, k -> new int[5]);
				totals[1] += attack.speed;
				totals[4]++;
				lastAttackTick = attack.startTick;
				lastAttackWeapon = attack.weapon;
			}
		}

		private void othersHit(int npc, int amount, int tick)
		{
			if (pausedTick < 0 && damageByNpc.containsKey(npc))
			{
				damageByNpc.get(npc)[1] += amount;
				lastDamageTick = tick;
			}
		}

		private void checkPause(int tick)
		{
			if (pausedTick >= 0 || firstHitTick < 0)
			{
				return;
			}

			if (client.getBoostedSkillLevel(Skill.HITPOINTS) == 0)
			{
				pausedTick = tick;
			}
			else if (tick - lastDamageTick >= IDLE_TICKS)
			{
				pausedTick = tick;
			}
		}

		public void reset()
		{
			damageByNpc.clear();
			Arrays.fill(damageBySource, 0);
			history.clear();
			firstHitTick = -1;
			pausedTick = -1;
			myDamage = 0;
			takenDamage = 0;
			lastAttackTick = -1;
			lastAttackWeapon = NO_WEAPON;
			theoryByWeapon.clear();
			thrallTicks = 0;
		}

		// Damage per second since your first hit, all sources.
		public double dps()
		{
			return firstHitTick < 0 ? 0 : myDamage / (elapsedTicks() * SECONDS_PER_TICK);
		}

		public double dps(Source source)
		{
			return firstHitTick < 0 ? 0 : damageBySource[source.ordinal()] / (elapsedTicks() * SECONDS_PER_TICK);
		}

		// All sources over the last seconds, or since your first hit if that's sooner.
		public double recentDps(int seconds)
		{
			return recentDps(seconds, null);
		}

		// Only weapon's hits when weapon isn't null.
		private double recentDps(int seconds, @Nullable Integer weapon)
		{
			if (firstHitTick < 0 || seconds <= 0)
			{
				return 0;
			}

			int end = pausedTick >= 0 ? pausedTick : client.getTickCount();
			int windowTicks = Math.min((int) Math.ceil(seconds / SECONDS_PER_TICK), elapsedTicks());
			int damage = 0;
			for (int[] hit : history)
			{
				if (hit[0] > end - windowTicks && (weapon == null || hit[2] == weapon))
				{
					damage += hit[1];
				}
			}
			return damage / (windowTicks * SECONDS_PER_TICK);
		}

		// Weapon item id (-1 unarmed) -> its stats, in the order first used.
		public Map<Integer, WeaponStats> weaponStats()
		{
			Map<Integer, WeaponStats> result = new LinkedHashMap<>();
			theoryByWeapon.forEach((weapon, totals) -> result.put(weapon, new WeaponStats(weapon, totals)));
			return result;
		}

		// One weapon's numbers in this tile, for {dps report}.
		public final class WeaponStats
		{
			private final int weapon;
			private final int[] totals;

			private WeaponStats(int weapon, int[] totals)
			{
				this.weapon = weapon;
				this.totals = totals;
			}

			public int damage()
			{
				return totals[0];
			}

			public double theoryDps()
			{
				return totals[1] == 0 ? 0 : totals[0] / (totals[1] * SECONDS_PER_TICK);
			}

			public double avgDps()
			{
				return firstHitTick < 0 ? 0 : totals[0] / (elapsedTicks() * SECONDS_PER_TICK);
			}

			public double recentDps(int seconds)
			{
				return Stats.this.recentDps(seconds, weapon);
			}

			// Of all your damage, including thralls etc.
			public double share()
			{
				return myDamage == 0 ? 0 : totals[0] / (double) myDamage;
			}

			// -1 before any hit.
			public double accuracy()
			{
				return totals[2] == 0 ? -1 : totals[3] / (double) totals[2];
			}

			// A multi-hit attack like claws counts once.
			public int numAttacks()
			{
				return totals[4];
			}

			// 0s and splashes.
			public int numMisses()
			{
				return totals[2] - totals[3];
			}

			public int numHits()
			{
				return totals[3];
			}
		}

		// Thrall damage over its attacks' speed, or 0 before it has hit.
		public double thrallTheoryDps()
		{
			return thrallTicks == 0 ? 0 : damageBySource[Source.THRALL.ordinal()] / (thrallTicks * SECONDS_PER_TICK);
		}

		// Frozen by death or going idle, until reset.
		public boolean isPaused()
		{
			return pausedTick >= 0;
		}

		public int damage(Source source)
		{
			return damageBySource[source.ordinal()];
		}

		public double theoryDps()
		{
			int ticks = weaponTotal(1);
			return ticks == 0 ? 0 : weaponTotal(0) / (ticks * SECONDS_PER_TICK);
		}

		// Sum of one theoryByWeapon field over every weapon.
		private int weaponTotal(int field)
		{
			return theoryByWeapon.values().stream().mapToInt(totals -> totals[field]).sum();
		}

		public int total()
		{
			return myDamage;
		}

		public int taken()
		{
			return takenDamage;
		}

		// Weapon hits that did damage, or -1 before any weapon hit.
		public double accuracy()
		{
			int hits = weaponTotal(2);
			return hits == 0 ? -1 : weaponTotal(3) / (double) hits;
		}

		// A multi-hit attack like claws counts once.
		public int numAttacks()
		{
			return weaponTotal(4);
		}

		// 0s and splashes.
		public int numMisses()
		{
			return weaponTotal(2) - weaponTotal(3);
		}

		public int numHits()
		{
			return weaponTotal(3);
		}

		// Your share of all damage dealt to the NPCs you hit, or -1 if nobody else hit them.
		public double share()
		{
			long others = 0;
			for (int[] damage : damageByNpc.values())
			{
				others += damage[1];
			}
			return others == 0 ? -1 : myDamage / (double) (myDamage + others);
		}

		private int elapsedTicks()
		{
			int end = pausedTick >= 0 ? pausedTick : client.getTickCount();
			return Math.max(1, end - firstHitTick);
		}
	}

	private final Client client;
	private final EventBus eventBus;
	private final WeaponStyleTracker weaponStyleTracker;
	private final ItemManager itemManager;
	private final MarkerStorage markerStorage;
	// A Provider since LabelResolver depends on this, through DpsLabelVariable.
	private final Provider<LabelResolver> labelResolver;

	// The tile whose label is being resolved, set by the plugin's refresh loop. Null elsewhere.
	@Nullable
	public WorldPoint resolvingTile;

	private final Map<WorldPoint, Stats> statsByTile = new HashMap<>();
	// This tick's hits by you, and on you, until classify() sorts them.
	private final List<Hit> pendingHits = new ArrayList<>();
	private final List<Integer> pendingTaken = new ArrayList<>();
	private int lastClassifiedTick = -1;
	private int lastThrallTick = -THRALL_ATTACK_TICKS;
	private int lastVengeanceRebound;
	private boolean splashedLastTick;

	// A new attack, and the fix to the previous attack's speed if this one came early.
	private static final class Attack
	{
		private final int speed;
		private final int startTick;
		private final int weapon;
		private final int previousTick;
		private final int previousCorrection;

		private Attack(int speed, int startTick, int weapon, int previousTick, int previousCorrection)
		{
			this.speed = speed;
			this.startTick = startTick;
			this.weapon = weapon;
			this.previousTick = previousTick;
			this.previousCorrection = previousCorrection;
		}
	}

	// An attack started (combat XP gained) that hasn't landed yet.
	private static final class PendingAttack
	{
		private final int tick;
		private final int weapon;

		private PendingAttack(int tick, int weapon)
		{
			this.tick = tick;
			this.weapon = weapon;
		}
	}

	// Oldest first.
	private final Deque<PendingAttack> pendingAttacks = new ArrayDeque<>();
	private long lastCombatXp = -1;
	private int lastMagicXp;
	private int lastAttackTick = -1;
	private int lastAttackSpeed;
	private int kerisSpecTick = -100;
	private int ayakSpecTick = -100;

	@Inject
	private DpsTracker(Client client, EventBus eventBus, WeaponStyleTracker weaponStyleTracker, ItemManager itemManager,
		MarkerStorage markerStorage, Provider<LabelResolver> labelResolver)
	{
		this.client = client;
		this.eventBus = eventBus;
		this.weaponStyleTracker = weaponStyleTracker;
		this.itemManager = itemManager;
		this.markerStorage = markerStorage;
		this.labelResolver = labelResolver;
	}

	public void startUp()
	{
		eventBus.register(this);
	}

	public void shutDown()
	{
		eventBus.unregister(this);
		statsByTile.clear();
		pendingHits.clear();
		pendingTaken.clear();
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		int tick = client.getTickCount();
		if (statsByTile.values().stream().noneMatch(stats -> stats.isActive(tick)))
		{
			return;
		}

		Hitsplat hitsplat = event.getHitsplat();
		Actor actor = event.getActor();
		if (actor == client.getLocalPlayer())
		{
			if (hitsplat.isMine())
			{
				pendingTaken.add(hitsplat.getAmount());
			}
			return;
		}

		if (!(actor instanceof NPC))
		{
			return;
		}

		int npc = ((NPC) actor).getIndex();
		if (hitsplat.isMine())
		{
			pendingHits.add(new Hit(npc, hitsplat.getAmount()));
		}
		else if (hitsplat.isOthers())
		{
			for (Stats stats : statsByTile.values())
			{
				if (stats.isActive(tick))
				{
					stats.othersHit(npc, hitsplat.getAmount(), tick);
				}
			}
		}
	}

	// Called whenever a {dps} label resolves; returns that tile's stats.
	public Stats markActive()
	{
		int tick = client.getTickCount();
		Stats stats = statsByTile.computeIfAbsent(resolvingTile, tile -> new Stats());
		if (!stats.isActive(tick))
		{
			stats.reset();
		}
		stats.lastActiveTick = tick;

		if (lastClassifiedTick != tick)
		{
			lastClassifiedTick = tick;
			classify(tick);
			statsByTile.values().removeIf(other -> tick - other.lastActiveTick > FORGET_TICKS);
		}

		stats.checkPause(tick);
		return stats;
	}

	// Each {dps} on the tile, resolved with that tile's stats, as one game message per line.
	public void exportToChat(WorldPoint tile)
	{
		LocalPoint local = LocalPoint.fromWorld(client.getTopLevelWorldView(), tile);
		GroundMarkerPointData marker = local == null ? null : markerStorage.findStoredPoint(WorldPoint.fromLocalInstance(client, local));
		if (marker == null || marker.getLabel() == null)
		{
			return;
		}

		Matcher token = DPS_TOKEN.matcher(marker.getLabel());
		resolvingTile = tile;
		try
		{
			while (token.find())
			{
				String text = labelResolver.get().resolve(token.group()).replaceAll("\\{/?align[^}]*}|</?col[^>]*>", "");
				for (String line : text.split("\\\\n"))
				{
					if (!line.isBlank())
					{
						client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", line.trim(), null);
					}
				}
			}
		}
		finally
		{
			resolvingTile = null;
		}
	}

	// Reset DPS for the tile at worldPoint.
	public void reset(WorldPoint worldPoint)
	{
		Stats stats = statsByTile.get(worldPoint);
		if (stats != null)
		{
			stats.reset();
		}
	}

	// Gives this tick's hits to every active tile, even if sorting them fails.
	private void classify(int tick)
	{
		List<Hit> hits = new ArrayList<>(pendingHits);
		List<Integer> taken = new ArrayList<>(pendingTaken);
		pendingHits.clear();
		pendingTaken.clear();

		Attack attack = null;
		try
		{
			attack = sortHits(hits, taken, tick);
		}
		catch (RuntimeException e)
		{
			log.warn("Couldn't sort {dps} hits", e);
		}

		for (Stats stats : statsByTile.values())
		{
			if (stats.isActive(tick))
			{
				stats.apply(hits, taken, tick, attack);
			}
		}
	}

	// Sorts hits by source and returns the attack landing this tick, if any. The attack-start check
	// must come before the thrall claim, and the splash after it.
	@Nullable
	private Attack sortHits(List<Hit> hits, List<Integer> taken, int tick)
	{
		Player player = client.getLocalPlayer();
		Actor target = player == null ? null : player.getInteracting();
		WeaponAttackType type = weaponStyleTracker.current().type;

		boolean newSplash = detectSplash(target, type);
		claimVengeanceAndRecoil(hits, taken);
		boolean attackStarted = detectAttackStart(tick, target, type);
		claimThrallHit(hits, tick, type, attackStarted);
		// After the thrall claim, so the 0 can't be taken for a thrall hit.
		if (newSplash)
		{
			hits.add(new Hit(((NPC) target).getIndex(), 0));
		}
		noteSpecAnimations(player, tick);

		int weaponDamage = 0;
		boolean weaponHit = false;
		for (Hit hit : hits)
		{
			if (hit.source == Source.WEAPON)
			{
				weaponHit = true;
				weaponDamage += hit.amount;
			}
		}
		return weaponHit && player != null ? landAttack(attackSpeed(player, target, type, tick, weaponDamage), tick, player, target, type) : null;
	}

	// A splash has no hitsplat, so your target newly splashing counts as a 0.
	private boolean detectSplash(@Nullable Actor target, WeaponAttackType type)
	{
		boolean splashed = target instanceof NPC && target.hasSpotAnim(SpotanimID.FAILEDSPELL_IMPACT) && type == WeaponAttackType.MAGIC;
		boolean newSplash = splashed && !splashedLastTick;
		splashedLastTick = splashed;
		return newSplash;
	}

	private void claimVengeanceAndRecoil(List<Hit> hits, List<Integer> taken)
	{
		int rebound = client.getVarbitValue(VarbitID.VENGEANCE_REBOUND);
		boolean vengeanceFired = lastVengeanceRebound == 1 && rebound == 0;
		lastVengeanceRebound = rebound;

		boolean recoilRing = !hits.isEmpty() && !taken.isEmpty() && wearingRecoilRing();
		for (int damage : taken)
		{
			if (vengeanceFired && claim(hits, Source.VENGEANCE, damage * 3 / 4))
			{
				vengeanceFired = false;
			}
			if (recoilRing && damage > 0)
			{
				claim(hits, Source.RECOIL, damage / 10 + 1);
			}
		}
	}

	// Combat XP means you started an attack. Magic XP only counts while attacking with magic,
	// since non-combat spells give it too.
	private boolean detectAttackStart(int tick, @Nullable Actor target, WeaponAttackType type)
	{
		long combatXp = combatXp();
		int magicXp = client.getSkillExperience(Skill.MAGIC);
		boolean magicAttack = target instanceof NPC && type == WeaponAttackType.MAGIC;
		boolean attackStarted = lastCombatXp >= 0 && (combatXp > lastCombatXp || magicAttack && magicXp > lastMagicXp);
		if (attackStarted)
		{
			pendingAttacks.addLast(new PendingAttack(tick, equippedWeaponId()));
		}
		lastCombatXp = combatXp;
		lastMagicXp = magicXp;
		pendingAttacks.removeIf(pending -> tick - pending.tick > MAX_HIT_DELAY_TICKS);
		return attackStarted;
	}

	// A lone small hit on a tick you started a melee attack is your swing, not the thrall.
	private void claimThrallHit(List<Hit> hits, int tick, WeaponAttackType type, boolean attackStarted)
	{
		boolean melee = type == WeaponAttackType.STAB || type == WeaponAttackType.SLASH || type == WeaponAttackType.CRUSH;
		boolean meleeSwing = melee && attackStarted && hits.size() == 1;
		if (client.getVarbitValue(VarbitID.ARCEUUS_RESURRECTION_ACTIVE) == 1 && tick - lastThrallTick >= THRALL_ATTACK_TICKS && !meleeSwing && claimThrall(hits, melee))
		{
			lastThrallTick = tick;
		}
	}

	private void noteSpecAnimations(@Nullable Player player, int tick)
	{
		int animation = player == null ? -1 : player.getAnimation();
		if (animation == AnimationID.TOA_KERIS_PARTISAN_SPECIAL01)
		{
			kerisSpecTick = tick;
		}
		else if (animation == AnimationID.HUMAN_EYE_OF_AYAK_SPECIAL)
		{
			ayakSpecTick = tick;
		}
	}

	// Timed by when attacks start, not land, since ranged/magic delay varies with distance. A start
	// too soon after the last is part of it (e.g. claws) unless it came from XP: then it's a faster
	// attack. Null if merged.
	@Nullable
	private Attack landAttack(int speed, int tick, Player player, @Nullable Actor target, WeaponAttackType type)
	{
		PendingAttack pending = pendingAttacks.peekFirst();
		int start = pending != null ? pending.tick : tick - hitDelay(player, target, type);
		int previousTick = lastAttackTick;
		int correction = 0;
		boolean early = previousTick >= 0 && start - previousTick < Math.max(2, lastAttackSpeed - 1);
		if (early)
		{
			if (pending == null)
			{
				return null;
			}
			correction = (start - previousTick) - lastAttackSpeed;
		}

		if (pending != null)
		{
			pendingAttacks.pollFirst();
		}
		lastAttackTick = start;
		lastAttackSpeed = speed;
		// A miss gives no XP, so its weapon is whatever's equipped as it lands.
		int weapon = pending != null ? pending.weapon : equippedWeaponId();
		return new Attack(speed, start, weapon, previousTick, correction);
	}

	// -1 when unarmed.
	private int equippedWeaponId()
	{
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		Item weapon = worn == null ? null : worn.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		return weapon == null || weapon.getId() <= 0 ? -1 : weapon.getId();
	}

	// Ticks from attacking to the hit landing, the same formulas core's spec counter uses.
	private static int hitDelay(Player player, @Nullable Actor target, WeaponAttackType type)
	{
		if (target == null || type != WeaponAttackType.RANGED && type != WeaponAttackType.MAGIC)
		{
			return 0;
		}

		int distance = target.getWorldArea().distanceTo(player.getWorldLocation());
		return type == WeaponAttackType.RANGED ? 1 + (3 + distance) / 6 : 1 + (1 + distance) / 3;
	}

	private long combatXp()
	{
		long xp = 0;
		for (Skill skill : COMBAT_SKILLS)
		{
			xp += client.getSkillExperience(skill);
		}
		return xp;
	}

	// Speed of the attack landing now. Rules ported from Attack Timer Metronome
	// (github.com/ngraves95/attacktimer, BSD 2-Clause), weapon effects only.
	private int attackSpeed(Player player, @Nullable Actor target, WeaponAttackType type, int tick, int weaponDamage)
	{
		// Eye of ayak spec sets the speed. Magic lands a few ticks after the cast.
		if (tick - ayakSpecTick <= MAGIC_SPEC_LAND_TICKS)
		{
			return 5;
		}

		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		Item weapon = worn == null ? null : worn.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		int weaponId = weapon == null ? -1 : weapon.getId();
		int category = client.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY);
		int styleIndex = client.getVarpValue(VarPlayerID.COM_MODE);

		int speed;
		if (styleIndex == 4) // Autocast
		{
			boolean standard = client.getVarbitValue(VarbitID.SPELLBOOK) == 0;
			speed = weaponId == ItemID.TWINFLAME_STAFF ? 6 : weaponId == ItemID.NIGHTMARE_STAFF_HARMONISED && standard ? 4 : 5;
		}
		else if (category == POWERED_STAFF_CATEGORY || category == POWERED_WAND_CATEGORY)
		{
			speed = 4;
		}
		else if (category == OBLIGATOR_CATEGORY)
		{
			speed = styleIndex == OBLIGATOR_SMASH_SLOT ? 3 : 5;
		}
		else if (weaponId == ItemID.ZORYAS_TOME)
		{
			// 3 ticks, or 2 for the empowered attacks after a landed spec.
			speed = target != null && target.hasSpotAnim(SpotanimID.VFX_ZORYA_TOME_SPECIAL_FOLLOW_UP_IMPACT) ? 2 : 3;
		}
		else
		{
			ItemStats stats = weaponId <= 0 ? null : itemManager.getItemStats(weaponId);
			speed = stats == null || stats.getEquipment() == null ? 4 : stats.getEquipment().getAspeed();
		}

		if (styleIndex == 1 && type == WeaponAttackType.RANGED) // Rapid
		{
			speed--;
		}
		if (player.hasSpotAnim(SpotanimID.SPECIAL_DUAL_MACUAHUITL_SPOTANIM)) // Blood moon set
		{
			speed--;
		}
		if (tick - kerisSpecTick <= 1 && weaponDamage > 0) // Red keris spec that hit
		{
			speed += 4;
		}
		return speed;
	}

	private static boolean claim(List<Hit> hits, Source source, int amount)
	{
		for (Hit hit : hits)
		{
			if (hit.source == Source.WEAPON && hit.amount == amount)
			{
				hit.source = source;
				return true;
			}
		}
		return false;
	}

	// Same-tick order is [delayed hits, thrall, vengeance, instant hits], so the thrall is the
	// first small hit for melee and the last for ranged/magic.
	private static boolean claimThrall(List<Hit> hits, boolean melee)
	{
		Hit thrall = null;
		for (Hit hit : hits)
		{
			if (hit.source == Source.WEAPON && hit.amount <= THRALL_MAX_HIT)
			{
				thrall = hit;
				if (melee)
				{
					break;
				}
			}
		}

		if (thrall == null)
		{
			return false;
		}
		thrall.source = Source.THRALL;
		return true;
	}

	private boolean wearingRecoilRing()
	{
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		Item ring = worn == null ? null : worn.getItem(EquipmentInventorySlot.RING.getSlotIdx());
		if (ring == null || ring.getId() <= 0)
		{
			return false;
		}

		String name = client.getItemDefinition(ring.getId()).getName().toLowerCase(Locale.ROOT);
		return name.contains("recoil") || name.contains("suffering");
	}
}
