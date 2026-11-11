---
name: zomboid-harvestman-presence-tech
description: Harvestman believability round (2026-10-29) - signs, presence hum/unease/hush, feints, eye shots, caution, wounds; plus the 89-idea full-potential doc
metadata:
  type: project
  modified: 2026-10-29T00:54:52.783Z
---

The 8 "believability" features were built 2026-10-29 on branch claude/project-thread-j04clk and merged into playtest at 77c135fc. The county smoke test watchdog-flaked in worldgen (Plan.depth / forceload), not in Harvestman code, so the fix was to rerun the failed jobs.

- HarvestmanSigns (server): chitter/creak sounds, smashLight (indoor lamps within 4.5), gouges hd_gouges_N at doorways, hd_drag trails while carrying, snack (eats a zombie when no player is within 40), decoyNoise knocks for the FEINT plan, openDoorBehind.
- HarvestmanPresence (client): a hum loop heard from 52 blocks, full at 26, fading at 20, silent at 11 (the silence is the warning). An unease vignette (hud/unease sprite) shows when WATCH == you and you can't see it. hush() quietens animals and amb.night through SoundEngineMixin.
- Mind: plan FEINT=19. Learned values lightNerve/gunNerve/feintBias (NBT hmLightNerve, hmGunNerve, hmFeint). Light check uses BLOCK light >= 11 only (sky light would make it flee by day). Peeks round corners while stalking. Gunfire makes it shy. Below 30% health it flees; when wounded, pick() goes REST, then FLEE, then HIDE; STRIKE needs >60% health.
- Body: EYES_ZONE=3 (eyes moved from the rig into HarvestmanBody.EYES); an eye hit does CORE damage plus RECOIL.
- Legs: gait respaces the remaining legs evenly after a leg is lost; probe() taps ledges and doorways; slip() happens on ice (0.12) and wet (0.012); reach().
- Entity synced data: LOOK_AT, LOOKING, WATCH, STUMBLE, RECOIL. Footfalls play the block's step sound and raise dust.
- Sounds: tools/audio/build_harvestman_presence.py synthesizes chitter, creak and hum (Josiah cut the old synthetic CLICK via Loop Bench, so its sound list is empty).

Full-potential ideas doc (89 numbered options, sized S/M/L): https://claude.ai/artifact/QELQLccAaw2LVobQZubMrk. Suggested next picks are #1 routes, #14 mimicry, #45 blood trail, #65 rescue a friend, #71 kill rewards and #73 field journal. 2026-10-29 01:41 Josiah marked YES on 56: 18-27, 29-32, 34-43, 45, 46, 49, 51, 52, 64, 65, 67-76, 78, 81-83, 87, 89. His notes: #22 a killed player's corpse is left at their door 1-2 days after respawn; #75 the lab one is a BOSS (military were modifying it as a weapon vs the others, unfinished): stronger, smarter, far more imposing look; #76 the Queen is an activity, NOT the end goal (else the zombies don't make sense). My thoughts doc with 7 open questions and 5 build waves: https://claude.ai/artifact/HdEjrBsrAfZyyhjh921amz (awaiting his go).

Related: [[zomboid-harvestman-game-tech]], [[zomboid-harvestman-route-tech]], [[zomboid-harvestman-direction]].
