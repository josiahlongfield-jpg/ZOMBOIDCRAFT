---
name: zomboid-mannequin-run
description: Josiah's first solo Mannequin run (2026-10-31): .blend quirks, weight painting learned, Mixamo rig with his own weights works
metadata:
  type: project
  modified: 2026-10-31T08:42:25.408Z
---

2026-10-31, Blender tutorial thread. Mannequin run sheet: https://claude.ai/artifact/LZ3jaerKbnyaYPQcbSoyB6. Related: [[zomboid-blender-tutorial]].

The mannequin .blend has three parts: BaseModel, BaseFace and Eyes, plus an Armature that is not linked to them. Its vertex groups don't match the bone names, so it was deleted in his copy.
- Ctrl+J broke the face material because it is object-linked, so the parts were kept separate.
- Export all three parts together. BaseFace was missing from the first FBX.

Mixamo auto-rig squashed the chest because LeftShoulder/RightShoulder weights ran down to mid chest. Josiah learned weight painting to fix it:
- Imported Mixamo's T-pose FBX and painted Spine2 over the chest, with Auto Normalize on.
- Used Blur on the hard edges, and the Rest/Pose Position toggle to check his work.
- Re-exported with Armature + Mesh, no leaf bones, no bake, then re-uploaded. Mixamo skips the markers and keeps his weights.
- 08:42: worked perfectly.

09:17: the export worked after he updated tools/npc/skinned from origin/playtest (his old export.html ignored skin= and took Rebecca's Breathing_Idle). The meshes show the model is really "Rosanna" (BaseModel(Rosanna).TaihouSkin_Rework, a game-style fan model), so it's LOCAL-ONLY and never goes in the jar. Skip the OH_Outline_Material meshes (outline shells). Clips: mq_Bashful (skin), mq_Breathing_Idle, mq_Firing_Rifle_walk, mq_Rifle_Idle, mq_Kneeling_Idle.

Next: pick the mannequin poses and download the clips. The mannequin shop code is waiting on his pose list.

2026-11-08: NO OneDrive (Josiah's rule). The mannequin folder moves from C:\Attachments\OneDrive\Desktop\mannequin to C:\Dev\Zomboidcraft-Desktop\additions\mannequin. Use that in every command (the pack cd, the textures, the spec) and in the Blender Save As.

2026-11-09 SWITCH (/mnt/project-files/endless-night/SWITCH.md): Reach-world work goes to the ENDLESS NIGHT Modrinth profile, the ENDLESS-NIGHT repo and the endless-night/ file store. The ZOMBOIDCRAFT repo is frozen. The mannequin shops are county world (they stay in MEDIEVAL GLOOM). Any companion or voice work for Reach goes to the new routes.
2026-11-09 09:33: ENDLESS-NIGHT ready. Companions were dropped from it, so this thread's NPC skin, voice and mannequin work has no Reach destination and stays with the ZOMBOIDCRAFT county world. Nothing from this thread needs pushing to ENDLESS-NIGHT; the zombie follow-through came across with the move.
