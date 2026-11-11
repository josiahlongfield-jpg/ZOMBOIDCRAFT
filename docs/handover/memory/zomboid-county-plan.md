---
name: zomboid-county-plan
description: ZomboidCraft county master-plan map page (artifact URL, drafts, how to read Josiah's edits, what draft 2 contains)
metadata:
  type: project
  modified: 2026-10-25T13:38:00.000Z
---

Map page: https://claude.ai/artifact/9cnuMAwguk7eCVmwhdBv3J (db capability). Josiah drags places and leaves notes there. Read them with ArtifactData `list` on collection `edits` (doc id = place id, {x,z,note,at}) and `get` on `general/notes` {text}. Rules come from [[zomboid-landscape-direction]].

Geometry: a 16,384-block square, 0,0 at the centre, fence at ±7950, world border at ±8192. +x is east, +z is south.

- Draft 1 (13:12): Zomboid's Kentucky layout. Copy at project files zomboid-mod/plan/knox-plan-draft1.html. Superseded.
- Draft 2 (13:37, version 2): "Grayhaven County Master Plan". Copy at zomboid-mod/plan/grayhaven-plan-draft2.html.
  - Walled metropolis Grayhaven on a bay in the east, with 13 districts: Downtown, Financial, Old Harbor, Midtown, Uptown, Eastside, Westgate, Northside, Southport, the stadium quarantine, Foundry Row, North Terminal and the Megaport.
  - Riverside and Ashby are the smaller inland cities.
  - Black Pine Mountains with forest, Fort Caldwell and Ridgeview Labs are in the NW. There is one small farm valley in the SW.
  - Also: Seabright beach town, Kingsbridge, airport, shipyard, refinery, Ravenrock island prison, Hollis Island quarantine, I-495 beltway.
  - Draft 1 place ids no longer match.

- Draft 3 (2026-10-27, version 3, copy zomboid-mod/plan/grayhaven-plan-draft3.html): downtown is now GRAYHAVEN ISLAND at the Calder mouth, between the river's North Arm and South Arm (RIVERS 'north-arm'/'south-arm', w 260; ISLE polygon from their centrelines). Island places: union (3050,-1150), stadium, tower, power, waterfront, mercy (sub:true = subway stops), plus university, park, civic, mall, arena, ballpark, market, and Downtown/Financial/Old Harbor districts. RAIL is one closed loop 'loop' through 14 'st-*' station places + Union (~38 km); SUBWAY one closed loop of the six stops (~5.3 km). Lines pass through place ids, so dragging a station moves the line. Rail drawn as viaduct (elevated) where onIsland or over water.

Open questions on the page: size, names, spawn town, identical worlds (recommended: fixed seed).

**Built into the generator 2026-10-27 (LANDSCAPE thread, branch claude/project-thread-d61kbn):** `new County(seed)` is now the planned county; `County.endless(seed)` is the old endless one (tools take -Dcounty.endless=true). Pipeline: `node tools/county/plan_dump.js PLAN.html out.json` then `python3 tools/county/plan_export.py out.json` writes resources data/zomboidcraft/county_plan.txt (Plan.java reads it). Layout.java lays out the places: the island/core tiled into city pieces on an 80-block grid (DowntownSite tile mode), fixed places from the plan (ports face the plan's piers; the naval station is built as the PORT/naval carrier wharf; the stadium is a 6x7-block lot at its pin), suburbs/farms filled in, plan roads with bridges, spurs to fixed places. "new" places (union, tower, university, airport, stations...) have no builder yet. Josiah's ask: one stadium only, on the island. Old worlds need a NEW world for the plan.

- Island zombies (Josiah 2026-10-27 09:54, in the performance thread): the island city is "our biggest landmark" and starts with 20,000 zombies filling buildings, towers and subway, not just roads. PROMISED to the performance thread (branch claude/project-thread-u0is1s, zombie/population Budgets.of): when the plan is built in, add static County.onIsland(int x, int z) (polygon from the arm centrelines) and put the 20,000 in Budgets.of, or tell that thread. Until then they key on PlaceType.DOWNTOWN; LANDMARK stadium is budgeted 1,200. Their spawner needs standable floors with a roof within 24 blocks.
