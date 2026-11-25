package com.groundmarkervariables.variables.support;

import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

import com.groundmarkervariables.variables.label.SimpleVariables;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

// Whether {active} effects are up, and for timed ones how much is left, from the same messages, varbits and
// durations as RuneLite's Timers & Buffs and Spell Reminder. A timer needs its start to have been seen.
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ActiveEffectTracker {
	public enum Effect {
		VENGEANCE, THRALL, DEATH_CHARGE, SHADOW_VEIL, MARK_OF_DARKNESS
	}

	// Matched against Text.standardize()'d messages.
	private static final String SHADOW_VEIL = "your thieving abilities have been enhanced.";
	private static final String MARK_PLACED = "you have placed a mark of darkness upon yourself.";
	private static final String MARK_FADED = "your mark of darkness has faded away.";
	// 60 seconds.
	private static final int DEATH_CHARGE_TICKS = 100;
	// Thralls last twice as long from the Master Combat Achievement tier, and take 4 ticks to appear.
	private static final int MASTER_TIER = 5;
	private static final int THRALL_SUMMON_TICKS = 4;
	private static final int PURGING_STAFF_MULTIPLIER = 5;

	@RequiredArgsConstructor
	private static final class Timer {
		private final int start;
		private final int ticks;
	}

	private final Client client;
	private final EventBus eventBus;
	private final SimpleVariables simpleVariables;
	private final Map<Effect, Timer> timers = new EnumMap<>(Effect.class);
	private int lastDeathCharge;

	public void startUp() {
		eventBus.register(this);
	}

	public void shutDown() {
		eventBus.unregister(this);
		timers.clear();
		lastDeathCharge = 0;
	}

	public boolean active(Effect effect) {
		switch (effect) {
			case VENGEANCE:
				return client.getVarbitValue(VarbitID.VENGEANCE_REBOUND) != 0;
			case THRALL:
				return client.getVarbitValue(VarbitID.ARCEUUS_RESURRECTION_ACTIVE) != 0;
			case DEATH_CHARGE:
				return client.getVarbitValue(VarbitID.ARCEUUS_DEATH_CHARGE_ACTIVE) != 0;
			case SHADOW_VEIL:
				return client.getVarbitValue(VarbitID.ARCEUUS_SHADOW_VEIL_ACTIVE) != 0;
			default:
				// No varbit: only its timer.
				return ticksLeft(effect) > 0;
		}
	}

	// Percent of an active effect's time left, or null if it isn't timed, its start wasn't seen, or its
	// estimate ran out while it's still up.
	@Nullable
	public Integer percentLeft(Effect effect) {
		Timer timer = timers.get(effect);
		int left = ticksLeft(effect);
		return timer == null || left <= 0 ? null : (int) Math.ceil(100.0 * left / timer.ticks);
	}

	private int ticksLeft(Effect effect) {
		Timer timer = timers.get(effect);
		return timer == null ? 0 : timer.start + timer.ticks - client.getTickCount();
	}

	@Subscribe
	public void onChatMessage(ChatMessage event) {
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) {
			return;
		}

		String message = Text.standardize(event.getMessage());
		int magic = client.getRealSkillLevel(Skill.MAGIC);
		if (message.endsWith(SHADOW_VEIL)) {
			start(Effect.SHADOW_VEIL, magic);
		} else if (message.endsWith(MARK_PLACED)) {
			start(Effect.MARK_OF_DARKNESS, magic * 3 * (wieldingPurgingStaff() ? PURGING_STAFF_MULTIPLIER : 1));
		} else if (message.endsWith(MARK_FADED)) {
			timers.remove(Effect.MARK_OF_DARKNESS);
		}
	}

	// Mark of Darkness ends on death.
	@Subscribe
	public void onActorDeath(ActorDeath event) {
		if (event.getActor() == client.getLocalPlayer()) {
			timers.remove(Effect.MARK_OF_DARKNESS);
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event) {
		int value = event.getValue();
		switch (event.getVarbitId()) {
			case VarbitID.ARCEUUS_DEATH_CHARGE_ACTIVE:
				// 2 is a recast while one's already up.
				if (value == 2 || value == 1 && lastDeathCharge == 0) {
					start(Effect.DEATH_CHARGE, DEATH_CHARGE_TICKS);
				} else if (value == 0) {
					timers.remove(Effect.DEATH_CHARGE);
				}
				lastDeathCharge = value;
				break;
			case VarbitID.ARCEUUS_RESURRECTION_COOLDOWN:
				// Set on cast: boosted Magic level ticks, after it appears.
				if (value == 1) {
					int ticks = client.getBoostedSkillLevel(Skill.MAGIC);
					start(Effect.THRALL, (simpleVariables.combatAchievementTier() >= MASTER_TIER ? ticks * 2 : ticks) + THRALL_SUMMON_TICKS);
				}
				break;
			case VarbitID.ARCEUUS_RESURRECTION_ACTIVE:
				if (value == 0) {
					timers.remove(Effect.THRALL);
				}
				break;
			case VarbitID.ARCEUUS_SHADOW_VEIL_ACTIVE:
				if (value == 0) {
					timers.remove(Effect.SHADOW_VEIL);
				}
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING) {
			timers.clear();
		}
	}

	private void start(Effect effect, int ticks) {
		timers.put(effect, new Timer(client.getTickCount(), ticks));
	}

	private boolean wieldingPurgingStaff() {
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		Item weapon = worn == null ? null : worn.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		return weapon != null && (weapon.getId() == ItemID.PURGING_STAFF || weapon.getId() == ItemID.BR_PURGING_STAFF);
	}
}
