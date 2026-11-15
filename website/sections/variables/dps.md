!!! warning
    DPS has been deprecated, and may be subject to removal in the future.

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
