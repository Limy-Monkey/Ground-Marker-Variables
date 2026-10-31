package com.groundmarkervariables.variables.label;

import java.util.regex.Matcher;
import javax.annotation.Nullable;

import com.groundmarkervariables.party.PartyDataSharing;
import com.groundmarkervariables.party.PartyValue;
import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.PartyMemberVariable;
import com.groundmarkervariables.variables.RichText;
import net.runelite.client.party.PartyMember;

// A {has<Spell>} variable, with an optional "icon" (group 1). For a party member it uses their core
// inventory/gear and their shared {spellbook}: with no spellbook yet it's true if the rest checks out,
// and the icon is drawn at 75%.
public abstract class SpellLabelVariable implements LabelVariable, PartyMemberVariable
{
	private final RichText richText;
	private final PartyDataSharing partyDataSharing;

	protected SpellLabelVariable(RichText richText, PartyDataSharing partyDataSharing)
	{
		this.richText = richText;
		this.partyDataSharing = partyDataSharing;
	}

	protected abstract boolean castable();

	// Null when everything checks out but spellbook is unknown.
	@Nullable
	protected abstract Boolean castable(PartyMember member, @Nullable String spellbook);

	// Rich Text name, e.g. "Freezes".
	protected abstract String text();

	protected abstract String icon(@Nullable Boolean castable);

	@Override
	public String resolvePlain(Matcher matcher)
	{
		return String.valueOf(castable());
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		boolean castable = castable();
		return matcher.group(1) != null ? icon(castable) : richText.booleanColored(castable, text());
	}

	@Override
	public String resolvePlain(Matcher matcher, PartyMember member)
	{
		return String.valueOf(!Boolean.FALSE.equals(memberCastable(member)));
	}

	@Override
	public String resolveRich(Matcher matcher, PartyMember member)
	{
		Boolean castable = memberCastable(member);
		return matcher.group(1) != null ? icon(castable) : richText.booleanColored(!Boolean.FALSE.equals(castable), text());
	}

	@Override
	public String resolveRowRich(Matcher matcher, RichText richText)
	{
		return resolveRich(matcher);
	}

	@Nullable
	private Boolean memberCastable(PartyMember member)
	{
		return castable(member, partyDataSharing.value(member.getMemberId(), PartyValue.SPELLBOOK));
	}
}
