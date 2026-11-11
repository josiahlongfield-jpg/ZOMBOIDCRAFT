---
name: zomboid-warthog-gunner-tech
description: Warthog minigun gunner details: eye/grips, hands, zoom, spread ring, fire rhythm matched to the recording, muzzle flash, standing pose
metadata:
  type: project
---

**Gunner pass 2026-10-30**: gunner eye game (0, 2.98, -1.884) right behind the grips; turret.grips (two spade grips, first-person arms via CarArms.grips with turned shoulders; third person CarHands.pose); gunner can't use items (CarProp.mayUse false, Use*Callback FAIL); right-click narrows FOV by turret.zoom 0.6 (AbstractClientPlayerMixin); gun handle dot on the grips, reached from behind the car (reach 3.2). Fire rhythm from AUDIO: groups of minigun.groupRounds 4 every groupTicks 5 (~945 rpm), weapon.minigun.loop (1.26 s, five groups) restarted every loopTicks 25 and stopped by ClientboundStopSoundPacket on release; no per-round shot sound. Playtest cfbe4b4f. Round 2 (8e490828): Josiah says the first-person gun placement is PERFECT, don't move it. Gunner stands (CarHands.pose legs straight); ring+spread minigun.hipSpread 0.9 / aimSpread 0.35 zoomed (CarNet ZOOM_ON/OFF -10/-11, car.turretZoom; defaults step 16); muzzle flash = CarRenderer.muzzleFlash discs (Interactables.disc); AirStrikeFx skips its muzzle square within 12 blocks of the camera (showed black under Photon).

Related: [[zomboid-warthog-tech]].
