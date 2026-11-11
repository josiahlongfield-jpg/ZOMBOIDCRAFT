---
name: zomboid-skin-style-reference
description: Art-AI body skin workflow: style reference renders of the 3D kits + stylised prompt in project files; first AI skin was too realistic
metadata:
  type: project
---

Josiah generates zombie body skins with an art AI from the turnaround template (/mnt/project-files/zomboid-mod/skin-template/). On 2026-10-26 he said the first AI skin "has too realistically of a design so it looks odd" next to the chunky 3D clothing kits. Fix given: style reference renders of the kits on the template layout plus a stylised prompt (flat cel colours, outer-edge outlines only, simplified features, mitten hands) in skin-template/style-reference/. Renders come from an offline software rasteriser in the session scratchpad (THREE shim + Chromium), since three.js is blocked offline. Related: [[zomboid-characters-direction]].

2026-10-26 02:45: Josiah sent stylised AI skin v2 as three turnarounds = mood faces: idle/shamble (closed mouth), alerted (snarl), chase (open mouth). Same body, only the face differs. Cut to skin-template/ai-skin-2/zombie_{idle,alert,chase}_*.png; kits preview v10 swaps the face by movement (scratchpad player/patch_moods.py, run after build_all.py). An in-game build would need the walker to swap face texture by AI state.
2026-10-26 03:27: Josiah sent Zombie_Boxers_15_Skins.zip (one boxers turnaround per zombie type, older pixel-blocky style, not the stylised look). Cut to skin-template/ai-skin-15/{type}_*.png (cut_skin.py + clean_gaps.py fills white edges); kits preview v11 default body skin = per-type (patch_15.py, JPEG embed to stay <16 MB).
03:40: Josiah: all kits permanently "badly worn" (pale_3: wear 1.0, blood .9) and red glowing eyes in previews. Done in kits preview v12 (patch_worn_eyes.py; eye whites auto-detected on AI skins, walker_eyes_hd masks for today's skins).
