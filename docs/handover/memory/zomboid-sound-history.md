---
name: zomboid-sound-history
description: ZomboidCraft sound rounds 1-12: picker artifact URLs and what Josiah picked or rejected each round
metadata:
  type: project
  modified: 2026-10-25T09:53:39.154Z
---

Log split out of [[zomboid-sound-design]] (2026-10-25). Picker pages store picks in db collection "picks", one doc per event (dots -> underscores), clips[] + note; read with ArtifactData list. Each round is a NEW artifact (255 files per publish; publish again to add more).
- R1-3 zombies: https://claude.ai/artifact/F291crDurHiKDZpJQaFsTj. Human sounds (choking, rasps, moans), not growls. All horde recordings rejected, so hordes are MIXED from idle voices (dormant) and chase/alert/aggressive voices (angry), "not too overpowering". KEEP dict in build_zombie_sounds.py. Open: less-human alert screams (Josiah sourcing spotting screams).
- Rifle: Freesound 421710 for shot and shot_distant, +3 dB baked (MC clamps gain at 1.0). Suppressed rifle/pistol from Sonniss (Sako TRG22, De Lisle, Gamemaster).
- R4 loot: https://claude.ai/artifact/WkvvhHXdy7xcx14hbUjTYj. Containers get ONLY open/close sounds, no rummaging. Zombies were "incredibly loud", so -6 dB.
- R5 UI: https://claude.ai/artifact/2SqD1eBN9Fhdy5XE5S9zM9. Physical foley, no bleeps. ui.tab = light switch, ui.wear = buckle (fanny pack). Josiah supplies ui.drop and ui.skill.
- R6 ambience: https://claude.ai/artifact/FJes8oxh25XzFrWAapcBda. Wanted heavier hail, far animals, car/house alarms.
- Occlusion: Josiah declined Sound Physics Remastered; we built our own Muffler.
- R7 ambience 2: https://claude.ai/artifact/EMmSzx3biJoiFpkww4nkpz (hail2, alarm; unpicked). 2026-10-26 Josiah: NO animal sounds (crow, owl, seagull bed removed, 0280231). Shipped round-6 ambience in playtest request 33d7654.
- R8 cars: https://claude.ai/artifact/MDso4csQs6mtqMmaFf64Z8. Scratch clips8/, zpick8/. Also 2026-10-25: weapon.bullet.flesh range 16 -> 64 (Josiah wanted hits heard further), b5fbd35.
- R8 built 937dd36, in playtest 1b47355 (cars thread wired start/stall/idle pitch by speed/horn/crash/doors).
- R9 result: Josiah picked sx6cc09fea@7 for idle and driving (no car.drive), 168f91d. R10 guns: https://claude.ai/artifact/BN2LWT8x6hRxTJRoBvYVKJ ("Dead Air Pistol & Shotgun Picker", 171 clips; db picks weapon_pistol_shot/_shot_distant/_dry/_mag_out/_mag_in/_slide, weapon_shotgun_shot/_shot_distant/_pump/_shell_in).
- R10 built 4683c31 (tools/audio/build_gun2_sounds.py). R11 hunting rifle: https://claude.ai/artifact/Y12ckZkKCgzzdvVqQ9nffi (db picks weapon_hunting_rifle_shot/_shot_distant/_dry/_bolt/_mag_out/_mag_in; mag moments share one pool). Scratch clips11/, zpick11/.
- R11 built daaa319 (build_hunting_rifle_sounds.py; shot +4 dB limiter; bolt trimmed to 0.40-1.25 s, the weapons thread retimed the animation to it). Merged into combat. R12 suppressed hunting rifle: https://claude.ai/artifact/9ucWvBkjA5g5cmnckvd2kU (db pick weapon_hunting_rifle_shot_suppressed). Josiah then said to just reuse the AR suppressor sound: shot_suppressed(+_distant) point at weapon/rifle/shot_suppressed files, att 48 (a5a4c9c). R12 picker is moot.
- R13 classics (branch classics): https://claude.ai/artifact/TcJGSnMYqhLtR2vjNna63s; db picks weapon_double_barrel_shot/_shot_distant/_dry/_open/_shell_in/_close and weapon_revolver_shot/_shot_distant/_dry/_cock/_open/_eject/_round_in/_close. Clip files live under pool folders (double_barrel.action, revolver.small, revolver.cylinder); scratch clips13/, zpick13/.
- R14 Gamemaster (paid packs): FULL page https://claude.ai/artifact/VonMFjCeJDCZM9hXPwPFE7 (all 828 sounds, unaltered; Josiah: never cut short or alter, show ALL options). Picks keyed by clip key '<pack>/<stem>'; one sprite ogg per section (off/dur), scratch gm15.py. First clip 'current' = what ships now. Raw: /mnt/project-files/weapons.zip. Raw files never go in the repo. Built 1e95e68 (tools/audio/build_gamemaster_sounds.py, takes used whole). AR bolt + suppressed + distants kept.
- Loop Bench editor and the far-off echo lesson: see [[zomboid-loop-bench]].
