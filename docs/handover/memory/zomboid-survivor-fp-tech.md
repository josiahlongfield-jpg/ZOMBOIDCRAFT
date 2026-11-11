---
name: zomboid-survivor-fp-tech
description: First-person survivor arms on guns - facts that broke runs 1-7 (hand frame offset, gun sizes) and the cloud preview harness
metadata:
  type: project
  modified: 2026-11-02T08:43:46.737Z
---

Learned 2026-11-02 (survivor models thread) after Josiah called run 6/7 arms "not useable in the slightest" and asked to "take the third person positioning and apply that to the first person view".

- RifleView's hand frame (the pose RifleArms gets) sits at vanilla's arm offset GunRigs.ARM (0.56,-0.52,-0.72) from the eye: the eye is at -ARM there, NOT the origin. Runs 1-7 put her eyes 0.9 m off.
- First-person guns are drawn bigger than life: AR hip scale 0.85 = 1.5x real (real ~0.56), ADS 0.72; pistol hip 1.25 = 2.9x real (real ~0.434); shotgun 0.9 = 1.8x. Real-size arms can't hold them without stretching. Fix (commit 7d28e129): SurvivorArms.drawGun draws her at k = gun size / life size (long guns: grip-to-handguard span / her palm gap in rifle_aim), carries Zomboid's rifle_aim/pistol_aim hold rigidly onto the gun, swings forearms about the wrist onto RifleView's Hand.elbow directions, draws only forearm/hand/finger triangles (NpcSkinDraw trisOf overload). Survivor pistols: RifleView.atArmsLength (scale 0.6, grip 0.56 ahead).
- Third-person "rifle upright when aiming": HoldPoses support line runs magwell->handguard, ~40 deg off the barrel when shouldered. Survivors.after now lays the gun by its item model display rotation (models/item/weapon/GUN/{hip,raised_hip,ads}/body.json thirdperson_righthand), via Survivors.display().
- Appearance drag-to-turn: changing state.bodyRot after extractRenderState does nothing (the inventory drawing sets it after); the spin is applied in SurvivorLayer for the noted figure state (AppearanceScreen.noteFigure/spinOf).
- Cloud preview harness: scratchpad fp/fp.py (poses the pack like the Java, writes a scene) + fp/shoot.js (three.js via Playwright). Packs at scratchpad pack7/. Scratchpad is per-session: rebuild from this note if lost. Look at every shot yourself before showing Josiah; the Remote Control report was too generous.

- 09:09-09:26 direction: gun sized to her arms but NOT pulled closer; front hand holds the handguard. aa618282 (RifleView.fitted, superseded, unapproved). Rejected: gun at her rifle_aim depth (0.15 m from eye).
- Plain old guns are retired on playtest (Retired.java): gametests must give guns carrying LocalGuns.ID.

Related: [[zomboid-survivor-lead-rule]], [[zomboid-video-preview-rule]].
- Mock-up page (thread at its 24-attachment cap): https://claude.ai/artifact/FmonNJKhSCSrcqLMvxt6s3, source scratchpad mock/survivor-arms.html; republish with each mock-up.
- 09:46-10:03: copied his FPS clip/still (Versions 1/2, fp/solve_ref.py screen solve, BIG/SHIFT/LSHIFT/THUMB env); superseded by his 10:13 picture (Version 3).
- 10:37 fixes (no cant, bigger, trigger hand ON the grip, no front-hand clash). Version 3 revised on the page: handfit.py = voxel clash field of the gun mesh + per-hand optimiser. fp.py env: REF=0.2551,-0.1373,-0.5717,-9.1219,-1.3791,-2.7625,0.56 BIG=1.3 BIGAT=ray (slide toward eye along grip ray) SC=0.56 HGZ=7.5,7.5 CUP=60,0 PALMH=0.005 HROLL=-35 THUMBF=40 THUMBU=55 THUMB=0 LSHIFT=0,0.012 RHAND=1 RT=0.35 SWR=0.25,-0.75,0.6 SWRK=0.5 HANDFIT=01 FINE0=0.25 CURLW0=3 PENW0=8 PENW1=4 AXW=12 OVW=3 ELW=0. (OLD packs only; the DETAILED survivors have full separate fingers, Josiah 2026-11-06) Zomboid hands: fingers are ONE bent piece; grip sits between palm and fingers (hand-local hole 0.112,0.015,-0.025, grip along local y). Awaiting his OK.
- 11:06 Josiah: gun still too small (should fill the front hand against the thumb); rear hand must keep its ORIGINAL angle, only slid forward onto the grip. Now: drop RHAND/SWR; MOVEONLY0=1 MOVEONLY1=1 (translate + finger/thumb only), GROWF=1.25 (gun grown about front-hand handguard spot before fitting), HANDFIT=10, PENW0=2.5 RTT=0.25 RTW=8 TCW=4. 1.4 too big for her fingers to wrap.
