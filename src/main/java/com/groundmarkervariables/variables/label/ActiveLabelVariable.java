package com.groundmarkervariables.variables.label;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.PartyMemberVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.ActiveEffectTracker;
import com.groundmarkervariables.variables.support.ActiveEffectTracker.Effect;
import com.groundmarkervariables.variables.support.Icons;
import net.runelite.api.Client;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.party.PartyMember;

// {active <effect> [icon]} -> true if Vengeance, a thrall, Death Charge, Shadow Veil or Mark of Darkness is up. The
// icon is the spell's sprite with a <badge>, a pie of the time left if known, or dimmed when not active. In
// {party} rows, other members only have vengeance.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ActiveLabelVariable implements LabelVariable, PartyMemberVariable
{
	private static final Pattern PATTERN = Pattern.compile(
		"\\{active\\s+(vengeance|venge|thrall|deathCharge|shadowVeil|markOfDarkness|mark)(\\s+icon)?\\}", Pattern.CASE_INSENSITIVE);

	private static final Map<String, Effect> EFFECTS = Map.of(
		"vengeance", Effect.VENGEANCE, "venge", Effect.VENGEANCE, "thrall", Effect.THRALL, "deathcharge", Effect.DEATH_CHARGE,
		"shadowveil", Effect.SHADOW_VEIL, "markofdarkness", Effect.MARK_OF_DARKNESS, "mark", Effect.MARK_OF_DARKNESS);
	private static final Map<Effect, String> NAMES = Map.of(
		Effect.VENGEANCE, "Vengeance", Effect.THRALL, "Thrall", Effect.DEATH_CHARGE, "Death Charge",
		Effect.SHADOW_VEIL, "Shadow Veil", Effect.MARK_OF_DARKNESS, "Mark of Darkness");
	private static final Map<Effect, Integer> SPRITES = Map.of(
		Effect.VENGEANCE, SpriteID.LunarMagicOn.VENGEANCE, Effect.THRALL, SpriteID.MagicNecroOn.RESURRECT_SUPERIOR_SKELETON,
		Effect.DEATH_CHARGE, SpriteID.MagicNecroOn.DEATH_CHARGE, Effect.SHADOW_VEIL, SpriteID.MagicNecroOn.SHADOW_VEIL,
		Effect.MARK_OF_DARKNESS, SpriteID.MagicNecroOn.MARK_OF_DARKNESS);

	private final Client client;
	private final ActiveEffectTracker tracker;
	private final RichText richText;

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		return client.getLocalPlayer() == null ? null : String.valueOf(tracker.active(effect(matcher)));
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		Effect effect = effect(matcher);
		boolean active = tracker.active(effect);
		return rich(matcher, effect, active, active ? tracker.percentLeft(effect) : null);
	}

	// Other members: only vengeance, from core's PartyDataService.
	@Override
	public String resolvePlain(Matcher matcher, PartyMember member)
	{
		return effect(matcher) == Effect.VENGEANCE ? String.valueOf(member.isVengeanceActive()) : null;
	}

	@Override
	public String resolveRich(Matcher matcher, PartyMember member)
	{
		return effect(matcher) == Effect.VENGEANCE ? rich(matcher, Effect.VENGEANCE, member.isVengeanceActive(), null) : null;
	}

	@Override
	public String resolveRowRich(Matcher matcher, RichText richText)
	{
		return resolveRich(matcher);
	}

	// The name colored by active, or with icon its sprite: dimmed, or with a <badge> of percent left.
	private String rich(Matcher matcher, Effect effect, boolean active, @Nullable Integer percent)
	{
		if (matcher.group(2) == null)
		{
			return richText.booleanColored(active, NAMES.get(effect));
		}
		if (!active)
		{
			return Icons.sprite(SPRITES.get(effect), false);
		}
		return Icons.sprite(SPRITES.get(effect)) + (percent == null ? "<badge>" : "<badge=" + percent + ">");
	}

	private static Effect effect(Matcher matcher)
	{
		return EFFECTS.get(matcher.group(1).toLowerCase(Locale.ROOT));
	}
}
