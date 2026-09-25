# Ground Marker Variables

Overrides the default Ground Marker plugin with a version that supports variables in labels.

Supported variables:

| Variable                                 | Description                                                                                              |
|------------------------------------------|----------------------------------------------------------------------------------------------------------|
| {rsn}                                    | Current player's display name                                                                            |
| {spellbook}                              | Active spellbook: Standard, Ancient, Lunar, or Arceuus                                                   |
| {metronome\<N>} / {metronome\<N>_\<M>}   | Counts down N → 1 and repeats, advancing every M ticks (default 1). Optional " - X" offsets it. Not usable inside a conditional |
| {weapon}                                 | Equipped weapon's item name, or Unarmed                                                                  |
| {attackStyle}                            | Current combat style name, e.g. Accurate, Aggressive, Casting                                            |
| {lvl_\<skill>}                           | Unboosted level in \<skill>, e.g. \{lvl_mining}                                                          |
| {boost_\<skill>}                         | Current boosted level in \<skill>                                                                        |
| {miscellania}                            | Kingdom of Miscellania approval rating, 0-127                                                            |
| {hasThralls}                             | true if on Arceuus Spellbook, has book of the dead, and runes for thralls                                |
| {hasAlchs}                               | true if on Standard Spellbook and has nature and fire runes for High Alchemy                             |
| {hasFreeze}                              | true if Ice Barrage is castable (Level not checked)                                                      |
| {hasEntangle}                            | true if Entangle is castable (Level not checked)                                                         |
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
- `This text is <col=teal>teal!`
  - Changes color of the word "teal!" to `#00FFFF`

## Options:

### Ground Markers

Tile marker appearance settings, migrated from RuneLite's core Ground Markers plugin the first time this plugin ever starts up.

- **Border width** — Width of the marked tile border.
- **Draw tiles on minimap** — Whether marked tiles should be drawn on the minimap.
- **Fill opacity** — Opacity of the tile fill color.
- **Show import/export/clear options** — Show the Import, Export, and Clear options on the world map orb right-click menu.
- **Tile color** — The default color for marked tiles.

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

## Example:
<img width="1309" height="890" alt="image" src="https://github.com/user-attachments/assets/a08bcbd3-a067-4cd5-a59c-f584a5878ad6" />


