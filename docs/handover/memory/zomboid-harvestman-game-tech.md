---
name: zomboid-harvestman-game-tech
description: How the Harvestman works in game - server legs, hit zones, grab/feed, Mind utility AI, Director hunter/lairs, camo, held view, sounds, /zc harvestman (2026-10-28)
metadata:
  type: project
---

Steps 1-4 of the game-ready plan built 2026-10-28 (branch claude/project-thread-wfvzb7). Package creature/ (+ client/creature/).

- HarvestmanBody: shared leg IK (bend), lower-leg arc, strike(): hull ellipsoid, CORE = under the belly (local y < HCY-0.12, |x|<0.4), leg capsules. Used by server hit test AND client rig.
- Crawl (2026-10-28 v2): holds ONE face `surf` (axis normal); climbs a face ahead, slides along slanted walls, pivots round outer edges (pivotAt just past the corner), steps down ledges, drops off floors only when the goal is below; moves only if fits() (3 hull boxes) and legs.support() >= 3; goal on another face -> wallward(). Offline voxel sim: scratchpad crawlsim/sim.py pattern (Python port) caught pivot/float bugs. /zc harvestman do runs choose() at once and returns why not.
- HarvestmanLegs: SERVER steps the feet (never onto air: solid[] flags; hooks round edges); four VECTOR3 FOOT synced; client draws them 2 ticks behind (feetSeen) to match entity lerp.
- HarvestmanEntity (Mob, noPhysics): 1200 hp, PLATES 0.1, CORE 2.0, LEG_HEALTH 250 each (lost bits, LEG_OFF), other damage x0.1. Dismember.shot skips it in the main loop and calls h.strike(); Guns.hitEntity returns early for it (no blood). Grab = player rides it (positionRider at GRIP, PlayerRidingMixin blocks sneak-dismount), struggle = jump presses (14+2*stage) or 120 damage taken; FEED_TICKS 200 drains via damage type zomboidcraft:harvestman. POSE HUNT/HOLD/FEED/DEAD, CAMO synced (mind.camo(): 0 feeding, ~0.15 striking, 1 roaming).
- HarvestmanMind: needs/traits/learned stage 1-5, plans in PLANS (do <plan>), events heard/hurt/lostLeg/fed.
- HarvestmanDirector: zomboidcraft_harvestmen.json at world root; one hunter server-wide, target by UUID, GRACE 48000 ticks, 7 days offline lets go, grudge switch; virtual travel + materialise 20-40 blocks from target; target-only scare sounds; haunt cells; 4-6 dens in dark interiors (>=160 from spawn), stocked with kept Corpses (Corpses.lay kept=true) + 30% bunker map. Noise.emit calls Director.noise.
- Client: HarvestmanClient registers renderer + CameraRig(5) shake while held in first person; hud/HeldHud red throb + "Mash jump to break free". Renderer lerps red corners to black by camo; six octahedron eyes from HarvestmanRig.EYES always FULL_BRIGHT.
- Model parts: body, armsHunt, armsFeed, thighF/R, lowF/R (pack.py ORDER). Rebuild: pack.py THEN lod.py (PERFORMANCE thread's 3-level LOD: near/mid 8+/far 20+, shadows use far; refuses to run twice). Split agreed 2026-10-28: PERFORMANCE owns renderer/model/LOD/pack/export; Harvestman thread owns Entity/Mind/Director/Legs/Body.
- Sounds: tools/audio/build_harvestman_sounds.py /mnt/project-files/zomboid-mod/sound-raw/sonniss/ (Sonniss layers pitched + synthetic clicks), entries at the TOP of sounds.json, CREDITS "Harvestman sounds" section.
- Offline strike test: compile HarvestmanBody with Vec3/Mth stubs (scratchpad pattern) - zones verified.

Related: [[zomboid-harvestman-tech]], [[zomboid-harvestman-direction]], [[zomboid-corpses-tech]].
