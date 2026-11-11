---
name: zomboid-bunkers-direction
description: Josiah's hidden-bunker direction for ZomboidCraft: mostly urban (skyscrapers, cargo ships, docks), rare map drops from hard places, rolled loot, approved choices
metadata:
  type: feedback
  modified: 2026-10-25T22:01:22.877Z
---

(2026-10-25, thread "Build the ZomboidCraft landscape". Plan doc: https://claude.ai/artifact/DxX1cErgwr3AvxeE9HLS6k)

Josiah's asks, in order:
- 21:24: hidden bunkers, hard to find. A rare map drop leads to a general area; an entrance leads down into a big bunker of rare items and weapons.
- 21:28: the map drops only "in a place that's very hard to access and clear out to loot". Bunkers hold "some of the rarest guns in the game but not all", with a rolled tier and roll of which guns. "A good bunker find could lead to the lesser of the best guns and equipment."
- 21:50 (via the weapons thread): loot never scales with distance from spawn; no real names or franchise references ([[zomboid-loot-rarity-direction]]).
- 21:58: lean urban: "under skyscrapers in the city, inside the hull of massive cargo ships (some out in the water) ... get creative"; forest/county kinds stay but are the minority.
- 21:59: plenty of detail and no vanilla Minecraft blocks ([[zomboid-building-detail-direction]]).

He approved all four of my recommended choices (21:58):
- the blast door is pried with a crowbar, and the noise draws zombies;
- a dead generator can be restarted with fuel;
- more than one kind of bunker (now many urban kinds);
- a duplicate map can lead to a bunker already looted.

**How to apply:**
- Most bunkers go in the metropolis and waterfront: skyscraper sub-levels, cargo ship holds (docked and offshore), dock container yards, subway, civil-defence shelters and the like. Forest, mountain and farm kinds are the minority.
- Every bunker is built from custom blocks only.
- Discuss the plan with him before building. Basements (shipped) are in [[zomboid-basements-bunkers-direction]]. Tech: [[zomboid-houses-tech]], [[zomboid-world-tech]]. Map: [[zomboid-county-plan]].

**Plan v2** (doc rev 12): city ~6/10 (skyscraper, missing floor, civil-defence shelter, ghost subway, bank vault), docks ~3/10 (quay ship, offshore ship, container tunnel, harbour fort), country ~1/10 (forest hatch, mine adit, silo). ~24 bunkers, 18 on maps.
- APPROVED 22:20: wave 1 = skyscraper, quay ship, civil-defence shelter, forest hatch. Offshore ships: swim and climb the pilot ladder now, boat later with cars.
- Kit code: tools/city/bunker.py (prefixes bunk_, ship_, container_inside/floor/welded).
- 2026-10-25 ~22:45: kit built (132 blocks), preview sheet sent, merged to playtest 66e8b38. Next: shared core, then wave one, each previewed. Tech: [[zomboid-bunker-kit-tech]].

(2026-10-25 23:30) Broken-into (breached) bunkers: busted lights that flicker, fixtures hanging or fallen from the ceiling, damage like that. "But not completely torn up, we still want recognisable, lootable interiors": every room keeps its furniture and containers; damage is dressing on top.

(2026-10-26) Cargo ships scaled massive (~280x42, all custom blocks); quay ship shipped ae07dd2, tech [[zomboid-ship-tech]]; carrier followed ([[zomboid-carrier-direction]]).
Status 2026-10-26 07:30: wave 1 done (tower, quay ship, civil-defence shelter, forest hatch) plus marked maps from soldiers and base/naval containers, playtest ac54502. Tech: [[zomboid-bunker-wave1-tech]]. Next per doc: bunker loot tables, dead inside/generator, wave 2 (crowbar door built, see tech).

(2026-10-31 22:07) REVIEW PASS PICKED (decision card): bring the 5 built bunkers (tower, quay ship, shelter, forest hatch, army keypad) up to today's rules (own layout per kind, 3D pipes/conduit, clutter, light switches, damage by state, photo-led), before/after shots per bunker, tower first; THEN wave 2 kinds (subway, vault, offshore ship, container tunnel, harbour fort, mine, silo, missing floor). REFERENCES asked for 13 bunker folders.
