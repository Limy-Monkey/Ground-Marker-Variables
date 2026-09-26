# Ground Marker Variables

Overrides the default Ground Marker plugin with a version that supports variables in labels.

Supported variables:

| Variable                                 | Description                                                                                              |
|------------------------------------------|----------------------------------------------------------------------------------------------------------|
| {rsn}                                    | Current player's display name                                                                            |
| {time} / {time24}                        | Current local time, 12-hour h:mm am/pm or 24-hour HH:mm                                                  |
| {spellbook}                              | Active spellbook: Standard, Ancient, Lunar, or Arceuus                                                   |
| {metronome\<N>} / {metronome\<N>_\<M>}   | Counts down N → 1 and repeats, advancing every M ticks (default 1). Optional " - X" offsets it. Not usable inside a conditional |
| {weapon}                                 | Equipped weapon's item name, or Unarmed                                                                  |
| {equip_\<slot>}                          | Equipped item name in \<slot> (helm, cape, amulet, body, shield, legs, gloves, boots, ring, ammo, quiver), or Empty |
| {attackStyle}                            | Current combat style name, e.g. Accurate, Aggressive, Casting                                            |
| {lvl_\<skill>}                           | Unboosted level in \<skill>, e.g. \{lvl_mining}                                                          |
| {boost_\<skill>}                         | Current boosted level in \<skill>                                                                        |
| {miscellania}                            | Kingdom of Miscellania approval rating, 0-127                                                            |
| {questPoints}                            | Current quest points                                                                                     |
| {kc \<boss>}                             | Tracked kill count for \<boss>, e.g. \{kc Zulrah}                                                        |
| {hasThralls}                             | true if on Arceuus Spellbook, has book of the dead, and runes for thralls                                |
| {hasAlchs}                               | true if on Standard Spellbook and has nature and fire runes for High Alchemy                             |
| {hasFreeze}                              | true if Ice Barrage is castable (Level not checked)                                                      |
| {hasEntangle}                            | true if Entangle is castable (Level not checked)                                                         |
| {autoRetaliate} / {autoRetal}            | true if Auto Retaliate is on                                                                             |
| {hasItem \<name>}                        | true if any item name in your inventory or equipment contains \<name>, e.g. \{hasItem rune pouch}        |
| {\<cond1> \[&& / \|\| \<cond2>] ? A : B} | Conditional — evaluates one or two of the above (==/!=/</>/<=/>=, or a bare boolean) and displays A or B |

## Examples:

- `You are currently on the {spellbook} spellbook!`
  - You are currently on the Standard spellbook!
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
<img align="left" height="559" alt="image" src="https://github.com/user-attachments/assets/d286df61-ba3d-4da5-8438-7751a2a8de10" /> ToB Entry Check

```[{"regionId":14642,"regionX":13,"regionY":19,"z":0,"color":"#41FFFFFF","label":"{hasItem salve ? {col\u003dgreen} : {col\u003dred}}Salve{/col} | {equip_ammo \u003d\u003d Empty ? {col\u003dred}Ammo : {col\u003dgreen}{equip_ammo}}{/col} |{spellbook \u003d\u003d Ancient || spellbook \u003d\u003d Arceuus ? {col\u003dred} : {col\u003dgray}}{hasThralls || hasFreeze ? {col\u003dgreen} : }{spellbook}{/col} spellbook {autoRetal ? | {col\u003dred}Auto Retaliate{/col} : }"}]```

<br clear="left"/><br />

<img align="left" height="553" alt="image" src="https://github.com/user-attachments/assets/d9b451ab-a4d4-42b6-9688-e99b24362b8f" /> Miscellania

```[{"regionId":10044,"regionX":32,"regionY":11,"z":0,"color":"#00FFFFFF","label":"{miscellania \u003d\u003d 127 ? {col\u003dgreen} : {col\u003dlightgray}}{miscellania}{/col} / 127"}]```

<br clear="left"/><br />

<img align="left" height="598" alt="image" src="https://github.com/user-attachments/assets/eb6e2895-d4de-495e-a186-4694734e3db8" /> Royal Titans Entry

```[{"regionId":11925,"regionX":5,"regionY":39,"z":0,"color":"#00FFFFFF","label":"{col\u003dgreen}Royal Titans{/col} kc: {col\u003dgreen}{kc royal titans}"},{"regionId":11925,"regionX":5,"regionY":37,"z":0,"color":"#00FFFFFF","label":"{hasItem blood rune ? {col\u003dgreen} : {col\u003dred}}Blood Runes{/col} | {attackStyle \u003d\u003d casting ? {col\u003dgreen} : {col\u003dred}}Autocast{/col} | {autoRetal ? {col\u003dred} : {col\u003dgreen}}Auto Retaliate{/col}"}]```

<br clear="left"/>

## Config Options:

### Ground Markers

Tile marker appearance settings, migrated from RuneLite's core Ground Markers plugin the first time this plugin ever starts up.

### Metronome

- **Reset metronome** — Set a hotkey to resets the internal \{metronome\} to the current tick.
- **Count Down** — Count \{metronome\} down from N → 1 instead of up from 1 → N.
- **Highlight Final Tick** / **Final Tick Color** — Color \{metronome\} on its final tick before it repeats.

### Party Sync

- **Party Sync** / **Sync Target** — Sync \{metronome\} to a party member's tick count instead of your own. Set Sync Target to their display name; they need Ground Marker Variables installed and must be in the same party.

### Advanced Editor

- **Use Advanced Label Editor** — Replace the plain Tile label prompt with the Advanced Label Editor. When off, a plain label prompt is used instead.
- **Show Current** — Show the "Current" section of the Advanced Label Editor's recommendations.
- **Show Recent** — Show the "Recent" section of the Advanced Label Editor's recommendations.
- **Show Nearby** — Show the "Nearby" section of the Advanced Label Editor's recommendations.
- **Autocomplete** — Autocomplete variable names, skills, colors, and metronome parameters while typing in the Advanced Label Editor.