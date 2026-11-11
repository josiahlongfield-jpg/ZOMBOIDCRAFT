---
name: zomboid-packtowers-tech
description: How the bought 20-buildings pack becomes ZomboidCraft downtown towers (tools/towers/towers.py, PackTower.java), how to preview and debug their interiors
metadata:
  type: reference
  modified: 2026-10-26T18:48:39.186Z
---

Built 2026-10-26 in the LANDSCAPE thread (overnight mandate). Pack originals stay at /tmp/claude-0/packs/pack-of-20-buildings (TowersJJ.fbx, cm, y-up; 20 "Estructure" models) and never go in the repo (see [[zomboid-bought-assets-plan]]).

**Pipeline**: `python3 tools/towers/towers.py PACKDIR [--only N,..] [--preview DIR]` (~6 min for all; writes src/main/resources/data/zomboidcraft/packtowers.bin only without --only, and always rewrites tools/towers/tex/pt_*.png + the pt_ entries in blocks.json). Then `photo_textures.py cc0 /tmp/claude-0/tex $(ls tools/towers/tex names)` and `gen_blocks.py`. Per-building pitch/G overrides and material maps live in towers.py (BUILDINGS, MATS, RECIPES). Template cells: 0 outside, 1 inside, >=2 palette; markers @ground @slab @lamp @doortop @door:kind:facing:hinge. Lamps are set flush into a plain pt_ wall cell next to where the model had them. ensure_door finds the first wall with rooms behind it (skipping yard walls, knocking a gate through them).

**Java**: world/city/PackTower.java (Template record, pick(), build()). DowntownSite places them (tower 55%, frontage flats 40%, whole-front landmarks). Fit-out: groundFloor finds the real front wall (wallZ), shop units per side as deep as the floor runs clear (side()), racked stockrooms and flats in leftovers() (greedy biggest free rectangle, door onto the side with most open floor, walls only on the building's own shell or on the rect's edge so corridors and the core stay open). kept[][] doubles as the claimed-floor mask. Offices: desk pods anywhere free.

**Preview**: `java -cp build/county:src/main/resources PackPreview OUT e8,e3 SEED [decay]` (ground = dump y 12, mesh x/z = world). Cutaways: /tmp/claude-0/pv/tw/crop.py DUMP OUT x0 z0 x1 z1 [ymax] (set the crop's origin small, e.g. [0,0,-4], or the mesher fails on big coords), then site_shot.sh. Plans in text: /tmp/claude-0/dbgj/TwDbg.java prints each level's inside map.

**Gotchas**: mesh.py dies on a block missing from blocks.json (and rshot then hangs forever); never `pkill -f` a pattern that also appears in the same shell command (it kills itself, exit 144). Civic kit cv_ blocks: [[zomboid-civic-tech]].
