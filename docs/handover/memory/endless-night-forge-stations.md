---
name: endless-night-forge-stations
description: Forge Armory stations (consoles, holotable with Cortana, stocked Halo + Tarkov racks) for Endless Night bases, 2026-11-10 state and file locations
metadata:
  type: project
  modified: 2026-11-10T07:32:09.920Z
---
2026-11-10 Josiah (thread "Build bases with Reach Forge"):
- Wanted screens/monitors, not just racks; racks must be FULL of different weapons for show. Picked "Consoles + hologram + table": tech console (standing + wall), desk hologram screen, planning table all open the Gunsmith (as do racks/shelves).
- "cortana needs to be on one of the consoles and also add some tarkov weapons too". Coordinator: reuse the Cortana thread's model/code, no second copy.

Built:
- Catalogue 350 pieces. 08:10 Josiah: "stack them neatly in the space they arent presented, you dont have to angle every gun" -> racks: one gun stood up, rest hung level in rows; shelves: 1-3 shown side-on, rest lying flat in neat stacks (mkcat.py gun_side/stack). Mock-up bases/monitor/armory_neat_mockup.jpg; catalogue+thumbs on PC 08:10 (no code change needed). New: weapon_rack_stocked, armory_shelf_stocked (Halo guns), weapon_rack_tarkov (ak102 m4a1 svds val mdr762), armory_shelf_tarkov (m870 mp5k glock17 usp pm sv98). planning_table = "Holotable (Cortana)", catalogue fields hologram "cortana", hologramAt [0,1.07,0] (metres, piece space).
- Code: Part pitch (f4cbe01) + Piece.hologram/hologramAt + ForgeClient.holograms() -> List<Anchor(who,x,y,z,yaw,owner)> (71a09fa), branch claude/forge-stocked on ENDLESS-NIGHT.
- Cortana thread agreed hook: she shows on the anchor when B has her shown and player within 16 m at own base, 0.45 size, facing player. Ping that thread when on playtest.
- PC: additions\halo-reach\forge\full\ now has new forge_catalog.json, forge_thumbs.zip, forge_models_tarkov_00..06.zip (device commit limit is 25 MB per file, so split zips).
- Tarkov GLBs made from operator .zsk by tools/zsk2glb.py; tools backup /mnt/project-files/endless-night/bases/tools/. Mock-ups bases/monitor/ (armory_station, armory_screens_racks, tarkov_armory_mockup.jpg).
- 08:45 Josiah: Tarkov rack guns same orientation as Halo rack. Cause: every Tarkov gun bone is 10.3 deg nose-up in the operator bind pose; zsk2glb now called with R=Rx(10.3)@diag(-1,1,-1). All 12 GLBs rebuilt level, re-sent to PC with catalogue+thumbs; mock-up bases/monitor/armory_level_mockup.jpg.
- 08:46 Josiah: floor hologram pad. Piece hologram_pad = ff_teleporter_receiver ring, hologramAt [0,0.1,0], hologramSize 1.0 (holotable 0.45). Anchor gained float size (970763107); Cortana thread uses a.size() (79833798b). Mock-up bases/monitor/hologram_pad_mockup.jpg. 09:03 coloured (Josiah asked): own model holo__pad.glb (gunmetal ring, cyan strips, opaque blue emitter; game uses baseColorFactor as sRGB tint, preview copy uses ^2.2) in PC forge_models_holo.zip.
Related: [[zomboid-base-rooms-plan]], [[endless-night-cortana-ingame]], [[endless-night-gunsmith]].
