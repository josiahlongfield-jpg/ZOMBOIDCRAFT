---
name: zomboid-reach-priority-order
description: Josiah's priority order for Endless Night Reach world (2026-11-10 03:14): campaign areas 100% first, then all MP maps, then in-between land/grand designs
metadata:
  type: project
  modified: 2026-11-10T03:14:30.593Z
---
Josiah 2026-11-10 03:14 (thread "Build the Halo Reach world"): "We need to prioritise getting the campaign areas 100% finished to the highest quality we can achieve and then immediately prioritise all multiplayer maps with the same level of care".
**Why:** he asked 03:06 whether campaign and MP locations were 100% finished; answer was no (world2 re-export not live, NA towers/ground floors, walkable PoA, Package cliff road, Lone Wolf move, in-game water all unbuilt; MP maps exported but not in the world; in-between land preview-only).
**How to apply:** campaign missions first, highest quality, then all 29 MP maps with the same care. Grand designs (Firebase Corsair parked at night-lighting stage, landv/corsair.py, landn night bake) and other in-between land wait until these are done unless he says otherwise.
Related: [[zomboid-reach-grand-designs]], [[zomboid-reach-places-direction]], [[zomboid-reach-mp-placement]], [[zomboid-reach-world-tech]].
- Josiah 2026-11-10 08:11-08:12: "the entire map needs the exact same intricate detail" (land between missions to mission density, Reach terrain materials, scatter). Order confirmed by card 08:12: finish missions (m05/m30/m45 placement, water, PoA, ledge road) + place all 29 MP maps FIRST, then the land.
- 2026-11-10 08:27 APPROVED (card, preview page Update 17 #place3): Nightfall m30 in Tip of the Spear's own frame (offset = m35's [-4299,0,3102]); shared 0.16 km2 strip keeps Spear's version (m30 pieces masked there), m35_001 far land masked out under Nightfall, m30_000 left out. Sabre ground (m45_001..015 + _000) offset (1423.2, ~377, -1462.4) = centre (1500,-2250) per MP plan. Noble Actual m05 (bsp m10_008) yaw 90, offset (-6500, ~2681, -3800), core box x -1300..1450 z -650..450. Mod: level "leave", "cut" {piece:{box,mask}}, "propsMask" (level-local coords). Tools scratchpad m30/ (fit.py, shared.py, compose.py).
- 2026-11-10 08:55 INSTALLED on PC (live config reach.json + places.json; world\ m05/m30/m45.zrv new, m35.zrv redone without m35_001 under Nightfall, land.zrh patched: footprints + Laplace-eased edges). Backups additions\halo-reach\backup\*.before-place3. Needs jar with commit 2d414eb3e (leave/cut/propsMask) or m45's corvette pieces draw at Sabre. Source files: /mnt/project-files/endless-night/reach-world/transit/place3/, scripts in reach-world/places/ (place3.py, landpatch.py, spots3.py). Exposure guesses m30 2.0, m45 2.1, m05 7.65. Old chunks keep old walls: fresh Reach world best.
