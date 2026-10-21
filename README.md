# Ground Marker Variables

Overrides the default Ground Marker plugin with a version that supports variables in labels.

<img align="left" height="580" alt="image" src="https://github.com/user-attachments/assets/eb26eef6-03ae-4957-aa23-21602eab8ca7" /> ToB Entry Check

```[{"regionId":14642,"regionX":13,"regionY":19,"z":0,"color":"#00FFFFFF","label":"{align\u003dleft}{spellbook} {spellbook \u003d\u003d Ancient ? -- {hasFreeze} : }{spellbook \u003d\u003d Arceuus ? -- {hasThralls} : } \\n {hasItem Salve} \\n {equip_ammo !\u003d Empty} \\n {hasItem Rune Pouch} \\n {autoRetaliate}"}]```

<br clear="left"/><br />

<details>
<summary>Supported variables:</summary>

| Modifiers                       | Description                                                                                                     |
|---------------------------------|-----------------------------------------------------------------------------------------------------------------|
| `{^...}`                        | Plain Text version of one of the below, e.g. `{^kc cg}` may evaluate to `385`.                                  |
| `{&...}`                        | Rich Text version of one of the below, e.g. `{&kc cg}` may evaluate to `Corrupted Gauntlet kc: 385`             |
| `\n`                            | Newline.                                                                                                        |
| `{col=<col>}`                   | Changes the text color. \<col>: named color or 6-digit hex.                                                     |
| `{tileCol=<col>}`               | Overrides the tile's color. \<col>: named color, hex, 8-digit hex with leading alpha, "N%", or a named opacity. |
| `{highlightSW=<col>}`           | Draws a bracket at \<corner> (SW/NW/NE/SE, or Corners/All). \<col>: named color.                                |
| `{align=left/right/center}`     | Changes text alignment from this line onward, until the next \{align}/\{/align} (which resets to center).       |
| `{b}Bold{/b}`, `{i}Italics{/i}` | Bold / italic text from this point onward (across lines), until the matching \{/b} or \{/i}.                    |
| `{borderWidth=2.5}`             | Overrides the tile's outline width (and \{highlight} brackets), up to 10.                                       |

