---
name: zomboid-cars-plan
description: ZomboidCraft car models: 80s American cars pack, licence rules, pack/wreck tools, boots, hull (thread "Make the cars drivable")
metadata:
  type: project
  modified: 2026-10-25T14:14:47.293Z
---

Josiah wanted real 3D cars, roughed up, instead of block-built ones whose stock colours were "way too bright". See [[zomboid-mod-status]], [[josiah-world-art-direction]]. Driving, sounds, safety and lights are in [[zomboid-cars-driving]].
- Pack: CGTrader "american-cars-80s-pack" (29 GLBs, metre scale, front = +Z). The licence is Royalty Free (no AI, no redistribution). The originals live only in project files zomboid-mod/cars/source/. Never commit GLB/FBX/OBJ to the public repo; ship only the scrambled .zcar files. CREDITS.md has a Vehicles section.
- Tools: tools/cars/wreck.py (damage variants usable/abandoned/wrecked/burnt, per-car atlas), pack.py (writes .zcar format 3 plus data/zomboidcraft/zc_cars/models.json with seats as eye positions, driver at +x first, and wheelbase), and car_loot.py. lamps.py must be rerun after pack.py. Each model takes about 25 s.
- He wants mostly usable or parked cars, few wrecked, rare burnt (configurable), and car parks both open and multi-storey: Props.vehicle(x, y, z, yaw, minLen, maxLen).
- Boots open physically (lids in .zcar) and show themed contents; loot comes from loot_table/car/*. CarHull blocks use quarter-block heights so you can shoot over cars. PRs #1 and #2 are merged; he said "thats all good".
- Smoke test gotcha: after forceload, wait several seconds before an @e selector can see a summoned entity.
