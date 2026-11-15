Shows a row based on `{party <template>}` per party member.

Also usable in math and conditionals as the number of players in the party `{party > 0 ? in party : not in party}`

- All variables that work for other members:
    - `readyCheck`, `rsn`, `partyNum`, `spec`, `runEnergy`, `invSpace`, `lvl_`, `boost_`, `active vengeance`
    - `equip_`, `weapon`, `hasItem`, `numItem`, `numDoses`, `showItems`, `showOneItem`
    - `spellbook`, `hasFreeze`, `hasAlchs`, `hasEntangle`, `hasVileVigour`, `hasVengeance`, `hasUndeadGrasp`, `hasMarkOfDarkness`, `hasSurge`, `hasBarrage`, `hasSpellbookSwap`, `hasThralls`
- Special, `{party}` only variables include:
    - `{readyCheck}` -- Reads in-game chat for ready messages or shows `:telescope:` for not nearby, `R` / `ready`, `P` / `pot` / `potting`, `not r` / `no r` / `un r`, `brb` / `5 mins`
    - `{partyNum}` -- The member's number in order. Can be used in conditionals to hide long lists with `{hoverOnly}`.
- `{partyCount <template>}` -- Sums each `<expr>` in `<template>` across all members: `{partyCount {hasFreeze} freezers, {numItem elder maul} mauls}`
    - Output is also usable in math and conditionals: `{{partyCount {readyCheck != ready}} > 0 ? Waiting : All ready}`
- `spellbook` is only known for members who also have Ground Marker Variables installed.
    - For others, `{spellbook}` shows `...`
    - `has<Spell>` variables only check their runes, shows slightly dimmed icon.
