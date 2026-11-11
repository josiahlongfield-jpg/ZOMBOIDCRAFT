---
name: zomboid-rail-tech
description: LANDSCAPE rail/subway world build (Railway.java routing, Track.java, RailGen.java per-column sections, rw_ kit, CountyRails hand-off, previews, route maps), Josiah's 13:12 ask, open items
metadata:
  type: project
  modified: 2026-10-27T14:15:33.582Z
---

Josiah 2026-10-27 13:12 (going to sleep): lay the railway round the whole map, then the subway, following the county plan map so the big landmarks fit round it (especially Grayhaven Island); work through the night and leave previews for the morning. Decisions: [[zomboid-trains-direction]]; contract: [[zomboid-track-contract]].

- county/Railway.java (built in Layout after city(), before suburbs): routes plan rails. Loop: plan line in the country (fillets up to r700, stops squared to the compass), street grid in cities (Dijkstra Router, corners r60 -> VACANT role); island = el at y77 (EL_Y) down x=3040, mainland/inland cities street level y67, bridges >= y70. Subway: grid loop through 6 stops at y56, corners r40 -> PLAZA role. Country stops slide off water with dry()/dryAlong() (Hollow Creek moved to -1650,5020).
- county/Track.java: samples 1 block apart (xs, zs, ys bed top, mode GROUND/VIADUCT/TUNNEL, street half, stopAt), stops, points for trains, crossings; stopPoint(), bedTop().
- county/RailGen.java: per-column sections. stairStart()/crossStreet() keep el and subway stairs out of cross streets; el stair blocks start at the landing's level (h = Y + 1 - step, fixed bd3a02b4: before, the top rise was a full block); litter()/weeds()/graffiti/broken tile/paving() (rw_platform, _2, _3 wear) so nothing looks new. CountyGen Column.lane keeps tree crowns off the line.
- world/gen/CountyRails.java: SERVER_STARTED -> Trains.lineExact ids grayhaven_loop / grayhaven_subway + Trains.crossings; logs "County lines laid for the trains"; /zc station [name] (spot minus st-, subway_ prefix).
- CityBlocks/ChunkWriter resolve full zomboidcraft: names (call buttons) even with stand-ins.
- CI county smoke test filters ListPlaces output to place rows (log lines broke the forceload loop until 3f3bc579) and forceloads Union el/subway, Millbrook, north-arm bridge.
- Offline: javac to /tmp/claude-0/build/rail; CountyPreview dumps; mesh.py --track tracks.json (TrackDump); scratch tools in /tmp/claude-0/plan/rs (TrackMap, RouteData = dark base map + JSON; /tmp/claude-0/plan/map/map.html renders the route maps in the kit palette with the repo's barlow_condensed.ttf).
- Previews and route maps: project files zomboid-mod/previews/rail/.
- Hearthbot tools were down 2026-10-27 10:45-18:20; the queued replies (PENDING-REPLY.md) were all sent at 18:25. Josiah 18:27: "You can have as many bridges as you need" (two river bridges approved).
- Union Station built (bd3a02b4): see [[zomboid-union-station-tech]].
- Depots built 2026-10-27: see [[zomboid-depot-tech]]. Through trusses dropped: every water crossing is diagonal or curved (headings 152, 63, -141 deg, curves), so block trusses would be jagged; crossings stay plate girder on piers.
- Subway stations: I-beam bents every BENT=5 samples (RailGen.bent/bentRoof; rw_subway_column/_base/_cap at s 3..4, rw_subway_beam at Y+5 behind, rw_subway_girder flush in the roof over the track), f8b28815.
- Open: REFERENCES thread queued rail/subway photos 2026-10-27 18:26 (rail/elevated-structure, subway/platform, subway/station-entrance, rail/small-town-depot, rail/grade-crossing-signals, rail/rail-bridge-trestle, rail/county-railway-track); a trickle, see refs sources.md. When they land, compare and redo what is off.
- STYLE RULE (Josiah 2026-10-27 18:43): stations need not look "classic"; elegant, yes, but functional and intricately made. Applied to the Union train hall and the town stations: fine steel trusses + glass, slim branching columns, departure boards, ticket gates, escalators beside wide stairs, lots of detail, still worn and overgrown. The country depots (period brick) were built before this rule.
