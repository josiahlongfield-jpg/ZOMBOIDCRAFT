---
name: zomboid-county-dress-tech
description: Shared county building dressing (Bldg.java + tools/city/dress.py bd_ kit): kit roofs for gable/hip, flat-roof coping, chimneys, rooftop units, downpipes, lintels; blocks.json order rules
metadata:
  type: project
  modified: 2026-10-29T05:40:23.329Z
---

Since 2026-10-29 (LANDSCAPE, commits 7ce22e56, 82fc4d85, ce8a7342), part of the [[zomboid-consistent-detail-rule]] pass:
- Bldg.gable/hip map each vanilla stairs family to a house-kit roof via Bldg.kitRoof() (deepslate→slate, woods→brown shingle, andesite/stone→tin_galv, tuff→tin_red, coppers→tin_green, brick/granite→red, other vanilla→grey). Hips use new ext_roof_{kind}_hip / _hip_eave corners (model high N+E; NW corner facing=east, NE south, SE west, SW north). Corners must be placed inside the edge loop, because tile() randomly skips (damage) and would leave the edge piece there.
- Bldg.flat(): the parapet now runs the full h, with a thin cap on top at y+h+1 (stone_trim→bd_coping, others→bd_coping_pc, front_coping_metal kept). It adds brick chimney stacks (bd_chimney_{red,dark,buff} + _top), a roof kit on clear deck one block in from the edge (bd_hatch, bd_hvac_a+b pair along +x, bd_vent/bunk_vent_cap, bd_drain, sometimes tv_dish_small) and ext_downspout pipes down both back corners. window() with a sill also puts a bd_lintel over the head.
- These consume rng, so a site's later random layout shifts against older builds. Compare structure-check counts, not block diffs.
- blocks.json rule: kit scripts must keep the shared order and indent=1 format. exterior.py and dress.py now do. tents/clutter/camp still re-append their prefix at the end, so on playtest merges rebuild from playtest HEAD order and insert only the new keys.
- Preview: /tmp/claude-0/camp/go.sh with CAMP="shop" builds a brick shop and a concrete box with flat roofs; CAMP="bldg" builds the hip test.
