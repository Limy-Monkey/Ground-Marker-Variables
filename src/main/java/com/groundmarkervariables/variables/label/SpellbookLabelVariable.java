package com.groundmarkervariables.variables.label;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import com.groundmarkervariables.variables.LabelVariable;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.Icons;
import net.runelite.api.Client;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarbitID;

public class SpellbookLabelVariable implements LabelVariable
{
	private static final Pattern PATTERN = Pattern.compile("\\{spellbook(\\s+icon)?\\}", Pattern.CASE_INSENSITIVE);
	// VarbitID.SPELLBOOK values: 0 = Standard, 1 = Ancient Magicks, 2 = Lunar, 3 = Arceuus.
	private static final String[] SPELLBOOKS = {"Standard", "Ancient", "Lunar", "Arceuus"};
	private static final int[] ICONS = {
		SpriteID.SideiconsInterface.MAGIC, SpriteID.SideiconsInterface.SPELLBOOK_ANCIENT_MAGICKS,
		SpriteID.SideiconsInterface.SPELLBOOK_LUNAR, SpriteID.SideiconsInterface.SPELLBOOK_ARCEUUS
	};

	private final Client client;
	private final RichText richText;

	@Inject
	private SpellbookLabelVariable(Client client, RichText richText)
	{
		this.client = client;
		this.richText = richText;
	}

	@Override
	public Pattern pattern()
	{
		return PATTERN;
	}

	@Override
	public String resolvePlain(Matcher matcher)
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		int spellbook = client.getVarbitValue(VarbitID.SPELLBOOK);
		return spellbook >= 0 && spellbook < SPELLBOOKS.length ? SPELLBOOKS[spellbook] : null;
	}

	@Override
	public String resolveRich(Matcher matcher)
	{
		if (matcher.group(1) != null)
		{
			int spellbook = client.getLocalPlayer() == null ? -1 : client.getVarbitValue(VarbitID.SPELLBOOK);
			return spellbook >= 0 && spellbook < ICONS.length ? Icons.sprite(ICONS[spellbook]) : null;
		}

		String name = resolvePlain(matcher);
		return name == null ? null : richText.labeled("Spellbook", name);
	}
}
