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

// {hasBarrage [smoke] [shadow] [blood] [ice] [icon]} — the barrages, Ancient spellbook (or Spellbook Swap). Unlike
// {hasFreeze}, a blighted sack doesn't count.
public class HasBarrageLabelVariable extends SpellSetLabelVariable
{
	private static final List<Spell> BARRAGES = List.of(
		new Spell("smoke", "Smoke Barrage", SpriteID.Magicon2.SMOKE_BARRAGE,
			Map.of(ItemID.DEATHRUNE, 4, ItemID.BLOODRUNE, 2, ItemID.FIRERUNE, 4, ItemID.AIRRUNE, 4)),
		new Spell("shadow", "Shadow Barrage", SpriteID.Magicon2.SHADOW_BARRAGE,
			Map.of(ItemID.DEATHRUNE, 4, ItemID.BLOODRUNE, 2, ItemID.AIRRUNE, 4, ItemID.SOULRUNE, 1)),
		new Spell("blood", "Blood Barrage", SpriteID.Magicon2.BLOOD_BARRAGE,
			Map.of(ItemID.DEATHRUNE, 4, ItemID.BLOODRUNE, 4, ItemID.SOULRUNE, 1)),
		new Spell("ice", "Ice Barrage", SpriteID.Magicon2.ICE_BARRAGE,
			Map.of(ItemID.DEATHRUNE, 4, ItemID.BLOODRUNE, 2, ItemID.WATERRUNE, 6)));

	@Inject
	private HasBarrageLabelVariable(Client client, RichText richText, PartyDataSharing partyDataSharing)
	{
		super(client, richText, partyDataSharing, "hasBarrage", "Barrage", Spellbooks.ANCIENT, BARRAGES);
	}
}
