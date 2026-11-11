---
name: zomboid-zbody-tech
description: How the new ZomboidCraft zombie bodies are drawn in game (ZBodyMesh/ZBodyLayer/ZMove/ZBodyAnimator, preview export pipeline, legless, /zc zombie)
metadata:
  type: project
  modified: 2026-10-26T08:09:43.556Z
---

Built 2026-10-26 (characters thread) from the approved preview (kits.html, artifact https://claude.ai/artifact/Mnik6r9hRkYoARfsLTdBxU). Direction: [[zomboid-characters-direction]], movements: [[zomboid-movement-v2]].

**Pipeline:** preview sources saved in /mnt/project-files/zomboid-mod/characters/preview-src/ (kits.js, move.js, build_all.py → patch_moods → patch_15 → patch_worn_eyes → patch_move; ref/ has the offline THREE shim + software rasteriser, since cdnjs/npm are blocked). Repo tools/zombies/: export.js (runs in the page via Chromium, bone-local quads + kit atlas), pack.py (writes assets zbody/<outfit>.bin, textures/entity/zbody/{skin,kit,eyes}/<outfit>.png), check.py.

**Java** (src/client/.../zombie/body/): ZBodyMesh loads .bin ("ZBD1"; quad flags SMALL=1 STUMP=2 CUT=4 BODY=8). ZMove = hand port of move.js, 11 blendable modes (IDLE SHAMBLE ALERT CHASE ATTACK DOWN CRAWL_REST/SLOW/FAST/ALERT/GRAB); keep it in step with move.js. ZBodyAnimator picks the mode per zombie (attack = rising getAttackAnim, aggressive+moving = chase, |head yaw|>10° = alert, swimming pose = down/crawl) and blends at 6/s; gait integrates walkAnimationPos. ZBodyLayer builds bone matrices (JOML rotateXYZ = three XYZ; hips rotateYXZ standing) and emits via submitCustomGeometry; preview→model map MCpx = (k·x, 24+0.4k−k·y, −k·z), k=32/35.2. Quads are stored clockwise, emitted TL,BL,BR,TR. LOD: all within look.zombieDetailDistance (16), then drops SMALL kit quads, past 2.5× drops kit quads thinner than 1 px.

**WalkerRenderer:** getRenderType returns null when the new body is on (old model hidden), vanilla layers wrapped in OldBodyOnly, old lie tilt skipped. look.newZombies=false restores the old model.

**Legless:** Downed.LEGLESS attachment (persistent, synced), combat.leglessSpawnChance 0.03, always a crawler; drawn with hip scale y 0.34, shins hidden, STUMP quads shown.

**Command:** `/zc zombie [outfit] [walker|crawler|legless]` (posted to the COMMANDS thread).

**Offline check:** scratchpad jtest/ compiles ZMove+ZBodyMesh with a JOML stand-in and renders front/side sheets (Java2D) — useful before CI.

**Follow-through (2026-10-31, Blender tutorial thread, playtest 73150599a):** ZBodyAnimator.flow() adds spring bow/lean/head-lead/arm swing from speed accel, body-yaw turn rate and landings, standing only (skipped crawling, lying, breaking in, dead). Added AFTER move.pose/hop/events and BEFORE ZHit shot jerks. Signs per ZMove doc: spine x>0 forward, spine z>0 to its right, head y<0 turns right, shoulder x<0 forward.
