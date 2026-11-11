# Ground Marker Variables

Ground Markers with variables in their labels. [Join our Discord](https://discord.gg/ehyvCPpdSn) to share your creations!

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
| `<item=ID>`, `<sprite=ID>`, `<npc=ID[,head]>`, `<flag=us>` | Icons; `@50%` dims items and sprites |
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
| `{showItems <a>, <b>}` / `{showOneItem …}`           | Boolean         | ✓     | *    | Icons of the listed items you hold (all / first)                                                                                                        |
| `{runePouch}`                                        | Boolean         | ✓     | *    | Icons of every rune you hold                                                                                                                            |
| `{spellbook}`                                        | Text            | ✓     | ✓    | Standard, Ancient, Lunar or Arceuus                                                                                                                     |
| `{has<Spell>}` / `{has<Spell> icon}`                 | Boolean         | ✓     | ✓    | Spell castable (runes and spellbook).<br />Supports Freeze, Thralls, Alchs, Entangle, VileVigour, Vengeance, UndeadGrasp, MarkOfDarkness, SpellbookSwap |
| `{hasSurge [elements]}` / `{hasBarrage [elements]}`  | Boolean         | ✓     | ✓    | Any, or the named, Surge / Barrage castable                                                                                                             |
| `{active <effect>}`                                  | Boolean         | ✓     | ✓    | vengeance, thrall, deathCharge, shadowVeil or markOfDarkness active                                                                                     |
| `{quickPrayers}` / `{quickPrayers <name>}`           | Icons / Boolean |       | *    | Quick prayer icons / that prayer is set                                                                                                                 |
| `{divine <potion>}`                                  | Number\*        |       |      | Ticks left on a divine potion                                                                                                                           |
| `{kc <boss>}`                                        | Number          |       |      | Kill count, uses Chat Commands plugin                                                                                                                   |
| `{pb <boss>}`                                        | Duration        |       |      | Personal best, uses Chat Commands plugin                                                                                                                |
| `{rank <boss>}`                                      | Number\*        |       |      | Hiscores rank                                                                                                                                           |
| `{loot <monster>}`                                   | Number          |       | *    | Loot Tracker gp; `items=N`, `lines=N`, `itemsOnly`, uses Loot Tracker plugin                                                                            |
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
<summary>Advanced Variable Usage:</summary>

### Plain vs Rich Text
Most variables have a short **Plain Text** value (`385`) and a descriptive **Rich Text** value (`Corrupted Gauntlet kc: 385`). Labels use Rich Text by default; force one with `{^...}` (Plain) or `{&...}` (Rich). Conditions and math always use Plain Text.

### Comparisons
`{<variable> <cmp> <value>}` shows the variable colored green if the comparison is true, red if false.
- Comparators: `==`, `!=`, `<`, `>`, `<=`, `>=`
- `==` / `!=` compare text, ignoring case: `{spellbook == Ancient}`
- Against a list in square brackets, `==` is true if it equals any item and `!=` if none: `{spellbook == [Ancient, Arceuus]}`
- `<` / `>` compare numbers (`10.5k` works), or clock times: `{time < 9:00pm}`
- The value can be another variable: `{boost_strength > lvl_strength}`

### Or / And
`{<a> || <b> || ...}` shows the first part that's true, and `{<a> && <b> && ...}` the first part that's false. If none is, the last part is shown.
- `{hasItem obligator || hasItem scythe}` shows Obligator if you have one, otherwise the scythe, green or red.
- The last part can be plain text, even empty: `{hasItem obligator || }` shows nothing without an Obligator.
- Parts can be comparisons: `{kc tob > 100 && hasItem scythe && Ready}`
- `||` and `&&` can't be mixed in one token.

### Conditionals
`{<condition> ? <if true> : <if false>}` shows one branch or the other.
- `{kc toa > 50 ? Toa veteran : Toa learner}`
- Combine with `&&` / `||` and group with parentheses: `{(weekday <= 4 || weekday == 7) && time > 10pm ? Go to bed : One more raid!}`
- A bare true/false variable works as a condition: `{hasThralls ? Thralls ready : No thralls}`
- The `: <if false>` part is optional: `{hasItem rune pouch ? Pouch}` shows nothing if false.
- Branches can contain other variables: `{kc toa > 5 ? {kc toa} kills : }`
- **Nested conditionals** go in their own braces, to any depth:
  - `{kc toa > 50 ? Toa master : {kc toa > 25 ? 25 to 50 kills : under 25}}`
  - `{spellbook == Ancient ? {hasFreeze ? Freeze ready : No freeze} : {spellbook == Arceuus ? {hasThralls}}}`
- A `:` between digits (`9:00`) is part of a time, not a branch separator.
- `{metronome}` can't be used inside a conditional.
- `{tileCol}`, `{highlight}` and `{borderWidth}` cannot be used in a conditional, except conditionals that only use `{group}`
- `{ground}` cannot be used in a conditional or around one (including `||` / `&&`), except conditionals that only use `{group}`

### Math
`{<a> <op> <b> ...}` with `+`, `-`, `*`, `/`. Operators need a space on each side, and parentheses group: `{(kc tob + kc hmt) / 2}`.
- Works with numbers (`1.5k`) and numeric variables: `kc`, `loot`, `lvl_`, `boost_`, `xpRate_`, `runEnergy`, `spec`, `questPoints`, `slayerPoints`, `slayerStreak`, `miscellania`, `weekday`, `combatAchievement`, `numItem`, `numDoses`, `party`, `invSpace`, `xp_`
- Usable in conditions: `{kc tob + kc hmt > 100 ? 100+ ToBs : }`
- Dividing by zero leaves the token as written.

### Date and time math
- **Days until a date**: `{oct 20 - date}` → `8 days`. Dates can be `Oct 20`, `October 20 2026`, `10/20` or `10-20-2026` (day/month order follows your system locale). With no year, the current year is used.
- **Time until / since a time of day**: `{6am - time}` → `1:23`, wrapping past midnight (at 7am it's `23:00`). `{time - 2am}` is time since 2am. Times can be `6am`, `8 pm`, `6:30 a.m.`, `7:00` or `15:00`, and two times work too: `{6am - 2am}` → `4:00`. Write `7:00` or `7am`, not `7`.
- **Countdown to a date and time**: `{nov 4 7am - now}`. Rich Text shows `9 days 21:06`, Plain Text shows total hours `237:06`, and it goes negative once the time has passed. `now` also works in place of `date` or `time` above.

### Other parameters
- `{attackStyle weapon=Twisted bow}` / `{attackType weapon=...}`: the style or type you last used with that weapon this session.
- `{loot <monster>, items=6, lines=2}`: how many items to list, and over how many lines.
- `{loot <monster>, itemsOnly}`: only the items and how many of each, without the name, kill count or gp value.

### Party
Shows a row based on `{party <template>}` per party member.

Also usable in math and conditionals as the number of players in the party `{party > 0 ? in party : not in party}`
- All variables that work for other members:
  - `readyCheck`, `rsn`, `partyNum`, `spec`, `runEnergy`, `invSpace`, `lvl_`, `boost_`, `active vengeance`
  - `equip_`, `weapon`, `hasItem`, `numItem`, `numDoses`, `showItems`, `showOneItem`
  - `spellbook`, `hasFreeze`, `hasAlchs`, `hasEntangle`, `hasVileVigour`, `hasVengeance`, `hasUndeadGrasp`, `hasMarkOfDarkness`, `hasSurge`, `hasBarrage`, `hasSpellbookSwap`, `hasThralls`
- Special, `{party}` only variables include:
  - `{readyCheck}` -- Reads in-game chat for ready messages or shows `:telescope:` for not nearby, `R` / `ready`, `P` / `pot` / `potting`, `not r` / `no r` / `un r`, `brb` / `5 mins`
  - `{partyNum}` -- The member's number in order. Can be used in conditionals to hide long lists with `{hoverOnly}`.
- `{partyCount <template>}` -- Sums each \<expr> in \<template> across all members: `{partyCount {hasFreeze} freezers, {numItem elder maul} mauls}`
  - Output is also usable in math and conditionals: `{{partyCount {readyCheck != ready}} > 0 ? Waiting : All ready}`
- `spellbook` is only known for members who also have Ground Marker Variables installed.
  - For others, `{spellbook}` shows `...`
  - `has<Spell>` variables only check their runes, shows slightly dimmed icon.

### DPS
WARNING: DPS has been deprecated, and may be subject to removal in the future.

Tracks your damage while you're near the tile. Each `{dps <template>}` tile keeps its own numbers, and they reset once you walk away from the tile. Only damage to NPCs counts.
- Keywords, replaced in the template like `{party}`:

| Keyword                                | Value                                                                                     | Per-Weapon / NPC |
|----------------------------------------|-------------------------------------------------------------------------------------------|------------------|
| `avgDps`                               | Damage per second of timeSpent, includes time spent not attacking.                        | Yes              |
| `theoryDps`                            | DPS using your weapon's attack speed, ignoring lost ticks                                 | Yes              |
| `pureTheoryDps`                        | DPS as `theoryDps`, but also ignoring tick-reducing boss mechanics                        | Yes              |
| `shareDps`                             | % of everyone's damage on the enemies you attacked, including other players / actors      | Yes              |
| `splitDps`                             | % of your own personal total damage.                                                      | Yes              |
| `totalDamage`                          | The total amount of damage you dealt                                                      | Yes              |
| `takenDamage`                          | The total amount of damage you took                                                       | No               |
| `acc`                                  | Weapon accuracy                                                                           | Yes              |
| `<N>s`                                 | Damage per second over the last N seconds, e.g. `10s`                                     | Yes              |
| `thrallDps` / `recoilDps`              | Damage per second from your thrall / ring of recoil                                       | No               |
| `numAttacks` / `numHits` / `numMisses` | Number of attacks you did / number of hitsplats above zero / number of zeros and splashes | Yes              |
| `timeSpent`                            | Time since your first attack, as m:ss. In reports: per weapon, NPC or boss stage (below)  | Yes              |
| `ticksSpent`                           | timeSpent in ticks                                                                        | Yes              |
| `ticksUsed`                            | Sum of all attack speeds of all weapons, denomonator of theoryDps                         | Yes              |
| `ticksLost`                            | ticksSpent - ticksUsed                                                                    | Yes              |

- `{dps}` is equal to `{dps avgDps (acc)}`. Write any text around the keywords: `{dps thrall: thrallDps | recent: 10s}`
- **Per-weapon report**: `{dps <top line> report <per weapon>}` adds a line per weapon you've used, sorted by split, then thrall, recoil and vengeance lines.
  - `{dps report}` is equal to `{dps theoryDps (splitDps | acc) report theoryDps (splitDps | acc)}`. The top row is labelled `Overall`.
  - Use keywords between `{dps` and `report` to refer to top row. Use keywords after `report` to refer to per-weapon values.
  - With no top line, the top row uses the per-row keywords: `{dps report theoryDps}` is equal to `{dps theoryDps report theoryDps}`. The same goes for `groupReport` and `bossReport`.
- **Legend**: reports start with a line naming the row values. Add `noLegend` after the report word to hide it.
- **`timeSpent` in reports**:
  - `report`: time the weapon was wielded since your first attack.
  - `groupReport` / `bossReport`: from the first attack on a boss stage or NPC until you attack a different one, or it dies. Returning to it adds to its time.
- **Group report**: `{dps <top line> groupReport <per NPC>}` adds a line per boss stage or NPC name you've damaged, bosses first, then by damage, e.g. `Nylocas Hagios x6: 233 (91% | 2.02)`.
  - `{dps groupReport}` is equal to `{dps totalDamage (shareDps | theoryDps) groupReport totalDamage (shareDps | theoryDps)}`, and `{dps bossReport}` to `{dps timeSpent (theoryDps | acc) bossReport timeSpent (theoryDps | acc)}`.
- **Full report**: `{dps fullReport <per row>}` is `bossReport` with an indented line per weapon under each target, e.g. your Twisted bow's damage on Xarth.
- **Pausing**: dying, or 30 seconds with no damage, pauses the tracking and shows `(paused)`. Your next weapon hit starts again from zero.
- **Shift right-click** the tile → **Variables** → **Reset DPS** to start over, **Export to Chat DPS** to send the current numbers to your chatbox, **Export Report DPS** to copy every row with all keywords' values, or **Export Raw Data DPS** to copy every hit (tick, source, target, weapon, damage), both ready to paste into Google Sheets.
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
