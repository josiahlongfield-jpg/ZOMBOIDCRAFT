---
name: zomboid-survivor-clothes-tech
description: How the PZ survivor bodies wear Zomboid's own clothes (packs, clothes_map.json, SurvivorClothes), and what's pending (2026-11-01)
metadata:
  type: project
  modified: 2026-11-01T05:12:06.371Z
---

Survivor bodies (Tomb/Goth/Detailed/Evil's Ink, local-only packs pz_survivor_f/m.zsk in config/zomboidcraft/npc) wear Zomboid's own clothes. Josiah chose this on 2026-11-01 ("Zomboid's own clothes").

- Mapping: src/main/resources/assets/zomboidcraft/survivor/clothes_map.json maps our 253 wearables to PZ clothingItems. It holds names only, no PZ data. Optional flags: camo, plate, near (stand-in), colour (#hex tint).
- Packer: tools/pz/survivor_pack.py clothes() adds the models, texture choices, painted-on body textures and union skin masks to the catalog's "clothes". Set PZ_MEDIA_EXTRA to give extra media roots, which are searched case-insensitively. Static PZ pieces are rigged to their attach bone.
- Game: client/survivor/SurvivorClothes builds the dress. Tint rolls are seeded by player UUID and garment. Camo uses our tiles from tools/pz/camo_tiles.py, written to textures/survivor/camo. Crusader plate gets a finish tint. Hat category swaps hair to PZ's hat alternate (nohair/nobeard hide it). HeadGearLayer skips mapped helmets on survivors, so NVG goggles don't show on survivor heads yet.
- Dressing-room preview: https://claude.ai/artifact/MaGwmRQ7NHRjUuJfq4HsiC
- Josiah 2026-11-01 05:24: any hat/helmet/hood hides the hair outright (done, commit 84187197b, CI green). Asked for more detailed clothes; detail-pass decision card posted (builder pass / +30 hand-painted / all by hand / bought pack). He worried a hand repaint would look like the blocky Minecraft-model clothes. An example repaint (jeans, field jacket, army boots at 1024: weave, stitching, pockets placed from texel 3D position, wear, grime) is in the dressing room behind a Paint switch. Script: scratchpad repaint/repaint.py (not in the repo). Awaiting his verdict.
- Pending as of 2026-11-01 05:15: packs not yet copied to his PC (Modrinth folder access was denied once). Merge to playtest waits for him to see the preview ([[zomboid-video-preview-rule]]). PZ zombies (Zomboid bodies + clothes on zombies, red glowing eyes that go out on death) were moved to their own thread at Josiah's request 2026-11-01 05:58; this thread keeps player models only.
- DECIDED 2026-11-01 05:45-05:49 (Josiah): builder pass on all 228 (tools/pz/cloth_paint.py, run at pack build; PZ_PLAIN=1 skips it), then hand-paint the 30 most-worn (cloth_paint.HAND, keyed by PZ texture path), and do the rest by hand incrementally in later batches. My estimate to him: ~10-15 min per piece, all 228 about a week of sessions.
- 2026-11-01 06:50: packs (hand-painted 768, rest 512, f 26.0 MB, m 19.0 MB; the PC copy takes files up to ~25 MiB) are on his PC in MEDIEVAL GLOOM (1)/config/zomboidcraft/npc (folder access to ModrinthApp\profiles granted 06:31). Copies in /mnt/project-files/zomboid-mod/survivor/packs/. Merged to playtest 23b0f74c7 on his "merge" 06:50 (CI green, release 0ef0cb27 contains it). Next: further hand-paint batches when he asks.
