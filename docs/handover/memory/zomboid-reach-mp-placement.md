---
name: zomboid-reach-mp-placement
description: Reach MP/Firefight maps worked into mission areas (rules 2026-11-09 20:03-20:05), Update 14 placements, Covenant spawn-site contract
metadata:
  type: project
  modified: 2026-11-09T20:23:30.690Z
---
- RULES (Josiah 2026-11-09 20:03/20:05): every mission stays whole. Each MP/Firefight map is worked INTO the area it represents (overlap/merge allowed if it reads better), joined by terrain/roads/doors/bridges, never a separate island. Anniversary remakes stay hidden.
- UPDATE 14 (preview page 1YWUtHBKxMq6zA2MLw7xjt) APPROVED by Josiah 20:30 ("look good, tweak later"): 22 entries in /mnt/project-files/endless-night/tools/mpmap/placements.json (Halo x,z; mpmap.py draws the map). Highlands across bay W of Sword Base via causeway; Powerhouse = hydro town on canyon floor 260 m downstream of Varna Spillway (2370,4264); Countdown inside Sabre launch facility; Reflection = Ivory Tower penthouse; Boneyard at PoA dock; Breakpoint on Package glacier; Tempest SE coast; Forge World offshore island E of New Alexandria; Zealot, Anchor 9/Condemned, Corvette FF in orbit. Outpost/Holdout/Courtyard/Unearthed are guesses.
- COVENANT SPAWN SITES (agreed 20:13 with thread "Bring Covenant enemies into Endless Night"): every structure/base placed between areas writes to /mnt/project-files/endless-night/covenant/spawn-sites.json (array of {site,x,y,z world blocks in reach.json frame, radius, size small|medium|large, kind outpost|camp|base|structure, indoor, note}). FIRST ENTRIES WRITTEN 20:35: 21 sites (19 ground MP/FF maps + dam crest + dam powerhouse; orbit maps left out); Covenant thread told. Update entries as maps get built in.
Related: [[zomboid-reach-places-direction]], [[zomboid-reach-grand-designs]].
