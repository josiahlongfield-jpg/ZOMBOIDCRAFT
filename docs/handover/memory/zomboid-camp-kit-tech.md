---
name: zomboid-camp-kit-tech
description: Army camp pieces (Camp.java) come from the cp_ kit in tools/city/camp.py; block names, facings, preview yard, what is still vanilla
metadata:
  type: project
  modified: 2026-10-29T05:03:14.012Z
---

Since 2026-10-29 (LANDSCAPE, commit 1102781b), after [[zomboid-consistent-detail-rule]], Camp.java has no vanilla structure blocks left. The only ones left are the terrain, the campfire and the mess hall's Bldg.gable roof. Bldg.gable still uses vanilla stairs county-wide, so it waits for the county roof pass.
- Kit: tools/city/camp.py (`python3 tools/city/camp.py` rewrites the cp_ rows; textures via camp.register() in photo_textures.py). First pass from general knowledge. REFERENCES has queued refs/military/watchtowers, gate-guard-booths, boom-barrier-gates, portable-toilets, radio-masts, range-targets-berms and light-towers; refine against them when they land.
- Watchtower: cp_tw_leg (post in the model's SE corner; legs face north/east/south/west at NW/NE/SE/SW), braces cp_tw_brace_a/b/x(_g), cp_tw_girt, cp_tw_deck(_post). Roof pieces cp_tw_roof[_n/_e/_s/_w/_nw/...] are NEVER rotated, so the ribs line up. The ladder goes up the back face through a deck hatch.
- Lights: Camp.flood() returns cp_flood(_lit), a lamp on an L bracket that stands on a beam or bolts to a wall. Camp.lamp(rng, dead, kind, facing) also covers cp_flood_hang and cp_flood_bar (mast crossbar). The whole lit block glows, so keep lit lamps as separate blocks.
- Others: cp_boom_post/arm/tip/up/cap (arm along +x), cp_target_lo/hi(_shot)/down, cp_flag(pole), cp_lane_post, cp_mast_r/w/top + tv_beacon, cp_cabinet, cp_bladder(_ring/_e/_c/_valve)(_slack), cp_porta_down (+ st_porta_lo/hi standing), cp_grave_cross/helmet + ch_grave_mound, cp_stake, cp_post/log/wall(_top)/step/bars(_end)/rope(_end), cp_pad(_yellow/_white).
- Light tower trailer: Camp.lightTower(x,z,raised) = cp_lt_rear/front_{olive,white}, cp_lt_cap_rear/front, cp_lt_mast, cp_lt_head(_lit). Gate booth: Camp.booth(x,z) = 3x3 ring of cp_gb_wall/_corner, cp_gb_win(_corner)(_broken), cp_gb_head(_corner), cp_gb_roof, Doors.SHOP door. Corner pieces must end in _corner (orientcheck).
- Helicopter = p.vehicle("=heli_hornbill@state"), yaw 180 = nose -z.
- Shared props in clutter.py: tire is now a standing ring tyre (also the carrier tugs' wheels), tire_flat and tire_stack are new. The "barrel" block is always swapped for the scanned pk_barrel models ("!barrel" in PropSwaps).
- Preview yard: /tmp/claude-0/camp/go.sh with env CAMP="tower booth toilet mast range fuel heli wreck pit course crates", SHOW="blocks..." and HIT; meshes as m_camp (CampYard.java is in /tmp/claude-0/camp/src, not in the repo).
