---
name: zomboid-highway-places
description: Highway places Josiah picked 2026-10-31 02:39 (rest area, toll plaza, I-88/I-495 cloverleaf): chosen spots, design, progress
metadata:
  type: project
  modified: 2026-10-31T02:43:45.910Z
---

Josiah picked "Highway places" on a decision card on 2026-10-31 at 02:39, after the room lights shipped ([[zomboid-room-lights]]). These are the last new places; every other county plan place is built (Ridgeview Labs belongs to the NETHER thread). He asked "how far off from complete map"; the answer was these three, then polish.

**Refs:** refs/roads/{rest-area, toll-plaza, highway-interchange, overpass, abandoned-traffic-jam}.

**Spots** (all on open interstate, checked with CountyMap crops):
- **Cloverleaf** where I-88 (running east-west, z about -3309) crosses I-495 (running north-south, tilted about 7 degrees), at about (-1638, -3309). Filler cul-de-sac suburbs sit there now; a fixed place pushes them off.
- **Toll plaza** on I-88 at about (-4800, -3547). The land is open and hilly, and the highway runs about 16 degrees off east-west.
- **Rest area** on I-95 north at about (3181, -7290). The highway runs nearly north-south, with county roads crossing about 140 and 190 blocks away. Not at (2390, 2800): that spot is inside a downtown district.

**How a road meets these places:** Layout.road() breaks a plan road at any place within 24 and runs it to the gate at the middle of the facing side, with a 24-block straight lead, its height pinned to place.level. CountyGen only paints roads outside places, so each builder draws its own highway from gate to gate. Highway: half 7, asphalt, double yellow line at the centre, white dashes at offset 3.5, asphalt_low half steps on slopes.

**Progress 2026-10-31 03:20:** rest area (RestArea.java) and toll plaza (TollPlaza.java, Westgate Toll Plaza at -4800 -3547, 260x140, ten lanes, checkpoint shut the westbound) written and previewed; cloverleaf (Interchange.java, Millbrook Interchange at -1638 -3309, 360x360, I-495 on an 8-high embankment and single-span bridge, four loops and outer ramps built in one quadrant frame turned four times) built. Shared code: Lanes.java (roadway painter), Hwy.java (signs, gantries, lamps, jams), kit tools/city/highway.py (hw_).

**Queued next in this thread (coordinator relay of Josiah's ask, 03:16):** an auto shop in every town sized to the town, more or bigger in large areas; leave room for VEHICLES' working car hoists; LOOT stocks tools and parts.
Auto shop spec from the VEHICLES thread (03:20): each hoist bay 4 wide x 7 deep inside, 4.5 blocks clear headroom, garage door at least 3x3; shop sizes per town in section 6 of artifact Lez5BQYtKQYAehuQYXHosr; VEHICLES sends hoist/jack/crane/washer block ids when they land in playtest.

**Tech lessons:** gen_blocks stops on axis models drawn along x, so highway.py's add() runs P.along_z on rotate=axis models (axis= names the run direction). Lanes skips guardrails in gores where two roadways meet and faces bridge abutments in hw_mse. Preview dumps put local y=0 at camera y 12. Flythrough: /tmp/claude-0/fly.js with fly.html (one mesh load, window.frame per camera).

**LANDSCAPE's share of the Project Zomboid review (handed out 2026-10-31 03:47, Josiah approved; artifact 7rkGg3jdUDUZuHf1QeEvVE):** section 12 auto shops (one per town at least, scaled: village garage, 2-3 bay repair shop, 4-bay repair + tyre centre, city dealer service centre + parts store, truck depot and scrapyard; lifts are VEHICLES' feature); section 8 places for world stories; section 14 house alarms and erosion; section 9 farm animals he said yes to; section 13 district substations for the power failure. Rule: Zomboid's version of every feature unless he confirmed a change. Discuss with Josiah before building.
