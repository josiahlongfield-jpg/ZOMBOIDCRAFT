---
name: tarkov-weapon-batch2
description: Josiah's second batch of Tarkov guns (ticked 2026-11-07 07:34 in the weapons list doc), to be done exactly like batch 1
metadata:
  type: project
  modified: 2026-11-07T07:35:02.119Z
---
ASK (Josiah 2026-11-07 07:34): "added some more guns to the gun list (checkboxed) ... pull and add in the same way you've done for the last guns".
New ticks in doc SmFemxnSpHc8JhvxS1GLMo (rev 43): HK USP .45, Desert Eagle Mk XIX, KRISS Vector (9x19/.45), SIG MCX .300 BLK, FN SCAR-H (black/FDE), SR-3M Vikhr, VSK-94, Remington RSASS, AK-50, AA-12 (gen 1/2). Unticked after toggling: Desert Eagle L5, MCX Spear, Scythe.
"Same way" = [[tarkov-weapon-batch1]] pipeline (full kit from SPT presets, reloads with his anim rules, sounds, previews for OK incl. ADS before reload) + [[tarkov-bench-fitting]] (all their attachments as bench parts), local-only files.
Variants chosen (told him 07:38, can swap): Vector .45 (5fb64bc92b1b027b1f50bcf2), SCAR-H black (6183afd850224f204c1da514), AA-12 Gen 2 (67124dcfa3541f2a1f0e788b). He OK'd the list 07:37.
Kits drafted 07:4x: /mnt/project-files/zomboid-mod/tarkov/batch2/kits.json (scratch tk2/kits_b2.json + kit_b2.py). AK-50 has no SPT preset (kit hand-built: AX-50 mount + ATACR, MBUS irons, bipod).
Bundles: 238 new (scratch tk3/need.json). device_stage_files can't reach the Steam install (max 7 folders deep), so copy_batch2.ps1 + batch2-list.txt were written to additions\tarkov-pilot and a Remote Control session asked to run it (copies into raw\ with flat names, writes batch2-sizes.txt); then stage from raw.
Bundles all in scratch raw1 (08:1x; staged via RC-made zips additions\tarkov-pilot\packs\b2_0..2.zip + pack_batch2.ps1). Kit export: tk/b1/kitexp_b2.py (TURN fix: Deagle Mk19 receiver root identity), shots tk/b2shots, page https://claude.ai/artifact/8rEXe85MURXjDFveYsgz5p sent 08:2x awaiting OK. SCAR-H kit uses 16in barrel. Next after OK: builds like batch 1.
BUILT 09:4x (kits OK'd 08:48): scratch /tmp/claude-0/tk/b2b (pack/ zsks, local/guns + reloads cues, mkguns.py, vids/plan2.py+run2.sh) and tk/b2 (208 new tkp parts, parts_game.json = new parts.json). AA-12 = pellets 8 + ammo shotgun_shell; LocalGuns makes a magazine gun with own pellets>1 use ShotgunStats (commit 120c0aaef on branch, not yet merged). Preview page https://claude.ai/artifact/QpM8ocZrBrUULEdezqNwpn sent, awaiting OK. After OK: stage to local-pc (npc zsks, guns/*.json, guns/reloads cues, guns/tarkov/parts.json, 208 tkp zips, sound pack tk/b1/pack_snd), copy to PC, merge to playtest. Flip-up irons stay up behind optics (co-witness); offered Tarkov-style folding. Unreachable at bench: Vector/MCX TCS 20, TCS 12, MOE RBP; SCAR-H ACE pad, PRS GEN2; VSK-94 Mark 5HD.
