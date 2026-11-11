---
name: zomboid-stadium-tech
description: Grayhaven Stadium build (Stadium.java, LandmarkSite, stadium.py st_ kit): bands, ramps, bay kinds, county hook, walk-check fixes, preview commands and cams
metadata:
  type: project
  modified: 2026-10-27T10:21:55.045Z
---

Shipped to playtest 2026-10-27: f7dbc654 failed CI (capital letters in st_let_/st_big_ ids; the game refuses them, gen_blocks now refuses them too), fixed in 4b50b806, green and published 10:44. If no reply with the jar link reached Josiah, post the draft in project files zomboid-mod/previews/stadium/PENDING-REPLY.md. LANDSCAPE thread. Rules: [[zomboid-access-rule]], [[zomboid-photo-reference-rule]].

- Code: world/county/Stadium.java (bowl, club, concourses, facade, ramps, checkpoints, camp, roofStairs, cores), LandmarkSite.java (lot 400x450, paving, stalls, poles, avenue jams). Kit: tools/city/stadium.py (1,107 st_ blocks; register(ns) into photo_textures). Never rerun shop_loot.py wholesale.
- Geometry: field HX 45 x HZ 70, corner radius RC 24; everything is in bands b = edge distance (edge(px,pz)). Lower rows b 3..44 (two deep rising one), cross-aisle 45 (y CLUB 24), suites 46..55 (GLASS 48, SUITE_BACK 56), corridor to 62, lounges to 73, club concourse; upper rows 49..98 (UPPER 40), facade 99..100, roof y 76 from b 80. seg(px,pz) gives [segment, position]; suites 8 wide (k), lounges 24 (v).
- Ramps: corners at 67.5 deg, RAMP_PITCH 16/3, span() smooths with st_parapet_n / st_soffit_n.
- RULE (Josiah 2026-10-27 10:48): ONE stadium in the world, on the main city island (plan draft 3 puts it at 2400,-1100, west end). Interim County.stadium(): a single PlaceType.LANDMARK "stadium" a cell E or W of the downtown nearest the origin (playtest 1454b415); DONE 2026-10-27 (playtest 318339e7): in planned worlds Layout.lot() cuts a 6x7-block lot (80-block grid) at the pin; the blocks round it own its streets (reserve inset EDGE+4). Endless (old) worlds keep the interim one. /zc goto stadium works. ZombieSpawner dresses its zombies soldier 20% / patient 25%.
- Cars: lot stall fill 0.06 (about 146 cars total) for FPS; raise only if Josiah asks.
- Walk-check traps fixed: vomitory steps must top out flush with the walkway (f = WALKWAY+1-(b-11)); the aisle needs a step at b = first-1; doors in curved ring walls go through doorCell() (both sides open, no partition), never "k == n" alone; no 300-level rooms in front of stair cores (blocked()); corner drink rails only againstGlass(). Remaining jump-only pockets are rail tops and seat tops (harmless).
- Preview: SitePreview st1 landmark stadium 400 450 1 4; mesh with --near=204,40,229 --radius 260 (~2.5 min, 4.9M quads). Mesh coords = dump coords (place +4 x/z, +12 y). Cams: aerial 440,200,520 -> 204,20,229 fov 45; bowl 204,64,318 -> 204,36,140 fov 70; gate A 252,22,6 -> 204,16,52. Shots in project files zomboid-mod/previews/stadium/.
- Walk-check helpers (scratch, rebuild if gone): a walkcheck copy with --save npz, then slice/pocket/frontier/plan scripts to find where walking stops (edge from walkable to jump-only with rise > 0.5).

Open: stadium-vomitory-tunnel and stadium-exterior-gates ref folders were still filling; redo those details when they land.
