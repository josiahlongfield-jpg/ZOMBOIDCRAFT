---
name: zomboid-access-tech
description: How ZomboidCraft keeps stairs, doorways, ladders and decks walkable (Builders post-passes, walkcheck --why, block surface heights, preview sizes, traps fixed)
metadata:
  type: project
  modified: 2026-10-27T23:40:00.000Z
---

Implements [[zomboid-access-rule]]. Checker and dump tools: [[zomboid-world-tech]].

**Post-passes** (Builders.build, in this order; CivicPreview runs them too): finishFlights,
clearHeadroom (Builders.HEADROOM = 4 clear over every tread; a "roof" block 4 over with only
sky above is LIFTED a block (curb) instead of holed, any other 4th-cell block cleared; the cell 2
over a flight's FOOT too, only when something sits on top of it, so deck landings stay), fitHatches, clearDoorways (litter and
FURNITURE out of doorways unless KEEP; a non-ship door with masonry/wall on one side at both
heights is bricked up with its own wall), clearGrass, clearTrees (leaves/limbs under or within
2 over anything built; bark boughs between built things or just over them, never a trunk an
arm goes through).

**Headroom check:** tools/county/headroom.py DUMP [N=4] lists treads with anything in the 4
cells over them and treads stacked under another flight (no pass can fix those: same lane
only every 2nd storey, or storeys >= 5; Stairwell needs h >= 5 for 4 clear). RoofHut is 4 clear
inside (roof at roofY+5). Walkup fire escapes (2026-10-27): longEscape (w >= 13, two-floor
flights in the outer lane, full landings along the wall) or switchback (narrow, end landings).
Left 2026-10-27: rare cellar flight under a house stair. Tower cores pass on fit-out branch l1uzx6 (trial merge scan: 0 blocked).

**Surface heights (they decide steps):** `grate` is a 1px plate at the block's FOOT (street
drains, library decks); `bunk_walkway` is grating at the block's TOP (fire escapes, catwalks).
`steel_stairs` plates sit at +1px and +9px, so a steel flight's top tread in a grate's plane
is flush. Fire escape landings = bunk_walkway level with the floor block inside.

**Barricades:** world gen can place the real `barricade[facing=F,planks=1-4,strength=0-2]`
(ModBlocks.BARRICADE via CityBlocks.resolve); FACING = side the boards are on (toward the
door). House BOARDED doors: shut door + barricades on the step outside (hammer pries).
walkcheck treats barricades and blast doors as doors.

**orientcheck.py** (tools/county) also lists windows turned OUT OF THEIR WALL (glass facing
along the wall, Josiah's lit crown windows 2026-10-27): more solid neighbours across a pane than
along it. Bare axis windows ("window_lit" with no [axis]) count as the game places them, axis=z,
so on an x wall they stand side-on. Write windows with Placer.inWall(block, alongX). Decay keeps
a builder's axis unless the neighbours settle it. Flags left on purpose: curved stadium bowl
walls (diagonal steps) and dump edges.

**walkcheck.py** (tools/county): `--why X Z` prints stand levels and links; BODY 2.0, STEP
0.5, JUMP 1.25. A pixel-thin plate over air holds a body. Dumps clip at their bottom
(pockets at dump y 0..4 under ships are artefacts). Boarded/holdout story barricades are meant.

**Preview sizes:** CivicPreview courthouse:30:40 library:28:36 townhall:28:38 bank:24:30
firestation:26:40 sheriff:24:30 postoffice:24:28 school:50:36 theatre:15:40. SitePreview: port
container 340 300, naval 430 300, county_seat 300 300, main_street 300 300, cul_de_sac 260
240, old_grid 240 220, motel 100 112, warehouse 104 136, army_base 308 286.

**Baselines 2026-10-27 (after fixes):** old_grid, cul_de_sac, civ8, civ9: 0 bad stairs/doors;
county_seat 1 door; naval 3 doors; port container 40 doors (ship engine rooms at dump bottom).
Fixed traps: courthouse sandbag gap, motel wing backwards, warehouse dock sill, ship stair-hall
passing lane, carrier trunk landing ceiling, quay ladders short, fire escape tree boughs.
Builder rules: a stair's foot reachable, top tread in the floor plane (mind the surface
heights above), a flight's opening never the only corridor.
