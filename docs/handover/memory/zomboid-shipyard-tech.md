---
name: zomboid-shipyard-tech
description: Pelican Point Shipyard (coast group site 1) build tech: FrameSite tiles + dredged basin, sy_ kit, dock/slip/halls split, preview + leak/orient checks
metadata:
  type: project
  modified: 2026-10-30T11:26:22.261Z
---

Built 2026-10-30 by LANDSCAPE (coast-and-industry group, see [[zomboid-landscape-queue]]). Refs: refs/shipyard.

- Frame: Shipyard.java (FrameSite, like [[zomboid-camp-black-pine-tech]]). RX 5880, RZ 1512 is the quay line, rot 0 (world = RX+x, RZ+z). Land x -300..299, z 0..447, level WATER+3 (65). 3 basin tiles north (z -200..-1, waterSide 0, quay 200) are dredged to Ships.BED.
- GOTCHA: FrameSite.tiles returns NOTHING if any 13x9 sample is water (g < 63.5). The first try at RZ 1500 hit 63.4. Also keep highway US1 >24 blocks from the tiles or Layout breaks the road.
- Road: county_plan `road shipyd ky - 5900,2000 5880,1930` reaches the south gate. Plan pier line is `pier shipyard 135,-489 135,-660 12`.
- Split: Shipyard.Yard (ground, roads, quay, office, canteen, car park, services, forecourt with propeller monument, fence, gate, story); ShipyardDocks (dry dock with a tanker on keel blocks + shores, caisson, goliath crane "PELICAN POINT", slipway hull via BigShip.onSlip, tower cranes, outfit jetty + container ship); ShipyardHalls (panel line hall A, block assembly hall B with barrel-vault roofs, staging yard of ship blocks, crawler, plate yard, blast shop, pipe shop, west yard).
- BigShip additions: onSlip(len,beam,deck,built) is a part-built hull; block(...) is a hull section.
- Kit: tools/city/shipyard.py, 222 sy_ blocks, registered in photo_textures.py; `python3 tools/city/shipyard.py` rewrites blocks.json. Signs are the SIGNS dict (sy_sgn_KEY_0/1).
- LESSON: Camp.fence(x0,z0,x1,z1) draws ONE straight run and fills the whole box, so a rectangle must be four calls (Yard.ring does that).
- Missing names that broke the mesher: Doors.GLASS, pw_insulator, sc_locker, asphalt_line_white (use road_line_white[axis=]), and gable families must be vanilla-style ("minecraft:andesite").
- Preview: /tmp/claude-0/plan/ship/go.sh NAME CX CZ R (dumps w/h/s/e). Leak scan: /tmp/claude-0/leak.py DUMP (water next to air) gave 0. orientcheck only flags sy_panel, a false positive: it's a floor-laid panel whose name contains "pane".
