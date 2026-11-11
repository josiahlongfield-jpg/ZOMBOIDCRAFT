---
name: zomboid-ballpark-tech
description: Grayhaven Ballpark tech (Ballpark/BallparkRooms/BallparkLz, bp_ kit, preview coords and checks); built 2026-10-28
metadata:
  type: project
---
Grayhaven Ballpark ("home of the Skippers") on Grayhaven island, lot x 2414..2626, z -626..-414 (Layout "ballpark", 3x3). Built 2026-10-28 by the LANDSCAPE thread, no ref photos yet (redo when photos land, see [[zomboid-photo-reference-rule]]).

- Ballpark.java (extends Grounds): HX=150,HZ=62 home cell; 1B line runs west, 3B line south. Bands by distance b from the foul line: low rows, concourse b19..23, unit rooms b24..35, facade b38; upper deck b15..33 over the corridor, roof b>=22. Cores {20,50,80} per side, gates {33,63}. Fence = 96 + 24*sin(2θ)^1.3.
- BallparkRooms.java: event-level rooms behind the stands (clubhouses, cages, grounds, laundry, armory, press, kitchen, office, aid, morgue), concourse units (stands, restrooms, store, aid), round ops hall behind home. Concourse vending skips any spot in front of a unit door (seeds 2/3 had blocked doors before).
- BallparkLz.java: field turned into a military landing zone: two helipads, heli 60%, triage tents, hesco, nest, body bags along 3B; lawn with trees outside the outfield, ramp and plazas.
- Kit: tools/city/ballpark.py (bp_, 230 blocks, registered in photo_textures.py after arena). bp_tarp is axis and drawn along z.
- Preview: `SitePreview DUMP landmark ballpark 213 213 SEED 8`; mesh y = site+12; cams in /tmp only. Checks: walkcheck/headroom/orientcheck/palcheck clean on seeds 1-3 (only jump pockets on stall tops).
