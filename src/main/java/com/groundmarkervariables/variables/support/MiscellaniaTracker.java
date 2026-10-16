package com.groundmarkervariables.variables.support;

import com.groundmarkervariables.GroundMarkerVariablesConfig;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

// Kingdom of Miscellania approval, the same way core's Kingdom plugin estimates it: the varbit is
// only current inside the kingdom, so elsewhere it's the last snapshot minus a fixed daily decay.
@Singleton
public class MiscellaniaTracker
{
	public static final int MAX_APPROVAL = 127;
	private static final Set<Integer> KINGDOM_REGIONS = Set.of(10044, 10300);
	private static final float DAILY_DECAY = 0.025f;
	private static final float DAILY_DECAY_ROYAL_TROUBLE = 0.010f;
	private static final String APPROVAL_KEY = "miscellaniaApproval";
	private static final String COFFER_KEY = "miscellaniaCoffer";
	private static final String LAST_CHANGED_KEY = "miscellaniaLastChanged";
	// Core Kingdom of Miscellania plugin's own snapshot.
	private static final String CORE_GROUP = "kingdomofmiscellania";
	private static final String CORE_APPROVAL_KEY = "approval";
	private static final String CORE_LAST_CHANGED_KEY = "lastChanged";

	private final Client client;
	private final ConfigManager configManager;
	private final EventBus eventBus;

	@Inject
	private MiscellaniaTracker(Client client, ConfigManager configManager, EventBus eventBus)
	{
		this.client = client;
		this.configManager = configManager;
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
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarbitId() != VarbitID.MISC_APPROVAL && event.getVarbitId() != VarbitID.MISC_COFFERS)
		{
			return;
		}

		int approval = client.getVarbitValue(VarbitID.MISC_APPROVAL);
		int coffer = client.getVarbitValue(VarbitID.MISC_COFFERS);
		if (client.getVarpValue(VarPlayerID.MISC_QUEST) > 0
			&& (inKingdom() || coffer > 0 && approval > 0)
			&& (stored(APPROVAL_KEY) != approval || stored(COFFER_KEY) != coffer))
		{
			configManager.setRSProfileConfiguration(GroundMarkerVariablesConfig.GROUP, LAST_CHANGED_KEY, Instant.now());
			configManager.setRSProfileConfiguration(GroundMarkerVariablesConfig.GROUP, APPROVAL_KEY, approval);
			configManager.setRSProfileConfiguration(GroundMarkerVariablesConfig.GROUP, COFFER_KEY, coffer);
		}
	}

	// Client thread only (quest state check). Uses core's Kingdom plugin snapshot instead of ours
	// when it's more recent.
	public int approval()
	{
		Instant lastChanged = configManager.getRSProfileConfiguration(GroundMarkerVariablesConfig.GROUP, LAST_CHANGED_KEY, Instant.class);
		int approval = stored(GroundMarkerVariablesConfig.GROUP, APPROVAL_KEY);

		Instant coreLastChanged = configManager.getRSProfileConfiguration(CORE_GROUP, CORE_LAST_CHANGED_KEY, Instant.class);
		if (coreLastChanged != null && (lastChanged == null || coreLastChanged.isAfter(lastChanged)))
		{
			lastChanged = coreLastChanged;
			approval = stored(CORE_GROUP, CORE_APPROVAL_KEY);
		}

		if (inKingdom() || lastChanged == null)
		{
			return client.getVarbitValue(VarbitID.MISC_APPROVAL);
		}

		long days = Duration.between(lastChanged.truncatedTo(ChronoUnit.DAYS), Instant.now().truncatedTo(ChronoUnit.DAYS)).toDays();
		float decay = Quest.ROYAL_TROUBLE.getState(client) == QuestState.FINISHED ? DAILY_DECAY_ROYAL_TROUBLE : DAILY_DECAY;
		return Math.max(0, approval - (int) (days * decay * MAX_APPROVAL));
	}

	private int stored(String key)
	{
		return stored(GroundMarkerVariablesConfig.GROUP, key);
	}

	private int stored(String group, String key)
	{
		Integer value = configManager.getRSProfileConfiguration(group, key, int.class);
		return value == null ? 0 : value;
	}

	private boolean inKingdom()
	{
		return client.getLocalPlayer() != null && KINGDOM_REGIONS.contains(client.getLocalPlayer().getWorldLocation().getRegionID());
	}
}
