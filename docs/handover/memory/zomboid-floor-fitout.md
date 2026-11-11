---
name: zomboid-floor-fitout
description: Every tower floor fitted out by building purpose, hotel towers, slim one-lift core, shared Stairwell (4 clear over every step); BUILT and in playtest 2026-10-28; tech notes + preview loop
metadata:
  type: project
---

Ask (Josiah 2026-10-27 22:06): every level of all buildings and towers kitted out in detail, no identical reprinted floors, "Everything needs purpose". Towers over 10 floors: core = ONE lift + ONE full-height stair. All floor types approved if they fit the purpose ([[zomboid-floor-purpose-rule]]). Hotels: intricate floors, penthouse, glass-bottom pools, sky nightclubs; he allowed rebuilding towers for it.

Status 2026-10-28 00:07: merged into playtest (branch claude/project-thread-l1uzx6). Preview page: https://claude.ai/artifact/Xwb4WB9cEAjktp5mzaNBsS (ideas page: https://claude.ai/artifact/Hd12DbkzkbNz84KTff3qMy).

Tech:
- world/city/Floorplan.java (cell grid, corridor/rooms/biggest/place keep connectivity; fixDoors + joinUp make every door reachable; glass room walls: posts at junctions/isolated cells) and Floors.java (Purpose -> floor program, Fitter per Type, zone(min,cells,max[,overWalk]) patches, groups, aftermath litter: bulky litter via place() and never beside a door).
- Slim top floors of setback towers are only ~4-5 wide round the core: zones fall back to overWalk (take the corridor). The dance floor zone must run AFTER rooms (rooms reset CLEAR cells).
- Hotel: Tower.hotelLobby, poolDeck = glass-bottom lap pool along the front hanging min(3,in) past the parapet; crowns mast/tank only.
- Stairwell.java: shared fire stair for Tower, PackTower, county Garage. Flights 3-5 steps alternate west/east lanes (lane repeats every 2nd flight => >=6 over a tread), far row cz0+8, bridge down the middle when a floor is reached at the far end. Plant room/roof hut +1 tall; PackTower capTop() when no hut; Tower stepped crown starts at roofY+FLOOR+2. Garage picks eastFirst so the lobby's first flight is west.
- of_ kit (31 blocks): tools/city/fitout.py, registered in photo_textures.py. Loot thread owns container contents (of_dj_booth is loot storage).
- Merge tip: generated files conflict (CitySpecs, lang, city_blocks_motion tag, blocks.json); take playtest's, rerun fitout.py + gen_blocks.py on a copy of the repo city textures, and append new lang keys instead of rewriting the file.
- Checks used: scratch head.py (cells 1-4 over every stairs block), walkcheck, orientcheck; PackPreview names tower:W:D:F, -Dzc.purpose=HOTEL, -Dzc.floors=TYPE, -Dzc.floordebug=true prints each floor's type + grid.

Known weak spots: executive floors look sparse; penthouse on slim top floors is mostly one open room; PackTower hotels get no pool/lobby (generated Tower only); industrial floors only belong in industrial places.

Core deal with LIFTS thread: this thread owns core geometry, LIFTS owns LiftBuild. Related: [[zomboid-packtowers-tech]], [[zomboid-lifts-direction]], [[zomboid-access-rule]], [[zomboid-photo-reference-rule]].

## Penthouse (2026-10-28, in playtest 6d45f05f8)
- Josiah 00:25: penthouse must be extravagant, ok to knock out exterior walls. Built: hotel towers with PENTHOUSE on top (and cx0,cz0>=6) force setbackFloor=floors, so the penthouse steps in 4 and Tower.terrace() fits the ring (of_deck, glass rail, shop doors where both sides clear, lane row by the glass kept clear via out()).
- New kit in fitout.py "penthouse and its terrace" (of_deck, out_sofa, daybed, fire_pit, bbq, out_counter, parasol, tree_pot/crown, pergola, telescope, grand_piano_l/r, art_a/b, sculpture, screen, island). Loot: of_bbq/of_out_counter use loot "grill", of_island "home_cupboard", of_dj_booth "dj" (set by the loot thread in fitout.py, playtest 8c9d96466); keep them when editing fitout.py.
- Floors.penthouse: slim floors (deepest<8) force f.band=3; rooms via list() so the master comes first; byGlass() lays the great room off SHELL-adjacent cells (f.window is false before the facade exists!).
- Fixed: Tower.scatter rubble blocked the stair door in big (42x36) hotel lobbies; luggage carts now go by walls.
- Paused by Josiah 01:17 with the other non-landscape threads. Renders in /mnt/project-files/zomboid-mod/previews/penthouse/.
