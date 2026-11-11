---
name: endless-night-covenant-vehicles-tech
description: How Covenant vehicle packs, Reach overlays, crews/seats and Phantom troop drops are built and rendered (tools + Java classes)
metadata:
  type: project
  modified: 2026-11-10T10:25:39.627Z
---
Covenant vehicles pipeline (ENDLESS-NIGHT repo, 2026-11-10). Bungie data never in repo/jar; packs go to config/endlessnight/npc on his PC.

- vehpack.py -> PACK_DIR (<id>_meshes/_mounts.json, turrets keep own skeleton as bones "t:<turret>/<bone>").
- vehclips.py -> <id>_clips_anim.json: base clips (world poses) + overlays "ov:K:NAME[@turret]" (K t/l/p/g).
  **Reach type-2 overlays are stored as each bone's change already** (raw t and q ARE the delta; rest-relative was wrong: Shade folded flat). Whole-pose clips used as overlays: from first frame (t,l) or bone rest (p,g). Grid descriptors in b_aim_yaw slot.
- vehseats.py -> <id>_seats.json from Reach seat markers (ghost_d, revenant_d, banshee_d, wraith_d, turret:marker for turret models e.g. shade_plasma_cannon:shade_d, wraith_anti_infantry:wraith_g, plasma_turret_mounted turret_g; phantom_p_{lb,lf,rb,rf}_{main,small_1,small_2}, phantom_pc_1..6). NOT the *_enter markers (they face backward). Marker q is xyzw Halo; glTF = (y,z,x,w), t*3.048 (y,z,x).
- covpack.py ID PACK_DIR OUT (COVENANT_DETAIL=PACK_DIR/detail) -> covenant_<id>.zsk; seats json stored as raw entry "seats".
- Seat clips: covenant_<species>_seats.zsk, clips "seat/<key>" (baked by /tmp/claude-0/cov/exp/seats.js; no weapon grip).
- Java: VehAnimator (base+overlays), CovVehicleRenderer, CovCrew (riders = species pack posed by seat clip at seat bone x marker; bone map by name). CovVehicle syncs CREW bits + EXIT tick; Phantom opens doors at phase 1, lance exits at phaseAge 10, drops to ground, soldiers spawned at 80.
- Markers: /tmp/claude-0/full/markers.json via vmark.py (m70.map has Shade/turret models). Preview sim /tmp/claude-0/hv4 (vsim.py, scenes.py) renders from the same pack data.

Related: [[endless-night-covenant-enemies]], [[zomboid-halo-reach-anim-format]], [[full-detail-previews-rule]].
