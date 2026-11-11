---
name: zomboid-arms-posing-tool
description: Josiah's web posing tool for survivor arms holding Brita guns (artifact Ur4ZP18eXgMUDALKpCEhyZ): current state, save rules, how to add guns
metadata:
  type: project
  modified: 2026-11-06T07:34:34.394Z
---

Private artifact https://claude.ai/artifact/Ur4ZP18eXgMUDALKpCEhyZ (v41, 2026-11-06), 129 Brita guns (14 pilot + 27 wave 2 + 88 wave 3). Version history: [[zomboid-arms-posing-history]].

- Source is scratchpad tool/web/index.html (+ guns/, parts/, arms.json, body.json, holds.json, poses-backup.json). A copy of the page and the wave-3 scripts is in /mnt/project-files/zomboid-mod/brita/poses/posing-tool-src/. Gun/part files are LOCAL-ONLY data (private artifact OK, never repo/jar/GitHub).
- SAVE: db doc poses/current = {state, t} only, under a 256 KiB limit. pushCloud saves compactState(P), which drops unposed default entries and rounds to 3 decimals, and refuses anything over 250000 bytes. 2026-11-06: 14 guns posed, ~55 KB compact; all 129 posed is about 180 KB. Read it with ArtifactData get poses/current (version 3474 on 2026-11-06).
- Cloud wins if its t is newer than what the device opened with (T0). The "Restore my saved poses" button and auto-restore (when nothing is posed on the device) use the published poses-backup.json, refreshed 2026-11-06 to db version 3474.
- State P: holds[hold][mode].R (trigger hand per hold kind), guns[g][mode].{gun,L}, own, follow, att, gripHand, clip, fixes. Modes: hip, ads, tpready, tpaim.
- TEMPLATES: DEFAULT_FOLLOW maps each new gun to the gun he posed in its class; derive() carries that pose over by grip. Wave 3 uses posing-wave3.json's follow list from WEAPONS NEW.
- REAL[g] = real overall length (m); kOf uses it for part scale and TP size. FP size = the game mesh px size.
- Sight classes (sp/build_sights.py): pistols and shotguns FastFire/EFLX only; SMGs 1x; assault/battle/MG max 8x; crossbow 4x; KAC_M203/M2A1 no optics. Grips and launchers per Arsenal underbarrel lists (underbarrel3.json).
- ADDING GUNS (wave 3 recipe):
  - brita/build_guns.py (grips from grips3.json, autogrip.py then corrected by eye) builds guns/<id>.json + .noirons.json; mkindex3.py rebuilds guns/index.json.
  - Add the new ids to DEFAULT_FOLLOW and REAL.
  - Test with node allguns.js (SEED=seedreal.json OUT=dir) and sizecheck3.js.
  - More than 64 MB or 255 files: publish in two passes (gun files first with the old page, then page + indexes).
  - Watch for doubled commas when inserting table lines (broke the page once).
- M29_44Carbine: WEAPONS NEW rebuilt it at carbine size (~31 px, lever_action fit, anchor still magnum); on the page from v41 (2026-11-06) with hold rifle (HOLD_ID), grip [8,4.4,14.2], new sights, and the full lever-gun optic range (Arsenal gives it a full rail; RIFLES list, v42). mkindex3 skips ids already in index.json, so drop an entry before rebuilding it.
- On PC, open it in Brave: sliders don't respond in the Claude desktop app viewer.
- PAUSED by Josiah 2026-11-06 09:09 ("pause development"). State: v42 live, all 129 guns + M29 carbine fix done, nothing half-built; resume only on his word.
- ROUTES 2026-11-09: when this work resumes it goes to repo ENDLESS-NIGHT (com.endlessnight, after its first green build), file store /mnt/project-files/endless-night/<area>/, Modrinth profile ENDLESS NIGHT; old ZOMBOIDCRAFT is frozen (/mnt/project-files/endless-night/SWITCH.md).
