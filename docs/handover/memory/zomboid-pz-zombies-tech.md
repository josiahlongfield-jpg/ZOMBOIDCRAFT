---
name: zomboid-pz-zombies-tech
description: PZ zombie bodies thread (2026-11-01): Zomboid bodies/outfits/clips on zombies, red eyes, Project Real Limbs port; data locations, branch, decisions
metadata:
  type: project
  modified: 2026-11-01T06:12:56.516Z
---

Thread "Add Zomboid zombie bodies to the game" (branch claude/project-thread-910w9i, based on survivor branch claude/project-thread-asz16x + playtest). Split from survivor thread 2026-11-01 05:58. Survivor tech: [[zomboid-survivor-clothes-tech]].

- Plan: tools/pz/zombie_pack.py (imports survivor_pack.py) -> pz_zombie_f/m.zsk (local only, config/zomboidcraft/npc). Body = survivor body mesh, skins = Zomboid {F,M}_ZedBody01-04[_level1-3], outfits from media/clothing/clothing.xml + fileGuidTable.xml, clips from anims_X/Zombie (~180 on his PC). Our 15 ZombieLook outfits map to Zomboid outfit names (repo JSON, names only). Near zombies get PZ body, far keep zbody/baked low.
- Red glowing eyes that fade out on death (Josiah 03:23, kept from current zombies).
- Folder access approved for this thread: Steam common\ProjectZomboid and workshop\content\108600.
- Josiah 06:07 asked to add Project Real Limbs (workshop 3827957976, mods/RealLimbs, v0.5.0, Java jar, 17-part physics body, staggers/falls/get-ups, own sandbox options) and ZombieBuddy (3619862853: just PZ's Java mod loader, nothing to port). Decompiled with vineflower 1.10.1 (28.6k lines). Order: PZ bodies first, then Real Limbs port on the PZ skeleton.
- DECIDED 06:15: Real Limbs as a setting: shots only flinch living zombies by default; a sandbox switch turns on Real Limbs' full shot physics. Everything else in Real Limbs applies regardless (wave 2).
- Code (2026-11-01): client/survivor/ZedBodies.java (look rolled from WalkerRenderState.variant so zombie and Corpse match; moves; eyes on head bone, fade 1 s on death), zombie/ZedLayer, state.zed in WalkerRenderState and CorpseRenderer.State; ZBodyLayer/Bolts/BodyWeapon skip when zed. Falls back to zbody when far (look.pzZombieDistance 24), dismembered, held, or no pack. Config look.pzZombies.
- Build packs with PZ_MEDIA_EXTRA=<base game media> (hair textures and clothing*tint base textures live at media/textures root). Painted build ~40 min per sex.
- Survivor thread tar: /mnt/project-files/zomboid-mod/pz-extract/media.tar (base/media, extra/media = PZ_MEDIA_EXTRA, pzmods/108600 Tomb/Goth/Detailed/Ink).
- RULE Josiah 08:14: never show a Minecraft/zbody zombie once PZ zombies are in, incl. fallbacks (dismembered, held, no pack, swaps). 08:13 he REVERSED the 06:44 call: EVERY zombie must look Zomboid ("wont accept 10 zomboid zombies and then 100's of minecraft ones"). Plan sent to this thread by FPS: PZ-look LODs (full near N, decimated PZ mesh mid, PZ sprite card far, cheap shadows); pzZombieMax caps only the full level.
- 08:53 Josiah REJECTED far picture cards for zombies ("terrible, scopes magnify pictures"): never use sprite/billboard zombies; far = level-2 PZ mesh, crowd = shared pre-skinned level-2 frames.
- PAUSED 10:37 by Josiah (all threads but FPS); he reopened this thread 2026-11-02 07:09 with the one-leg bug.
- 2026-11-02 HOP: one-legged zombies looped Zomboid's stumble (a trip-to-floor clip) = lie down/get up every step. Fix: client/survivor/ZedHop.java lays a procedural hop (lean, sink, push-off, planted foot by 2-bone IK, arms up, stump lift) over idle/lunge_idle. Preview sent 07:25; merged to playtest 252bfa719 07:46, jar sent 08:03; Josiah 09:05 "looks good" (done).
- Sounds and the 2026-11-01 freeze fix: [[zomboid-pz-zombie-sounds-tech]].
- Pack install, roughing-up and limbs history (2026-11-01 07:30-08:05): [[zomboid-pz-zombies-history]].
