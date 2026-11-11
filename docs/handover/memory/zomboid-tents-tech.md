---
name: zomboid-tents-tech
description: Every tent in the world is built by Tents.java from the tn_ kit (tools/city/tents.py); widths, uses, block naming, preview yard
metadata:
  type: project
  modified: 2026-10-29T04:12:35.934Z
---

Since 2026-10-29 (LANDSCAPE, commit 07cd3a83), after Josiah's consistent-detail rule ([[zomboid-consistent-detail-rule]]), all tents come from one builder. It replaced the five copies in Grounds, Stadium, UnionStation, Hospital and Camp. Camp's old tents were vanilla wool, planks, beds and tripwire.
- Kit: tools/city/tents.py (`python3 tools/city/tents.py` rewrites the tn_ rows in blocks.json; textures via photo_textures.py, tents.register()). Built from refs/military/field-hospital-tent.
- Canvas colours: olive (width 6), tan (6, 10), white (6, 10, 18). Blocks are tn_{c}_wall/_p/_lo/_lo_p/win/win_open/cross/lbl_KEY_i/corner/corner_lo/door_hi|lo_l|r/curtain_hi, roof_K[f][r], roofend_K[r]_a|b, sheet/heap/sag. Shared blocks: tn_tie(_lamp(_lit)), tn_guy(_cut), tn_mat/ply/groundsheet, tn_screen, tn_table_med/supply, tn_genset, tn_cable, tn_ruck, tn_stand_KEY, tn_pole(_down), tn_curtain_lo.
- Geometry: walls 2 high in the outer cells, with the canvas on the model's north edge and facing = outward. Panel k sits at row 3 + floor(k*tan22.5), rotated -22.5 about x. Gables go on the model's east (a) or west (b) edge. Frame rows (every other row) get rafters, poles, a tie bar with a lamp, and guy lines.
- Tents.java (world/county): `new Tents(placer, rng)` with fields hit, lit, backDoor, bare. Methods: pitch / fallen / tent(..., fallChance). Uses: gp, shelter, supply, triage, ward. Tents.canvasFor(use) gives the default colour; width() snaps w down to a width the kit has. The door is in the middle of the north end.
- Preview yard: /tmp/claude-0/tents/go.sh [seed] (TentYard.java in /tmp/claude-0/tents/src, not in the repo) meshes it as m_tents. Walkcheck is clean apart from ridge "flat roofs" and the tops of props.
