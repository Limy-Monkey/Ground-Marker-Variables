#!/usr/bin/env python3
# Prints data/bosses.json for {dps bossReport}: boss NPC id -> {"name": ...}, e.g.
# "12077": {"name": "Phantom Muspah (Ranged)"}. The API's names are lowercase, so they're title-cased here.
# Leagues "(Echo)" bosses are left out.
#   python3 scripts/build-bosses.py > data/bosses.json
import json
import re
import sys
import urllib.request

SOURCE = "https://api.gearscape.net/api/monster"
SMALL_WORDS = {"of", "the", "and", "a"}

# Our own changes on top of the API, by NPC id. None drops an id.
OVERRIDES = {
    9021: {"name": "Crystalline Hunllef (Melee)"},
    9022: {"name": "Crystalline Hunllef (Ranged)"},
    9023: {"name": "Crystalline Hunllef (Magic)"},
    9035: {"name": "Corrupted Hunllef (Melee)"},
    9036: {"name": "Corrupted Hunllef (Ranged)"},
    9037: {"name": "Corrupted Hunllef (Magic)"},
}


def title(name):
    words = name.split(" ")
    return " ".join(word if i > 0 and word in SMALL_WORDS else re.sub(r"^(\(?)([a-z])", lambda m: m.group(1) + m.group(2).upper(), word)
        for i, word in enumerate(words))


def main():
    with urllib.request.urlopen(SOURCE) as response:
        monsters = json.load(response)["monsters"]

    bosses = {monster["id"]: {"name": title(monster["name"])} for monster in monsters
        if monster["boss"] and "(echo)" not in monster["name"]}
    bosses.update(OVERRIDES)
    bosses = {npc_id: boss for npc_id, boss in bosses.items() if boss is not None}
    sys.stdout.write("{\n" + ",\n".join(json.dumps(str(npc_id)) + ":" + json.dumps(bosses[npc_id]) for npc_id in sorted(bosses)) + "\n}\n")


if __name__ == "__main__":
    main()
