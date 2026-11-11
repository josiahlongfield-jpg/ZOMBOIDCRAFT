---
name: zomboid-harbour-fort-tech
description: LANDSCAPE's harbour fort hideout (round sea fort in the bay, ARMY bunker under it): code, kit, location, preview (2026-11-06)
metadata:
  type: project
  modified: 2026-11-06T06:20:13.840Z
---

Commit 8c03b3810 on claude/project-thread-d61kbn, CI green, in playtest 016046e89 (2026-11-06 06:35). This was the last item of bunker wave 2.

- **county/FortSite.java:** HIDEOUT "harbour_fort", one per county, the same pattern as OffshoreSite.
  - County.harbourFort() caches the place, and hideoutsNear includes it.
  - Builders dispatches it. Bunkers.KINDS has "fort", plus candidates and fortName(). There are /zc bunker texts and a PaperMaps hint.
- **Spot:** the search runs over the sea in 40-block steps, keeping 1500 in from the sea polygon's map edges.
  - Kept: coast -160..-420, depth WATER-7..-22, clear of islands, ships, roads, rails and places.
  - Picked: the spot with the most land in 16 bearings.
  - In the Grayhaven plan this lands about (4779, -3580), in the bay off the city.
- **Frame:** local +x faces the nearest shore (the landing).
  - Heights: LOW -3 (magazines), GUN 2, ROOF 8, sea top -4. R=22, WELL=6, place HALF 58.
  - 12 casemates at 30 degrees, a port in each. The old guns are in bays 3 and 9, and the hatch is under bay 6's magazine.
  - The bunker is ARMY, floored 9 below the lowest seabed.
- **Kit:** tools/city/harbourfort.py (hf_). Textures: hf_granite, _tide, hf_armour, _streak, hf_port_lo/_hi, hf_iron, hf_carriage. Blocks: hf_granite, hf_gun/_muzzle, hf_racer, hf_shot.
- **round.py:** WALLS hfg, hfgw, hfa, hfas, hfpl, hfpu (216 rd_ panels).
- **Preview:**
  - Sheet: previews/harbour-fort.jpg.
  - Pipeline: CountyPreview from build/zcp `CountyPreview OUT 12345 4779 -3580 70 30 60`, then vanilla.py, then mesh.py `--near 4779,35,-3580` (the WORLD coords; the mesh's x/z wrap mod 4096, so shot cams use 683,y,516 for the fort's middle).
- **WARNING:** tools/city/blocks.json is now 55 MB. GitHub warns over 50 MB and rejects files over 100 MB.

Related: [[zomboid-bunker-wave2-tech]], [[zomboid-ship-tech]].
