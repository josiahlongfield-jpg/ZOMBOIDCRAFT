---
name: zomboid-track-contract
description: Track/station geometry contract agreed between TRAINS and LANDSCAPE threads (bed, envelope, platforms, grades, call button, Trains.line API), 2026-10-27
metadata:
  type: reference
  modified: 2026-10-27T07:34:10.340Z
---

Agreed 2026-10-27 between the TRAINS thread (vehicle, branch claude/project-thread-q2g5bz, package com.zomboidcraft.world.trains) and LANDSCAPE (world gen). See [[zomboid-trains-direction]].

- A line is a CLOSED loop of control points (x, y, z); y = top of the track bed. Straights run with the centreline on a block boundary. Corner points only; TRAINS rounds them (min radius 40 rail, 30 subway) so keep a clear corridor round each curve. y is linear along path distance between points.
- World gen lays only the bed: 6 wide (centreline +-3), custom ballast blocks at bed level, nothing solid above. Rails and sleepers are RENDERED by the train code (not blocks); keep trees, grass and props off the bed and out of the envelope.
- Clearance envelope: 6 wide, 6 tall above the bed top (tunnels, bridges, girders).
- Cars 22 long, 4 wide, floor at bed + 1, 3 headroom, roof at bed + 4.5; rail trains 4 cars (~90), subway 3 (~67).
- Platforms: top = bed + 1, edge exactly at centreline +-2, length >= 96 rail / >= 72 subway, straight and level, ~10 extra straight each end.
- Grades max 3%; stations level.
- A stop = name + a point on its platform. Out-and-back: a loop may run beside itself (centrelines 6 apart) or round an island platform (spacing = platform width + 4); give two stops with the SAME name, one per platform.
- Call button block zomboidcraft:train_call_button[facing], one or two per platform at head height; links to the nearest stop within 48.
- Level crossings: lay road-surface blocks in the bed with bed top = road top; Trains.crossings(lineId, points) makes OTTO sound the horn.
- Viaducts and bridge decks: solid bed under the 6, plus a 1-block walkway with railing each side outside the envelope.
- API (merged into playtest 2026-10-27 08:07): com.zomboidcraft.world.trains.Trains.line(ServerLevel, String id, Line.Kind.RAIL|SUBWAY, String name, List<int[]{x, bedTopY, z}> closed loop, List<Trains.StopSpec(name, x, z)>) is idempotent (keeps running trains if points unchanged), so call it on every server start. Trains.crossings(id, List<int[]>), Trains.remove(id). Lines persist in world/zomboidcraft_trains.json. Check in game: /zc train list, /zc train test rail|subway.
