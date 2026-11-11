---
name: zomboid-signals-roads-tech
description: Working traffic signals (Signal phase blocks), every street lamp lit, and the road-network fix (Layout.tieEnds/openGates) from 2026-10-31
metadata:
  type: project
  modified: 2026-10-31T01:32:27.127Z
---

Josiah 2026-10-31 01:03-01:05 asked for: traffic lights that cycle green, a short amber and red, with long green and red; every street lamp working; no road that stops short, with every place linked. Shipped in commit d05658d6 and playtest merge a0bbe622.

- **Signals.** The heads are ts_head, ts_head_swing and ts_flasher (the hamlet's four-way head, now with three lenses on each face). The blocks.json spec key "signal" holds:
  - light 8;
  - on: the lit texture swaps (ts_face to ts_face_g/y/r; the four-way uses ts_face for its north/south faces and ts_face_x for east/west);
  - states: for each facing, the model of each of the 6 phases.
- **How the phases run.** world/blocks/Signal.java uses IntegerProperty phase 0-5: 0 dark, 1 NS green, 2 NS amber, 3 all red, 4 EW green, 5 EW amber. It reads the world game time. Timings are GREEN 800, AMBER 80 and CLEAR 40 ticks, so one cycle is 1840 ticks.
- **How a head starts.** ChunkWriter marks Signal.Lamp blocks for post-processing. updateShape then schedules the first tick, and each tick schedules the next phase change. Random ticks are the fallback. CityBlocks flag SIGNAL = 32.
- **Generated files.** gen_blocks writes NAME_KEY models and facing,phase blockstates. To preview, run mesh.py with --signal-phase N.
- **Kit scripts and blocks.json order.** signals.py, welcome.py and tarps.py now rewrite blocks.json in place. They used to move their prefix to the end, which breaks the append-only order rule. Copy this pattern for any kit script.
- **Lamps.** Every intact streetlight_head and cv_lamp_globe lamp post is lit. The old dead-bulb dice are still rolled so layouts don't move. Smashed and knocked-down lamps stay broken. Indoor lights are not changed yet: room lights and switches are waiting on Josiah's answers ([[zomboid-room-lights]]).
- **Roads.** Layout.tieEnds() runs after airportRoad():
  - A place with no road at all gets spurAny to the nearest road.
  - openGates(): every unserved gate (no road within 30) of a TOWN, SUBURB, FARM or ROADSIDE place gets a road out to the nearest road point on that side, capped at 1500. These sites run a street to all four gates (Kit.beyond). Before this fix, about 340 of those streets stopped at the grass.
  - A dead road end has its tail trimmed back to its last crossing (within 160) or is extended to the nearest road.
  - The blown causeway to Ravenrock is deliberately left blown.
  - Road count went from 523 to 864.
- **Audit tools** are in /tmp/claude-0/audit (they don't survive the session; rewrite if needed). RoadAudit.java lists dead ends and places with no road. Comp.java lists network components. edges.py lists road cells on a dump's edge.
