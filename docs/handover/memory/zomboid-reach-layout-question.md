---
name: zomboid-reach-layout-question
description: 2026-11-11 21:12 his clock: Josiah chose to PACK the Reach missions together so their real Halo land meets (world shrinks, our land only seams)
metadata:
  type: project
  modified: 2026-11-11T11:11:46.406Z
---
Josiah 2026-11-11 21:07-21:11 (his clock), after the in-game tour showing invisible areas / NA unwalkable: asked if a smaller world with missions closer would help, then "im just concerned we wont ever be able to get such a large map to look consistently as if im walking through the reach game itself".
Measured (land v5, 4 m cells, 16x16 km): Halo-made walkable ground ~75 km2 (MISSION 56.6 dry + FOOTPRINT 18.2), our generated LAND 142.5 km2 dry (+ sea).
Card posted (21:11): A pack missions together so their real _000 land meets, our land only thin seams, beyond = painted backdrops [recommended]; B keep size, improve our land; C keep size, fence our land off (travel by Pelican). PICKED A 2026-11-11 21:12 (his clock): pack missions together. Next: map each mission's real Halo land reach, packed layout map to him BEFORE rebuilding.
Josiah 21:13: ALL areas must stay playable, NO invisible walls, and plenty of room for Scarabs and all sorts of vehicles to roam (he's adding them). So: pack only to close gaps; each mission's broad _000 land made walkable/drivable; world edge natural (sea, unclimbable ranges) with painted backdrops beyond.
**How to apply:** if A, redo placement (reach.json offsets), land.zrh, farMasks, city zrvs and audits; preview before install. Draw/walk fixes (v5 land, city collision, fog 80%, clouds, NA cards) are needed either way.
Related: [[zomboid-reach-backdrops-direction]], [[zomboid-reach-real-surroundings]], [[zomboid-reach-whole-world-quality-rule]].

- 2026-11-11 21:25 his clock: layout draft on preview artifact YbkLMF1VC3VepFf65oNWmd#layout (v8): packed ~11 km across, Halo ground ~175 km2, ours ~8. Halo walking-detail (<150 m tris) only 1.5-3 km round each mission; beyond = coarse far land (plan: smooth+detail it). Tools /tmp/claude-0/pack (farland.py rasters <m>_far.npz, pack.py, layout.py). Awaiting his OK on the map.
- Interim install 21:27: live world\land.zrh = v5 347ae980 (draw/walk audit fix, scratch holefix/v5.py), new m52_far.zrv 4095fa00 + m50_far.zrv e2049696 (cityvox.py, city _000 collision); backup jar-backups\land-before-place5.zrh. Jar 55e0412 (clouds, NA cards, fog 80%) install via RC requested.
- 21:35 his clock: Josiah OK'd the packed layout ('layout can go ahead'). Real size ~14 km across (I first said 11 km by mistake; corrected on pages + message). Per-level moves (x,z blocks, multiples of 32) in /tmp/claude-0/pack/deltas.json: m10/m05 [832,-864], m20 [-640,576], m60 [-2432,1856], m35/m30 [1600,-2624], m52 [-1664,-544], m50 [0,-448], m70/m70_bonus [128,-1536], m45 [-1856,1088]. Plan: shift each level's old surroundings (land, footprints, farMask, zrv chunk index, copies) by its move, ~250 m blended seams between territories, redo sky_regions.zsk + ReachSkySets bearings, then draw/walk audit (v5).
- Josiah 21:40: 'prioritize the detail, with less open land and more focus put on the physical campaign missions ... a like for like walkthrough of the reach game'. -> pack TIGHTER round each mission's real playable ground (footprint + Halo walking-detail land); coarse far land = backdrop beyond the edge. Revised map to him before rebuild.
- 21:43 APPROVED tight layout (artifact UoKbaJE3VsM1psx8cTCxTT v3, ~11x10 km, play ~95 km2) 'so long as you can make it flow from one area to another'. Moves in /tmp/claude-0/pack/deltas_tight.json (core.json centres, r80 radii). Builder /tmp/claude-0/pack/packland.py (SEAM 160 m) + drawn2.py ALL=1 rasters in pack/ras. Joins must blend ground, climate, textures; preview joins before install.
