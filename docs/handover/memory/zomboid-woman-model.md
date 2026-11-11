---
name: zomboid-woman-model
description: "Woman" character from Josiah's additions folder (replaces the dark elf companion): rig, pieces, preview page, 2026-10-29
metadata:
  type: project
  modified: 2026-10-29T11:04:29.730Z
---

2026-10-29. Josiah put a "woman" character in additions\Woman (FBX, OBJ/MTL, .blend, textures, rig tools) to REPLACE the dark elf companion (see [[zomboid-npc-models]]).

Facts:
- Unreal mannequin skeleton (pelvis, spine_01-05, clavicle/upperarm/lowerarm/hand, thigh/calf/foot/ball, neck_01/02, head) plus an ARP face rig; 248 bones. NO animations ship (FBX AnimationStack 0, blend has no actions), so mocap clips (Mixamo retarget / UE-standard) are needed.
- Body is fully modelled and anatomically nude under the clothes. Previews must always keep her clothed (underwear always on; bra auto-on when no top).
- Mesh pieces: shirt=yellow sweater, Dress=white slip, _bra, skirt=pleated, short=denim shorts, pant=red briefs, underwear=garter+panties, sock=black stockings, boot=ankle boots, shoes=heels, hair2=clip. Hair: blackhair (bob), hair (brown layers), hair1 (red crop), longhair (silver), rope (braided bun). Eyes: brown/blue/gold/green/mix textures.

Preview: https://claude.ai/artifact/6iFtLo6sRF12Zn7jAcQ5dW (5 outfits, piece toggles, hair, eyes, 6 hand-set poses). Built in headless Chromium: FBXLoader + mergeVertices into a custom quantised binary (bones deduped by name, root.matrixWorld kept or she lies flat), shipped as base64 model.txt.

11:20 "pixelated" fix (v2): byte skin weights must be renormalised to sum 1 (her bind space is ~325 units, so a 0.4% sum error tore the skin into jagged patches). Textures now body 4K, clothes/hair 2K, alphaToCoverage on hair. Same rule applies when packing her for the game.

11:25 Josiah's GAME LOOK: black bob (blackhair), GREEN eyes, _bra + underwear (garter/panties) + sock (stockings), barefoot; no sweater/skirt/shoes. Mixamo T-pose: zomboid-mod/assets/woman/woman_tpose_mixamo.zip (also in his additions\Woman), built by scratch woman/page/tpose.html + tpose.js (args: pieces, out, FOOT deg). She is modelled on tiptoe for heels; flat feet = aim foot->ball 24 deg below horizontal, toes keep world turn.

11:27 He asked for the UNEDITED A-pose instead: zomboid-mod/assets/woman/woman_apose_mixamo.zip (tpose.js 5th arg raw). 11:36 He now wants HEELS (shoes) on for natural feet: woman_apose_heels_mixamo.zip and woman_apose_nobra_heels_mixamo.zip (unedited A-pose). Game look = above + shoes, bra optional. 11:42 A-pose made Mixamo cut chunks from the breasts (arms too close); T-pose heels files: woman_tpose_nobra_heels_mixamo.zip, woman_tpose_bra_heels_mixamo.zip (tpose.js 5th arg nofeet). 12:28 IN GAME (merge 39078307b to playtest): 8 Mixamo clips (MIXIMO.zip: Breathing Idle, Female Walk, Running, Dying, Aiming Gun, Firing Rifle, Walk With Rifle, Rifle Aiming Idle). tools/npc/skinned/{export.html,run.js,pack.py} -> woman.zsk (LOCAL ONLY, 12 MB, in his config/zomboidcraft/npc via folder access; copy at zomboid-mod/assets/woman/woman.zsk; spec + face dump + preview tools in assets/woman/tools/). Lod0 full detail (170k tris), lod1 past 10 blocks. Face: jaw + lid_l/lid_r bones copied from the original ARP rig (pivot = c_jawbonex / eye centres, rotateX). Client NpcSkin/NpcSkinAnimator/NpcSkinDraw; NpcLines chat lines; /zc npc wear|remove <bra|stockings|heels|hair>, outfit, name. Underwear always on. Gun-in-hand placement untested. 12:45 playtest jar 6253e2c green with her in it; link sent. Spawn /zc npc woman. Open: gun grip in hand untested, voice audio offered for later. 13:18 (playtest 4c2f657): Josiah said she looked 'somewhat transparent' and rifles went through her shoulder. All her meshes now drawn entityCutout (no cull; winding checked CCW-outward, cause unconfirmed) and long guns (GunType !handgun) slide LONG_GUN_FORWARD=0.13 along the hand in NpcRenderer.skinHeld. Awaiting his check; asked for a screenshot with shaders on/off if still see-through.
