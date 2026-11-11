---
name: zomboid-spider7-model
description: Josiah's royalty-free "Spider 7" creature model (rigged, 30 pre-made animations, 5 skins) and its web preview, 2026-10-29
metadata:
  type: project
---

2026-10-29 09:00. Josiah has a royalty-free 3D model with pre-made animations and asked for a preview. It's in his PC folder additions\spider\spider-7.zip (1.2 GB), which he extracted. The zip holds:
- Anim.rar, with Anim_Spider_7.fbx (58 takes = 30 clips, each also present as a ROOT| duplicate) and Anim_Spider_7_Turn.fbx
- SK_Mesh.rar
- Textures.rar, extracted on his PC: 4K PNGs for 5 body skins and 4 skull skins (BaseColor, Normal, Roughness), red and blue emissive, AO, metallic (all 0), and a thickness map
- Uproject.rar (an Unreal project, not used)

The model is a spider-legged creature with a skull head, claws, arms and a tail. It has 49 def_* bones and 157k verts, measures about 3.7 x 2.6 m and stands 2.5 m tall, and is modelled in cm. The clips are Idle 1-10, Attack 1-7, Rage, Walk/back/left/right, Run, Turn L/R, Jump, Hit 1-2 and Death 1-2.

Preview: https://claude.ai/artifact/24Wcae3iy7hC3DSSiZQfpP (three.js 0.160 from jsdelivr).
- The GLB is built in headless Chromium with FBXLoader + GLTFExporter (export.html/runexp.js, using the three npm module in the scratch folder).
- Clips are deduped, and scale tracks and bone position tracks are dropped except ROOT/def_pelvis, giving 13.7 MB.
- Artifacts do not serve .glb, so it is shipped as two base64 .txt halves and fetched and parsed.
- Textures are 2K JPGs with flipY=false.
- Copies are in /mnt/project-files/zomboid-mod/assets/spider-7/.

Not in game yet; Josiah hasn't said what it's for.

09:25: Josiah reported that Walk_Right folds two legs. Fix: Walk_Right is rebuilt as a mirror of Walk_left in export.html. Each bone's turn away from bind is mirrored across x in the skinned mesh's BIND space and put on its L/R twin, then converted back to local. The FBX's default bone pose is NOT the bind pose and is badly asymmetric, so take rest from skeleton.boneInverses. The knee angles swap L/R exactly. Republished as v2.

09:29-09:31 DECISION (Josiah): Spider 7 REPLACES THE HARVESTMAN ENTIRELY, keeping this exact design (skull head, humanoid upper body). This overrides the old look rules in [[zomboid-harvestman-direction]]. The licence is royalty free, like the other assets. Step 1: walking in game on flat ground using the model's own clips. Step 2: leg IK onto server feet, climbing, hit zones on its bones. The Harvestman's AI, mind and director all stay.

09:53 IN GAME (step 1, playtest 1923a5566):
- Packing: tools/creatures/spider/ (export.html, bake.html, pack.py) produces assets/zomboidcraft/creatures/spider.zsp. It's scrambled with the seed "spider", KEEP=0.35, giving 18k tris and 2.4 MB.
- Clips store each bone's model-space turn+pos per frame (G). Skin = G·inverse.
- Code: client/creature/spider/ SpiderModel, SpiderAnimator, SpiderDraw. HarvestmanRenderer draws the spider first and the old model is the fallback. HarvestmanEntity.body holds the SpiderDraw.
- Clip map:
  - Walk_1 natural ground speed 0.80 m/s, Run_1 3.03 m/s (in place, no root motion). Strafe/back clips.
  - Turn_L/R on the spot. Idle_1 + Idle_2/5/8 variety; Idle_9 alert.
  - Attack_6/7 when rear rises; Rage_1 if lit (display). Attack_4 = low lunge.
  - Attack_2 slow loop = feeding. Hit_1/2 flinch; Death_1/2 held.
- Placement: feet at centre − up·lift. A y-squash under low ceilings is a stopgap until step 2 IK.
- Hidden: body glow-map areas darken. SKULL ALWAYS GLOWS (Josiah 10:04): skull drawn full bright + its glow pass always on.
- Step 2 next: leg IK onto server feet (4 leg chains def_leg_A/B_1-3 L/R match the old 4 legs), climbing, hit zones on bones.
- 10:07 Josiah: BODY SKIN 2 permanent (pack.py args "2 1"). RAGE used often:
  - Mechanism: entity.rage(at, force) is synced as RAGE; it pauses for RAGE_TICKS=86 with a screech. There's a 20 s gap between rages unless forced.
  - 10:15 Josiah: LESS OFTEN, and NO rage before strikes or on being surprised. Now: gap 75 s; seen again after 90 s 40%; random 0.0015 per sense; hurt 20%; display forced.

10:34 STEP 2 IN GAME (playtest b6c6dc1):
- SpiderIk (client): each foot is raycast along the model's -y, from 1.05 above to 1.5 below the clip foot, then CCD (ankle, knee, hip; hip capped at 0.25 rad).
- The body rises to 0.8 x the average foot height, and pitches and rolls toward the feet. It leans off lost legs and lurches on STUMBLE.
- Under a low ceiling (drop = 2.05 - lift) it sinks up to 0.55 and folds at spine_1 by up to 0.95 rad.
- The head turns to LOOK_AT.
- Animator: falling (climb < -3.5 m/s) uses Jump_1 at its midpoint; HOLD/FEED overlays the arms with Attack_2 at 0.85.
- Lost legs collapse knee+ankle to the knee; the piece flies off, lands, and stays 40 s.
- Legs map 0=A_L, 1=B_L, 2=A_R, 3=B_R (A = front).
- Server hit test is now SpiderShape: skull = EYES_ZONE, tail sac (0,0.68,-0.27) = CORE, plate capsules, three-part rest-pose legs. The old HarvestmanBody.strike is unused.
- Offline IK check: scratch iktest.py / ikview.html in /tmp/claude-0/spidertool. Feet land within 1-4 cm on steps and slopes.
