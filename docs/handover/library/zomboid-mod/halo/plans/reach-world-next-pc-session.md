# Reach world: what to do next time the PC is on

Written 2026-11-08 22:12. The PC went off on a timer mid-pull. Everything below is ready to run.

## 1. Enemy and vehicle spawn spots (about 10 minutes)
Done already: m45 Long Night of Solace, m10 Winter Contingency, m20 Sword Base (439 squads). Saved in `/mnt/project-files/zomboid-mod/halo/squads/sq_<map>.json`.

DONE 2026-11-09: m30 m52 m35 m50 m70 m60 pulled too (squads folder). Only the firefight maps below remain, optional.
| Map | Where on PC | Size |
|---|---|---|
| m30 Nightfall | C:\XboxGames\Halo- The Master Chief Collection\Content\haloreach\maps\m30.map | 341 MB |
| m52 New Alexandria | ...\maps\m52.map | 361 MB |
| m35 Tip of the Spear | C:\Dev\Zomboidcraft-Desktop\additions\halo-reach\split\m35.map.part1 + part2 | 404 MB |
| m50 Exodus | ...\split\m50.map.part1 + part2 | 410 MB |
| m70 Pillar of Autumn | ...\split\m70.map.part1 + part2 | 418 MB |
| m60 The Package | ...\split\m60.map.part1 + part2 | 463 MB |
| Firefight maps (ff10, ff20, ff30, ff45, ff50, ff60_airview, ff60_icecave, ff70, ff_unearthed) | ...\maps\ff*.map | 150-215 MB each, two per call |

Per map: stage, wait for the full size, then `python3 /tmp/claude-0/halo/worldlay/sqpull.py <map>` (joins parts, writes sq_<map>.json, deletes the staged copy). Copy results to the squads folder. If the container was reset, the tools are in `/mnt/project-files/zomboid-mod/halo/tools/` (squads.py needs reach.py + rres.py from the same folder) and need `shared.map` + `campaign.map` staged too.

## 2. Surrounding landscapes still missing
Rendered already: m10, m20, m30/m35 (shared), m50, m52, m60, m70. Missing: m45's ground terrain render (`world\m45\m45_000.glb`, 18 MB) and the multiplayer maps' far scenery (`*_000.glb` in each `world\<map>` folder: forge_halo, trainingpreserve, ff_unearthed, m70_bonus, 52_ivory_tower, cex_hangemhigh). Stage, then `bdrender.py`.

## 3. Nothing needs the PC for
- The 16 km layout (in progress in the cloud, preview to Josiah first).
- Settlements, mini bases, activities, PLANS placements, grand design ideas.
- Detail pass planning (shader tiling, lightmaps, decals are all in the exported GLB material data).

## Rules
Previews before anything is written to the PC. Never write to OneDrive folders. C:\XboxGames is read-only.

## Pillar of Autumn, walkable (Josiah OK 2026-11-08 23:27)
- Outside: Reach model vehicles__human__unsc_fleet__unsc_halcyon_class_cruiser_poa (already in additions\halo-reach\vehicles) docked in m70 with poa_gantry scenery at its m70 placement.
- Inside: Reach has none. Use Halo CE a10 (The Pillar of Autumn) and d40 (The Maw) as the deck plan (bridge, cryo, corridors, hangars, engine room), rebuilt in Reach's look, same approach as the corvette with Truth and Reconciliation.
- PC step: stage C:\XboxGames\Halo- The Master Chief Collection\Content\halo1\maps\a10.map and d40.map (read only). Read with /tmp/claude-0/halo/cemap.py + coll.py (backup in tools/), map rooms onto the Reach hull at the same scale.

## Terrain rule (Josiah 2026-11-08 23:31)
- Never flatten a level. Keep every cliff, canyon, height change and building level. Cliffs at level borders stay; every cliff base / canyon floor gets a way back out. See memory zomboid-reach-terrain-rule.
- Survey for "seen but not enterable" structures: compare each level's render GLBs (PC) with its walkable collision (cloud layout/) and list big shells that could get interiors.

## Forge base builder survey (BASES thread, Josiah 2026-11-09 00:00; previews only, nothing built)
- PC step: stage C:\XboxGames\Halo- The Master Chief Collection\Content\haloreach\maps\forge_halo.map (read only) to the cloud, plus shared.map if its palette tags live there.
- Cloud: read the scenario's Forge object palette (categories, every piece's tag name, cost/limit), pull each piece's render model + collision with the existing reach.py/rmodel.py/rcoll.py tools, and measure bounding boxes in Minecraft blocks.
- Outputs to Josiah: a preview sheet per category (blocks, buildings, bridges/platforms, inclines, walls, natural, decorative, Forge-only items), a sizes table, and a snap-grid proposal.
- Interior set: weapon racks, armour stands, trophy cases, crates, consoles, lights and decor picked from Reach props (sword base, Spire, armoury pieces) for free placement inside Forge shells.

