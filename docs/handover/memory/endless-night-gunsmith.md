---
name: endless-night-gunsmith
description: Operator weapon customisation (Gunsmith) in Armory/New Soldier, replaces /localgun; Josiah's CoD reference and open questions (2026-11-09)
metadata:
  type: project
---
Josiah 09:10 2026-11-09: Tarkov operators get full weapon customisation in character selection + Armory (replaces /localgun).
- 09:14: gun must fill most of the screen like Call of Duty's Gunsmith. 09:24: don't angle the gun (flat side-on), click-and-drag spin, gun must be fully solid (mock-ups looked see-through).
- 09:32 EXACT REFERENCE: MWII EBR-14 Gunsmith screenshot, /mnt/project-files/endless-night/ui/refs/josiah-cod-gunsmith-ref.png: slot callouts placed AROUND the gun by their part (icon tile + name/slot when fitted, round + when empty), dark workbench backdrop, stats bottom left, receiver card bottom right. Clicking a callout opens that slot's part tiles with camera zoom.
- Mock-ups by UI thread in endless-night/ui/previews/ (gunsmith-*.jpg, armory-v3-*.jpg). Parts fit by bench Tarkov rules; stat numbers placeholders (real ones need his Tarkov install).
- 09:40 Josiah: PRIMARY + SIDEARM; ALL PARTS UNLOCKED; slot-pick page (pic 3) must fit the MWII reference too. 09:43 (c): loadouts change mid-game ONLY at the base the player sets up (plus menu). Build only after his OK on the redone mock-ups.
- 10:06 Josiah: NO spin (flat side-on only); every gun auto-scaled to fit with no collision with labels/UI; detail as high as Tarkov's own selection/inspect screens (LOD0, full-res PBR textures, studio lighting).
- 10:06 APPROVED: "make those changes and build" -> UI thread builds on ENDLESS-NIGHT; in-game preview before merge.
- 11:28 PAUSED by Josiah ("Pause development for now"); UI + Forge threads told to stop at a clean point. Resume only on his word.
Review thread: "Review Armory previews". Related: [[zomboid-main-menu]], [[tarkov-bench-fitting]].
- PAUSED 2026-11-09 11:27 (Josiah: "Pause development, I'll sort this tomorrow"). State: Gunsmith + soldier-selection fix (af74b4443) in playtest, green. His ENDLESS NIGHT mods has jar 2f73805 (Gunsmith, no soldier fix); af74b44 jar downloaded to C:\Dev\Zomboidcraft-Desktop\JARS\endless-night-playtest-af74b44.jar, NOT swapped in. In-game fit check (Operator Armory > G > F9) never run: computer-use Allow needs him at the PC. Old jar backup: additions\pack-backups-20261108\old-jars\endless-night-playtest-b52549a.jar.
- RESUMED 2026-11-10 06:59 UTC (Josiah "resume"): UI to swap af74b44+ jar into ENDLESS NIGHT and run the 11-gun fit check; Forge resumes Armory station.
- 2026-11-10 07:27 IN-GAME FIT CHECK DONE (jar b00c2dd, 58 builds/29 guns, all callouts placed). Shots /mnt/project-files/endless-night/ui/fitcheck-20261110/. Fixed in 8463d4b95: title no-go box now measured; list pictures clear of names. OPEN: optic floats on AA-12/MP-18. Armory kit armory.zip had to be copied into profile hud\ (installed 07:21). Computer use: grant "Modrinth App" + msedgewebview2 full path + javaw.exe (resolve once running); Modrinth window may need Josiah to open; main-menu mouse hover selects rows wrongly, use keys (S/Enter) after a focus click on the right side.
- 2026-11-10 07:31 Josiah: gun pictures looked messy (flat item icons). 984c609d3: list/receiver/callout/part-list pictures now 3D studio renders via GunFigure (parts via TarkovPartRenderer.drawUnit). Tarkov has no separate display models (inspect/icons render from LOD0). Optics on AA-12/MP-18 checked: NOT floating (UTG on tall riser; MP-18 Leupold on rings).
