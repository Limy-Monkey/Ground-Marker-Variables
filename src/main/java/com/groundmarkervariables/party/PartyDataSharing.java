package com.groundmarkervariables.party;

import com.groundmarkervariables.variables.support.SkillFinder;
import com.groundmarkervariables.variables.support.Spellbooks;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
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
import net.runelite.client.party.messages.PartyMemberMessage;
import net.runelite.client.party.messages.UserSync;
import net.runelite.client.plugins.Plugin;

// Shares party data on demand: we publish what our nearby {party} tiles need, and subscribe
// PartyDataService to the union of what everyone else needs. Our own PartyValues are sent on change
// while we or anyone else subscribes to them.
@Slf4j
@Singleton
public class PartyDataSharing
{
	private static final Pattern ITEM_WORD = Pattern.compile("\\b(?:hasItem|numItem|numDoses|showItems?)\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern SPELL_WORD = Pattern.compile(
		"\\b(?:hasFreezes?|hasAlchs|hasEntangle|hasVileVigour|hasVengeance|hasThralls)\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern SPELLBOOK_WORD = Pattern.compile("\\bspellbook\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern EQUIP_WORD = Pattern.compile("\\bequip_(\\w+)\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern WEAPON_WORD = Pattern.compile("\\bweapon\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern SKILL_WORD = Pattern.compile("\\b(?:lvl|boost)_([a-z]+)\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern SPEC_WORD = Pattern.compile("\\bspec\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern ENERGY_WORD = Pattern.compile("\\brunEnergy\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern DISTANCE_WORD = Pattern.compile("\\b(?:playerDistance|readyCheck)\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern HP_WORD = Pattern.compile("\\bhp\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern PRAYER_WORD = Pattern.compile("\\bprayer\\b", Pattern.CASE_INSENSITIVE);

	private final Client client;
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
	private final Set<PartyValue> tickValues = EnumSet.noneOf(PartyValue.class);
	private Set<PartyDataType> sentCore = EnumSet.noneOf(PartyDataType.class);
	private Set<Skill> sentSkills = EnumSet.noneOf(Skill.class);
	private Set<PartyValue> sentValues = EnumSet.noneOf(PartyValue.class);
	private volatile boolean resend;

	// Our values as last published.
	private final Map<PartyValue, String> publishedValues = new EnumMap<>(PartyValue.class);
	private volatile boolean republishValues;

	// Arrive on WSClient's thread.
	private final Map<Long, GMVPartySubscriptions> memberSets = new ConcurrentHashMap<>();
	private final Map<Long, Map<PartyValue, String>> memberValues = new ConcurrentHashMap<>();
	private final Set<PartyDataType> subscribedCore = EnumSet.noneOf(PartyDataType.class);
	private final Set<Skill> subscribedSkills = EnumSet.noneOf(Skill.class);
	private volatile Set<PartyValue> othersValues = EnumSet.noneOf(PartyValue.class);

	@Inject
	private PartyDataSharing(Client client, EventBus eventBus, PartyService partyService, PartyDataService partyDataService, WSClient wsClient)
	{
		this.client = client;
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
			wsClient.registerMessage(GMVPartyValues.class);
			registered = true;
		}
		catch (IllegalArgumentException e)
		{
			log.warn("Party data sharing unavailable due to a message type collision with another plugin", e);
			unregisterMessage(GMVPartySubscriptions.class);
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
		unregisterMessage(GMVPartySubscriptions.class);
		unregisterMessage(GMVPartyValues.class);
		registered = false;
		clearParty();
	}

	private void unregisterMessage(Class<? extends PartyMemberMessage> message)
	{
		try
		{
			wsClient.unregisterMessage(message);
		}
		catch (IllegalArgumentException e)
		{
			// Never registered successfully.
		}
	}

	// A member's shared value, or null if they haven't sent it.
	@Nullable
	public String value(long memberId, PartyValue value)
	{
		Map<PartyValue, String> values = memberValues.get(memberId);
		return values == null ? null : values.get(value);
	}

	// Adds the data a {party} template needs from other members.
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
		if (SPELL_WORD.matcher(inside).find())
		{
			tickCore.addAll(EnumSet.of(PartyDataType.INV, PartyDataType.WORN, PartyDataType.RUNE_POUCH));
			tickValues.add(PartyValue.SPELLBOOK);
		}
		if (SPELLBOOK_WORD.matcher(inside).find())
		{
			tickValues.add(PartyValue.SPELLBOOK);
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
		Set<PartyValue> values = EnumSet.noneOf(PartyValue.class);
		values.addAll(tickValues);
		tickCore.clear();
		tickSkills.clear();
		tickValues.clear();

		if (!partyService.isInParty())
		{
			return;
		}

		sendSubscriptions(core, skills, values);
		publishValues();
	}

	private void sendSubscriptions(Set<PartyDataType> core, Set<Skill> skills, Set<PartyValue> values)
	{
		if (!resend && core.equals(sentCore) && skills.equals(sentSkills) && values.equals(sentValues))
		{
			return;
		}

		boolean gainedCore = !sentCore.containsAll(core) || !sentSkills.containsAll(skills);
		resend = false;
		sentCore = core;
		sentSkills = skills;
		sentValues = values;
		partyService.send(new GMVPartySubscriptions(new ArrayList<>(core), new ArrayList<>(skills), new ArrayList<>(values)));
		if (gainedCore)
		{
			partyService.send(new UserSync());
		}
	}

	// Our values that we or anyone else subscribes to, when they change.
	private void publishValues()
	{
		Set<PartyValue> publishing = EnumSet.noneOf(PartyValue.class);
		publishing.addAll(sentValues);
		publishing.addAll(othersValues);
		publishedValues.keySet().retainAll(publishing);

		boolean all = republishValues;
		republishValues = false;
		Map<String, String> changed = new HashMap<>();
		for (PartyValue value : publishing)
		{
			String current = localValue(value);
			if (current != null && (all || !current.equals(publishedValues.get(value))))
			{
				changed.put(value.name(), current);
				publishedValues.put(value, current);
			}
		}

		if (!changed.isEmpty())
		{
			partyService.send(new GMVPartyValues(changed));
		}
	}

	@Nullable
	private String localValue(PartyValue value)
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		switch (value)
		{
			case SPELLBOOK:
				return Spellbooks.name(client);
			default:
				return null;
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
		if (event.getValues() != null && !event.getValues().isEmpty())
		{
			republishValues = true;
		}
	}

	@Subscribe
	public void onGMVPartyValues(GMVPartyValues event)
	{
		if (isOwn(event.getMemberId()) || event.getValues() == null)
		{
			return;
		}

		Map<PartyValue, String> values = memberValues.computeIfAbsent(event.getMemberId(), id -> new ConcurrentHashMap<>());
		event.getValues().forEach((name, text) ->
		{
			PartyValue value = find(name);
			if (value != null && text != null)
			{
				values.put(value, text);
			}
		});
	}

	@Nullable
	private static PartyValue find(String name)
	{
		for (PartyValue value : PartyValue.values())
		{
			if (value.name().equals(name))
			{
				return value;
			}
		}
		return null;
	}

	// Someone joined: they need everyone's set and values.
	@Subscribe
	public void onUserSync(UserSync event)
	{
		if (!isOwn(event.getMemberId()))
		{
			resend = true;
			republishValues = true;
		}
	}

	@Subscribe
	public void onUserPart(UserPart event)
	{
		memberValues.remove(event.getMemberId());
		if (memberSets.remove(event.getMemberId()) != null)
		{
			updateSubscription();
		}
	}

	@Subscribe
	public void onPartyChanged(PartyChanged event)
	{
		clearParty();
		resend = true;
	}

	private void clearParty()
	{
		memberSets.clear();
		memberValues.clear();
		updateSubscription();
		sentCore = EnumSet.noneOf(PartyDataType.class);
		sentSkills = EnumSet.noneOf(Skill.class);
		sentValues = EnumSet.noneOf(PartyValue.class);
		publishedValues.clear();
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
		Set<PartyValue> values = EnumSet.noneOf(PartyValue.class);
		for (GMVPartySubscriptions set : memberSets.values())
		{
			addNonNull(core, set.getCore());
			addNonNull(skills, set.getSkills());
			addNonNull(values, set.getValues());
		}
		othersValues = values;

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
