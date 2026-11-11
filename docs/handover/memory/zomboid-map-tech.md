---
name: zomboid-map-tech
description: Kit MAP page tech (UI thread, 2026-10-29): whole county drawn on the client from CountyGen, train lines, all players, block colour table
metadata:
  type: project
  modified: 2026-10-29T04:44:49.755Z
---

Josiah 2026-10-29: the map must show the WHOLE current map in detail (no discovery/fog), train lines, and every player's position to everyone. In playtest c14865d0.

- Server: world/map/MapNet sends Atlas(seed, planned) on join for county worlds; Players (all positions, spectators skipped) every 10 ticks to everyone.
- Client: client/map/CountyAtlas rebuilds the county from CountyGen; 256px tiles at 4 levels (1/4/16/64 blocks per px); levels 0-1 read place voxels top-down, 2-3 are a sketch by place type. Cache config/zomboidcraft/atlas/<seed>_<plan|endless>_<version>_<jar date>/. 2 worker threads.
- Our blocks have MapColor.NONE, so colours come from src/main/resources/assets/zomboidcraft/map_colours.txt, made by `python3 tools/map/map_colours.py` (top-texture average; prop=1 when the model covers under half the block, drawn see-through). Rerun it when many new blocks land.
- Buildings (3+ blocks over the lot) get inked edges and a SE shadow at levels 0-1.
- client/kit/MapPage: lines() train/subway with stops, labels() Plan spots, others() player arrows/edge diamonds, survivors list with jump-to. Old fog path explored() only for non-county worlds. Paper maps no longer add to it.
- Josiah 2026-10-29 06:02: BARE map in the county: no pins, no jump-to-player, no enemies. Others are a ring over their 64-block area (MapNet.AREA, server snaps positions, sent every 40 ticks); survivors list shows names + nearest place only. Water-named props (barrels) are not water. Playtest d189f95f.
- Josiah 2026-10-29 09:46: zoomed out needs the same detail as zoomed in. Levels 2-3 are now composed from level-1 tiles shrunk (State.small 64x64 per L1, compose()); the sketch only fills undrawn parts and isn't saved; around() queues every L1 tile in the county nearest first. Playtest b6c6dc16.
- UI test (KitUiTest mapTest) shots map-county/island/downtown/streets.

Related: [[zomboid-ui-pages-tech]], [[zomboid-trains-tech]].
