package com.groundmarkervariables.variables.label;

import java.util.List;
import java.util.Map;
import javax.inject.Inject;

import com.groundmarkervariables.party.PartyDataSharing;
import com.groundmarkervariables.variables.RichText;
import com.groundmarkervariables.variables.support.Spellbooks;
import net.runelite.api.Client;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;

// {hasSurge [wind] [water] [earth] [fire] [icon]} — the elemental surges, Standard spellbook (or Spellbook Swap).
// "air" also means wind.
public class HasSurgeLabelVariable extends SpellSetLabelVariable
{
	private static final List<Spell> SURGES = List.of(
		new Spell("wind", "Wind Surge", SpriteID.Magicon2.WIND_SURGE, Map.of(ItemID.WRATHRUNE, 1, ItemID.AIRRUNE, 7), "air"),
		new Spell("water", "Water Surge", SpriteID.Magicon2.WATER_SURGE, Map.of(ItemID.WRATHRUNE, 1, ItemID.AIRRUNE, 7, ItemID.WATERRUNE, 10)),
		new Spell("earth", "Earth Surge", SpriteID.Magicon2.EARTH_SURGE, Map.of(ItemID.WRATHRUNE, 1, ItemID.AIRRUNE, 7, ItemID.EARTHRUNE, 10)),
		new Spell("fire", "Fire Surge", SpriteID.Magicon2.FIRE_SURGE, Map.of(ItemID.WRATHRUNE, 1, ItemID.AIRRUNE, 7, ItemID.FIRERUNE, 10)));

	@Inject
	private HasSurgeLabelVariable(Client client, RichText richText, PartyDataSharing partyDataSharing)
	{
		super(client, richText, partyDataSharing, "hasSurge", "Surge", Spellbooks.STANDARD, SURGES);
	}
}
