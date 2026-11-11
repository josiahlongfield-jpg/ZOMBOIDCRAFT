---
name: zomboid-tank-ranges-tech
description: Caldwell Tank Ranges build (LANDSCAPE 2026-10-30): TankRanges frame on FrameSite, layout coords, rg_ tank hulk kit + Tanks.tank, igloos, preview
metadata:
  type: project
  modified: 2026-10-30T04:09:12.001Z
---

Built 2026-10-30 (commit f20478b0, playtest e22dcc12), second site of [[zomboid-nw-military-plan]].
- FrameSite.java = shared base for big tiled sites: parses "kind@RX,RZ@fx,fz,side;..." variants, clip placer, rnd, piece cache + copy, and FrameSite.tiles(kind, spot, county, plan, h, x0,z0,x1,z1, tileW) which ALSO replays Layout's road breaking to find gates. ArmyFieldSite and TankRanges both extend it. Layout.fixed special-cases spot ids "airfield" and "ranges"; LandmarkSite dispatches "armyfield@" / "ranges@".
- TankRanges frame round (-3000,-7300): fx -400..399, fz -300..299, tiles 240. Trail ring 14 in from the edge; range road fz 228; firing line LINE_Z 196 with six berms at fx -250..250 step 100 (sand tanks at 2); downrange target rows fz 140..-160, hulks every 4th; impact area fz < -200 (70 craters, burnt_ground, rg_frag, rg_shell, burnt hulks); control compound fx -70..70, fz 238..276 (tower, bleachers sc_bleacher, porta toilets, flag); igloos from fx -360, fz 240 (4 vaults span 11, rise 6, earth mound, tx_concrete headwall, garage_door). Seed-12345 gates: east (399,60), west (-400,60).
- rg_ kit = tools/city/ranges.py: rg_track/_end, rg_hull, rg_glacis, rg_turret_c/e/m/f, rg_barrel, rg_muzzle, each _sand/_rust/_burnt; rg_danger_0/1, rg_tanks_0/1 signs; rg_shell; rg_frag decal. Tanks.tank(placer, x, z, quarters, finish, damage, rng) builds a 5x8 hulk nose north (turret may be blown off, gun snapped, track links missing).
- Preview: /tmp/claude-0/plan/rg/go.sh NAME 400 "RZ@399,0,1;-400,150,3;" W D; line = RZ -110 D 190, down = RZ 310 D 430. Shots: /tmp/claude-0/shots.sh PREFIX specs (starts and stops the web server itself; the long-running server hit its time limit).
- Sheet: /mnt/project-files/zomboid-mod/tank-ranges/ranges-preview.jpg.
