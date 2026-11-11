---
name: zomboid-round-kit-tech
description: rd_ round-wall kit (tools/city/round.py + Round.java): smooth silo/bin/tank drums, stepped domes and cones; Farmstead silo/grainBin use it; farm preview yard
metadata:
  type: project
  modified: 2026-10-29T06:03:43.660Z
---

Since 2026-10-29 (LANDSCAPE, commit adbb5081), part of the [[zomboid-consistent-detail-rule]] pass:
- tools/city/round.py writes rd_ pieces: a 3 px panel per block the circle passes, turned to the tangent (turn 0/m22/p22/p45 x 4 facings = 16 bearings) and pushed to the radius (depth index 0..8 = -8..+8 px). Walls: stave, stave_hoop, blue, bin, brick. Roof tiers rd_{galv,blue,tinred}_{s,m}_* step 2 px (s) or 4 px (m) per 4 px course; rd_*_flat tops; rd_cage (climbable caged ladder), rd_finial, rd_bin_cap, rd_fan.
- Round.java: ring(cx,cz,r) rasterises the circle (arc midpoint per cell picks bearing/depth); wall(), roof(profile "s"/"m" per ring), dome(r), cone(r). Centres are block centres (x+0.5), radius to outside face.
- Panel lengths 16.5/22/24 leave no slits for radii 1.5-12 (checked by /tmp/claude-0/farm/py/ringcheck.py). Model coords must stay within -16..32 (tier reach = 30 - cz).
- Farmstead.silo/grainBin use it; to add another round thing (water tank, fuel tank, corn crib) add a WALLS material in round.py.
- Preview: /tmp/claude-0/farm/go.sh (FARM="round" or "farm silo bin"), FarmYard.java in /tmp/claude-0/farm/src; yard dump needs height L+44 for tall silos.
