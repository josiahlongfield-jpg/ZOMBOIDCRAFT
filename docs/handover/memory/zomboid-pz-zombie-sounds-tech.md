---
name: zomboid-pz-zombie-sounds-tech
description: Zomboid zombie audio pack (FSB5 extraction, ZedSounds voices) and the 2026-11-01 shoot-freeze fix (ZSprites removed)
metadata:
  type: project
---

Split from [[zomboid-pz-zombies-tech]].

- SOUNDS (Josiah 09:10): Zomboid's zombie audio replaces ours completely. Source = FMOD bank ProjectZomboid/media/sound/banks/Desktop/ZomboidSound.bank (647 MB, FSB5 Vorbis, 15,102 named samples; loose media/sound wavs are old B41). Moved to the container via RC split into 50 MB parts (additions\pz-sound\small, 330 MB stage timed out). tools/pz/pz_sounds.py (own FSB5 reader; setup header found by matching libvorbis quality CRC) + tools/pz/zombie_sounds_pack.py -> resource pack ZomboidZombieSounds.zip (md5 bf9ba310...) installed in profile resourcepacks 09:40; he must enable it. Game: zombie/ZedSounds (10 voices fa..smb by UUID roll matching ZedBodies sex, states idle/aggro/chase/swing/bite/hurt/pushed/eating/reanimate/death; shamblers die silent + bodyfall), EntitySoundMixin swaps vanilla zombie ambient/hurt/death/step. Jar sounds.json pz.zombie.* fall back to old sounds. Commit c071b7818, preview pz-zombie-sounds-preview.mp4 sent 09:41, merged to playtest dd9115d2d 09:43, jar sent 10:05.
- 2026-11-01 10:40 FREEZE FIX (FPS thread relay of Josiah "worse than ever, freezes when I shoot"): ZBodyLayer still drew zbody in the shader shadow pass for zed zombies and far ones went to ZSprites.get (sheet bake 89-248 ms on render thread). ZSprites.java deleted, CrowdFx without packs draws nothing, Gibs skipped when state.zed != null (old-body limbs). Playtest fc3b31e0e; FPS thread sends the jar.
