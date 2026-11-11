---
name: zomboid-spider7-glow-hits
description: Spider 7 Harvestman round 3 (2026-10-29): blue/red glow rule, rage skull flare, server-posed bone hitbox, cuttable arms/scythes, idle fixes
metadata:
  type: project
---

Follows [[zomboid-spider7-model]]. Josiah's asks 10:55-10:57, sent as playtest merge bafffd589.

2026-10-30 RULE CHANGE (Josiah 02:06): glow goes fully OUT, skull too, when it hides, stalks, observes, lies in wait, ambushes, shelters, or does fake-outs (mind.dark(), synced DARK, SpiderDraw eases it). This overrides "skull always glows" and "red while stalking". It's red again to warn, display, strike and feed. Tuck (synced TUCK, mind.tuck()) and the surface-flow work live in SpiderIk: ahead-reach, spin lead/lag, edge hooks, TURN_SURF 4.5.

DECISIONS 2026-10-30 02:34 (Josiah): (1) the Harvestman health bar shows ONLY while a player is fighting it: shot by a player in the last 30 s (synced ENGAGED, EnemyBars). (2) A starving non-hunter MAY strike a lone, unaware player in the dark (HarvestmanChoice STRIKE rule kept; never in grace, never while hunting someone else).

Look rules (Josiah):
- SKULL SKIN 2 permanent, and body skin 2 (pack.py args "2 2").
- Glow BLUE while calm. RED while stalking, trapping, anything that could harm a player, or feeding on zombies.
- The WHOLE skull glows during the rage animation.

Code: SpiderModel, SpiderAnimator, SpiderIk and SpiderPose now live in common creature/spider/. The client keeps only SpiderDraw and SpiderLooks.
- Glow colour: HarvestmanMind.menace() is synced as MENACE. It's true for STALK, AMBUSH, STRIKE, DRAG, FEED, ZOMBIE, the tricks, GLIMPSE, or while holding someone. SpiderDraw eases red (2.5/s) and draws both the red and blue glow passes, weighted. The .zsp packs body/skull_glow (red) + _glow_blue.
- Rage flare: animator.rage() returns a 0..1..0 envelope. The skull is redrawn with eyes()/beaconBeam using its own texture, tinted the current glow colour.
- Hitbox: the server runs a SpiderPose every tick (same clips + IK as the client), and SpiderHits tests mesh-fitted capsules on every bone. Zones: skull = EYES, tail = CORE, legs = LEG 0-3, LIMB = arms 0-1, long scythes 2-3, short claws 4-5. SpiderShape is only the fallback.
- Cut limbs: synced CUT bits plus limbHealth[6] (90 each), saved as hmCut/hmLimbN, and they regrow in REST every 12000 ticks. They fly off like the legs.
- Idle pacing fix: walk hysteresis (start 0.3, stop 0.12 m/s), 4th-power direction weights, and the surface height raycast and eased. Wander/perch rests are now 6-16 s (they were 2-6 s).
- Narrow stance: each foot tries scales 1/0.8/0.64/0.5/0.38 toward the pelvis until it finds footing (and not inside a wall).
