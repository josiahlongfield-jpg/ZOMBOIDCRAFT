---
name: zomboid-camp-black-pine-tech
description: Camp Black Pine build (LANDSCAPE 2026-10-30): CampBlackPine FrameSite frame/zones, smooth quonset huts via ArmyFieldBase.arc, cleared zones vs pines, preview
metadata:
  type: project
  modified: 2026-10-30T04:54:42.032Z
---

Built 2026-10-30 (commit 536fdd7e), third site of [[zomboid-nw-military-plan]].
- CampBlackPine.java extends FrameSite: spot "camp" (-6600,-3250), frame fx -180..179, fz -150..149, tiles 240 (Layout.fixed special-cases "camp", LandmarkSite dispatches "camp@"). Whole camp is ONE piece (inner class Yard extends Grounds) built in the frame and copied per tile, including ground, wire and gates. Seed-12345 gate: north (120,-150), the COUNTY road from army_base.
- Zones (frame): parade ground x -45..44 z -32..31 (asphalt_b, yellow squad marks, cp_post bollards, flood masts, reviewing stand + 2 flagpoles south); HQ Camp.headquarters(40,16) facing south at z -66..-51; quonset huts 4x2 from (-148,-118) step 24/32, span 11 len 22 (mf_vault arc on the ground, siding_sage ends, cots, wood stove); barracks x2 + mess hall facing east at x -146/-110; outbreak SW (tents, hospital, burn pit, trench, graves); motor pool NE (2 sheds, 4 bays, maintenance quonset 22x28 on 3-high walls, fuel bladders, wash rack); east: armoury, helipad + heli, mast, crates, toilets; SE: assaultCourse(60,48,82), range(62,72,40,40), water tower at (126,92) (cp_tw legs, Round.wall rd_wtw r 3.6, cone roof; rd_wtw has NO _hoop pieces).
- Pines: Kit.plant PINE (8% DEAD) on a 5-grid with fbm density, skipped inside CLEARED boxes and where clear() finds anything within 4.
- Placer turned for buildings: at(x0,z0,bw,bd,facing) mirrors Placer.forLot.
- Preview: /tmp/claude-0/plan/cbp/go.sh all 180 "150@120,-150,0;" 360 300, mk.sh all 188,13,158 260 (mesh x = fx+180, z = fz+150). Sheet /mnt/project-files/zomboid-mod/camp-black-pine/camp-preview.jpg.
- Could improve: a steel lattice water tower (legs are the wooden watchtower kit), a routed camp sign at the gate (only posts now).
