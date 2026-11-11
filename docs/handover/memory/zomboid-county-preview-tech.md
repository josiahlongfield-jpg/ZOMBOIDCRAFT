---
name: zomboid-county-preview-tech
description: Previewing and checking the planned county offline (CountyPreview at world coords, mesh origin overflow fix, LOD, fog, endless flag, one-of-every-place sweep)
metadata:
  type: project
---

Tools in tools/county; see also [[zomboid-world-tech]], [[zomboid-access-tech]], [[zomboid-county-plan]].

- Compile: `javac -nowarn -d BUILD src/main/java/com/zomboidcraft/world/city/*.java src/main/java/com/zomboidcraft/world/county/*.java tools/county/*.java`, run with `-cp BUILD:src/main/resources` (the plan file loads from resources).
- `-Dcounty.endless=true` on CountyPreview, CountyMap, ListPlaces gives the old endless county; default is the plan.
- Island and core sit at world x 2000-4500: mesh.py stores int16 1/16-block positions plus the origin, so dumps there render blank. Rewrite the dump json "origin" to [0, y0, 0] (keep "world_origin") and use local cams (ground at CITY 66 with y0 50 is y 16).
- City dumps mesh to 6-9 M quads; use `--near X,Y,Z --radius 60` so the rest is LOD. The viewer takes `&fog=0.0012` for clear aerials (default 0.0045 hides everything past ~300).
- vanilla_blocks.json in the scratch tex folder lacked minecraft:clay (river beds); add it or mesh.py dies with KeyError.
- One of every place: ListPlaces 12345 9000 lists the nearest of each type/variant; dump each with CountyPreview (radius half its size + 24, y0 10, height 300), then run orientcheck.py and walkcheck.py on the dumps. 2026-10-27 sweep: 0 sideways windows after the fix. Walkcheck pockets in tiled downtown (pack tower interiors, flat_door rooms) are on the same scale as the old county's downtown, so not caused by the tiling.
