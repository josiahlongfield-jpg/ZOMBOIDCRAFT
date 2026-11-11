---
name: zomboid-glass-tech
description: Shootable glass tech: GlassShots for building glass, CarGlass per-window car panes (holes at exact u,v, wind down with V), glass_marks texture, preview tool
metadata:
  type: project
  modified: 2026-10-28T00:54:14.576Z
---

Shootable glass (VEHICLES thread, built 2026-10-28; Josiah asked: all glass shot through with bullet holes, shatter after N by type/size, car windows wind down, glass must be visible).

**Building glass** — combat/GlassShots.java. Guns.fire re-clips past each glass block (up to 6), bullet keeps ×0.85 per pane, holes are Impacts decals (impacts.png cells 5-6), per-block hole counter (forgotten after a day), shatters via BashGoal.broken (now public) or destroyBlock. Config guns.glassHolesSmall/Window/Large/Shop/Block.

**Car glass** — world/cars/CarGlass.java + CarProp:
- tools/cars/glass.py packs per variant per pane: centre, outward normal, half axes a/b, kind (0 windscreen, 1 side, 2 rear, 3 other), hole count, convex outline in u,v. Also glassTris (window of each glass triangle per mesh: body, then lids). Needs a full pack.py repack when panes change (aircraft keep no windows). CAUTION: pack.py keeps only kind "jet" rows, so a rerun drops LANDSCAPE's boats (kind "boat", tools/cars/boats.py): rerun boats.py after, or keep "boat" rows in pack.py.
- CarProp synced STRING GLASS: per window "0/1/2" (up/down/shattered) then marks as 2 letters (64 steps), a crack prefixed '*'. Saved as "glass"; bash HP per window "glass_left". SMASHED now means all shattered. Empty text = from the look (null panes shattered).
- Guns: hull block hit → CarGlass.throughHull (window met within 0.9 m of the strike → continue past the car); then CarGlass.along holes every closed window crossed.
- Zombies: blowAtRider(rider, attacker, amount) bashes the window facing the attacker (cracks at each third), reach only when that window is open/gone AND rider eye within 1.6 m. shielded(rider, from) per zombie (Grabs).
- V (shove key) in a car sends CarNet.WINDOW → seatWindow (side window on the seat's side nearest the eye). Client eases CarProp.wound over 2 s.
- Draw: client/cars/CarGlassDraw at LOD 0 only: skips shattered, slides lowered tris toward the sill, cut-out marks (textures/cars/glass_marks.png from tools/cars/glass_tex.py: dust sheet, 2 holes, 2 cracks, solid edge patch) drawn inside too, top-edge line while winding.
- Preview: scratchpad glassprev/prev.py (uses interior/v2.py raster) → /mnt/project-files/zomboid-mod/cars/car-glass-preview.png.

Related: [[zomboid-cars-driving]], [[zomboid-guns-tech]], [[zomboid-window-climb]].
