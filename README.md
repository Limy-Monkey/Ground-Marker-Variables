# Ground Marker Variables

Ground Markers with variables in their labels. [Join our Discord](https://discord.gg/ehyvCPpdSn) to share your creations!

Advanced usage details can be found in API format [here](https://limy-monkey.github.io/Ground-Marker-Variables/).

<table><tr>
<td valign="top"><img width="450" height="440" alt="java_bE4E5Vp6Hu" src="https://github.com/user-attachments/assets/a80ad5c5-4c38-4194-81bb-a833a89f621b" /></td>
<td valign="top">
<details>
<summary>Butterfly markers</summary>

```json
[
{"regionId": 14676, "regionX": 29, "regionY": 40, "z": 1, "color": "#9C7C7C7C", "label": "{group Butterfly == 3 tick ? Attack : attack}"},
{"regionId": 14676, "regionX": 36, "regionY": 40, "z": 1, "color": "#9C7C7C7C", "label": "{group Butterfly == 3 tick ? Attack : attack}"},
{"regionId": 14676, "regionX": 31, "regionY": 38, "z": 1, "color": "#9C7C7C7C", "label": "{group Butterfly == 3 tick ? Attack : {tileCol=00FFFFFF}}"},
{"regionId": 14676, "regionX": 34, "regionY": 38, "z": 1, "color": "#9C7C7C7C", "label": "{group Butterfly == 3 tick ? Attack : {tileCol=00FFFFFF}}"},
{"regionId": 14676, "regionX": 25, "regionY": 36, "z": 1, "color": "#9C7C7C7C", "label": "{group Butterfly == 3 tick ? Attack}"},
{"regionId": 14676, "regionX": 29, "regionY": 36, "z": 1, "color": "#00FFFFFF", "label": "{setGroup Butterfly, *4 tick, 3 tick}{ground}:arrow_anticlockwise_gapped_circle_arrow@red:{/ground}"},
{"regionId": 14676, "regionX": 32, "regionY": 36, "z": 1, "color": "#9C7C7C7C"},
{"regionId": 14676, "regionX": 33, "regionY": 36, "z": 1, "color": "#9C7C7C7C"},
{"regionId": 14676, "regionX": 36, "regionY": 36, "z": 1, "color": "#00FFFFFF", "label": "{setGroup Butterfly, *4 tick, 3 tick}{ground}:arrow_clockwise_gapped_circle_arrow@highlight:{/ground}"},
{"regionId": 14676, "regionX": 40, "regionY": 36, "z": 1, "color": "#9C7C7C7C", "label": "{group Butterfly == 3 tick ? Attack}"},
{"regionId": 14676, "regionX": 25, "regionY": 34, "z": 1, "color": "#9C7C7C7C", "label": "{group Butterfly == 3 tick ? {tileCol=00FFFFFF} : attack}"},
{"regionId": 14676, "regionX": 31, "regionY": 34, "z": 1, "color": "#9C7C7C7C", "label": "{group Butterfly == 3 tick ? Attack : attack}"},
{"regionId": 14676, "regionX": 34, "regionY": 34, "z": 1, "color": "#9C7C7C7C", "label": "{group Butterfly == 3 tick ? Attack : attack}"},
{"regionId": 14676, "regionX": 40, "regionY": 34, "z": 1, "color": "#9C7C7C7C", "label": "{group Butterfly == 3 tick ? {tileCol=00FFFFFF} : attack}"},
{"regionId": 14676, "regionX": 27, "regionY": 32, "z": 1, "color": "#9C7C7C7C", "label": "{group Butterfly == 3 tick ? Attack}"},
{"regionId": 14676, "regionX": 38, "regionY": 32, "z": 1, "color": "#9C7C7C7C", "label": "{group Butterfly == 3 tick ? Attack}"},
{"regionId": 14676, "regionX": 27, "regionY": 31, "z": 1, "color": "#9C7C7C7C", "label": "{group Butterfly == 3 tick ? Attack}"},
{"regionId": 14676, "regionX": 38, "regionY": 31, "z": 1, "color": "#9C7C7C7C", "label": "{group Butterfly == 3 tick ? Attack}"},
{"regionId": 14676, "regionX": 25, "regionY": 29, "z": 1, "color": "#9C7C7C7C", "label": "{group Butterfly == 3 tick ? {tileCol=00FFFFFF} : attack}"},
{"regionId": 14676, "regionX": 31, "regionY": 29, "z": 1, "color": "#9C7C7C7C", "label": "{group Butterfly == 3 tick ? Attack : attack}"},
{"regionId": 14676, "regionX": 34, "regionY": 29, "z": 1, "color": "#9C7C7C7C", "label": "{group Butterfly == 3 tick ? Attack : attack}"},
{"regionId": 14676, "regionX": 40, "regionY": 29, "z": 1, "color": "#9C7C7C7C", "label": "{group Butterfly == 3 tick ? {tileCol=00FFFFFF} : attack}"},
{"regionId": 14676, "regionX": 25, "regionY": 27, "z": 1, "color": "#9C7C7C7C", "label": "{group Butterfly == 3 tick ? Attack}"},
{"regionId": 14676, "regionX": 29, "regionY": 27, "z": 1, "color": "#00FFFFFF", "label": "{setGroup Butterfly, *4 tick, 3 tick}{ground}:arrow_clockwise_gapped_circle_arrow@highlight:{/ground}"},
{"regionId": 14676, "regionX": 32, "regionY": 27, "z": 1, "color": "#9C7C7C7C"},
{"regionId": 14676, "regionX": 33, "regionY": 27, "z": 1, "color": "#9C7C7C7C"},
{"regionId": 14676, "regionX": 36, "regionY": 27, "z": 1, "color": "#00FFFFFF", "label": "{setGroup Butterfly, *4 tick, 3 tick}{ground}:arrow_anticlockwise_open_circle_arrow@red:{/ground}"},
{"regionId": 14676, "regionX": 40, "regionY": 27, "z": 1, "color": "#9C7C7C7C", "label": "{group Butterfly == 3 tick ? Attack}"},
{"regionId": 14676, "regionX": 31, "regionY": 25, "z": 1, "color": "#9C7C7C7C", "label": "{group Butterfly == 3 tick ? Attack : {tileCol=00FFFFFF}}"},
{"regionId": 14676, "regionX": 34, "regionY": 25, "z": 1, "color": "#9C7C7C7C", "label": "{group Butterfly == 3 tick ? Attack : {tileCol=00FFFFFF}}"},
{"regionId": 14676, "regionX": 29, "regionY": 23, "z": 1, "color": "#9C7C7C7C", "label": "{group Butterfly == 3 tick ? Attack : attack}"},
{"regionId": 14676, "regionX": 36, "regionY": 23, "z": 1, "color": "#9C7C7C7C", "label": "{group Butterfly == 3 tick ? Attack : attack}"}
]
```

</details>
</td>
</tr><tr>
<td valign="top"><img width="294" height="142" alt="java_sUtnLw5YMI" src="https://github.com/user-attachments/assets/fa534035-80d0-4463-8d51-c77fdd729b5d" /></td>
<td valign="top">
<details>
<summary>Lobby marker</summary>

```json
[{"title": "lobby", "regionId":16470,"regionX":44,"regionY":55,"z":0,"color":"#42FFFFFF","label":"
{party == 0 ?
  {kc tfa}\n
  {loot tfa > 2m ? {loot tfa itemsOnly}} {
    {spellbook == Arceuus || spellbook == Lunar ? {hasThralls ? : \s{tip=Missing thralls}:triangular_flag_on_post: <item=25818@0%>{/tip}}} ||
    {spellbook == Lunar ? {hasVengeance ? : \s{tip=Missing venge}:triangular_flag_on_post: <sprite=564@0%>{/tip}}} ||
    {spellbook == Standard ? {hasSurge || hasItem Blood rune ? : \s{tip=Missing Elemental Spells}:triangular_flag_on_post: {hasSurge icon}{/tip}}} ||
    {spellbook == Ancient ? {hasFreeze ? : \s{tip=Missing freezes}:triangular_flag_on_post: <sprite=328@0%>{/tip}}} ||
    {hasItem Twisted bow || hasItem Venator bow || hasItem Scorching bow ? {numItem Arrow > 100 ? : \s{tip=Missing arrows}:triangular_flag_on_post: <item=33595@0%>{/tip}}} ||
    {hasItem crossbow ? {hasItem bolts (e) || hasItem ascension bolts ? : \s{tip=Missing bolts}:triangular_flag_on_post: <item=21944@0%>{/tip}}} ||
  }
 :
  {hover=black@25%}
  {party > 4 ? {hoverHide}}
    {kc tfa}\n
    {{partyCount {hasFreeze}} > 0 ? {partyCount {hasFreeze}x<sprite=328>\s}}
    {{partyCount {hasSurge}} > 0 ? {partyCount {hasSurge}x<sprite=362>\s}}
    {{partyCount {hasItem Venator bow}} > 0 ? {partyCount {hasItem Venator bow}x<item=27610>\s}}
    {{partyCount {hasItem Elder maul || hasItem Dragon warhammer}} > 0 ? {partyCount {hasItem Elder maul || hasItem Dragon warhammer}x<item=21003>\s}}
    {{partyCount {hasItem Tonalztics of Ralos}} > 0 ? {partyCount {hasItem Tonalztics of Ralos}x<item=28922>\s}}
    {{partyCount {hasItem Obligator}} > 0 ? {partyCount {hasItem Obligator}x<item=34715>\s}}
  {party > 4 ? {/hoverHide} : \n}
  {hoverOnly}
    \t\t\t{tip=Melee mainhand}<sprite=197>{/tip}\t{tip=Ranged mainhand}<sprite=200>{/tip}\t{tip=Defence Drain}<sprite=199>{/tip}
    {{partyCount {hasItem Twisted bow || hasItem Venator bow || hasItem Scorching bow ? {numItem Arrow > 100 ? : 1}}} + {partyCount {hasItem crossbow ? {hasItem bolts (e) || hasItem ascension bolts ? : 1}}} + {partyCount {spellbook == Arceuus ? {hasThralls ? : 1}}} > 0 ? \t{tip=Missing Items}:triangular_flag_on_post:{/tip}}
    \n
    {party {readyCheck}\t@1px
      {hasSpellbookSwap icon || hasFreeze icon || hasSurge icon || showOneItem Venator bow, Chinchompa, Dinh's Bulwark}\t@2px
      {tip=kc: {kc tfa}}{rsn}{/tip}\t
      {showOneItem Obligator, Scythe, Soulreaper axe, Inquisitor mace, Emberlight, Abyssal bludgeon, Ursine chainmace, Zombie Axe, Zamorakian hasta || :x@black:}\t
      {showOneItem Twisted bow, Blowpipe, Crossbow, Scorching bow, Bow of Faerdhinen || :x@black:}\t
      {showOneItem Tonalztics of Ralos, Elder Maul, Dragon Warhammer, Bandos Godsword, Bone dagger}{showItem Soulflame horn}\t
      {hasItem Twisted bow || hasItem Venator bow || hasItem Scorching bow ? {numItem Arrow > 100 ? : {tip=Missing arrows}<item=33595@0%>{/tip}}}
      {hasItem crossbow ? {hasItem ruby bolts (e) || hasItem ruby dragon bolts (e) || hasItem ascension bolts ? : {tip=Missing bolts}<item=21944@0%>{/tip}}}
      {spellbook == Arceuus || hasItem book of the dead ? {hasThralls ? : {tip=Missing thralls}<item=25818@0%>{/tip}}}
      {spellbook == Ancient ? {hasFreeze ? : {tip=Missing freezes}<sprite=328@0%>{/tip}}}
    }
  {/hoverOnly}
}
{party > 1 ? {hoverHide}}
  {6Hour < 1.5 ? \n{6Hour < 0.75}}
{party > 1 ? {/hoverHide}}
"}]
```

</details>
</td>
</tr><tr>
<td valign="top"><video src="https://github.com/user-attachments/assets/e49f5d67-f53d-44a5-882d-1d1f329d3044"></video></td>
<td valign="top">
<details>
<summary>Ocellus markers</summary>

```json
[
    {"regionId": 16472, "regionX": 29, "regionY": 44, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 ? {setGroup ~Rotation, NW green, *none}{group Rotation == [none, SE orange, SW red, NW green] ? {ground@s}{col=green}1{/col}{/ground}{hover}{hoverOnly}1}}"},
    {"regionId": 16472, "regionX": 28, "regionY": 44, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 ? {setGroup ~Rotation, NW orange, *none}{group Rotation == [none, SE red, SW green, NW orange] ? {ground@s}{col=orange}1{/col}{/ground}{hover}{hoverOnly}1}}"},
    {"regionId": 16472, "regionX": 25, "regionY": 47, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 ? {setGroup ~Rotation, NW red, *none}{group Rotation == [none, SE green, SW orange, NW red] ? {ground@s}{col=red}1{/col}{/ground}{hover}{hoverOnly}1}}"},
    {"regionId": 16472, "regionX": 24, "regionY": 44, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE orange, SW red, NW green] ? {ground@s}{col=green}2{/col}{/ground}{hover}{hoverOnly}2}"},
    {"regionId": 16472, "regionX": 28, "regionY": 48, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE red, SW green, NW orange] ? {ground@s}{col=orange}2{/col}{/ground}{hover}{hoverOnly}2}"},
    {"regionId": 16472, "regionX": 23, "regionY": 46, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE green, SW orange, NW red] ? {ground@s}{col=red}2{/col}{/ground}{hover}{hoverOnly}2}"},
    {"regionId": 16472, "regionX": 20, "regionY": 44, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE orange, SW red, NW green] ? {ground@s}{col=green}3{/col}{/ground}{hover}{hoverOnly}3}"},
    {"regionId": 16472, "regionX": 23, "regionY": 44, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE red, SW green, NW orange] ? {ground@s}{col=orange}3{/col}{/ground}{hover}{hoverOnly}3}"},
    {"regionId": 16472, "regionX": 21, "regionY": 44, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE green, SW orange, NW red] ? {ground@s}{col=red}3{/col}{/ground}{hover}{hoverOnly}3}"},
    {"regionId": 16472, "regionX": 24, "regionY": 48, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE orange, SW red, NW green] ? {ground@s}{col=green}4{/col}{/ground}{hover}{hoverOnly}4}"},
    {"regionId": 16472, "regionX": 27, "regionY": 44, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE red, SW green, NW orange] ? {ground@s}{col=orange}4{/col}{/ground}{hover}{hoverOnly}4}"},
    {"regionId": 16472, "regionX": 26, "regionY": 47, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE green, SW orange, NW red] ? {ground@s}{col=red}4{/col}{/ground}{hover}{hoverOnly}4}"},
    {"regionId": 16472, "regionX": 26, "regionY": 46, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE orange, SW red, NW green] ? {ground@s}{col=green}5{/col}{/ground}{hover}{hoverOnly}5}"},
    {"regionId": 16472, "regionX": 25, "regionY": 46, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE red, SW green, NW orange] ? {ground@s}{col=orange}5{/col}{/ground}{hover}{hoverOnly}5}"},
    {"regionId": 16472, "regionX": 22, "regionY": 47, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE green, SW orange, NW red] ? {ground@s}{col=red}5{/col}{/ground}{hover}{hoverOnly}5}"},
    {"regionId": 16472, "regionX": 33, "regionY": 44, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 ? {setGroup ~Rotation, NE green, *none}{group Rotation == [none, SW orange, NW red, NE green] ? {ground@s}{col=green}1{/col}{/ground}{hover}{hoverOnly}1}}"},
    {"regionId": 16472, "regionX": 34, "regionY": 44, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 ? {setGroup ~Rotation, NE orange, *none}{group Rotation == [none, SW red, NW green, NE orange] ? {ground@s}{col=orange}1{/col}{/ground}{hover}{hoverOnly}1}}"},
    {"regionId": 16472, "regionX": 37, "regionY": 47, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 ? {setGroup ~Rotation, NE red, *none}{group Rotation == [none, SW green, NW orange, NE red] ? {ground@s}{col=red}1{/col}{/ground}{hover}{hoverOnly}1}}"},
    {"regionId": 16472, "regionX": 38, "regionY": 44, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SW orange, NW red, NE green] ? {ground@s}{col=green}2{/col}{/ground}{hover}{hoverOnly}2}"},
    {"regionId": 16472, "regionX": 34, "regionY": 48, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SW red, NW green, NE orange] ? {ground@s}{col=orange}2{/col}{/ground}{hover}{hoverOnly}2}"},
    {"regionId": 16472, "regionX": 39, "regionY": 46, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SW green, NW orange, NE red] ? {ground@s}{col=red}2{/col}{/ground}{hover}{hoverOnly}2}"},
    {"regionId": 16472, "regionX": 42, "regionY": 44, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SW orange, NW red, NE green] ? {ground@s}{col=green}3{/col}{/ground}{hover}{hoverOnly}3}"},
    {"regionId": 16472, "regionX": 39, "regionY": 44, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SW red, NW green, NE orange] ? {ground@s}{col=orange}3{/col}{/ground}{hover}{hoverOnly}3}"},
    {"regionId": 16472, "regionX": 41, "regionY": 44, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SW green, NW orange, NE red] ? {ground@s}{col=red}3{/col}{/ground}{hover}{hoverOnly}3}"},
    {"regionId": 16472, "regionX": 38, "regionY": 48, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SW orange, NW red, NE green] ? {ground@s}{col=green}4{/col}{/ground}{hover}{hoverOnly}4}"},
    {"regionId": 16472, "regionX": 35, "regionY": 44, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SW red, NW green, NE orange] ? {ground@s}{col=orange}4{/col}{/ground}{hover}{hoverOnly}4}"},
    {"regionId": 16472, "regionX": 36, "regionY": 47, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SW green, NW orange, NE red] ? {ground@s}{col=red}4{/col}{/ground}{hover}{hoverOnly}4}"},
    {"regionId": 16472, "regionX": 36, "regionY": 46, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SW orange, NW red, NE green] ? {ground@s}{col=green}5{/col}{/ground}{hover}{hoverOnly}5}"},
    {"regionId": 16472, "regionX": 37, "regionY": 46, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SW red, NW green, NE orange] ? {ground@s}{col=orange}5{/col}{/ground}{hover}{hoverOnly}5}"},
    {"regionId": 16472, "regionX": 40, "regionY": 47, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SW green, NW orange, NE red] ? {ground@s}{col=red}5{/col}{/ground}{hover}{hoverOnly}5}"},
    {"regionId": 16472, "regionX": 29, "regionY": 38, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 ? {setGroup ~Rotation, SW green, *none}{group Rotation == [none, SE red, SW green, NE orange] ? {ground@n}{col=green}1{/col}{/ground}{hover}{hoverOnly}1}}"},
    {"regionId": 16472, "regionX": 28, "regionY": 38, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 ? {setGroup ~Rotation, SW orange, *none}{group Rotation == [none, SE green, SW orange, NE red] ? {ground@n}{col=orange}1{/col}{/ground}{hover}{hoverOnly}1}}"},
    {"regionId": 16472, "regionX": 25, "regionY": 35, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 ? {setGroup ~Rotation, SW red, *none}{group Rotation == [none, SE orange, SW red, NE green] ? {ground@n}{col=red}1{/col}{/ground}{hover}{hoverOnly}1}}"},
    {"regionId": 16472, "regionX": 24, "regionY": 38, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE red, SW green, NE orange] ? {ground@n}{col=green}2{/col}{/ground}{hover}{hoverOnly}2}"},
    {"regionId": 16472, "regionX": 28, "regionY": 34, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE green, SW orange, NE red] ? {ground@n}{col=orange}2{/col}{/ground}{hover}{hoverOnly}2}"},
    {"regionId": 16472, "regionX": 23, "regionY": 36, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE orange, SW red, NE green] ? {ground@n}{col=red}2{/col}{/ground}{hover}{hoverOnly}2}"},
    {"regionId": 16472, "regionX": 20, "regionY": 38, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE red, SW green, NE orange] ? {ground@n}{col=green}3{/col}{/ground}{hover}{hoverOnly}3}"},
    {"regionId": 16472, "regionX": 23, "regionY": 38, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE green, SW orange, NE red] ? {ground@n}{col=orange}3{/col}{/ground}{hover}{hoverOnly}3}"},
    {"regionId": 16472, "regionX": 21, "regionY": 38, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE orange, SW red, NE green] ? {ground@n}{col=red}3{/col}{/ground}{hover}{hoverOnly}3}"},
    {"regionId": 16472, "regionX": 24, "regionY": 34, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE red, SW green, NE orange] ? {ground@n}{col=green}4{/col}{/ground}{hover}{hoverOnly}4}"},
    {"regionId": 16472, "regionX": 27, "regionY": 38, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE green, SW orange, NE red] ? {ground@n}{col=orange}4{/col}{/ground}{hover}{hoverOnly}4}"},
    {"regionId": 16472, "regionX": 26, "regionY": 35, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE orange, SW red, NE green] ? {ground@n}{col=red}4{/col}{/ground}{hover}{hoverOnly}4}"},
    {"regionId": 16472, "regionX": 26, "regionY": 36, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE red, SW green, NE orange] ? {ground@n}{col=green}5{/col}{/ground}{hover}{hoverOnly}5}"},
    {"regionId": 16472, "regionX": 25, "regionY": 36, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE green, SW orange, NE red] ? {ground@n}{col=orange}5{/col}{/ground}{hover}{hoverOnly}5}"},
    {"regionId": 16472, "regionX": 22, "regionY": 35, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE orange, SW red, NE green] ? {ground@n}{col=red}5{/col}{/ground}{hover}{hoverOnly}5}"},
    {"regionId": 16472, "regionX": 33, "regionY": 38, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 ? {setGroup ~Rotation, SE green, *none}{group Rotation == [none, SE green, NW orange, NE red] ? {ground@n}{col=green}1{/col}{/ground}{hover}{hoverOnly}1}}"},
    {"regionId": 16472, "regionX": 34, "regionY": 38, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 ? {setGroup ~Rotation, SE orange, *none}{group Rotation == [none, SE orange, NW red, NE green] ? {ground@n}{col=orange}1{/col}{/ground}{hover}{hoverOnly}1}}"},
    {"regionId": 16472, "regionX": 37, "regionY": 35, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 ? {setGroup ~Rotation, SE red, *none}{group Rotation == [none, SE red, NW green, NE orange] ? {ground@n}{col=red}1{/col}{/ground}{hover}{hoverOnly}1}}"},
    {"regionId": 16472, "regionX": 38, "regionY": 38, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE green, NW orange, NE red] ? {ground@n}{col=green}2{/col}{/ground}{hover}{hoverOnly}2}"},
    {"regionId": 16472, "regionX": 34, "regionY": 34, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE orange, NW red, NE green] ? {ground@n}{col=orange}2{/col}{/ground}{hover}{hoverOnly}2}"},
    {"regionId": 16472, "regionX": 39, "regionY": 36, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE red, NW green, NE orange] ? {ground@n}{col=red}2{/col}{/ground}{hover}{hoverOnly}2}"},
    {"regionId": 16472, "regionX": 42, "regionY": 38, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE green, NW orange, NE red] ? {ground@n}{col=green}3{/col}{/ground}{hover}{hoverOnly}3}"},
    {"regionId": 16472, "regionX": 39, "regionY": 38, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE orange, NW red, NE green] ? {ground@n}{col=orange}3{/col}{/ground}{hover}{hoverOnly}3}"},
    {"regionId": 16472, "regionX": 41, "regionY": 38, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE red, NW green, NE orange] ? {ground@n}{col=red}3{/col}{/ground}{hover}{hoverOnly}3}"},
    {"regionId": 16472, "regionX": 38, "regionY": 34, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE green, NW orange, NE red] ? {ground@n}{col=green}4{/col}{/ground}{hover}{hoverOnly}4}"},
    {"regionId": 16472, "regionX": 35, "regionY": 38, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE orange, NW red, NE green] ? {ground@n}{col=orange}4{/col}{/ground}{hover}{hoverOnly}4}"},
    {"regionId": 16472, "regionX": 36, "regionY": 35, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE red, NW green, NE orange] ? {ground@n}{col=red}4{/col}{/ground}{hover}{hoverOnly}4}"},
    {"regionId": 16472, "regionX": 36, "regionY": 36, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE green, NW orange, NE red] ? {ground@n}{col=green}5{/col}{/ground}{hover}{hoverOnly}5}"},
    {"regionId": 16472, "regionX": 37, "regionY": 36, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE orange, NW red, NE green] ? {ground@n}{col=orange}5{/col}{/ground}{hover}{hoverOnly}5}"},
    {"regionId": 16472, "regionX": 40, "regionY": 35, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 && group Rotation == [SE red, NW green, NE orange] ? {ground@n}{col=red}5{/col}{/ground}{hover}{hoverOnly}5}"},
    {"regionId": 16472, "regionX": 31, "regionY": 49, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == None ? {tileCol=40FFFFFF}{ground}<npc=16672,head>{/ground}{setGroup ~Ocellus, p2, *None}}"},
    {"regionId": 16472, "regionX": 31, "regionY": 46, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 ? {tileCol=40FFFFFF}{ground}<npc=16677>{/ground}{setGroup ~Ocellus, p3, *None}}"},
    {"regionId": 16472, "regionX": 31, "regionY": 36, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == p2 ? {tileCol=40FFFFFF}{ground}<npc=16677>{/ground}{setGroup ~Ocellus, p3, *None}}"},
    {"regionId": 16472, "regionX": 31, "regionY": 33, "z": 1, "color": "#00FFFFFF", "label": "{group Ocellus == None ? {tileCol=40FFFFFF}{ground}<npc=16672,head>{/ground}}{setGroup ~Ocellus, p2, *None}"},
    {"title": "ocellus", "regionId":16472,"regionX":31,"regionY":23,"z":1,"color":"#00FFFFFF","label":
"{((hasBarrage blood && autocast != Blood Barrage) || (hasSurge && autocast != Earth Surge)) && showItem staff, wand, sceptre ? {tip=Autocast}:triangular_flag_on_post: {autocast icon}{/tip}\n}
{setGroup ~Ocellus, *None, p2}
"}
]
```

</details>
</td>
</tr></table>



<details>
<summary>Variables</summary>

### Full Docs: [link](https://limy-monkey.github.io/Ground-Marker-Variables/)

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

------------------------

**Tile settings**: These variables modify the tile itself, not the label text.

Not allowed in conditionals, except conditionals that only use `{group}`.

| Token | Description |
|---|---|
| `{tileCol=<col>}` | Tile color: named color, hex, 8-digit hex with leading alpha, `N%`, or a named opacity |
| `{highlightSW=<col>}` | Corner bracket: SW, NW, NE, SE, or Corners / All |
| `{borderWidth=2.5}` | Outline width, up to 10 |
| `{ground}…{/ground}` | Icons inside drawn flat on the tile (not around a conditional either) |

------------------------

**Variable List**

- Returns: Type of `{^...}` - what gets used in conditions, math, etc
- Party: Can be used inside `{party {var} }`
- Icon: Has an icon option to display as icon `{... icon}`

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
| `{loot <monster>}`                                   | Number          |       | *    | Loot Tracker gp; `items=N`, `itemsPerLine=N`, `oneline`, `itemsOnly`, uses Loot Tracker plugin                                                          |
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
- `::manageGMV` opens the Manager to copy, delete and re-import markers.
- Other plugins can resolve labels and draw tiles through us: see [PluginMessageHandler.java](https://github.com/Limy-Monkey/Ground-Marker-Variables/blob/master/src/main/java/com/groundmarkervariables/PluginMessageHandler.java).

</details>

<details>
<summary>Gallery</summary>

<img align="left" height="580" alt="image" src="https://github.com/user-attachments/assets/eb26eef6-03ae-4957-aa23-21602eab8ca7" /> ToB Entry Check

```[{"regionId":14642,"regionX":13,"regionY":19,"z":0,"color":"#00FFFFFF","label":"{align=left}{spellbook} {spellbook == Ancient ? -- {hasFreeze} : }{spellbook == Arceuus ? -- {hasThralls} : } \\n {hasItem Salve} \\n {equip_ammo != Empty} \\n {hasItem Rune Pouch} \\n {autoRetaliate}"}]```

<br clear="left"/><br />

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
