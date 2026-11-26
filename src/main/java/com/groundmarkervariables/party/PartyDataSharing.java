package com.groundmarkervariables.party;

import com.groundmarkervariables.GroundMarkerVariablesConfig;
import com.groundmarkervariables.variables.label.MetronomeLabelVariable;
import com.groundmarkervariables.variables.support.Spellbooks;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
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

// Shares party data for {party} and Party Sync: all of core's PartyDataService data, plus our own PartyValues,
// sent on change (at most once a tick) and all again after a UserSync.
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PartyDataSharing {
    private final Client client;
    private final ClientThread clientThread;
    private final EventBus eventBus;
    private final PartyService partyService;
    private final PartyDataService partyDataService;
    private final WSClient wsClient;
    private final GroundMarkerVariablesConfig config;
    // A Provider: the metronome reads members' values from here.
    private final Provider<MetronomeLabelVariable> metronome;

    private Plugin plugin;
    private boolean registered;

    // Our values as last published.
    private final Map<PartyValue, String> publishedValues = new EnumMap<>(PartyValue.class);
    private volatile boolean republishValues;

    // Arrive on WSClient's thread.
    private final Map<Long, Map<PartyValue, String>> memberValues = new ConcurrentHashMap<>();

    public void startUp(Plugin plugin) {
        this.plugin = plugin;
        partyDataService.subscribe(plugin, PartyDataType.values());
        partyDataService.subscribe(plugin, Skill.values());

        try {
            wsClient.registerMessage(GMVPartyValues.class);
            registered = true;
        } catch (IllegalArgumentException e) {
            log.warn("Party value sharing unavailable due to a message type collision with another plugin", e);
        }
        eventBus.register(this);

        // Others only send core data on change, so ask for it all now.
        if (partyService.isInParty()) {
            clientThread.invokeLater(() -> partyService.send(new UserSync()));
        }
        republishValues = true;
    }

    public void shutDown() {
        eventBus.unregister(this);
        partyDataService.unsubscribe(plugin, PartyDataType.values());
        partyDataService.unsubscribe(plugin, Skill.values());
        if (registered) {
            wsClient.unregisterMessage(GMVPartyValues.class);
            registered = false;
        }
        memberValues.clear();
        publishedValues.clear();
    }

    // A member's shared value, or null if they haven't sent it.
    @Nullable
    public String value(long memberId, PartyValue value) {
        Map<PartyValue, String> values = memberValues.get(memberId);
        return values == null ? null : values.get(value);
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (registered && partyService.isInParty()) {
            publishValues();
        }
    }

    // Our values, when they change.
    private void publishValues() {
        boolean all = republishValues;
        republishValues = false;
        Map<String, String> changed = new HashMap<>();
        for (PartyValue value : PartyValue.values()) {
            String current = localValue(value);
            if (current != null && (all || !current.equals(publishedValues.get(value)))) {
                changed.put(value.name(), current);
                publishedValues.put(value, current);
            }
        }

        if (!changed.isEmpty()) {
            partyService.send(new GMVPartyValues(changed));
        }
    }

    @Nullable
    private String localValue(PartyValue value) {
        if (client.getLocalPlayer() == null) {
            return null;
        }

        switch (value) {
            case SPELLBOOK:
                return Spellbooks.name(client);
            case METRONOME_RESET:
                return String.valueOf(metronome.get().resetTick());
            case METRONOME_COUNT_DOWN:
                return String.valueOf(config.countDown());
            default:
                return null;
        }
    }

    @Subscribe
    public void onGMVPartyValues(GMVPartyValues event) {
        if (isOwn(event.getMemberId()) || event.getValues() == null) {
            return;
        }

        Map<PartyValue, String> values = memberValues.computeIfAbsent(event.getMemberId(), id -> new ConcurrentHashMap<>());
        event.getValues().forEach((name, text) -> {
            PartyValue value = find(name);
            if (value != null && text != null) {
                values.put(value, text);
            }
        });
    }

    @Nullable
    private static PartyValue find(String name) {
        for (PartyValue value : PartyValue.values()) {
            if (value.name().equals(name)) {
                return value;
            }
        }
        return null;
    }

    // Someone joined or started up: they need our values.
    @Subscribe
    public void onUserSync(UserSync event) {
        if (!isOwn(event.getMemberId())) {
            republishValues = true;
        }
    }

    @Subscribe
    public void onUserPart(UserPart event) {
        memberValues.remove(event.getMemberId());
    }

    @Subscribe
    public void onPartyChanged(PartyChanged event) {
        memberValues.clear();
        publishedValues.clear();
        republishValues = true;
    }

    private boolean isOwn(long memberId) {
        PartyMember local = partyService.getLocalMember();
        return local != null && memberId == local.getMemberId();
    }
}
