---
name: zomboid-town-station-tech
description: Town stations at the four street rail stops (tx_ transit kit, TownStation.java, Railway.stations hand-off, Role.STATION) and preview coords; built 2026-10-27
metadata:
  type: project
  modified: 2026-10-27T19:22:19.883Z
---

Built 2026-10-27 (LANDSCAPE thread, d884b4a1, in playtest 03e550ac) for Josiah's 18:39 ask "every stop needs an open stop or a station". Style follows the STYLE RULE in [[zomboid-rail-tech]] (elegant, functional, intricate; steel + glass; worn). Built from text knowledge; redo when REFERENCES photos land (rail/light-rail-station-pavilion, rail/station-fittings).

- Kit: tools/city/transit.py, tx_ prefix, 122 blocks (palette GREEN/CREAM/WHITE/ANTH/AMBER). STATIONS dict maps key to the name on the parapet tiles (tx_name_<key>_0..7). Regenerate: photo_textures.py (imports transit) then gen_blocks.py, see [[zomboid-world-tech]].
- Hand-off: Railway.stations() picks each RAIL stop with street>0 and mode GROUND, steps street+24 off the platform side, keys the PITCH cell; markTiles copies them into TileRails.stations. DowntownSite.railRoles gives that block Role.STATION when its street side faces the line; station(b) centres a 28x17 lot on the stop middle (falls back to walkups if the block is too small), fills the rest of the block with normal frontage and marks the canopy cells BUILT so no street lamps go under it.
- county/TownStation.java: L=28, D=17. Glass front at z=0 with tx_door pairs at x 4/13/22 and columns at 0/9/18/27; service wall z=10, back z=15; roof y8 with rooflight, parapet + name y9, back ladder at x=L-4 (roof reached for the access rule). Canopy at y6 matches the platform canopy height. Hall: validators, bench rows, maps, TVMs (40% smashed), ticket windows + departure board, clock, pendants. Rooms behind: toilets, staff office, ticket office (safe, loot cash), kiosk.
- Stops (seed 12345): Uptown (2160,-3650) station west; Riverside (-2560,-2260) east; Foundry Row (1520,1650) east; Southport (2350,880) north.
- Preview dumps: ts_up origin (2096,-3696), ts_rv (-2592,-2304), ts_fd (1488,1600), ts_sp (2304,816), via dpdump.sh. shot.js saves PREFIX-name.png (pass the prefix without a trailing dash). Riverside platform cam: 35.5,19.6,16 -> 46,20.5,38. Previews 35-40 in project files previews/rail/.
- Checks run: orientcheck 0 problems; walkcheck no blocked doors (only furniture tops unreachable).
