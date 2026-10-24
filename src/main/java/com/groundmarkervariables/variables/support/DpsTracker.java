package com.groundmarkervariables.variables.support;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import com.groundmarkervariables.GroundMarkerPointData;
import com.groundmarkervariables.MarkerStorage;
import com.groundmarkervariables.variables.LabelResolver;
import com.groundmarkervariables.variables.label.DpsLabelVariable;
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
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
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
import net.runelite.client.util.Text;

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
		private final NPC actor;
		private final int npc;
		private final String name;
		// The boss name with its stage, or name if it isn't a boss.
		private final String bossName;
		private final boolean boss;
		private final int amount;
		private Source source = Source.WEAPON;

		private Hit(NPC npc, @Nullable String bossName, int amount)
		{
			this.actor = npc;
			this.npc = npc.getIndex();
			String shownName = shown(npc).getName() == null ? null : Text.removeTags(shown(npc).getName());
			this.name = shownName == null || shownName.isEmpty() || shownName.equals("null") ? "Unknown" : shownName;
			this.bossName = bossName == null ? name : bossName;
			this.boss = bossName != null;
			this.amount = amount;
		}
	}

	// weapon is NO_WEAPON for thralls, recoil and vengeance. target is the hit's NpcStats name.
	private static final class HistoryHit
	{
		private final int tick;
		private final int damage;
		private final int weapon;
		private final Source source;
		private final String target;

		private HistoryHit(int tick, int damage, int weapon, Source source, String target)
		{
			this.tick = tick;
			this.damage = damage;
			this.weapon = weapon;
			this.source = source;
			this.target = target;
		}
	}

	// An NPC you attacked, until it died or despawned, so its death can end a timeSpent run.
	private static final class NpcLife
	{
		private final NPC npc;
		private int end = -1;

		private NpcLife(NPC npc)
		{
			this.npc = npc;
		}
	}

	// bossReport's timeSpent: a run on one name lasts until the first attack on another, or the NPC it
	// was attacking dies. Runs on the same name add up.
	private static final class RunTime
	{
		private final Map<String, Integer> closedTicks = new HashMap<>();
		@Nullable
		private String name;
		private int start;
		private int npc;

		private void attack(String attackName, int attackNpc, int attackStart)
		{
			if (!attackName.equals(name))
			{
				close(attackStart);
				name = attackName;
				start = attackStart;
			}
			npc = attackNpc;
		}

		private void died(int deadNpc, int tick)
		{
			if (deadNpc == npc)
			{
				close(tick);
			}
		}

		private void close(int end)
		{
			if (name != null)
			{
				closedTicks.merge(name, Math.max(0, end - start), Integer::sum);
				name = null;
			}
		}

		private int ticks(String forName, int end)
		{
			int ticks = closedTicks.getOrDefault(forName, 0);
			return forName.equals(name) ? ticks + Math.max(0, end - start) : ticks;
		}

		private void clear()
		{
			closedTicks.clear();
			name = null;
		}
	}

	// The form shown in game. A transforming NPC's own definition is a placeholder named "null".
	private static NPCComposition shown(NPC npc)
	{
		NPCComposition transformed = npc.getTransformedComposition();
		return transformed != null ? transformed : npc.getComposition();
	}

	private static double perSecond(int damage, int ticks)
	{
		return ticks <= 0 ? 0 : damage / (ticks * SECONDS_PER_TICK);
	}

	private static boolean hasName(NPC npc)
	{
		String name = shown(npc).getName();
		return name != null && !Text.removeTags(name).isEmpty() && !name.equals("null");
	}

	// One {dps} tile's numbers.
	public final class Stats
	{
		// NPC index -> {my damage, others' damage}
		private final Map<Integer, int[]> damageByNpc = new HashMap<>();
		private final int[] damageBySource = new int[Source.values().length];
		// Each of your hits, for recentDps() and Export Raw Data.
		private final List<HistoryHit> history = new ArrayList<>();
		private int lastActiveTick = -1;
		private int firstHitTick = -1;
		// Tick you died or went idle at, or -1.
		private int pausedTick = -1;
		private int lastDamageTick = -1;
		private int myDamage;
		private int takenDamage;
		private int lastAttackTick = -1;
		private int lastAttackWeapon = NO_WEAPON;
		// Weapon item id (-1 unarmed) -> {damage, theory ticks, hits, hits that did damage, attacks,
		// theory ticks without early-attack corrections}, in the order first used.
		private final Map<Integer, int[]> theoryByWeapon = new LinkedHashMap<>();
		private int thrallTicks;
		// for {dps groupReport} and {dps bossReport}
		private final Map<String, NpcStats> bossStats = new LinkedHashMap<>();
		@Nullable
		private Hit lastAttackHit;
		// For timeSpent.
		private int firstAttackTick = -1;
		private int lastCountedTick;
		private final Map<Integer, Integer> ticksByWeapon = new HashMap<>();
		private final Map<Integer, NpcLife> lifeByIndex = new HashMap<>();
		private final RunTime bossTime = new RunTime();

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
				bossStats.computeIfAbsent(hit.bossName, name -> new NpcStats(name, hit.boss)).add(hit, weapon);
				if (hit.source == Source.WEAPON)
				{
					startLife(hit);
				}
				damageByNpc.computeIfAbsent(hit.npc, k -> new int[2])[0] += hit.amount;
				damageBySource[hit.source.ordinal()] += hit.amount;
				myDamage += hit.amount;
				history.add(new HistoryHit(tick, hit.amount, hit.source == Source.WEAPON ? weapon : NO_WEAPON, hit.source, hit.bossName));
				if (hit.source == Source.WEAPON)
				{
					if (weapon != NO_WEAPON)
					{
						int[] totals = theoryByWeapon.computeIfAbsent(weapon, k -> new int[6]);
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
					addNpcTicks(lastAttackHit, lastAttackWeapon, attack.previousCorrection, 0, 0);
				}
				int[] totals = theoryByWeapon.computeIfAbsent(attack.weapon, k -> new int[6]);
				totals[1] += attack.speed;
				totals[4]++;
				totals[5] += attack.speed;
				lastAttackTick = attack.startTick;
				lastAttackWeapon = attack.weapon;

				// The attack's NPC is the one its first weapon hit landed on.
				lastAttackHit = hits.stream().filter(hit -> hit.source == Source.WEAPON).findFirst().orElse(null);
				addNpcTicks(lastAttackHit, attack.weapon, attack.speed, attack.speed, 1);

				if (firstAttackTick < 0)
				{
					firstAttackTick = attack.startTick;
					lastCountedTick = attack.startTick;
				}
				if (lastAttackHit != null)
				{
					bossTime.attack(lastAttackHit.bossName, lastAttackHit.npc, attack.startTick);
				}
			}
		}

		// A new life the first time a weapon hit lands on this NPC (index reuse is a new NPC).
		private void startLife(Hit hit)
		{
			NpcLife life = lifeByIndex.get(hit.npc);
			if (life == null || life.npc != hit.actor)
			{
				lifeByIndex.put(hit.npc, new NpcLife(hit.actor));
			}
		}

		// Each tick after the attacks: the equipped weapon gets the tick, and NPCs that died stop counting.
		private void countTime(int tick, int weapon)
		{
			if (firstAttackTick < 0 || pausedTick >= 0)
			{
				return;
			}

			ticksByWeapon.merge(weapon, tick - lastCountedTick, Integer::sum);
			NpcStats running = bossTime.name == null ? null : bossStats.get(bossTime.name);
			if (running != null)
			{
				running.weaponTicks.merge(weapon, tick - lastCountedTick, Integer::sum);
			}
			lastCountedTick = tick;
			for (NpcLife life : lifeByIndex.values())
			{
				if (life.end < 0 && life.npc.isDead())
				{
					npcGone(life.npc, tick);
				}
			}
		}

		private void npcGone(NPC npc, int tick)
		{
			NpcLife life = lifeByIndex.get(npc.getIndex());
			if (life != null && life.npc == npc && life.end < 0 && pausedTick < 0)
			{
				life.end = tick;
				bossTime.died(npc.getIndex(), tick);
			}
		}

		private void addNpcTicks(@Nullable Hit hit, int weapon, int theoryTicks, int pureTicks, int attacks)
		{
			NpcStats stats = hit == null ? null : bossStats.get(hit.bossName);
			if (stats != null)
			{
				stats.theoryTicks += theoryTicks;
				stats.pureTicks += pureTicks;
				int[] totals = stats.byWeapon.computeIfAbsent(weapon, k -> new int[6]);
				totals[1] += theoryTicks;
				totals[4] += attacks;
				totals[5] += pureTicks;
			}
		}

		private void othersHit(Hit hit, int tick)
		{
			if (pausedTick < 0 && damageByNpc.containsKey(hit.npc))
			{
				damageByNpc.get(hit.npc)[1] += hit.amount;
				NpcStats stats = bossStats.get(hit.bossName);
				if (stats != null)
				{
					stats.others += hit.amount;
				}
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
			bossStats.clear();
			lastAttackHit = null;
			firstAttackTick = -1;
			ticksByWeapon.clear();
			lifeByIndex.clear();
			bossTime.clear();
		}

		// avgDps: damage per second of timeSpent, all sources.
		public double dps()
		{
			return perSecond(myDamage, timeSpentTicks());
		}

		public double avgDps(Source source)
		{
			return perSecond(damageBySource[source.ordinal()], timeSpentTicks());
		}

		public double dps(Source source)
		{
			return firstHitTick < 0 ? 0 : damageBySource[source.ordinal()] / (elapsedTicks() * SECONDS_PER_TICK);
		}

		// All sources over the last seconds, or since your first hit if that's sooner.
		public double recentDps(int seconds)
		{
			return recentDps(seconds, hit -> true);
		}

		public double recentDps(int seconds, Source source)
		{
			return recentDps(seconds, hit -> hit.source == source);
		}

		private double recentDps(int seconds, Predicate<HistoryHit> counts)
		{
			if (firstHitTick < 0 || seconds <= 0)
			{
				return 0;
			}

			int end = pausedTick >= 0 ? pausedTick : client.getTickCount();
			int windowTicks = Math.min((int) Math.ceil(seconds / SECONDS_PER_TICK), elapsedTicks());
			int damage = 0;
			for (HistoryHit hit : history)
			{
				if (hit.tick > end - windowTicks && counts.test(hit))
				{
					damage += hit.damage;
				}
			}
			return damage / (windowTicks * SECONDS_PER_TICK);
		}

		// Weapon item id (-1 unarmed) -> its stats, in the order first used.
		public Map<Integer, WeaponStats> weaponStats()
		{
			Map<Integer, WeaponStats> result = new LinkedHashMap<>();
			theoryByWeapon.forEach((weapon, totals) -> result.put(weapon, new WeaponStats(weapon, totals, null)));
			return result;
		}

		// One weapon's numbers in this tile for {dps report}, or on one target for {dps fullReport}.
		public final class WeaponStats
		{
			private final int weapon;
			private final int[] totals;
			@Nullable
			private final NpcStats target;

			private WeaponStats(int weapon, int[] totals, @Nullable NpcStats target)
			{
				this.weapon = weapon;
				this.totals = totals;
				this.target = target;
			}

			public int damage()
			{
				return totals[0];
			}

			public double theoryDps()
			{
				return totals[1] == 0 ? 0 : totals[0] / (totals[1] * SECONDS_PER_TICK);
			}

			// theoryDps without attacks sped up by things we don't model, e.g. boss mechanics.
			public double pureTheoryDps()
			{
				return totals[5] == 0 ? 0 : totals[0] / (totals[5] * SECONDS_PER_TICK);
			}

			public int ticksUsed()
			{
				return totals[1];
			}

			public double avgDps()
			{
				return perSecond(totals[0], timeSpentTicks());
			}

			public double recentDps(int seconds)
			{
				return Stats.this.recentDps(seconds, hit -> hit.weapon == weapon && (target == null || hit.target.equals(target.name)));
			}

			// Of everyone's damage on your targets (or the target).
			public double share()
			{
				long group = target == null ? groupTotal() : target.damage + target.others;
				return group == 0 ? 0 : totals[0] / (double) group;
			}

			// Of all your damage (or on the target), including thralls etc.
			public double split()
			{
				int mine = target == null ? myDamage : target.damage;
				return mine == 0 ? 0 : totals[0] / (double) mine;
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

			// Ticks wielded since your first attack (or during the target's timeSpent).
			public int timeSpentTicks()
			{
				return (target == null ? ticksByWeapon : target.weaponTicks).getOrDefault(weapon, 0);
			}
		}

		public List<NpcStats> bossStats()
		{
			return new ArrayList<>(bossStats.values());
		}

		// Every NPC with one name, e.g. a wave of adds, for {dps groupReport} and {dps bossReport}.
		public final class NpcStats
		{
			private final String name;
			// A boss stage from BossNames, not just an NPC name.
			private final boolean boss;
			private final Set<Integer> indexes = new HashSet<>();
			// All your sources.
			private int damage;
			private int others;
			private int weaponDamage;
			private int weaponHits;
			private int weaponLanded;
			private int theoryTicks;
			private int pureTicks;
			// Like theoryByWeapon and ticksByWeapon, for this target only.
			private final Map<Integer, int[]> byWeapon = new LinkedHashMap<>();
			private final Map<Integer, Integer> weaponTicks = new HashMap<>();

			private NpcStats(String name, boolean boss)
			{
				this.name = name;
				this.boss = boss;
			}

			public int timeSpentTicks()
			{
				return bossTime.ticks(name, endTick());
			}

			public boolean isBoss()
			{
				return boss;
			}

			private void add(Hit hit, int weapon)
			{
				indexes.add(hit.npc);
				damage += hit.amount;
				if (hit.source == Source.WEAPON)
				{
					weaponDamage += hit.amount;
					weaponHits++;
					weaponLanded += hit.amount > 0 ? 1 : 0;
					if (weapon != NO_WEAPON)
					{
						int[] totals = byWeapon.computeIfAbsent(weapon, k -> new int[6]);
						totals[0] += hit.amount;
						totals[2]++;
						totals[3] += hit.amount > 0 ? 1 : 0;
					}
				}
			}

			// Weapon item id (-1 unarmed) -> its stats on this target, in the order first used.
			public Map<Integer, WeaponStats> weaponStats()
			{
				Map<Integer, WeaponStats> result = new LinkedHashMap<>();
				byWeapon.forEach((weapon, totals) -> result.put(weapon, new WeaponStats(weapon, totals, this)));
				return result;
			}

			// A multi-hit attack like claws counts once.
			public int numAttacks()
			{
				return byWeapon.values().stream().mapToInt(totals -> totals[4]).sum();
			}

			public int numMisses()
			{
				return weaponHits - weaponLanded;
			}

			public int numHits()
			{
				return weaponLanded;
			}

			// Weapon hits on it that did damage, or -1 before any.
			public double accuracy()
			{
				return weaponHits == 0 ? -1 : weaponLanded / (double) weaponHits;
			}

			public String name()
			{
				return name;
			}

			public int count()
			{
				return indexes.size();
			}

			public int damage()
			{
				return damage;
			}

			// Your % of everyone's damage on it.
			public double share()
			{
				return damage + others == 0 ? 1 : damage / (double) (damage + others);
			}

			// Its part of all your damage.
			public double split()
			{
				return myDamage == 0 ? 0 : damage / (double) myDamage;
			}

			public double avgDps()
			{
				return perSecond(damage, timeSpentTicks());
			}

			// -1 before a weapon attack on it.
			public double theoryDps()
			{
				return theoryTicks <= 0 ? -1 : weaponDamage / (theoryTicks * SECONDS_PER_TICK);
			}

			public double pureTheoryDps()
			{
				return pureTicks <= 0 ? -1 : weaponDamage / (pureTicks * SECONDS_PER_TICK);
			}

			public int ticksUsed()
			{
				return theoryTicks;
			}

			public double recentDps(int seconds)
			{
				return Stats.this.recentDps(seconds, hit -> hit.target.equals(name));
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

		public double pureTheoryDps()
		{
			int ticks = weaponTotal(5);
			return ticks == 0 ? 0 : weaponTotal(0) / (ticks * SECONDS_PER_TICK);
		}

		// theoryDps's ticks: every attack's speed, after early-attack corrections.
		public int ticksUsed()
		{
			return weaponTotal(1);
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
			long group = groupTotal();
			return group == myDamage ? -1 : myDamage / (double) group;
		}

		// Your damage plus everyone else's on the NPCs you hit.
		public long groupTotal()
		{
			long group = myDamage;
			for (int[] damage : damageByNpc.values())
			{
				group += damage[1];
			}
			return group;
		}

		// From your first attack's start until now, or 0 before it.
		public int timeSpentTicks()
		{
			return firstAttackTick < 0 ? 0 : Math.max(0, endTick() - firstAttackTick);
		}

		private int endTick()
		{
			return pausedTick >= 0 ? pausedTick : client.getTickCount();
		}

		private int elapsedTicks()
		{
			return Math.max(1, endTick() - firstHitTick);
		}
	}

	private final Client client;
	private final EventBus eventBus;
	private final WeaponStyleTracker weaponStyleTracker;
	private final ItemManager itemManager;
	private final MarkerStorage markerStorage;
	// A Provider since LabelResolver depends on this, through DpsLabelVariable.
	private final Provider<LabelResolver> labelResolver;
	private final Provider<DpsLabelVariable> dpsLabelVariable;
	private final BossNames bossNames;

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
		// Before landing effects, see landingSpeed().
		private final int speed;

		private PendingAttack(int tick, int weapon, int speed)
		{
			this.tick = tick;
			this.weapon = weapon;
			this.speed = speed;
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

	// What you held on one recent tick, for misses (no XP) that land after a weapon switch.
	private static final class WeaponTick
	{
		private final int tick;
		private final int weapon;
		private final WeaponAttackType type;
		private final int speed;
		// You started an animation this tick.
		private final boolean animated;

		private WeaponTick(int tick, int weapon, WeaponAttackType type, int speed, boolean animated)
		{
			this.tick = tick;
			this.weapon = weapon;
			this.type = type;
			this.speed = speed;
			this.animated = animated;
		}
	}

	// Oldest first.
	private final Deque<WeaponTick> weaponHistory = new ArrayDeque<>();
	private int lastAnimationTick = -1;

	@Inject
	private DpsTracker(Client client, EventBus eventBus, WeaponStyleTracker weaponStyleTracker, ItemManager itemManager,
		MarkerStorage markerStorage, Provider<LabelResolver> labelResolver, Provider<DpsLabelVariable> dpsLabelVariable, BossNames bossNames)
	{
		this.dpsLabelVariable = dpsLabelVariable;
		this.client = client;
		this.eventBus = eventBus;
		this.weaponStyleTracker = weaponStyleTracker;
		this.itemManager = itemManager;
		this.markerStorage = markerStorage;
		this.labelResolver = labelResolver;
		this.bossNames = bossNames;
	}

	public void startUp()
	{
		eventBus.register(this);
		bossNames.load();
	}

	public void shutDown()
	{
		eventBus.unregister(this);
		statsByTile.clear();
		pendingHits.clear();
		pendingTaken.clear();
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		int tick = client.getTickCount();
		for (Stats stats : statsByTile.values())
		{
			if (stats.isActive(tick))
			{
				stats.npcGone(event.getNpc(), tick);
			}
		}
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

		// Nameless NPCs (e.g. hidden fight helpers) can't be attacked.
		if (!(actor instanceof NPC) || !hasName((NPC) actor))
		{
			return;
		}

		Hit hit = new Hit((NPC) actor, bossNames.name(shown((NPC) actor).getId()), hitsplat.getAmount());
		if (hitsplat.isMine())
		{
			pendingHits.add(hit);
		}
		else if (hitsplat.isOthers())
		{
			for (Stats stats : statsByTile.values())
			{
				if (stats.isActive(tick))
				{
					stats.othersHit(hit, tick);
				}
			}
		}
	}

	// Eating and drinking potions share these.
	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Actor actor = event.getActor();
		int animation = actor.getAnimation();
		if (actor == client.getLocalPlayer() && animation != -1 && animation != AnimationID.HUMAN_EAT && animation != AnimationID.HUMAN_EAT_WITHSOUND)
		{
			lastAnimationTick = client.getTickCount();
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
		forEachDpsToken(tile, token ->
		{
			String text = labelResolver.get().resolve(token).replaceAll("\\{/?align[^}]*}|</?col[^>]*>", "");
			for (String line : text.split("\\\\n"))
			{
				if (!line.isBlank())
				{
					// Chat only shows what's after the last "|".
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", line.trim().replace('|', '/'), null);
				}
			}
		});
	}

	// Each {dps} on the tile's rows with every keyword, copied as tab-separated text to paste into Google Sheets.
	public void exportReport(WorldPoint tile)
	{
		StringBuilder text = new StringBuilder();
		forEachDpsToken(tile, token ->
		{
			for (List<String> row : dpsLabelVariable.get().exportRows(token))
			{
				text.append(String.join("\t", row)).append('\n');
			}
			text.append('\n');
		});
		if (text.toString().isBlank())
		{
			return;
		}

		copyToClipboard(text.toString().stripTrailing(), "Copied the DPS report to your clipboard.");
	}

	// The tile's hits, one tab-separated row each, copied to paste into Google Sheets.
	public void exportRawData(WorldPoint tile)
	{
		Stats stats = statsByTile.get(tile);
		if (stats == null || stats.history.isEmpty())
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "No DPS hits to export.", null);
			return;
		}

		StringBuilder text = new StringBuilder("Tick\tSource\tTarget\tWeapon\tDamage");
		for (HistoryHit hit : stats.history)
		{
			String weapon = hit.weapon == NO_WEAPON ? "" : hit.weapon < 0 ? "Unarmed" : itemManager.getItemComposition(hit.weapon).getName();
			text.append('\n').append(hit.tick).append('\t').append(Text.titleCase(hit.source)).append('\t').append(hit.target)
				.append('\t').append(weapon).append('\t').append(hit.damage);
		}
		copyToClipboard(text.toString(), "Copied " + stats.history.size() + " DPS hits to your clipboard.");
	}

	private void copyToClipboard(String text, String message)
	{
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
	}

	// With resolvingTile set to tile.
	private void forEachDpsToken(WorldPoint tile, Consumer<String> action)
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
				action.accept(token.group());
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
				stats.countTime(tick, equippedWeaponId());
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
		weaponHistory.addLast(new WeaponTick(tick, equippedWeaponId(), type, baseSpeed(type), lastAnimationTick == tick));
		weaponHistory.removeIf(entry -> tick - entry.tick > MAX_HIT_DELAY_TICKS);

		boolean newSplash = detectSplash(target, type);
		claimVengeanceAndRecoil(hits, taken);
		boolean attackStarted = detectAttackStart(tick, target, type);
		claimThrallHit(hits, tick, type, attackStarted);
		// After the thrall claim, so the 0 can't be taken for a thrall hit.
		if (newSplash)
		{
			hits.add(new Hit((NPC) target, bossNames.name(shown((NPC) target).getId()), 0));
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
		return weaponHit && player != null ? landAttack(tick, player, target, type, weaponDamage) : null;
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
			pendingAttacks.addLast(new PendingAttack(tick, equippedWeaponId(), baseSpeed(type)));
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
	private Attack landAttack(int tick, Player player, @Nullable Actor target, WeaponAttackType type, int weaponDamage)
	{
		PendingAttack pending = pendingAttacks.peekFirst();
		WeaponTick fired = pending == null ? firedWeapon(tick, player, target) : null;
		int weapon;
		int baseSpeed;
		int start;
		if (pending != null)
		{
			weapon = pending.weapon;
			baseSpeed = pending.speed;
			start = pending.tick;
		}
		else if (fired != null)
		{
			weapon = fired.weapon;
			baseSpeed = fired.speed;
			start = fired.tick;
		}
		else
		{
			// Nothing matched: assume what's equipped as it lands.
			weapon = equippedWeaponId();
			baseSpeed = baseSpeed(type);
			start = tick - hitDelay(player, target, type);
		}
		int speed = landingSpeed(baseSpeed, weapon, target, tick, weaponDamage);
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
		return new Attack(speed, start, weapon, previousTick, correction);
	}

	// A miss gives no XP, so find the recent tick that fired it: you animated then, and that tick's
	// weapon's hit delay lands now. Projectiles can be a tick off if either of you moved.
	@Nullable
	private WeaponTick firedWeapon(int tick, Player player, @Nullable Actor target)
	{
		for (int slack = 0; slack <= 1; slack++)
		{
			Iterator<WeaponTick> newestFirst = weaponHistory.descendingIterator();
			while (newestFirst.hasNext())
			{
				WeaponTick entry = newestFirst.next();
				int delay = hitDelay(player, target, entry.type);
				if (entry.animated && (slack == 0 || delay > 0) && Math.abs(entry.tick + delay - tick) <= slack)
				{
					return entry;
				}
			}
		}
		return null;
	}

	// -1 when unarmed.
	public int equippedWeaponId()
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

	// Speed of an attack as it starts, from what's equipped. Rules ported from Attack Timer
	// Metronome (github.com/ngraves95/attacktimer, BSD 2-Clause), weapon effects only.
	private int baseSpeed(WeaponAttackType type)
	{
		int weaponId = equippedWeaponId();
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
		else if (weaponId == ItemID.ZORYAS_TOME || weaponId == ItemID.ASCENSION_CROSSBOWS)
		{
			speed = 3;
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
		Player player = client.getLocalPlayer();
		if (player != null && player.hasSpotAnim(SpotanimID.SPECIAL_DUAL_MACUAHUITL_SPOTANIM)) // Blood moon set
		{
			speed--;
		}
		return speed;
	}

	// Effects only known once the attack lands.
	private int landingSpeed(int speed, int weapon, @Nullable Actor target, int tick, int weaponDamage)
	{
		// Eye of ayak spec sets the speed. Magic lands a few ticks after the cast.
		if (tick - ayakSpecTick <= MAGIC_SPEC_LAND_TICKS)
		{
			return 5;
		}
		// Zorya's tome empowered attacks after a landed spec.
		if (weapon == ItemID.ZORYAS_TOME && target != null && target.hasSpotAnim(SpotanimID.VFX_ZORYA_TOME_SPECIAL_FOLLOW_UP_IMPACT))
		{
			return 2;
		}
		// Red keris spec that hit.
		return tick - kerisSpecTick <= 1 && weaponDamage > 0 ? speed + 4 : speed;
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
