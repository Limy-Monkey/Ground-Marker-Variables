package com.groundmarkervariables.variables.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Hitsplat;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

// Damage for {dps}, only counted while a {dps} label is resolving (i.e. near one of its tiles).
// Resets once none has resolved for a couple of ticks.
@Singleton
public class DpsTracker
{
	public enum Source
	{
		WEAPON, THRALL, VENGEANCE, RECOIL
	}

	private static final int ACTIVE_TICKS = 2;
	// 30 seconds with no damage from you or to the NPCs you've hit.
	private static final int IDLE_TICKS = 50;
	private static final double SECONDS_PER_TICK = 0.6;
	private static final int THRALL_ATTACK_TICKS = 4;
	private static final int THRALL_MAX_HIT = 3;

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

	private final Client client;
	private final EventBus eventBus;
	private final WeaponStyleTracker weaponStyleTracker;

	// NPC index -> {my damage, others' damage}
	private final Map<Integer, int[]> damageByNpc = new HashMap<>();
	private final int[] damageBySource = new int[Source.values().length];
	// This tick's hits by you, and on you, until classify() sorts them.
	private final List<Hit> pendingHits = new ArrayList<>();
	private final List<Integer> pendingTaken = new ArrayList<>();
	private int lastActiveTick = -1;
	private int firstHitTick = -1;
	// Tick you died at, or the last damage tick once idle, or -1.
	private int pausedTick = -1;
	private int lastDamageTick = -1;
	private int lastThrallTick = -THRALL_ATTACK_TICKS;
	private int lastVengeanceRebound;
	private int myDamage;

	@Inject
	private DpsTracker(Client client, EventBus eventBus, WeaponStyleTracker weaponStyleTracker)
	{
		this.client = client;
		this.eventBus = eventBus;
		this.weaponStyleTracker = weaponStyleTracker;
	}

	public void startUp()
	{
		eventBus.register(this);
	}

	public void shutDown()
	{
		eventBus.unregister(this);
		reset();
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (!isActive())
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
		else if (hitsplat.isOthers() && pausedTick < 0 && damageByNpc.containsKey(npc))
		{
			damageByNpc.get(npc)[1] += hitsplat.getAmount();
			lastDamageTick = client.getTickCount();
		}
	}

	// Called whenever a {dps} label resolves.
	public void markActive()
	{
		if (!isActive())
		{
			reset();
		}
		lastActiveTick = client.getTickCount();
		classify();

		if (pausedTick < 0 && firstHitTick >= 0 && client.getBoostedSkillLevel(Skill.HITPOINTS) == 0)
		{
			pausedTick = lastActiveTick;
		}
		else if (pausedTick < 0 && firstHitTick >= 0 && lastActiveTick - lastDamageTick >= IDLE_TICKS)
		{
			pausedTick = lastDamageTick;
		}
	}

	// Sorts this tick's hits into vengeance, recoil and thrall, the rest being weapon damage.
	private void classify()
	{
		int rebound = client.getVarbitValue(VarbitID.VENGEANCE_REBOUND);
		boolean vengeanceFired = lastVengeanceRebound == 1 && rebound == 0;
		lastVengeanceRebound = rebound;

		List<Hit> hits = new ArrayList<>(pendingHits);
		List<Integer> taken = new ArrayList<>(pendingTaken);
		pendingHits.clear();
		pendingTaken.clear();
		if (hits.isEmpty())
		{
			return;
		}

		boolean recoilRing = !taken.isEmpty() && wearingRecoilRing();
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

		int tick = client.getTickCount();
		if (client.getVarbitValue(VarbitID.ARCEUUS_RESURRECTION_ACTIVE) == 1 && tick - lastThrallTick >= THRALL_ATTACK_TICKS && claimThrall(hits))
		{
			lastThrallTick = tick;
		}

		// Paused: only your own weapon hit starts a fresh run.
		if (pausedTick >= 0)
		{
			if (hits.stream().noneMatch(hit -> hit.source == Source.WEAPON))
			{
				return;
			}
			reset();
		}

		for (Hit hit : hits)
		{
			damageByNpc.computeIfAbsent(hit.npc, k -> new int[2])[0] += hit.amount;
			damageBySource[hit.source.ordinal()] += hit.amount;
			myDamage += hit.amount;
		}
		if (firstHitTick < 0)
		{
			firstHitTick = tick;
		}
		lastDamageTick = tick;
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
	private boolean claimThrall(List<Hit> hits)
	{
		WeaponAttackType type = weaponStyleTracker.current().type;
		boolean melee = type == WeaponAttackType.STAB || type == WeaponAttackType.SLASH || type == WeaponAttackType.CRUSH;
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

	// Damage per second since your first hit, all sources.
	public double dps()
	{
		if (firstHitTick < 0)
		{
			return 0;
		}

		int end = pausedTick >= 0 ? pausedTick : client.getTickCount();
		int ticks = Math.max(1, end - firstHitTick);
		return myDamage / (ticks * SECONDS_PER_TICK);
	}

	public int damage(Source source)
	{
		return damageBySource[source.ordinal()];
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

	private boolean isActive()
	{
		return lastActiveTick >= 0 && client.getTickCount() - lastActiveTick <= ACTIVE_TICKS;
	}

	public void reset()
	{
		damageByNpc.clear();
		Arrays.fill(damageBySource, 0);
		firstHitTick = -1;
		pausedTick = -1;
		myDamage = 0;
	}
}
