---
name: zomboid-detail-kits-tech
description: Kits added in the 2026-10-29 detail pass (freight, trackbed, weave, memorial, gazebo, poles, cellar barrels, ship fairing) and how they're placed
metadata:
  type: project
---

Kits added by LANDSCAPE on 2026-10-29 in the consistent-detail pass. Each is a tools/city/*.py that writes its prefix into blocks.json, registered at the end of photo_textures.py.

- trackbed.py `tb_`: sleepers, rails, crossings. Used in CityCivic subway and TownSite.
- freight.py `fw_` + Freight.java: boxcars and chassis (LEN 13). Door quarters n = row*2+col. Corner "l" is the x=16 side.
- weave.py `wv_`: mill looms (l/r halves, _cut), spinning frame, cotton bales. Used in Landmarks.mill.
- memorial.py `mm_`: obelisk courses, field guns, wreath. Used in Landmarks.memorial.
- gazebo.py `gz_`: bandstand (deck, step, posts, frieze, rail, finial). Kit.gazebo puts a slate Bldg.hip on top; the finial replaces the apex ridge at y8.
- poles.py `pl_`: stump, fallen pole, pole top, wires, transformer can. Used in Kit.powerLine.
- basement.py: cellar_barrel and cellar_barrel_side (cask), placed by Cellar. Cellar walls are fm_fieldstone or ch_ashlar. Its ladder is fm_ladder (added to Builders.LADDERS).
- vessel.py `vessel_fair_<col>[_seam]_<1..3>` + BigShip.fair(): quarter-block fairing plates on the bow and stern taper. Ships.Hull.halfAt(cx, y) gives the continuous hull half-beam.

Yard preview modes in /tmp/claude-0/yard (scratch, not in repo): park, ship, freight, mill. For ship, set NEAR/RAD env vars in fast.sh.

See [[zomboid-landscape-pause-after-list]] and [[zomboid-building-detail-direction]].
