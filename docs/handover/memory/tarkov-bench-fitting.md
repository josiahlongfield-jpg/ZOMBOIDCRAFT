---
name: tarkov-bench-fitting
description: Josiah's 2026-11-07 06:10 go-ahead to make ALL pulled Tarkov attachments game ready for bench fitting; how it's built, where files are staged, what's left
metadata:
  type: project
  modified: 2026-11-07T06:50:42.703Z
---
ASK (Josiah 2026-11-07 06:10): "prepare all attachments that you've pulled to be game ready remembering all the adjustments we've made and rules that have been set".
Rules honoured: [[tarkov-sight-switch-tech]], strict grip rule [[tarkov-grip-holds-tech]], irons at start, scope PiP/1x, local-only Tarkov files (never repo/jar), previews before builds, inspect on its own key.

BUILT (branch claude/project-thread-5z3m2y, commits dc5ad7f23, 946d001ca, 4b88015b2; NOT yet merged to playtest, waiting his OK on previews):
- 673 parts (672 models tkp_<id>.zsk) for the 10 batch-1 guns. Java: combat/TarkovParts (parts.json), TarkovFit (resolver: Tarkov slot filters, mounts/adapters auto, kit places preferred, retry pass so chassis stocks carry handguard/grip), TarkovPartItem, 10 new bench slots; client TarkovRig/OperatorView/OperatorLayer/TarkovPartRenderer; BenchPage compact list for many sockets.
- All 10 kits rebuild exactly; every catalogued part reachable at the bench (some only after swapping a parent, as in Tarkov) except 3 SVDS buttpads (3 links deep).
- Magazines: exported+packed but NOT bench parts yet (capacity/GunState deferred).
- AK-102 FP model fixed 2026-11-07: kit muzzle brake sat 10 cm off the barrel (fp_ak102.json patched, zsk repacked).

STAGED for his PC (/mnt/project-files/zomboid-mod/tarkov/local-pc/): guns/tarkov/parts.json, guns/tarkov_*.json (with "tarkov" block), npc/operator_tarkov_ak102.zsk (repacked), npc-tarkov-parts.zip (672 tkp zsks -> config/zomboidcraft/npc/). COPIED to his PC 2026-11-07 08:21 (all batch-1 guns/npc/hands/reloads, parts.json, motion.json, new operator.zsk, 672 tkp zsks, sound pack) via 7 x 23MB zips (device_commit_files reads max 25 MB per file) + RC Expand-Archive into the profile.
Previews: /mnt/project-files/zomboid-mod/tarkov/previews/bench/sheet_<gun>.jpg. Scripts (python twin fit.py, preview.py renders real zsks): /mnt/project-files/zomboid-mod/tarkov/tools-b2/.
Related: [[tarkov-weapon-batch1]].
MOVEMENT (07:11 ask, 2026-11-07): Gait.java (Tarkov paces, guns.tarkovPace), TarkovMotion (motion.json, gun = root displacement+turn about the base bone pivot), TP walk/run/sprint clips in new operator.zsk; commits f799e5c22, 01812590a; preview https://claude.ai/artifact/PCGaa9UXTKtXeCRRa5reJB awaiting OK. motion.json + new operator.zsk on his PC since 08:21.
LIMP FIX (08:4x): walk_aim_0 head bob dips unevenly on alternate steps (rifle-side lean) and read as a limp in FP; motion.json walk/run now symmetrised over a half cycle (y/z/pitch averaged with +12 frames, x/yaw/roll antisymmetric). Backup tk/motion_asym.json. walk_aim_0 legs/pelvis are symmetric; run_aim_0 legs equal walk's. _top/_down clips are 1-bone aim-pitch layers.
