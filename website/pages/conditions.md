# Conditions & math

## Plain vs Rich Text
Most variables have a short **Plain Text** value (`385`) and a descriptive **Rich Text** value (`Corrupted Gauntlet kc: 385`). Labels use Rich Text by default; force one with `{^...}` (Plain) or `{&...}` (Rich). Conditions and math always use Plain Text.

## Comparisons
`{<variable> <cmp> <value>}` shows the variable colored green if the comparison is true, red if false.

- Comparators: `==`, `!=`, `<`, `>`, `<=`, `>=`
- `==` / `!=` compare text, ignoring case: `{spellbook == Ancient}`
- Against a list in square brackets, `==` is true if it equals any item and `!=` if none: `{spellbook == [Ancient, Arceuus]}`
- `<` / `>` compare numbers (`10.5k` works), or clock times: `{time < 9:00pm}`
- The value can be another variable: `{boost_strength > lvl_strength}`

## Or / And
`{<a> || <b> || ...}` shows the first part that's true, and `{<a> && <b> && ...}` the first part that's false. If none is, the last part is shown.

- `{hasItem obligator || hasItem scythe}` shows Obligator if you have one, otherwise the scythe, green or red.
- The last part can be plain text, even empty: `{hasItem obligator || }` shows nothing without an Obligator.
- Parts can be comparisons: `{kc tob > 100 && hasItem scythe && Ready}`
- `||` and `&&` can't be mixed in one token.

## Conditionals
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

## Math
`{<a> <op> <b> ...}` with `+`, `-`, `*`, `/`. Operators need a space on each side, and parentheses group: `{(kc tob + kc hmt) / 2}`.

- Works with numbers (`1.5k`) and numeric variables: `kc`, `loot`, `lvl_`, `boost_`, `xpRate_`, `runEnergy`, `spec`, `questPoints`, `slayerPoints`, `slayerStreak`, `miscellania`, `weekday`, `combatAchievement`, `numItem`, `numDoses`, `party`, `invSpace`, `xp_`
- Usable in conditions: `{kc tob + kc hmt > 100 ? 100+ ToBs : }`
- Dividing by zero leaves the token as written.

## Date and time math
- **Days until a date**: `{oct 20 - date}` → `8 days`. Dates can be `Oct 20`, `October 20 2026`, `10/20` or `10-20-2026` (day/month order follows your system locale). With no year, the current year is used.
- **Time until / since a time of day**: `{6am - time}` → `1:23`, wrapping past midnight (at 7am it's `23:00`). `{time - 2am}` is time since 2am. Times can be `6am`, `8 pm`, `6:30 a.m.`, `7:00` or `15:00`, and two times work too: `{6am - 2am}` → `4:00`. Write `7:00` or `7am`, not `7`.
- **Countdown to a date and time**: `{nov 4 7am - now}`. Rich Text shows `9 days 21:06`, Plain Text shows total hours `237:06`, and it goes negative once the time has passed. `now` also works in place of `date` or `time` above.
