package com.groundmarkervariables.variables.label;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;

import com.groundmarkervariables.party.PartyDataSharing;
import com.groundmarkervariables.party.PartyValue;
import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.PartyMemberVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.Icons;
import com.groundmarkervariables.variables.support.Spellbooks;
import net.runelite.api.Client;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.party.PartyMember;

// {spellbook} -> Standard, Ancient, Lunar or Arceuus. {spellbook icon} is its tab icon. A party
// member's comes from our shared PartyValue, unavailable until they've sent it.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SpellbookLabelVariable implements LabelVariable, PartyMemberVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{spellbook(\\s+icon)?\\}", Pattern.CASE_INSENSITIVE);
	// By Spellbooks.NAMES index.
	private static final int[] ICONS = {
		SpriteID.SideiconsInterface.MAGIC, SpriteID.SideiconsInterface.SPELLBOOK_ANCIENT_MAGICKS,
		SpriteID.SideiconsInterface.SPELLBOOK_LUNAR, SpriteID.SideiconsInterface.SPELLBOOK_ARCEUUS
	};

	private final Client client;
	private final RichText richText;
	private final PartyDataSharing partyDataSharing;

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		return client.getLocalPlayer() == null ? null : Spellbooks.name(client);
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		String name = resolvePlain(matcher);
		if (name == null)
		{
			return null;
		}
		return matcher.group(1) != null ? icon(name) : richText.labeled("Spellbook", name);
	}

	@Override
	public String resolvePlain(Matcher matcher, PartyMember member)
	{
		return partyDataSharing.value(member.getMemberId(), PartyValue.SPELLBOOK);
	}

	@Override
	public String resolveRich(Matcher matcher, PartyMember member)
	{
		String name = resolvePlain(matcher, member);
		if (name == null)
		{
			return null;
		}
		return matcher.group(1) != null ? icon(name) : richText.highlightValue(name);
	}

	@Override
	public String resolveRowRich(Matcher matcher, RichText richText)
	{
		return matcher.group(1) != null ? resolveRich(matcher) : PartyMemberVariable.super.resolveRowRich(matcher, richText);
	}

	private static String icon(String name)
	{
		int index = Spellbooks.NAMES.indexOf(name);
		return index < 0 ? null : Icons.sprite(ICONS[index]);
	}
}
