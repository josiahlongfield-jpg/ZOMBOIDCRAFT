---
name: zomboid-glass-line
description: Glass Line underwater subway tube (Mercy General to Eastside Quay under the South Arm): route, depths, kit, world-gen code, stations, leak rules, previews
metadata:
  type: project
---

The ask and pitch: [[zomboid-subway-tube-direction]]. Rail context: [[zomboid-rail-tech]], [[zomboid-track-contract]].

Route map sent 2026-10-27 20:50: project files zomboid-mod/previews/rail/48-glass-line-route-map.jpg; sources zomboid-mod/plan/glass-line/ (map.html, shuttle2.py geometry turtle, profile.py times). Josiah PICKED 'Own shuttle, as drawn' 2026-10-27 21:28. Design change told to him: island platforms at both stations (A and B 12 apart, platform 8 wide between), so trains stop both ways; Mercy has stairs up to the subway platform above, Eastside Quay an underwater glass hall with a glass stair up to a pier pavilion.

- South Arm at the tube: water z -109..151 (260 wide, kept), natural bed y48 mid, water top 62. Dredge the channel along the tube to ~y37 (25 deep).
- Shuttle (as drawn): closed loop 3463 blocks, twin tracks 12 apart (B on A's LEFT), turning loops (bend 40 r 60deg, sweep r52 300deg) at both ends. A starts (2790,-400) east under the subway's Mercy platform (Glass platform directly below, y46, one flight down), r40 south at x 2948, south to z -80, r100 left to east at z 20, east to x 3230, S-bend r158 (50deg each) to z ~133, quay reach east 150, r64 south into Eastside, loop, back. Stops: Mercy on A (z -400), Eastside Quay on B (z 121, glass side; station box in the water off the south seawall z 151).
- Depth: bed y46 on land, dives 3% to y40 mid-channel, back to 46 before the quay. Tube crown = bed + 8 (8-14 blocks of water above).
- Sights on the map: under the el, tug+barge, sunk freighter, cars+bus under the highway bridge (its piers dropped in the tube corridor).
- Kit (e41e3706): tools/city/glassline.py, 25 gl_ blocks; per bore (o from centre): keel raft, sill+walk at the cars' floor, wall_lo/wall, three 45-degree chamfers, crown roof; _rib every 4th; damage variants; exit signs. Facing = outward (glass at z0).
- Preview: view.html BLENDS glass and water (mesh.py "blend"); mesh.py fills submerged water.
- BUILT 2026-10-27 (08768a9f, merged to playtest 7dec9123): GlassLine.java (turtle route, TUBE/TUNNEL modes, grade, stops, Track.Island squared to the grid), RailGen tube()/portal()/islandHall()/islandTunnel()/mercy()/quay()/pavilion(), trench() for ground and bridge columns (CountyGen), overTube() skips el and road-bridge piers. Track.TUBE=3, Track.tubed, Track.tubeWay (exit sign arrows). /zc station glass_mercy, glass_quay.
- WATERTIGHT RULES: water flows through any mod block NOT in the blocks_motion tag (26.3) -> gl_crown is tagged via heightmap_tags.py SHELTER. On curves the grade's Y step can put an inner column a block above its outer neighbour: stepsUp() raises that wall column's chamfer a block (gl_wall under it). Portal room must equal the tube's room (no taller), or curved portal edges leak. Scan: /tmp/claude-0/plan/tube/leaks.py (air touching water below y62) over CountyPreview dumps; whole channel clean.
- Call buttons: Trains.call adds |dy| so stacked Mercy platforms (glass under subway, same xz) call their own line.
- Previews: project files zomboid-mod/previews/rail/49..56-glass-line-*.jpg. Scratch tools /tmp/claude-0/plan/tube/ (mk.sh mesh+link, slice.py, near.py, leaks.py, extra.json call-button spec).
- WRECKS BUILT 2026-10-27 (2e42ae05, playtest 98477d41): Wrecks.java (County.wrecks, copied in CountyGen before rails.lay). Each drawn once into a Voxels in WORLD coords via the Sunk sink (shear = heel/trim, air<=61 flooded). Freighter = BigShip.container(150,26) stern x3075 keel z-28, oy 48, heel .35, trim .035, gash facing the tubes. Barge x2900 z-58 + tug x2907 z-70 (gl_coal block, spill fan). Bus+cars = entities via vehicle(models+'@wrecked'); ChunkWriter now pins a variant after '@'. bed() mirrors CountyGen (roads' bank lerp + trench). Previews 57-60. Still: redo against photos when subway/underwater-tube refs land.
