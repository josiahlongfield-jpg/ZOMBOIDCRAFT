---
name: zomboid-arena-tech
description: Harbor Arena (island landmark at 3640,-480) build notes - Arena.java/ArenaRooms.java, ar_ kit in arena.py, preview coords and checks
metadata:
  type: project
---

Harbor Arena, the Gulls' hockey arena, BUILT 2026-10-28 by the LANDSCAPE thread (Layout lot 3520..3760 x -560..-400, no rail lines cross it).

- Arena.java (extends Grounds): bowl from a rounded-rect band distance (`band`, `tread(b)`); event floor EV=-10 sunk under the street, concourse CONC=0, upper floor UP=13, roof 30. Four stair CORES with roof huts. Facade uses hash-coherent precast weathering (most panels plain), so keep it calm.
- ArenaRooms.java: back-of-house ROOMS round the event level (garage+resurfacer, ice plant, kitchen, locker suites with drop ceilings, aid, isolation morgue, press, security), concourse units (ar_ stand headers for grill/nacho/store, st_ for others), overflow cots in two corner lobbies (`lobby()` keeps them off doors/stairs), the shelter's cots on plywood over the ice.
- Kit: tools/city/arena.py (ar_, about 340 blocks: signs, ads, ribbon, marquee, scoreboard, resurfacer cells). Registered in photo_textures.py. Same kit pipeline as the others; gen_blocks removes orphans.
- Preview: `SitePreview DIR landmark arena 213 133 SEED 8` (dump = site+8, y = site+12); meshes use site x/z, y+12. Cams: concourse 80,14.6,18 to 130,15,18; rink 86,6,58 to 120,2,72.
- Checks on seeds 1-3: 0 blocked doors, 0 head-knockers, 0 blocked treads; 10 corner st_rail pieces turned (harmless).
- No reference photos existed; redo from photos when arena refs land. See [[zomboid-photo-reference-rule]], [[zomboid-stadium-tech]].
