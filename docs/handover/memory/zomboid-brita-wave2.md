---
name: zomboid-brita-wave2
description: Brita wave 2 (the other 27 picked guns): pulled 2026-11-03, review page, open calls, next steps
metadata:
  type: project
---

2026-11-03 09:57 Josiah "pull them now" / 09:59 "go ahead": convert the other 27 of his 41 wave-1 picks (brita/wave1-picks.txt minus the 14 pilot guns).

- Staged from his PC (container-local, re-stage in a new session): 27 gun .x + textures, 22 extra part models (mags, Bayonet_Type30, Arsenal Switch_Striker). Texture quirks: G36C->G36.png, SCARL->SCARH.png (SCAR-L mesh is the same file as SCAR-H), SPAS12_Fixed->SPAS12_FIXED.png, Ruger_No1_308->Ruger_No1.
- Upright (no 180 flip): Automag, Colt1903, Colt_Service38, G17, HK_USP, SW_327, MP7, UZI (bore z > 0); long guns flip as before.
- Review page v1 https://claude.ai/artifact/G5dddELPz2kzEqYt4DGZHF (localStorage brita-wave2-review): look, Arsenal attachments, stats; no irons/sounds yet. Tools copied to brita/tools/wave2/ (stats2.py anchors+refs list, build_data2.py, build_parts2.py, wave2_conv.py).
- Open calls on the page: new calibres 7.62x39 (AK47), .30-06 (M1), 7.62x54R (SV_98, PKM) need round items + head x2.5 (my proposal); .380/.38 round items too; Colt Service weight 0.34 kg typo -> 1.1?; semi-auto shotguns at pump speed (Arsenal parity); .308 damage differs by anchor (FAL 20, Ruger 40, M40A3 53); AUG9 gets no optics (Arsenal lists none); MP7 fires 9mm per Arsenal; Glock auto switches.
- Irons: refs requested from REFERENCES 2026-11-03 (brita/irons-ref-request-wave2.md, folders under refs/items/weapons/irons/). Sounds board still to do.
- HOLD still applies: nothing to his PC/game until he approves.
Related: [[zomboid-brita-weapons]], [[zomboid-brita-irons-tech]], [[zomboid-brita-loader-tech]].
- 10:09 Josiah: ALL 27 GOOD (the page's numbers and my proposed calls stand). Do NOT add them to the posing/position editing page yet; he wants to finish the guns already posed and get those into the game first. Told the survivor arms thread (owns the posing tool).
- 10:41 irons for all 27 built: brita/tools/irons/irons2.py (python3 -I irons2.py [gun]; writes local-pc/guns/). Flip-up rule (pack flip-ups) on MP7, F2000, SCARL/SCARH, M82, M40A3 (back-up, ships scope-only), UTS15, AR15 rear. Off-centre bores: M1 x 7.68, M40A3 7.75, SV_98 7.8, M60 9.05, PKM 8.28. Uzi front is between Brita's front ears (z 6.4), cocking knob kept. Preview video brita/previews/brita-wave2-iron-sights.mp4 (vf3.py+mkvid3.py in tools/wave2) sent for his OK. Sound board next.
- 11:21 2026-11-03: Josiah: after the Photon scope fix and the 14 posed guns are in game, put the 27 wave-2 guns on the posing page (lifts the 10:09 hold). Poses->game converter: /mnt/project-files/zomboid-mod/brita/poses/to_game.py writes config/zomboidcraft/guns/poses/<id>.json (client GunPoses).
- 11:35 step 1 (FP posed guns) merged to playtest 991ca3d (GunPoses + SurvivorArms.drawPosed, finger split, forearm-down cut); pose files + newer Honey/AR57/BOAR meshes copied to his config. Full-detail cloud preview harness: scratchpad fpport/ (run_tool.js headless tool, gameview.py, full_export.py + full/page.html). Scope fix 7802aed: shader pack -> whole-view zoom (ScopeView.mode via RailLights.shaderPackOn).
- 11:52 Josiah: PAUSE this thread's development after the scope fix + step 1 jar (7c8df16) is delivered. Queued when he resumes: step 2 (third person tpready/tpaim), step 3 (attachments), 27 wave-2 guns onto the posing page (via survivor bodies thread, which owns the tool), wave-2 sound board.
- 12:3x 2026-11-03 STEP 2 (third person) + STEP 3 (attachments) built on branch (commits 11f7b4b, 46ed663), NOT in playtest yet: previews posed-guns-step2-third-person.mp4 / posed-guns-step3-attachments.mp4 sent, awaiting his OK + decision card (items you fit vs always fitted; recommended items). On OK: copy brita/poses/game-v2/*.json -> his config guns/poses, brita/local-pc/parts/* -> guns/parts, guns-noirons-game/*_noirons.zmesh -> guns/, merge to playtest, jar, then PAUSE. Tools: poses/to_game.py (numbers + pack + attachment runs), tools/attach/pack_parts.py; tool runs scratch fpport/attstates + run_tool.js (one sight/grip per gun per run). TP poses Spine1-relative pack space (tool frame = pack turned 180 about y, -1.62 y). 27 wave-2 guns published on posing page v34 by survivor bodies thread. COMMANDS told about /zc localgun part <id>.
