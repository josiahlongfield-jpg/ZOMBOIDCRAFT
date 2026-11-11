---
name: zomboid-substations-tech
description: Power districts (PowerGrid) and the 17 substation yards LANDSCAPE built for LOOT's power grid (2026-10-31)
metadata:
  type: project
  modified: 2026-10-31T07:50:10.394Z
---

LANDSCAPE built the power map on 2026-10-31 (commit 86dad57e, branch claude/project-thread-d61kbn). This is part of Josiah's LANDSCAPE review plan [[zomboid-landscape-review-plan]]: power can be restored, but not easily; there is an always-on toggle and an adjustable shutoff. LOOT owns the grid logic.

- **Districts.java** (world/county, public; renamed from PowerGrid 08:20 to avoid LOOT's power.PowerGrid). It defines 18 Districts (id, name, x, z, Kind STATION/CITY/TOWN/HAMLET):
  - 8 city districts: midtown, uptown, eastside, westgate, northside, southport, riverside, ashby
  - 4 towns: seabright, kingsbridge, millbrook, fairview
  - 5 hamlets: hollowcreek, dunmore, timberline, capemerrow, gullharbor
  - the island, fed by Harbor Point's "power" plant
- **Districts API** (game side: world.gen.PowerDistricts.at/id(level,pos), yards(level)):
  - `district(plan, x, z)`: the nearest district; anything on the isle belongs to the island.
  - `byId(id)`
  - `substation(county, d)`: the yard's centre.
- **Substation.java** builds a yard by the district's kind:
  - city: control house with relay panels, mimic board, fusebox, standby generator (bunk_gen_*), batteries; 2 transformers, gantries, breakers
  - town: brick kiosk and one transformer
  - hamlet: kiosk and H-pole
  - Each yard is marked "substation <id>".
- **Placement:** county_plan.txt has `place sub-<id> substation ...` lines. Layout puts in-core yards as LANDMARK "substation" and the rest as ROADSIDE "substation". Adding them nudged the county layout.
- **Preview:** `bash /tmp/claude-0/plan/sub/go.sh s1 substations 150 64 75 30 110` builds the "substations" landmark variant (all three kinds). Video: previews/substations.mp4.

**How to apply:** if power districts change, edit Districts.DISTRICTS and county_plan together. LOOT drives failure and restore from these yards.

**Repair (Josiah's pick 10:13, "fix parts round the yard"; world/gen/YardRepair, 7ee9b42b):** in a dead district, held G at fittings within 40 of the yard (160 for the island's STATION). Steps:
- pw_breaker: Electrical 6, generator_parts + 2 electronics_scrap, 20 s.
- bunk_fusebox: Electrical 3, 3 fuse_box, 10 s.
- pw_reel (cable): Electrical 4, 2 wire_spool + electrical_tape, 15 s.
- Then the switch (pw_desk/annun/vb_lo/vb_hi) calls PowerGrid.restore.

A screwdriver must be in hand. Electrical 8+ halves the time. Hamlets have no breaker step and the STATION has no fuses step. Progress is stored in the overworld attachment yard_repairs.

