---
name: zomboid-farm-kit-tech
description: fm_ farm kit (tools/city/farm.py) used by Farmstead/FarmSite: gambrel roof pieces, bales, rail fences, gates, stalls, yard props, loft ladders; preview yard modes
metadata:
  type: project
  modified: 2026-10-29T06:52:59.996Z
---

Since 2026-10-29 (LANDSCAPE, commit 7d6cc40f), part of the [[zomboid-consistent-detail-rule]] pass; builds on [[zomboid-round-kit-tech]].
- tools/city/farm.py writes ~164 fm_ blocks and registers its textures with photo_textures.py (`import farm; farm.register(...)`). Run `python3 tools/city/farm.py` then gen_blocks.
- Gambrel: fm_gam_{kind}_{steep_lo,steep_hi,low,high,*_crest,ridge_*} + fm_gam_{kind}_eave; kinds are Bldg.kitRoof's (tin_galv, tin_red, tin_green, slate, brown, charcoal, red, grey). Farmstead.gambrelRoof(x0,x1,z0,z1,y,family,wall,hood) builds it (even span), sets `peak`; cupola() sits on it.
- Doors: fm_gate (steel) and fm_stall_door (wood) are real doors: place with Doors.place(p,x,y,z,type,facing,hinge,open).
- Fences: Kit.railFence(p,rng,x0,z0,x1,z1,white,broken); FarmSite.fence() picks rail/white rail/barbed wire+T-post/chainlink by field use.
- Round things go through Round.wall materials field/tank/crib/paddock (round.py).
- Ladders: fm_ladder / fm_ladder_broken (climb=True, wall on the model's south, same facing as vanilla ladder); steel ones use bunk_ladder. Other county files still use vanilla `ladder` (23 places) — swap them in their own passes.
- Farm vehicles (tractor, hay wagon, horse trailer) became .zcar models via tools/cars/farm_machines.py on 2026-10-29.
- Preview: /tmp/claude-0/farm/go.sh with FARM="farm barn [horse|hay|gable]" or "farm yard"; mesh coords = dump coords + origin (-8,0,-8).
