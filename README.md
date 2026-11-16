# Ground Marker Variables

Ground Markers with variables in their labels. [Join our Discord](https://discord.gg/ehyvCPpdSn) to share your creations!

Advanced usage details can be found in API format [here](https://limy-monkey.github.io/Ground-Marker-Variables/).

<img align="left" height="580" alt="image" src="https://github.com/user-attachments/assets/eb26eef6-03ae-4957-aa23-21602eab8ca7" /> ToB Entry Check

```[{"regionId":14642,"regionX":13,"regionY":19,"z":0,"color":"#00FFFFFF","label":"{align=left}{spellbook} {spellbook == Ancient ? -- {hasFreeze} : }{spellbook == Arceuus ? -- {hasThralls} : } \\n {hasItem Salve} \\n {equip_ammo != Empty} \\n {hasItem Rune Pouch} \\n {autoRetaliate}"}]```

<br clear="left"/><br />

<details>
<summary>Variables</summary>

**Formatting**

| Token | Description |
|---|---|
| `\n`, `\t`, `\s` | New line, table column, non-breaking space |
| `{col=<col>}`, `{b}…{/b}`, `{i}…{/i}` | Color, bold, italic |
| `{align=left\|right\|center}` | Alignment from this line |
| `:emoji:` / `:emoji@<col>:` | Emoji, optionally tinted |
| `<item=name>`, `<sprite=name>`, `<npc=name[,head]>`, `<flag=us>` | Icons; `@50%` dims items and sprites |
| `<badge>` / `<badge=N>` | Dot or pie on the previous icon |
| `{split}…{/split}` | 2–4 icons as one |
| `{hover}`, `{hoverOnly}…`, `{hoverHide}…`, `{tip=<text>}…{/tip}` | Hover background, hover-only / hidden text, tooltip |
| `{setGroup <name>, <a>, <b>}` | Pick an option from the tile's Shift right-click menu |

**Tile settings**: not allowed in conditionals, except conditionals that only use `{group}`.

| Token | Description |
|---|---|
| `{tileCol=<col>}` | Tile color: named color, hex, 8-digit hex with leading alpha, `N%`, or a named opacity |
| `{highlightSW=<col>}` | Corner bracket: SW, NW, NE, SE, or Corners / All |
| `{borderWidth=2.5}` | Outline width, up to 10 |
| `{ground}…{/ground}` | Icons inside drawn flat on the tile (not around a conditional either) |

**Returns** is the Plain Text value used by conditions and math: Number works in math, Number\* only in comparisons. **Party** ✓ works for other members in `{party}`. **Icon** ✓ takes `icon` for an icon instead of text, e.g. `{weapon icon}`.

| Variable                                             | Returns         | Party | Icon | Description                                                                                                                                             |
|------------------------------------------------------|-----------------|-------|------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| `{rsn}`                                              | Text            | ✓     |      | Display name                                                                                                                                            |
| `{world}` / `{worldInfo}`                            | Number\*        |       |      | World; `worldInfo` adds flag, players and ping (`hideWorld` hides the number)                                                                           |
| `{runEnergy}` / `{spec}`                             | Number          | ✓     |      | 0–100                                                                                                                                                   |
| `{invSpace}`                                         | Number          | ✓     |      | Empty inventory slots                                                                                                                                   |
| `{questPoints}`                                      | Number          |       |      | Quest points                                                                                                                                            |
| `{combatAchievement}`                                | Number          |       |      | CA tier, 0–6                                                                                                                                            |
| `{autoRetaliate}`                                    | Boolean         |       |      | Auto Retaliate on                                                                                                                                       |
| `{lvl_<skill>}` / `{boost_<skill>}`                  | Number          | ✓     |      | Real / boosted level                                                                                                                                    |
| `{xp_<skill>}` / `{xp_<level>}`                      | Number          |       |      | XP in a skill / needed for a level                                                                                                                      |
| `{xpRate_<skill>}`                                   | Number          |       |      | XP/hour, uses XP Tracker plugin                                                                                                                         |
| `{weapon}` / `{equip_<slot>}`                        | Text            | ✓     | ✓    | Equipped item name, `Unarmed` / `Empty`                                                                                                                 |
| `{attackStyle}` / `{attackType}`                     | Text            |       |      | Style / damage type; `weapon=<name>` for that weapon's last                                                                                             |
| `{autocast}`                                         | Text            |       | ✓    | Last autocast spell                                                                                                                                     |
| `{hasItem <name>}`                                   | Boolean         | ✓     |      | Holding an item with `<name>` in its name                                                                                                               |
| `{numItem <name>}` / `{numDoses <name>}`             | Number          | ✓     |      | Total quantity / doses of matching items                                                                                                                |
| `{charges <item>}`                                   | Number          |       | ✓    | Item charges; `icon`, `icons`, `<stored item>`, uses Item Charges Improved or Item Charges plugin                                                       |
| `{showItems <a>, <b>}` / `{showOneItem …}`           | Boolean         | ✓     | *    | Icons of the listed items you hold (all / first)                                                                                                        |
| `{runePouch}`                                        | Boolean         | ✓     | *    | Icons of every rune you hold                                                                                                                            |
| `{spellbook}`                                        | Text            | ✓     | ✓    | Standard, Ancient, Lunar or Arceuus                                                                                                                     |
| `{has<Spell>}` / `{has<Spell> icon}`                 | Boolean         | ✓     | ✓    | Spell castable (runes and spellbook).<br />Supports Freeze, Thralls, Alchs, Entangle, VileVigour, Vengeance, UndeadGrasp, MarkOfDarkness, DeathCharge, SpellbookSwap |
| `{hasSurge [elements]}` / `{hasBarrage [elements]}`  | Boolean         | ✓     | ✓    | Any, or the named, Surge / Barrage castable                                                                                                             |
| `{active <effect>}`                                  | Boolean         | ✓     | ✓    | vengeance, thrall, deathCharge, shadowVeil or markOfDarkness active                                                                                     |
| `{quickPrayers}` / `{quickPrayers <name>}`           | Icons / Boolean |       | *    | Quick prayer icons / that prayer is set                                                                                                                 |
| `{divine <potion>}`                                  | Number\*        |       |      | Ticks left on a divine potion                                                                                                                           |
| `{kc <boss>}`                                        | Number          | ✓     |      | Kill count, uses Chat Commands plugin or Hiscores for party members                                                                                     |
| `{pb <boss>}`                                        | Duration        |       |      | Personal best, uses Chat Commands plugin                                                                                                                |
| `{rank <boss>}`                                      | Number\*        |       |      | Hiscores rank                                                                                                                                           |
| `{loot <monster>}`                                   | Number          |       | *    | Loot Tracker gp; `items=N`, `lines=N`, `itemsOnly`, uses Loot Tracker plugin                                                                            |
| `{log <boss>}`                                       | Number          |       | *    | Collection log items obtained, uses RuneProfile plugin                                                                                                  |
| `{dps <template>}`                                   | Text            |       |      | Damage tracker, see below                                                                                                                               |
| `{date}` / `{time}` / `{time24}`                     | Text / Time     |       |      | Date / clock time                                                                                                                                       |
| `{weekday}`                                          | Number          |       |      | 1 (Mon) – 7 (Sun)                                                                                                                                       |
| `{6HourTimeRemaining}`                               | Duration        |       |      | Time until 6-hour log, e.g. `{6HourTimeRemaining < 1.5}`                                                                                                |
| `{slayerTask}` / `{slayerStreak}` / `{slayerPoints}` | Text / Number   |       |      | Slayer (task needs the Slayer plugin)                                                                                                                   |
| `{miscellania}`                                      | Number          |       |      | Miscellania approval, 0–127                                                                                                                             |
| `{metronome<N>}` / `{m<N>}`                          | Number\*        |       |      | Counts to N each tick; `_<M>` every M ticks                                                                                                             |
| `{party <template>}`                                 | Text            |       |      | A row per party member; a number in math                                                                                                                |
| `{partyCount <template>}`                            | Number          |       |      | Sums the template across the party                                                                                                                      |
| `{partyNum}` / `{readyCheck}`                        | Number / Text   | ✓     |      | Row number / ready check status                                                                                                                         |
| `{group <name>}`                                     | Text            |       |      | Option picked with `{setGroup}`                                                                                                                         |

</details>

<details>
<summary>Managing markers</summary>

- **Smart Save** (on by default) keeps `{variable}` labels separately, so core's Ground Markers shows those tiles without a label.
- `::manageGroundMarkerVariables` opens the Manager to copy, delete and re-import markers.
- Other plugins can resolve labels and draw tiles through us: see [PluginMessageHandler.java](https://github.com/Limy-Monkey/Ground-Marker-Variables/blob/master/src/main/java/com/groundmarkervariables/PluginMessageHandler.java).

</details>

<details>
<summary>Gallery</summary>

<img align="left" height="521" alt="image" src="https://github.com/user-attachments/assets/8ee3ee10-3ec6-473c-8b04-520e7a70dd53" /> Tombs of Amascut Loot

```[{"regionId":13454,"regionX":31,"regionY":27,"z":0,"color":"#00FFFFFF","label":"{loot Tombs of Amascut}"}]```

<br clear="left"/><br />

<img align="left" height="575" alt="image" src="https://github.com/user-attachments/assets/5ccf632f-802b-4ee5-b4d0-3574d3077637" /> Miscellania

```[{"regionId":10044,"regionX":32,"regionY":11,"z":0,"color":"#00FFFFFF","label":"{miscellania}"}]```

<br clear="left"/><br />

<img align="left" height="711" alt="image" src="https://github.com/user-attachments/assets/6c7d8942-e7c6-414c-8fe2-a2a2e6122d2b" /> Skilling

```[{"regionId":11050,"regionX":20,"regionY":10,"z":0,"color":"#00FFFFFF","label":"{xpRate_woodcutting}"}]```

<br clear="left"/><br />

</details>
