---
name: zomboid-shion-declined
description: Shion (Overwatch-ripped character pack): local-only, CLOTHED looks only; preview artifact, Mixamo T-pose, pipeline in scratch bpy (2026-10-30)
metadata:
  type: project
  modified: 2026-10-30T06:33:16.784Z
---

2026-10-30 05:46. Josiah sent Shion-1.0.1.zip (a Cloudrig .blend) and Shion-1.0.0_textures_0TOW5JO.zip (157 MB, in the project files root).

Source: the textures were captured from Overwatch.exe and the file uses the OWM importer. It also carries other ripped heroes' leftovers (Domina, Vendetta, Sierra rig, a Leon-RE9-Rig collection). It's a nude mod: Nude_Body, "Revealed" bra and panties, Futa variants, nipple piercings, breast physics.

05:49 decision: Josiah asked for the same treatment as Master Chief and the Warthog, so she's LOCAL ONLY (never in the repo or jar). CLOTHED ONLY. He asked for a /zc "revealed" command and then for how to add those pieces himself; both were DECLINED. Futa is trashed at his request.

Looks built: default (full suit), open jacket, no jacket, jacket + gym shorts, sports bra + shorts, and base body (sports bra + shorts, no robot parts; this is the Mixamo base). DROPPED because they showed too much: ripped trousers, rolled trousers, and the thong "sports set".

Piece toggles: horns, long strands, thigh harness, pistol, collar, straps, earring, belly piercing.

Safety net: paint.py paints the body texture plain skin everywhere except triangles visible in some look and not under the bra or shorts.

Pipeline (scratch shion/, bpy 5.2.3 wheel in scratch bpyenv):
- common.py remaps the Windows texture paths.
- Rig props drive everything. They live on pose bones Properties_Skin_Default and Properties_Accessories: Suit_Jacket 0-2, Pants 0-3, SportsBra 2=normal and 1=revealed, Accessories Pants 1=gym and 2=short.
- bake.py: Cycles DIFFUSE COLOR bake per material. Objects hidden by drivers fail; those fall back to the raw diffuse.
- looks.py: geo.json/bin, Y-up, deduped meshes.
- mktex.py: builds the site files.
- tpose.py: aims FK-UpperArm/Forearm/Hand to horizontal and exports the FBX.

Preview: https://claude.ai/artifact/LUE9X3xwNMfDcpHLJ5XSR7.

Mixamo upload: zomboid-mod/assets/shion/shion_tpose_mixamo.zip.

Next: he uploads it to Mixamo and sends the 8 clips (same list as the woman). Then pack shion.zsk with pieces as toggles, and install it via folder access.

07:28 v2: SportsBra/shorts colours now baked (bake.py removes hide drivers first); paint.py fills masked skin by normalised blur (no flat patches). ALL attachments (horns, strands, harness, pistol, collar, straps, earring, belly, boots) export in every look; page opens each look with its natural pieces (geo.json 'natural'), any can be toggled. Mixamo zip rebuilt.
07:37 v3: looks exported as 'look|1' (boots) and 'look|0' (bare-foot: robotic feet or body feet), geo.json boots{} = natural boots; page swaps list on the Boots toggle. Mixamo zip = MINIMAL (Josiah: as little clothing as possible): body + sports bra + shorts + eyes/teeth/nails/brows, no hair/collar/piercings, colour attributes stripped. Hair etc get weights via add_pieces-style transfer when packing.
08:10 v4 HUMAN LIMBS (Josiah: no robotic limbs in any outfit, human limbs and feet): looks.py sets Thight_Robotic/Robotic_Knee False, skips Robotic_Hands/LowerArm/LowerArm_Jacket/Legs_* objects, and trims the body's Mask_Shion_Robotic_UpperBody vertex group to skin within 1 cm of the torso suit (it used to hide her forearms/hands). Torso bodysuit, chest plate, head/neck pieces kept. Extra Mixamo clips coming (start/stop walking) to smooth her animator.
08:35 IN GAME (merge 2e7bf78dc to playtest): 17 Mixamo clips (SHION MIXAMO.zip), 16 packed: idle, walk, run, die, aim, fire (stationary), rifle_walk, walk_start/walk_stop/rifle_stop/aim_up/aim_down (one-offs, root motion stripped), fire_walk, rifle_idle, fidget (Catwalk twist), pose (Female Standing Pose held 6 s). "Walking" left out. shion.zsk 10 MB installed in his npc folder; copy + all scripts at project files zomboid-mod/assets/shion/ (tools/: tlooks.py T-pose looks, align.py, build.py IDW weights from the Mixamo body, shion_spec.json, dec.py + index.html checker). Groups carry piece "name@look|1,look|0,..." (|0 = shoes off); client NpcSkin.lookKey picks the look from a "look=NAME" entry in HIDDEN. Server Npcs.dress(model) reads pieces/looks from the .zsk; Npcs.dresses() = has woman wardrobe pieces (only then NpcWardrobe runs). export.html fixed: inverse binds without bindMatrix, cm uploads scaled to metres, ?clips= list.
09:20 CRASH FIX: texture keys must match [a-z0-9/._-] (Identifier). build.py now writes body_nude_1001 not body_nude@1001; shion.zsk repacked + reinstalled; NpcSkin.texture now sanitises keys (playtest d6952a822). NOTE this file's name says "declined" but Shion IS in game, local-only and clothed; only revealing variants were declined.
