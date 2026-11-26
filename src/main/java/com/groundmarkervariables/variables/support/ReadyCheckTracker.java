package com.groundmarkervariables.variables.support;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.util.Text;

// {readyCheck}: each party member's latest ready status, from chat or a new boost. Only recorded
// while a {readyCheck} tile is nearby, and reset once none is, e.g. on leaving a raid lobby.
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ReadyCheckTracker {
    // A status and when it was set.
    public static final class Status {
        public enum Kind {
            READY,
            POTTING,
            POTTED,
            NOT_READY,
            AWAY
        }

        public final Kind kind;
        public final LocalTime time;

        private Status(Kind kind) {
            this.kind = kind;
            this.time = LocalTime.now();
        }
    }

    private static final Skill[] POT_SKILLS = {Skill.STRENGTH, Skill.RANGED, Skill.MAGIC, Skill.HITPOINTS};

    private static final Pattern READY = Pattern.compile("r|ready|i['’]?m r|r2p", Pattern.CASE_INSENSITIVE);
    private static final Pattern POTTING = Pattern.compile("p|pot|potting", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOT_READY = Pattern.compile("(?:not|no|un) r", Pattern.CASE_INSENSITIVE);
    private static final Pattern AWAY = Pattern.compile("\\bbrb\\b|\\b\\d+ mins\\b", Pattern.CASE_INSENSITIVE);

    private final Client client;
    private final EventBus eventBus;
    private final PartyService partyService;

    // Set by the plugin while it refreshes nearby tiles.
    public boolean collecting;

    private boolean seenThisTick;
    private boolean active;

    // By standardized name.
    private final Map<String, Status> statuses = new HashMap<>();
    // Last tick's boosted levels in POT_SKILLS order, -1 if not known yet.
    private final Map<String, int[]> lastBoosts = new HashMap<>();

    public void startUp() {
        eventBus.register(this);
    }

    public void shutDown() {
        eventBus.unregister(this);
        statuses.clear();
        lastBoosts.clear();
        active = false;
        seenThisTick = false;
    }

    @Nullable
    public Status status(@Nullable String name) {
        seenThisTick |= collecting;
        return name == null ? null : statuses.get(standardize(name));
    }

    public void clear(@Nullable String name) {
        if (name != null) {
            statuses.remove(standardize(name));
        }
    }

    @Nullable
    public Status localStatus() {
        return client.getLocalPlayer() == null ? null : status(client.getLocalPlayer().getName());
    }
    // After the plugin's own onGameTick, which resolves the nearby tiles.
    @Subscribe(priority = -1)
    public void onGameTick(GameTick event) {
        active = seenThisTick;
        seenThisTick = false;
        if (!active) {
            statuses.clear();
            lastBoosts.clear();
            return;
        }

        checkBoosts();
    }

    // A boost that rose since last tick and is above base means they just potted.
    private void checkBoosts() {
        PartyMember localMember = partyService.getLocalMember();
        for (PartyMember member : partyService.getMembers()) {
            if (member.getDisplayName() == null) {
                continue;
            }

            boolean local = member.equals(localMember);
            String name = standardize(member.getDisplayName());
            int[] last = lastBoosts.get(name);
            int[] boosts = new int[POT_SKILLS.length];
            boolean potted = false;
            for (int i = 0; i < POT_SKILLS.length; i++) {
                Skill skill = POT_SKILLS[i];
                int base = local ? client.getRealSkillLevel(skill) : member.getStatBase(skill);
                int boost = local ? client.getBoostedSkillLevel(skill) : member.getStatBoost(skill);
                boosts[i] = base > 0 ? boost : -1;
                potted |= last != null && last[i] >= 0 && boosts[i] > last[i] && boost > base;
            }

            lastBoosts.put(name, boosts);
            if (potted) {
                statuses.put(name, new Status(Status.Kind.POTTED));
            }
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (!active || !isPlayerChat(event.getType()) || event.getName() == null) {
            return;
        }

        String name = standardize(event.getName());
        if (!isPartyMember(name)) {
            return;
        }

        String message = Text.removeTags(event.getMessage()).replace(' ', ' ').trim();
        Status.Kind kind = READY.matcher(message).matches() ? Status.Kind.READY
            : POTTING.matcher(message).matches() ? Status.Kind.POTTING
            : NOT_READY.matcher(message).matches() ? Status.Kind.NOT_READY
            : AWAY.matcher(message).find() ? Status.Kind.AWAY
            : null;
        if (kind != null) {
            statuses.put(name, new Status(kind));
        }
    }

    @Subscribe
    public void onPartyChanged(PartyChanged event) {
        statuses.clear();
        lastBoosts.clear();
    }

    private boolean isPartyMember(String name) {
        return partyService.getMembers().stream().anyMatch(member -> name.equals(standardize(member.getDisplayName())));
    }

    private static boolean isPlayerChat(ChatMessageType type) {
        switch (type) {
            case PUBLICCHAT:
            case MODCHAT:
            case PRIVATECHAT:
            case MODPRIVATECHAT:
            case FRIENDSCHAT:
            case CLAN_CHAT:
            case CLAN_GUEST_CHAT:
            case CLAN_GIM_CHAT:
                return true;
            default:
                return false;
        }
    }

    // Party names can arrive with underscores in place of spaces.
    private static String standardize(String name) {
        return Text.standardize(Text.removeTags(name).replace('_', ' '));
    }
}
