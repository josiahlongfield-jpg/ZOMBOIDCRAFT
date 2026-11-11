---
name: zomboid-world-tech
description: ZomboidCraft world/worldgen technical notes: rain-blocking tag rule, county generator layout, offline preview tools
metadata:
  type: reference
  modified: 2026-10-25T08:08:03.488Z
---

(2026-10-25, thread "Build the ZomboidCraft landscape", which owns world and city code.)

- RULE: Minecraft 26.3 decides what stops rain, rain sound and falling things with the block tags minecraft:blocks_motion / blocks_motion_no_leaves (the MOTION_BLOCKING heightmaps read them; BlockState.blocksMotion() is gone). Every solid mod block must be in them. tools/city/heightmap_tags.py writes data/zomboidcraft/tags/block/city_blocks_motion.json from CitySpecs.java (gen_blocks.py runs it); the minecraft tags include it plus zomboidcraft:barricade. CityBlocks logs "Rain cover:" or "Rain falls through ..." at server start and the CI smoke test requires the first. Old worlds keep stale heightmaps for cities built before the fix.
- County world type (zomboidcraft:county): pure-Java planner in world/county (County, Terrain, Road, Place, *Site builders, House), MC adapter in world/gen. Listed on the create-world screen since 2026-10-25 (data/minecraft/tags/worldgen/world_preset/normal.json): Josiah picks World Type "ZomboidCraft County"; not superflat. Cheats on: /zc places [type|variant], /zc goto type|variant. The CI "County world smoke test" uses level-type in server.properties.
- Offline previews: this env can't reach npm or CDNs (no three.js). Use tools/county/CountyPreview.java (dump) -> tools/county/vanilla.py (vanilla block stand-ins) -> tools/city/mesh.py --extra -> tools/county/view.html (plain WebGL2) via tools/county/shot.js and a local http server. City textures: copy src/main/resources/assets/zomboidcraft/textures/block/city into a work dir; regenerate _nrm_gl maps from _n for mesh.py.
- County block textures live in tools/city/county_textures.py (registered into photo_textures.py).
- RULE: an axis city block's axis is the direction it RUNS ALONG (window on a wall along x = window[axis=x]; same for chain-link, fence_boards, rails, power_line). Base models in tools/city/blocks.json must run along z. Windows were modelled the other way until 2026-10-25 (every pane stood side-on in game). Pane blocks placed without an axis keep axis=z whatever the Placer turn, so always give one (the city's Decay pass re-orients windows from their neighbours).
- Vanilla names: 26.3 renamed chain to minecraft:iron_chain. StandIns warns 'Unknown vanilla block' and the CI county test fails on it.
- Site builders (world/county/*Site, Kit, Camp, Bldg, Placer): Kit's use[] grid (FREE/ROAD/WALK/VERGE/LOT/PARK/BUILT) decides what overgrows; claim BUILT for yards. Voxels.get returns null for explicit "air", so lawn before builders, never after (it refills pools). Mod AXIS blocks are horizontal only (tire[axis=y] is invalid). Never pass "air" as a Bldg.door type (makes "air[facing...]"); set air directly. Placer.vehicle(x,y,z,yaw,"a,b",seed) spawns a car; each one costs FPS, keep a site near 30.
- Preview loop: SitePreview NAME TYPE variant W D seed -> dump; mesh/camera coords = dump minus 24 in x and z, ground y 12. Check every new place at full and 3/4 size over several seeds before merging.
- A county chunk that throws used to leave the server thread waiting forever, which the CI saw as a Server Watchdog "single tick took 60 s" inside forceload. CountyChunkGenerator now logs "Exception generating county chunk" instead. To find a stall, the CI test spaces forceloads 2 s apart: the last "Marked N chunks" line names the place before the one that hung. Example: a vehicle list naming a model twice made ChunkWriter's Set.of throw (fixed 2026-10-25).

- Town roads (2026-10-26): County.joins(mx,mz) joins every place in a macro cell into one network, plus a road to the east and south macros.

Related: [[zomboid-houses-tech]], [[zomboid-landscape-direction]], [[zomboid-mod-build-notes]], [[zomboid-playtest-build]].
