---
name: zomboid-farm-pens-tech
description: Farm pens for chickens and cows (2026-10-31, LANDSCAPE): new fm_ pen pieces, corrals and coops, FarmHerds chunk records and the animal/* block tags for SURVIVAL
metadata:
  type: project
---

Shipped 2026-10-31 (commit cdb1d9e1; merged to playtest as 96cad85e). This is part of [[zomboid-landscape-review-plan]]: Josiah said to start small, with chickens and one larger animal (cows). SURVIVAL owns the animals themselves ([[zomboid-landscape-contracts]]).

- **New blocks** (tools/city/farm.py, built from refs/farm):
  - fm_panel / fm_panel_post: galvanised corral panels (axis).
  - fm_stock_tank / fm_ring_feeder: round pieces, a quarter per block. Farmstead.quad(x,z) places four of them round a corner, facing north at (x,z), east at (x+1,z), south at (x+1,z+1) and west at (x,z+1).
  - fm_feeder: poultry tube feeder.
- **Farmstead.corral(w,d,cows):** pipe-panel pen with trodden earth, a tank and a feeder, a gate on the north side, and a herd. Corrals are on every dairy (14x12), ranch (16x14; ranches run cattle because there are no horses) and homestead (9x9).
- **Coops:** FarmSite.coop(x,z) adds a coop and run. Every homestead has one, 60% of crop farms and 50% of dairies. Each coop gets feeders and a chicken herd.
- **Pastures:** on dairy, ranch and homestead farms, a stock tank (60%) or the old trough, a ring feeder (60%), and up to 6 cows. The dairy barn holds 3 to 5 cows each side of the feed alley.
- **Herd records:**
  - BlockSink/Placer.herd(species, count, x0, z0, x1, z1, y, seed) emits a vehicle string "!herd:species;count;w;d" at the box middle.
  - ChunkWriter passes it to world.gen.FarmHerds.add, which turns the box with the frame's yaw and stores it on the chunk in the persistent attachment FarmHerds.HERDS (List<Herd>, with a spawned flag; done() marks it).
  - FarmHerds.register() is called from ZomboidCraft init.
- **Tags:** data/zomboidcraft/tags/block/animal/{feed_trough, water_trough, nest_box, hutch, coop_perch, milking_stall, pen_gate}.json. Butcher hooks added 08:5x (cbff0f9a): fm_butcher_hook hangs at the back of every stall/horse barn's drive, and Farmstead.smokehouse() (5x5, fm_meat_rail) is on every homestead and ~35% of crop farms; tag animal/butcher_hook. SURVIVAL keeps trough levels in its own data, so no level property is needed.
- **Preview:** `bash /tmp/claude-0/plan/farm/go.sh NAME dairy|crop|horse_ranch|homestead W D CX CZ R seed`. Mesh coords are dump coords + origin (-8,0,-8). Video: previews/farm-pens.mp4.
- **Note:** tools/city/blocks.json is now 54.9 MB. GitHub warns above 50 MB and hard-fails at 100 MB.
