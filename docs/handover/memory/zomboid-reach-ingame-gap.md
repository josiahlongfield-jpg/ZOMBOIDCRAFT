---
name: zomboid-reach-ingame-gap
description: 2026-11-10 evening: Josiah found the in-game Reach world looks nothing like the previews; causes found with the debug view, fixes, PC-use OK
metadata:
  type: project
  modified: 2026-11-10T10:11:23.060Z
---
- 19:16 first full test (jar f0f1de8, 12 GB after OOM at 6): "nothing renders properly" vs previews (cloud real.html; in-game path never checked area by area).
- Tour 19:30: blown whites, pink/black patches, no Halo skies, flat land, grey walls, flat water, Exodus floor copies.
- DEBUG VIEW FINDINGS (19:55): decals had no light -> ReachDecalLight (f23432009); fixed exposure blew sunlit views white -> ReachMeter auto exposure (6ec3ea28f, reach.json "meter": [target, emin, emax]).
- 2026-11-10 20:40 jar c7838bf: Halo skies exported on PC (tools\reexport\pc_skies.py -> world2\sky\<m>__<sky>.glb, reach.json "sky" abs paths; backup additions\jar-backups\reach-before-sky.json) and showing in game. Meter worked (log: mean log E -2.2 at ToTS, exposure 5.0) but still too bright: preview metered whole picture incl. sky glow + real albedo. Fixed 4c76e81ff (albedo from picture means, fog, sky glow). debug.txt 8 = fixed exposure, 9 = log meter every 2 s. Air choice now by level piece ground box (m30/m35 share offset).
- 21:10 jar 4c76e81: ToTS matches preview cand m35_5 at same viewpoint (cams real/web/<m>_cands.json; world = local + offset). Winter Contingency white slabs + upside-down hill = untextured prop helpers m10_020_cap/m10_030_cap -> skipped in 49f710238. Preview sky background = clear blue 0.55,0.66,0.80 (ReachSkies stand-ins differ, e.g. ToTS tan). Overwrite transit/farmask/debug5.txt is now "9".
- Debug files: transit/farmask/debug0..5.txt; commit to config\endlessnight\reach\debug.txt (read each second).
- PROJECT FILES FULL: new files 404 on device_commit_files; overwrite existing synced files (transit/farmask/*) instead.
- JAR INSTALLS (Josiah 20:18 "yes you may"): private repo -> actions_get download_workflow_run_artifact signed URL (~10 min), PC session curls+unzips, old jar to additions\jar-backups. Needs RC approval card.
- PC use OK (19:28/19:35). javaw app; menu Continue + Enter; experimental button (952,400) in 1456x819.
- 21:30-21:45 tour (jar 4c76e81): NA tower inside (cand m52_0) and Exodus valley (m50_16) match previews. Exodus dark sky slabs = ReachSkies stand-in colour through gaps in Halo sky layers -> fixed 4f0b9cf (playtest): under a Halo sky, SKY/FOG colour = preview clear 0.55,0.66,0.80, CLOUD_COLOR/SUNRISE alpha 0, stars 0 (also removes MC square clouds). Not yet installed.
- Previews' outer cams (e.g. m52_16) sit on ReachLand LAND cells: _000 clipped there by design -> flat climate-colour land (vanilla pipeline, no textures) = the in-between land job (priority: after campaign+MP). Lone Wolf: config 'only' m70_bonus_epilogue, which doesn't exist (no Lbsp) -> nothing drawn; world2 has no m70_bonus_000.glb; needs PC re-export + placement (needs Josiah's approval). Package lab cam m60_0 blocked by a closed door prop.
- 2026-11-11 05:15 (his clock): jar 0.5.2+160ba25 installed; skies back on.
- Josiah 2026-11-11 05:20: wants IN-GAME screenshot previews of any big game improvement from this work. Computer use now needs Modrinth App + javaw.exe (resolve by basename while running) + File Explorer (taskbar, game starts minimised).
Related: [[zomboid-reach-shader-tech]], [[zomboid-reach-world-tech]], [[zomboid-reach-priority-order]].
- 2026-11-11 06:58: jar 81cbd2c in (sky backdrop). Comparison page DRKt89iPdKMgCp5qjNDLL4. WC hole FIXED+INSTALLED: m05 footprint now only where m10_008 is drawn (PC grid, eroded 16 m) -> land.zrh md5 9e686e97, m05.zrv 431ec814 (tools scratchpad m05fix/clip05.py+landpatch.py; old files in cloud halo/place3). Raising land to render rock tops made a huge grey wall: rejected. Left: thin gap under hill edge; plain land vs preview's snowy valley = land job.
