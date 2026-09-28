# Ground Marker Variables

Overrides the default Ground Marker plugin with a version that supports variables in labels.

Supported variables:

| Modifiers                                  | Description                                                                                                                                |
|--------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| `{^...}`                                   | Plain Text version of one of the below, e.g. `{^kc cg}` may evaluate to `385`.                                                             |
| `{&...}`                                   | Rich Text version of one of the below, e.g. `{&kc cg}` may evaluate to `Corrupted Gauntlet kc: 385`                                        |
| `{*...}`                                   | Opposite of the default behavior, e.g. if Rich Text Default config option is on, then plaintext, otherwise richtext.                       |

| Variable                                | Description                                                                                                                                                      |
|-----------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `{rsn}`                                 | Current player's display name                                                                                                                                    |
| `{time}` / `{time24}`                   | Current local time, 12-hour h:mm am/pm or 24-hour HH:mm                                                                                                          |
| `{spellbook}`                           | Active spellbook: Standard, Ancient, Lunar, or Arceuus                                                                                                           |
| `{metronome<N>}` / `{metronome<N>_<M>}` | Counts down N → 1 and repeats, advancing every M ticks (default 1). Optional " - X" offsets it. Not usable inside a conditional                                  |
| `{weapon}`                              | Equipped weapon's item name, or Unarmed                                                                                                                          |
| `{equip_<slot>}`                        | Equipped item name in \<slot> (helm, cape, amulet, body, shield, legs, gloves, boots, ring, ammo, quiver), or Empty                                              |
| `{attackStyle}`                         | Current combat style name, e.g. Accurate, Aggressive, Casting                                                                                                    |
| `{lvl_<skill>}`                         | Unboosted level in \<skill>, e.g. \{lvl_mining}                                                                                                                  |
| `{boost_<skill>}`                       | Current boosted level in \<skill>                                                                                                                                |
| `{xpRate_<skill>}`                      | Current xp/hour in \<skill> from XP Tracker, or N/A if that plugin isn't running                                                                                 |
| `{miscellania}`                         | Kingdom of Miscellania approval rating, 0-127                                                                                                                    |
| `{questPoints}`                         | Current quest points                                                                                                                                             |
| `{kc <boss>}`                           | Tracked kill count for \<boss>, e.g. \{kc Zulrah}                                                                                                                |
| `{slayerTask}`                          | Current slayer task. Requires Slayer plugin to be running.                                                                                                       |
| `{6HourTimeRemaining < 1.5}`            | Warning when you are within 1.5 hours of being 6-hour-logged. Shows highlighted color normally, red under compared time.                                         |
| `{hasThralls}`                          | true if on Arceuus Spellbook, has book of the dead, and runes for thralls                                                                                        |
| `{hasAlchs}`                            | true if on Standard Spellbook and has nature and fire runes for High Alchemy                                                                                     |
| `{hasFreeze}`                           | true if Ice Barrage is castable (Level not checked)                                                                                                              |
| `{hasEntangle}`                         | true if Entangle is castable (Level not checked)                                                                                                                 |
| `{autoRetaliate}` / `{autoRetal}`       | true if Auto Retaliate is on                                                                                                                                     |
| `{hasItem <name>}`                      | true if any item name in your inventory or equipment contains \<name>, e.g. \{hasItem rune pouch}                                                                |
| `{<cond1> && / \|\| <cond2> ? A : B}`   | Conditional — evaluates one or two of the above (==/!=/</>/<=/>=, or a bare boolean) and displays A or B. Conditions always evaluate using Plain Text variables. |
| `{<cond>}`                              | Boolean Variable — evaluates one of the above (==/!=/</>/<=/>=) and displays the Plain Text version of the variable, colored Green or Red.                       |

## Examples:

- `{equip_helm}`
  - Helm: Helm of Neitiznot
- `{lvl_agility < 87 ? Bring Summer Pie! : }`
  - Reminder to bring summer pie to boost for Hallowed Sepulcher!
  - Empty if you're already level 87.
- `{hasFreeze && weapon == staff of the dead ? Gigachad : Noob}`
  - Remind yourself what it takes to be a gigachad
- `{metronome4}`
  - 4 tick metronome on the tile
  - Cannot be used inside of a \<cond\>.
  - `{m4}` works as an alias as well.
- `{time > 10pm ? Go to bed : One more raid!}`
- `This text is {col=cyan}cyan!`
  - Changes color of the word "cyan!" to `#00FFFF`

## Images:
<img align="left" height="911" alt="java_zoCv5CveVH" src="https://github.com/user-attachments/assets/0bf33505-df1b-4372-a983-1ed42fd894d6" /> ToB Entry Check

```[{"regionId":14642,"regionX":13,"regionY":19,"z":0,"color":"#41FFFFFF","label":"{hasItem Salve} | {equip_ammo !\u003d Empty} | {hasThralls} | {hasFreeze} | {autoRetaliate}"}]```

<br clear="left"/><br />

<img align="left" height="575" alt="image" src="https://github.com/user-attachments/assets/5ccf632f-802b-4ee5-b4d0-3574d3077637" /> Miscellania

```[{"regionId":10044,"regionX":32,"regionY":11,"z":0,"color":"#00FFFFFF","label":"{miscellania}"}]```

<br clear="left"/><br />

<img align="left" height="631" alt="image" src="https://github.com/user-attachments/assets/5550a608-1352-4255-91ec-9ac9266cbb2e" /> Royal Titans Entry

```[{"regionId":11925,"regionX":5,"regionY":39,"z":0,"color":"#00FFFFFF","label":"{kc royal titans}"},{"regionId":11925,"regionX":5,"regionY":37,"z":0,"color":"#00FFFFFF","label":"{hasItem Blood Rune} | {attackStyle \u003d\u003d casting} | {autoRetal}"}]```

<br clear="left"/><br />

<img align="left" height="711" alt="image" src="https://github.com/user-attachments/assets/6c7d8942-e7c6-414c-8fe2-a2a2e6122d2b" /> Skilling

```[{"regionId":11050,"regionX":20,"regionY":10,"z":0,"color":"#00FFFFFF","label":"{xpRate_woodcutting}"}]```

<br clear="left"/><br />
