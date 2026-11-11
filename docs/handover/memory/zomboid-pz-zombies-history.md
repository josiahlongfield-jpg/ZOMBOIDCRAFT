---
name: zomboid-pz-zombies-history
description: PZ zombie bodies 2026-11-01 morning: pack install md5s, roughing-up fx, stumps, LOD cap, fx pack parts on his PC
metadata:
  type: project
---

Split from [[zomboid-pz-zombies-tech]].

- 2026-11-01 07:30: painted packs installed in his profile npc folder (md5 f 6cfaeb1e..., m 24c0f69e...); .part leftovers in additions\pz-zombie\pack (delete blocked; he can remove). Device commit limit 25 MB/file: split to 20 MB parts + RC join. STAND_IN map in ZedBodies for 4 items with packed-only textures. Video preview /mnt/project-files/zomboid-mod/previews/pz-zombies-preview.mp4 sent; awaiting his OK to merge to playtest.
- Josiah 07:36: merged; after seeing in game they may need roughing up ("very normal human"). Offered: Zomboid blood/dirt on clothes+skin by body part, holes in clothes showing wounds, more rotted skins. Awaiting his in-game verdict.
- 07:45 clean PZ zombies in playtest 0c1f1ab32 (jar sent). Roughing-up (tools/pz/zombie_fx.py: zfx/blood1|blood2|grime|hole/<Part>; SurvivorTextures KEEP/REVEAL; look.roughZombies) on branch, preview pz-zombies-rough-preview.mp4 sent ~07:52; fx-patched packs at /tmp/claude-0/zout/fx (container only) awaiting his go to install + merge.
- 08:05: Josiah saw most zombies as old body + dismembered swap. Fixed on branch: textures composed at 512 (SurvivorTextures.body(pack,layers,size)); collapse() shrinks cut bones (Dismember bits) into the joint; one leg -> "stumble"; cap look.pzZombieMax 10 via ZDetail.nearestSq (FPS thread's file, accessor only); NpcSkinDraw pairs tris into quads (-46% verts; FPS thread asked, survivor thread agreed I own it). Limbs preview sent; awaiting go to install fx packs + merge.
- fx packs staged as 4 parts each in additions\pz-zombie\fx (md5 f 2c7c162f75bedc748fcad7d085b8e6eb, m f7805dbe8e04745b928382cffeac8c09); RC join into profile npc on his go.