## First in-game test of the Reach world (built 2026-11-09, branch claude/project-thread-d61kbn)
- Copy /mnt/project-files/zomboid-mod/halo/reach-install/ into the Modrinth profile's config\zomboidcraft\reach\ (reach.json + world\m20.zrv). reach.json points the drawer at C:\Dev\Zomboidcraft-Desktop\additions\halo-reach\world\m20 (offset -167,0,-5261; spawn -142.5,63.5,-5508.5). Check whether that folder holds whole .glb or .glb.partNN (drawer joins parts, but tex/ URIs must resolve next to the original files).
- New world, World Type "Endless Night: Reach". Check: you stand on Sword Base's ground, the real meshes draw, log lines "Reach world: ... on the graphics card", and the "drawing levels with view" line.
- Known gap: Halo's blended ground (rmtr terrain shaders) has no exported textures, so ground draws plain grey. Fix on the PC: stage m20.map, export base_map_m_0..2 + blend_map per terrain shader, bake a blended ground texture per terrain piece (or per-triangle dominant layer), re-pack.

## Main menu slideshow shots (Josiah 2026-11-09 01:00, via UI)
- Once the Reach meshes draw in game: capture clean in-game screenshots with the HUD hidden (F1, then F2) and put them in the Modrinth profile's config\zomboidcraft\menu-backgrounds\ (the menu plays every picture there in name order).
- Shots: Sword Base, the farm (Winter Contingency), the Spire, New Alexandria rooftops, the corvette from below, the corvette deck. Real captures only, never renders from the map files. Previews of the picks to Josiah before writing them to the PC.

## New Alexandria walkable city (told Josiah 2026-11-09 02:35)
- Josiah wants the cityscape thick with buildings like the game shows it.
- m52 collision is only rooftops/pads. Plan: draw the game's own m52 models; voxelize the RENDER meshes of the city (streets, building shells, rooftops) into invisible walls; keep the mission's real interiors enterable (hospital, club, rooftop gun sites); other buildings solid shells.
- Check on PC: whether the far towers are low-detail skybox versions (bd materials metal_skyscraper_grey_skybox). If thin up close, replace with copies of detailed nearby buildings.
- Same render-mesh voxelizing for the Exodus far skyline if it should be walkable.
- DECIDED (Josiah 2026-11-09 02:36): landmark towers (~12) get full interiors up to the roof from Reach interior pieces; every other building gets an enterable ground floor (lobby, shop, garage). First step: shortlist of landmark towers from the m52/m50 models + an interior style preview before building.

## Land between the levels (2026-11-09 03:30)
- New file world/land.zrh (16.7 MB) in the staged install: the whole 16 km land between the missions (walls + drawn ground + sea). Mission .zrv files re-made at new heights (one sea level): y offsets m10 +100, m20 0, m60 -22, m35 0, m52 +131, m50 +114, m70 +225 (reach.json updated to match).
- Each level's _000 (far land) is cut in game to where that level's land is; check the seams where Halo land meets the new land.
- Lone Wolf (m70_bonus epilogue) still to place on its rise at about (2300, 6900).

## Shared sky and atmosphere (Josiah 2026-11-09 03:45)
He wants every mission's ships in orbit and atmosphere in the world so it reads like the game. Josiah chose (04:06): each region keeps its own mission's sky and stage of the war, blending gradually between regions like the climate.
- Cloud side found (placements.json): corvettes over Sword Base (m20, 2) and Nightfall (m30, 4); corvettes (6) and civilian transport ships (5) at Exodus (m50); 2 Covenant cruisers over New Alexandria (m52); Halcyon cruiser PoA, a Covenant cruiser, longswords and gantries at the Pillar of Autumn (m70); Stalwart frigates and longswords at Tip of the Spear (m35, m45).
- Not in the cloud: each level's sky tag (skybox clouds, the New Alexandria supercarrier, glassing, far fleet, fog and light settings). On the PC: export each mission's sky tag and its sky objects with the map reader, plus the ship models' textures, into additions\halo-reach\world\sky\.

## Far drawing pass and horizon (in the branch 2026-11-09)
- In the Reach world the camera draws out to 48 km (near plane 0.2 m), distance fog 900 m to 22 km; the land carries on 22 km past the map's edge (ReachHorizon).
- Check: latest.log has "Reach world: far plane ... -> 48000" (the mixin is silent if it misses its target); far land visible with Photon on and off; Photon's own border fog; z-fighting on far buildings and the coast; FPS and VRAM with full detail.
- Tuning without a rebuild: JVM args -Dzomboidcraft.reach.far=, .near=, .fogStart=, .fogEnd= in the Modrinth profile.