| Variable                                | Description                                                                                                                                                      |
|-----------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `{rsn}`                                 | Current player's display name                                                                                                                                    |
| `{date}`                                | Today's date, e.g. `October 20th 2026`                                                                                                                           |
| `{time}` / `{time24}`                   | Current local time, 12-hour h:mm am/pm or 24-hour HH:mm                                                                                                          |
| `{weekday}`                             | Day of the week, 1 (Monday) through 7 (Sunday)                                                                                                                   |
| `{world}` / `{worldInfo}`               | Current world number. Rich Text shows the world type; `{worldInfo}` adds the region flag, player count and ping (rounded up, avg over 10 ticks)                  |
| `{metronome<N>}` / `{metronome<N>_<M>}` | Counts down N → 1 and repeats, advancing every M ticks (default 1). `{m+4}`/`{m-4}` force up/down. Not usable inside a conditional                               |
| `{6HourTimeRemaining < 1.5}`            | Warning when you are within 1.5 hours of being 6-hour-logged. Shows highlighted color normally, red under compared time.                                         |
| `{kc <boss>}`                           | Tracked kill count for \<boss>, e.g. \{kc Zulrah}                                                                                                                |
| `{pb <boss>}`                           | Personal best time for \<boss>                                                                                                                                   |
| `{rank <boss>}`                         | Hiscores rank for \<boss>                                                                                                                                        |
| `{loot <monster>}`                      | Gp value of loot tracked for \<monster> by the core Loot Tracker plugin, e.g. \{loot toa, items=6, lines=2}                                                      |
| `{spellbook}`                           | Active spellbook: Standard, Ancient, Lunar, or Arceuus                                                                                                           |
| `{weapon}`                              | Equipped weapon's item name, or Unarmed                                                                                                                          |
| `{equip_<slot>}`                        | Equipped item name in \<slot> (helm, cape, amulet, body, shield, legs, gloves, boots, ring, ammo, quiver), or Empty                                              |
| `{attackStyle}`                         | Current combat style name, e.g. Accurate, Rapid, Casting. Optional `weapon=<name>` for that weapon's last seen style, e.g. `{attackStyle weapon=Scythe}`         |
| `{attackType}`                          | Current combat style's damage type, e.g. Stab, Slash, Crush, Ranged, Magic. Optional `weapon=<name>`                                                             |
| `{autocast}`                            | Last autocast spell seen on a weapon that can autocast, or None                                                                                                  |
| `{party <template>}`                    | Optional template as parameter (default "num. name \| hp \| prayer")                                                                                             |
| `{lvl_<skill>}`                         | Unboosted level in \<skill>, e.g. \{lvl_mining}                                                                                                                  |
| `{xp_<skill>}`                          | Exact experience in \<skill>. Rich Text also shows level and hiscore rank                                                                                        |
| `{boost_<skill>}`                       | Current boosted level in \<skill>                                                                                                                                |
| `{xpRate_<skill>}`                      | Current xp/hour in \<skill> from XP Tracker, or N/A if that plugin isn't running                                                                                 |
| `{miscellania}`                         | Kingdom of Miscellania approval rating, 0-127                                                                                                                    |
| `{slayerTask}`                          | Current slayer task. Requires Slayer plugin to be running.                                                                                                       |
| `{slayerStreak}`                        | Current slayer task streak (tasks completed in a row)                                                                                                            |
| `{slayerPoints}`                        | Current slayer points                                                                                                                                            |
| `{runEnergy}`                           | Current run energy, 0-100                                                                                                                                        |
| `{spec}`                                | Current special attack energy, 0-100                                                                                                                             |
| `{combatAchievement}`                   | Current Combat Achievement tier, 0 (none) through 6 (Grandmaster)                                                                                                |
| `{questPoints}`                         | Current quest points                                                                                                                                             |
| `{hasThralls}`                          | true if on Arceuus Spellbook, has book of the dead, and runes for thralls                                                                                        |
| `{hasAlchs}`                            | true if on Standard Spellbook and has nature and fire runes for High Alchemy                                                                                     |
| `{hasFreeze}`                           | true if Ice Barrage is castable (Level not checked)                                                                                                              |
| `{hasEntangle}`                         | true if Entangle is castable (Level not checked)                                                                                                                 |
| `{hasVileVigour}`                       | true if Vile Vigour is castable (Level not checked)                                                                                                              |
| `{autoRetaliate}` / `{autoRetal}`       | true if Auto Retaliate is on                                                                                                                                     |
| `{divine <potion>}`                     | Ticks remaining on \<potion>'s buff. Green > 25 ticks remaining, Yellow <= 25 ticks, Red = 0 ticks                                                               |
| `{hasItem <name>}`                      | true if any item name in your inventory or equipment contains \<name>, e.g. \{hasItem rune pouch}                                                                |
| `{numItem <name>}`                      | Total quantity of every item whose name contains \<name>, e.g. \{numItem dragon arrow}                                                                           |
| `{numDoses <name>}`                     | Total doses (quantity × the "(N)" in the item's name) of every matching item, e.g. \{numDoses brew}                                                              |
| `{<a> +-*/ <b> ...}`                    | Math — arithmetic over numeric variables and/or literal numbers or dates, e.g. \{kc tob + kc hmt}. Usable inside a conditional's \<expr>.                        |
| `{<when> - now}`                        | Countdown as d days h:mm, e.g. `{nov 4 7am - now}`, `{now - 10/20/2026 6am}`, `{dec 25 - now}`, `{6pm - now}`                                                    |
| `{<cond>}`                              | Boolean Variable — evaluates one of the above (==/!=/</>/<=/>=) and displays the Plain Text version of the variable, colored Green or Red.                       |
| `{<cond1> && / \|\| <cond2> ? A : B}`   | Conditional — evaluates one or two of the above (==/!=/</>/<=/>=, or a bare boolean) and displays A or B. Conditions always evaluate using Plain Text variables. |

</details>
<details>
<summary>Examples:</summary>

- `{equip_helm}`
  - Helm: Helm of Neitiznot
- `{lvl_agility < 87 ? Bring Summer Pie! : }`
  - Reminder to bring summer pie to boost for Hallowed Sepulcher!
  - Empty if you're already level 87.
- `{hasFreeze && weapon == staff of the dead ? Gigachad : Noob}`
  - Remind yourself what it takes to be a gigachad
- `{metronome4}`
  - 4 tick metronome on the tile
  - Cannot be used inside a \<cond\>.
  - `{m4}` works as an alias as well.
- `{(weekday <= 4 || weekday == 7) && time > 10pm ? Go to bed : One more raid!}`
- `{Oct 20 - date} to Raids 4 release!`
- `This text is {col=cyan}cyan!`
  - Changes color of the word "cyan!" to `#00FFFF`
</details>
<details>
<summary>Managing Ground Markers:</summary>

Because Ground Marker Variables ARE RuneLite Core Ground Markers that happen to have variables in their labels, uninstalling Ground Marker Variables 
and using core's Ground Markers plugin again will continue to render tiles with variables in their label.

To make it easy to remove markers with variables, type `::manageGroundMarkerVariables` in chat to open the Manager Window.

From here, you can select ground markers by region, or filter to only markers with variables using `{ }` and then select them all with `All`, to export and/or delete.

<img width="857" height="234" alt="java_ZgXhq7fRF6" src="https://github.com/user-attachments/assets/3b62431e-3d08-4ccb-ada5-eed6a8a5faee" />

</details>

<details>
<summary>Images:</summary>
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
