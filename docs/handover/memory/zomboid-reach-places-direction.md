---
name: zomboid-reach-places-direction
description: Reach world place decisions: New Alexandria/Exodus landmark towers + ground floors, Lone Wolf by the Pillar of Autumn, PoA walkable
metadata:
  type: project
  modified: 2026-11-09T20:23:23.305Z
---
Split from [[zomboid-reach-world-direction]] 2026-11-09.
- NEW ALEXANDRIA (Josiah 2026-11-09 02:32): city must be thick with buildings like the game. Plan told: game models drawn; city render meshes voxelized into walls (streets, shells, roofs); mission interiors enterable; swap thin skybox towers for detailed copies if needed (in PC plan file). DECIDED 02:36 (card): ~12 landmark towers get full interiors to the roof built from Reach interior pieces; every other building gets an enterable ground floor (lobby/shop/garage). Josiah: city must be highly detailed, imposing, elaborate, not scattered buildings on green ground. EXODUS (02:39): mission district is already walkable with Halo interiors; its far skyline gets the same treatment (landmark towers + ground floors), on the same shortlist.
- Josiah 2026-11-09 02:40 LONE WOLF: m70_bonus_epilogue (small ~230 m arena Halo hides ~1 km under m70, halo x1315-1547 z1450-1684 y-924..-762) must sit near the Pillar of Autumn; plan: lift to surface on high ground overlooking the PoA dock; show on climate/coast map.
- Josiah 23:27 PoA WALKABLE: Reach PoA hull at Aszod dock + interior rebuilt in Reach look from CE a10/d40 deck plan (plan file). Package underground lab (m20_042/060/080, ~1 km deep) sits under its valley.
- TOWER SHORTLIST (04:05, build log Preview 6): NA 1 ONI Bldg, 2 Ivory Tower, 3 Hospital, 4 Club Errera, 5 Arch (-885,-787), 6 Red-panel (433,391), 7 Aluminium spire (203,-451), 8 Vent (-1094,291), 9 Triple (-870,750), 10 Twin (548,-963), 11 Searchlight (-963,948), 12 Supertower (-2132,876); Exodus E1 Hologram (183,6), E2 Triple-crown (-121,-223), E3 Traxus (956,917). Halo coords. Interiors from ACTUAL mission floors (NA m52_*, Exodus m50_*), not Sword Base; mixed/mirrored, some wrecked, lifts+stairs.
- 05:40: floor-layout LIBRARY from interiors across ALL Reach missions, every tower varied floor to floor; shortlist taken as OK.
- PREVIEW 10 (#tower-floors): library 96 floors from 13 maps; tower fill = lift/stair core + rooms around it (rotate/mirror, no repeat within 5 storeys). Approved 07:00 ("looking good"). Scratch tools floorlib.py, towerfill.py, towerview.py.
- Josiah 2026-11-09 07:00: tower levels "looking good" (Preview 10 OK). THE PACKAGE ACCESS: replace the shore boardwalk with a LEDGE ROAD cut into the cliffs (zig-zag up from shore, short tunnels); underground lab reached by the facility's own lift (keep it). Show in next layout update.
- MULTIPLAYER MAPS: all 29 exported in PC additions\halo-reach\world\<map>, not yet in reach.json. Placement rules + Update 14 spots: [[zomboid-reach-mp-placement]]. Grand designs: [[zomboid-reach-grand-designs]].
- Josiah 2026-11-10 03:26 PoA INTERIOR: layout from Halo CE a10/d40 (collision already in cloud /tmp/claude-0/halo/ce), LOOK mapped off Halo 4's Forward Unto Dawn interiors (more up-to-date Halo ship interiors; he OK'd the 343 style for this). Needs Halo 4 Dawn mission map (m10_crash?) staged read-only from PC; Halo 4 sbsp (level geometry) not decoded yet (h4 tools read models/bitmaps/anims only).

- Towers/scenery 2026-11-10 decisions: [[zomboid-reach-towers-direction]].