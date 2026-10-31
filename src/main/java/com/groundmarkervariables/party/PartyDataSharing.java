package com.groundmarkervariables.party;

import com.groundmarkervariables.variables.support.SkillFinder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.party.PartyDataService;
import net.runelite.client.party.PartyDataType;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.party.messages.UserSync;
import net.runelite.client.plugins.Plugin;

// Shares core party data on demand: we publish what our nearby {party} tiles need, and subscribe
// PartyDataService to the union of what everyone else needs.
@Slf4j
@Singleton
public class PartyDataSharing
{
	private static final Pattern ITEM_WORD = Pattern.compile("\\b(?:hasItem|numItem|numDoses|showItems?)\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern EQUIP_WORD = Pattern.compile("\\bequip_(\\w+)\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern WEAPON_WORD = Pattern.compile("\\bweapon\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern SKILL_WORD = Pattern.compile("\\b(?:lvl|boost)_([a-z]+)\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern SPEC_WORD = Pattern.compile("\\bspec\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern ENERGY_WORD = Pattern.compile("\\brunEnergy\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern DISTANCE_WORD = Pattern.compile("\\b(?:playerDistance|readyCheck)\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern HP_WORD = Pattern.compile("\\bhp\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern PRAYER_WORD = Pattern.compile("\\bprayer\\b", Pattern.CASE_INSENSITIVE);

	private final EventBus eventBus;
	private final PartyService partyService;
	private final PartyDataService partyDataService;
	private final WSClient wsClient;

	private Plugin plugin;
	private boolean registered;

	// Set by the plugin while it refreshes nearby tiles.
	public boolean collecting;

	private final Set<PartyDataType> tickCore = EnumSet.noneOf(PartyDataType.class);
	private final Set<Skill> tickSkills = EnumSet.noneOf(Skill.class);
	private Set<PartyDataType> sentCore = EnumSet.noneOf(PartyDataType.class);
	private Set<Skill> sentSkills = EnumSet.noneOf(Skill.class);
	private volatile boolean resend;

	// Arrive on WSClient's thread.
	private final Map<Long, GMVPartySubscriptions> memberSets = new ConcurrentHashMap<>();
	private final Set<PartyDataType> subscribedCore = EnumSet.noneOf(PartyDataType.class);
	private final Set<Skill> subscribedSkills = EnumSet.noneOf(Skill.class);

	@Inject
	private PartyDataSharing(EventBus eventBus, PartyService partyService, PartyDataService partyDataService, WSClient wsClient)
	{
		this.eventBus = eventBus;
		this.partyService = partyService;
		this.partyDataService = partyDataService;
		this.wsClient = wsClient;
	}

	public void startUp(Plugin plugin)
	{
		this.plugin = plugin;
		try
		{
			wsClient.registerMessage(GMVPartySubscriptions.class);
			registered = true;
		}
		catch (IllegalArgumentException e)
		{
			log.warn("Party data sharing unavailable due to a message type collision with another plugin", e);
			return;
		}
		eventBus.register(this);
		resend = true;
	}

	public void shutDown()
	{
		if (!registered)
		{
			return;
		}

		eventBus.unregister(this);
		wsClient.unregisterMessage(GMVPartySubscriptions.class);
		registered = false;
		memberSets.clear();
		updateSubscription();
		sentCore = EnumSet.noneOf(PartyDataType.class);
		sentSkills = EnumSet.noneOf(Skill.class);
	}

	// Adds the core data a {party} template needs from other members.
	public void addNeeds(String template)
	{
		if (!collecting)
		{
			return;
		}

		StringBuilder inside = new StringBuilder();
		StringBuilder outside = new StringBuilder();
		int depth = 0;
		for (char c : template.toCharArray())
		{
			if (c == '{')
			{
				depth++;
			}
			(depth > 0 ? inside : outside).append(c);
			if (c == '}' && depth > 0)
			{
				depth--;
				inside.append(' ');
			}
		}

		tickCore.add(PartyDataType.CHARACTER_NAME);
		if (ITEM_WORD.matcher(inside).find())
		{
			tickCore.addAll(EnumSet.of(PartyDataType.INV, PartyDataType.WORN, PartyDataType.RUNE_POUCH, PartyDataType.QUIVER));
		}
		Matcher equip = EQUIP_WORD.matcher(inside);
		while (equip.find())
		{
			tickCore.add(PartyDataType.WORN);
			if (equip.group(1).equalsIgnoreCase("quiver"))
			{
				tickCore.add(PartyDataType.QUIVER);
			}
		}
		if (WEAPON_WORD.matcher(inside).find())
		{
			tickCore.add(PartyDataType.WORN);
		}
		Matcher skill = SKILL_WORD.matcher(inside);
		while (skill.find())
		{
			Skill found = SkillFinder.find(skill.group(1));
			if (found != null)
			{
				tickSkills.add(found);
			}
		}
		if (SPEC_WORD.matcher(inside).find())
		{
			tickCore.add(PartyDataType.SPEC);
		}
		if (ENERGY_WORD.matcher(inside).find())
		{
			tickCore.add(PartyDataType.ENERGY);
		}
		if (DISTANCE_WORD.matcher(inside).find())
		{
			tickCore.add(PartyDataType.COORD);
		}
		if (HP_WORD.matcher(outside).find())
		{
			tickSkills.add(Skill.HITPOINTS);
		}
		if (PRAYER_WORD.matcher(outside).find())
		{
			tickSkills.add(Skill.PRAYER);
		}
	}

	// After the plugin's own onGameTick, which collects this tick's needs.
	@Subscribe(priority = -1)
	public void onGameTick(GameTick event)
	{
		Set<PartyDataType> core = EnumSet.noneOf(PartyDataType.class);
		core.addAll(tickCore);
		Set<Skill> skills = EnumSet.noneOf(Skill.class);
		skills.addAll(tickSkills);
		tickCore.clear();
		tickSkills.clear();

		if (!partyService.isInParty())
		{
			return;
		}

		if (!resend && core.equals(sentCore) && skills.equals(sentSkills))
		{
			return;
		}

		boolean gained = !sentCore.containsAll(core) || !sentSkills.containsAll(skills);
		resend = false;
		sentCore = core;
		sentSkills = skills;
		partyService.send(new GMVPartySubscriptions(new ArrayList<>(core), new ArrayList<>(skills)));
		if (gained)
		{
			partyService.send(new UserSync());
		}
	}

	@Subscribe
	public void onGMVPartySubscriptions(GMVPartySubscriptions event)
	{
		if (isOwn(event.getMemberId()))
		{
			return;
		}

		memberSets.put(event.getMemberId(), event);
		updateSubscription();
	}

	// Someone joined: they need everyone's set.
	@Subscribe
	public void onUserSync(UserSync event)
	{
		if (!isOwn(event.getMemberId()))
		{
			resend = true;
		}
	}

	@Subscribe
	public void onUserPart(UserPart event)
	{
		if (memberSets.remove(event.getMemberId()) != null)
		{
			updateSubscription();
		}
	}

	@Subscribe
	public void onPartyChanged(PartyChanged event)
	{
		memberSets.clear();
		updateSubscription();
		sentCore = EnumSet.noneOf(PartyDataType.class);
		sentSkills = EnumSet.noneOf(Skill.class);
		resend = true;
	}

	private boolean isOwn(long memberId)
	{
		PartyMember local = partyService.getLocalMember();
		return local != null && memberId == local.getMemberId();
	}

	// Subscribes PartyDataService to exactly the union of every other member's set.
	private synchronized void updateSubscription()
	{
		Set<PartyDataType> core = EnumSet.noneOf(PartyDataType.class);
		Set<Skill> skills = EnumSet.noneOf(Skill.class);
		for (GMVPartySubscriptions set : memberSets.values())
		{
			addNonNull(core, set.getCore());
			addNonNull(skills, set.getSkills());
		}

		Set<PartyDataType> removedCore = EnumSet.noneOf(PartyDataType.class);
		removedCore.addAll(subscribedCore);
		removedCore.removeAll(core);
		Set<Skill> removedSkills = EnumSet.noneOf(Skill.class);
		removedSkills.addAll(subscribedSkills);
		removedSkills.removeAll(skills);

		if (!removedCore.isEmpty())
		{
			partyDataService.unsubscribe(plugin, removedCore.toArray(new PartyDataType[0]));
		}
		if (!removedSkills.isEmpty())
		{
			partyDataService.unsubscribe(plugin, removedSkills.toArray(new Skill[0]));
		}
		if (!core.isEmpty())
		{
			partyDataService.subscribe(plugin, core.toArray(new PartyDataType[0]));
		}
		if (!skills.isEmpty())
		{
			partyDataService.subscribe(plugin, skills.toArray(new Skill[0]));
		}

		subscribedCore.clear();
		subscribedCore.addAll(core);
		subscribedSkills.clear();
		subscribedSkills.addAll(skills);
	}

	private static <T> void addNonNull(Set<T> into, Collection<T> values)
	{
		if (values != null)
		{
			values.stream().filter(Objects::nonNull).forEach(into::add);
		}
	}
}
