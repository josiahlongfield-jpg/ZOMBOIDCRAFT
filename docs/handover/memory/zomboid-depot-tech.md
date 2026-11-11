---
name: zomboid-depot-tech
description: Country rail depots (DepotSite.java, rw_dp_ kit in railway.py) at Pinecrest/Millbrook/Fairview/Kingsbridge/Seabright; placement and gotchas
metadata:
  type: project
  modified: 2026-10-27T16:11:42.829Z
---

Country depots shipped to playtest 2026-10-27 (merge f221892d). Part of [[zomboid-rail-tech]].

- Layout.depots() runs right after railway.markTiles(places): for each RAIL stop in Layout.DEPOTS it fits a 44x24 lot (DEPOT_LEN/DEPOT_DEPTH) behind the platform on an axis-aligned GROUND straight; rejects water, river, other places within 16, other lines within 8. Sets Track.depots bit so RailGen draws the depot canopy instead of fence/shelter (DEPOT_REACH 22, canopy 19).
- Place is PlaceType.ROADSIDE variant "depot" (MILITARY also uses variant "depot", so check the type too). Builders sends it to DepotSite. Place level = Y-1: lot y0 is the yard, y1 floor/platform paving.
- DepotSite lot coords: X0 4..X1 39, front wall z4 (track side), back z13, rooms split at x17 and x24 (waiting / office with bay / freight). waterSide = track side so the spur road comes in the back or end gates.
- Blocks: rw_dp_* from tools/city/railway.py (rerun railway.py then gen_blocks). Walls must be named rw_dp_wall* so the Builders WALL regex matches. Semaphore blades are split boxes (UV stretching put the band on the wrong end).
- Previews 23-31 in previews/rail/; dumps via a CountyPreview dump script (Millbrook lot -4773..-4730/-1031..-1008 at seed 12345, Seabright 3507..3530/5527..5570).
- Halts (d1d56467): a country stop with no plan road within 90 (Layout.roadless) gets a 36x14 car park lot (variant "halt", HaltSite: stalls, wheel stops, cinder path to the fence gates at |da| 14..15) via the same Layout.lot(); links() spurs a county road to it. At seed 12345: Fort Caldwell, Dunmore, Airport. HaltSite only draws the back drive if a road ends at the back gate.
- Cars from kit.car are not in CountyPreview dumps (no cars key), so previews never show them.
- Not done: other stops still have the plain shelter; no new /zc commands.
