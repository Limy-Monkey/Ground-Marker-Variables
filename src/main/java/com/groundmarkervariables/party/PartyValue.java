package com.groundmarkervariables.party;

// A plain-text value our plugin shares with the party, on top of core's PartyDataService data.
// Sent by name, so a client ignores names it doesn't know.
public enum PartyValue {
	SPELLBOOK,
	// The map clock tick our {metronome}s count from, and our Count Down setting, for Party Sync followers.
	METRONOME_RESET,
	METRONOME_COUNT_DOWN
}
