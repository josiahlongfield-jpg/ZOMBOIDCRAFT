---
name: zomboid-landscape-queue
description: LANDSCAPE thread's queue of county-plan places that still have no builder (2026-10-28), grouped, plus the next pick and refs status
metadata:
  type: project
  modified: 2026-10-28T00:06:20.460Z
---

As of 2026-10-28 00:04 (after stair headroom, [[zomboid-access-rule]]), 23 plan places have no builder of their own. Layout.fixedPlace returns null for them, so island ones are plain DOWNTOWN tiles and county ones are empty land. Plan file: resources data/zomboidcraft/county_plan.txt (lines with type "new"). See [[zomboid-county-plan]].

Groups offered to Josiah on a decision card (00:04). He PICKED 1 at 00:10: island landmarks, mall first, then Mercy General. RULE (Josiah 00:10): "Try to prioritise finishing grayhaven island completely": every island place gets built before anything off the island. 00:21: "just run through each of them until you're done": build them back to back without stopping to ask, shipping each to the playtest jar.
1. Island landmarks: mall (3250,-300) BUILT 2026-10-28 (commit 82b7a1f1, see [[zomboid-mall-tech]]), Mercy General BUILT 2026-10-28 (lot nudged to 2880,-320, playtest merge 4117c612, see [[zomboid-hospital-tech]]), university BUILT 2026-10-28 (3x3 lot at 2760,-1720, playtest merge f3c69970, see [[zomboid-university-tech]]), civic centre BUILT 2026-10-28 (2x2 lot, playtest merge 5fb63fa6, see [[zomboid-civic-centre-tech]]), park + museum BUILT 2026-10-28 (3x3 lot, playtest merge 9f4b2b03, see [[zomboid-park-museum-tech]]), Harbor Arena BUILT 2026-10-28 (lot 3640,-480, see [[zomboid-arena-tech]]), Ballpark BUILT 2026-10-28 (lot 2520,-520, see [[zomboid-ballpark-tech]]), Broadcast tower BUILT 2026-10-28 (3100,-2050, see [[zomboid-broadcast-tech]]), Power station BUILT 2026-10-28 (lot x 3800, see [[zomboid-power-tech]]), Old Market BUILT 2026-10-28 (3850,-2150, see [[zomboid-market-tech]]), Waterfront BUILT 2026-10-29 with Pier 9 and a Ferris wheel (see [[zomboid-waterfront-tech]]). ALL ISLAND LANDMARKS BUILT.
2. Grayhaven International Airport: BUILT 2026-10-29/30 at (3740,3610), see [[zomboid-airport-tech]], [[zomboid-airport-grounds-tech]].
3. Northwest military (Josiah PICKED this next 2026-10-30 03:27, after the airport Guard ramp): Ravenrock Penitentiary, Caldwell army airfield, tank ranges, Camp Black Pine, Black Pine Dam. (Ridgeview Labs was handed to the NETHER thread on 2026-10-29 23:38 as the Nether breach site with a ~300-block quarantine zone; leave it out.)
4. Coast and industry: Pelican Point Shipyard (BUILT 2026-10-30, see [[zomboid-shipyard-tech]]), Cape Merrow Lighthouse (BUILT 2026-10-30, see [[zomboid-cape-tech]]), Seabright Boardwalk (BUILT 2026-10-30, see [[zomboid-boardwalk-tech]]), Hollis Island quarantine (BUILT 2026-10-30, see [[zomboid-hollis-tech]]), Foundry Row (BUILT 2026-10-30, see [[zomboid-foundry-tech]]). GROUP 4 DONE. PICKED by Josiah 2026-10-30 10:37 (after the NW zone). Order: shipyard (5900,2000, piers -300/100 x -450..-850), lighthouse (6750,-5150), boardwalk (4550,6050, pier 200..950), Hollis Island (6400,6200, island hollis-i 420x280), Foundry Row (1300,1950). Refs: refs/shipyard, coast, quarantine, industry, port.

Refs: asked REFERENCES for city/mall-* (7 folders) and city/hospital-* (7 folders). Queued after the UI gear set. Wikimedia is throttled to about 1 h per folder, and refs/ is capped at 500 MB. The LANDSCAPE container can't reach Wikimedia ([[zomboid-refs-collection]]).

Landmark lots: Layout.lot(spot, LANDMARK, variant, nx, nz) on the 80-block grid, dispatched in LandmarkSite.build() by variant (see stadium, union_station). Escalators: world/lifts/EscalatorBlock (LIFTS thread). Top-down check: CountyMap OUT.png 12345 cx cz scale size.

Refs landed 2026-10-28 11:03: refs/city/ has a folder for every island landmark, plus rail/, subway/, roadside/, farm/, military/. Next: review each built island place against its folder and redo what's off (photo reference rule).

DETAIL PASS PICKED 2026-10-30 21:53: see [[zomboid-detail-pass]].
