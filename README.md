# Ground Marker Variables

Overrides the default Ground Marker plugin with a version that supports variables in labels.

Supported variables:

| Variable                                 | Description                                                                                                               |
|------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| {rsn}                                    | Current player's display name                                                                                             |
| {spellbook}                              | Active spellbook: Standard, Ancient, Lunar, or Arceuus                                                                    |
| {metronome\<N>} / {metronome\<N>_\<M>}   | Counts down N → 1 and repeats, advancing every M ticks (default 1)                                                        |
| {weapon}                                 | Equipped weapon's item name, or Unarmed                                                                                   |
| {attackStyle}                            | Current combat style name, e.g. Accurate, Aggressive, Casting                                                             |
| {lvl_\<skill>}                           | Unboosted level in \<skill>, e.g. \{lvl_mining}                                                                           |
| {boost_\<skill>}                         | Current boosted level in \<skill>                                                                                         |
| {hasThralls}                             | true if on Arceuus Spellbook, has book of the dead, and runes for thralls                                                 |
| {hasFreeze}                              | true if Ice Barrage is castable (Level not checked)                                                                       |
| {hasEntangle}                            | true if Entangle is castable (Level not checked)                                                                          |
| {hasItem \<name>}                        | true if any item name in your inventory or equipment contains \<name>, e.g. \{hasItem rune pouch}                         |
| {\<cond1> \[&& / \|\| \<cond2>] ? A : B} | Conditional — evaluates one or two of the above (==/!=/</>/<=/>=, or a bare boolean) and displays A or B                  |

## Examples:

- `You are currently on the {spellbook} spellbook!`
  - You are currently on the Standard spellbook!
- `{lvl_agility < 87 ? Bring Summer Pie! : }`
  - Reminder to bring summer pie to boost for Hallowed Sepulcher!
  - Empty if you're already level 87.
- `{hasFreeze && weapon == staff of the dead ? Gigachad : Noob}`
  - Remind yourself what it takes to be a gigachad
- `{metronome16 > 8 ? {metronome4} : }`
  - 16 tick cycle, showing a 4-tick metronome for 8 ticks then hiding it for 8 ticks.
 
## Note:

The Runescape Quill font used for the `Tile label` chat box renders squiggly bracket `{ }` symbols similarly to parentheses `( )`. Only squiggly brackets are supported.
 
<img width="568" height="603" alt="java_1hdlq8BOsb" src="https://github.com/user-attachments/assets/4b81bb86-ea82-45a7-bd39-0c600bfdf7bc" />
