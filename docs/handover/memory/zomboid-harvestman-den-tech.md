---
name: zomboid-harvestman-den-tech
description: Harvestman den (hd_ block kit, HarvestmanDens carve/bodies/setOut), posed corpses (Corpse.pose SAT/SLUMPED/HUNG, client ZPosed), scrap working in REST, /zc harvestman den (2026-10-28)
metadata:
  type: project
  modified: 2026-10-28T10:53:13.471Z
---

Built 2026-10-28 on branch claude/project-thread-wfvzb7 (Josiah picked "mind games" + "proper den").

- Blocks: tools/city/harvestman_den.py (hd_ prefix, registered in photo_textures.py after market). hd_heap/_b/_tall/_low, hd_nest/_b, hd_debris/_b, hd_bones, hd_drag (axis), hd_gouges_0..2 (wall plate, facing = away from wall), hd_cable/_top/_end, hd_cocoon(+_top). Textures are procedural (no photos): `photo_textures.py ANY_DIR OUT hd_...` then copy textures/block/city + new pngs into a dir and run gen_blocks.py on it.
- creature/HarvestmanDens: carve(level, lair, from) tears out non-full non-structural blocks within 4.5 (keeps stairs/doors/windows/rails/etc. by name), nest r<1.6, debris ring, heaps on wall-adjacent cells (never doorways/corridors/next to doors), gouges up the walls, drag trail toward `from`. bodies() = cocoon 60% + 3-6 kept corpses, first two 70% hung. setOut(level, body, at, from, hangChance): hang (needs ceiling 3-7 up, cable cells, neck at floor+2.05) / doorway (jambs + lintel) / against wall facing `from`. scrap()/work()/use(): heap shrinks one size per regrown leg.
- Director.makeDen(level, floor, from) = record + furnish + materialize resting; tryLair and `/zc harvestman den` (which also takes one leg so it works scrap) use it.
- Mind: leave_body ends with setOut (35% hang) toward the target's position; REST with a lost leg walks to the nearest heap, rears and stabs (rearWant pulses every 50 ticks), sparks + clang, scrape every 260 ticks.
- Corpse: synced POSE (FALLEN 0, SAT 1, SLUMPED 2, HUNG 3, saved "pose"), keep(); HUNG skips falling. CorpseRenderer: posed ones use ZPosed.pose + entity yaw (not Deaths.yaw), hung sways/turns about ZPosed.NECK (1.68), culling box raised.

Related: [[zomboid-harvestman-game-tech]], [[zomboid-corpses-tech]], [[zomboid-harvestman-direction]].
