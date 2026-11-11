---
name: zomboid-nw-military-plan
description: LANDSCAPE's northwest military zone (picked 2026-10-30 03:27): five sites, plan coords, refs, proposed order, design notes from photos
metadata:
  type: project
  modified: 2026-10-30T03:29:39.593Z
---

Josiah picked "Northwest military zone" 2026-10-30 03:27 (after the airport). Thread renamed "Build the northwest military zone".
Sites (county_plan.txt): Caldwell Army Airfield (-1300,-6900), Caldwell Tank Ranges (-3000,-7300), Camp Black Pine (-6600,-3250), Black Pine Dam (-5400,-2350; river calder-up starts there, lake reservoir -6400,-2600 1000x420), Ravenrock Penitentiary (6600,-1500, cliff island 560x360, actually EAST coast, needs ferry/causeway). Fort Caldwell main post (-2700,-5900) already exists as MILITARY:army_base (MilitarySite). Ridgeview Labs belongs to the NETHER thread.
- Road "tanktrail" runs fort -> airfield spot -> ranges -> back; it passes THROUGH the airfield centre; Layout.planRoads breaks a road where it enters a place (ends at a gate facing the way it came), so the airfield needs gates where the trail meets it (NE side from the fort, SW side to the ranges).
- ORDER CHOSEN by Josiah 03:30: airfield, ranges, camp, dam, prison. (04:12 he inserted the airport airliners job, see [[zomboid-fleet-tech]]; camp paused for it.) Airfield BUILT 03:50 ([[zomboid-army-airfield-tech]]); ranges BUILT 04:10 ([[zomboid-tank-ranges-tech]]); camp BUILT 05:00 ([[zomboid-camp-black-pine-tech]]); next Black Pine Dam. REFERENCES queued the top-up (lands when he runs it on his PC).
- Refs: refs/airfield-mil, ranges, camp, dam, prison, military (~200 photos). Asked REFERENCES to top up thin folders + airfield-mil/runway-arresting-gear, dam/reservoir-shore.
- Photo notes, airfield: revetments = U/L walls of corrugated steel bins filled with earth, ~3-4 high, open to taxiway, jets inside, carts beside; heli revetments = low timber/steel bin walls with a membrane pad; army tower = steel lattice legs, glass cab + catwalk, "ELEV" sign; hangar = barrel-vault roof, big sliding doors, lean-to shops along the side.
- No tank/APC/military truck vehicle models exist (only jets, heli, Warthog local-only): range tanks are block-built wrecks.

Related: [[zomboid-landscape-queue]], [[zomboid-camp-kit-tech]], [[zomboid-airport-grounds-tech]].
- 2026-10-30 05:10 cards posted, awaiting Josiah: DAM (all world water is one level, Terrain.WATER 62; lake "reservoir" floor 46 and the river head at -5400,-2350 are both at sea level on ~64 land): options dry spillway gorge below (recommended) / raise the reservoir ~25 (terrain+water code change) / low barrage. PRISON access: island ravenrock-i is a flat plateau ~79 with cliffs, ~1100x700, about 1,400 blocks of open sea (floor 38) east of the mainland coast; options ferry (recommended) / causeway bridge / both. Dam refs in refs/dam/* (arch dam, spillway, intake towers, powerhouse, crest road, galleries).
- 2026-10-30 07:23 Josiah picked PRISON FERRY (no causeway). Dam card still open, so the prison is built first while the dam waits.
- 2026-10-30 Ravenrock BUILT (prison island, dock, Waterfront slip, ferry bell). See zomboid-ravenrock-tech.md. Next: Black Pine Dam once the dam card is answered.
- 2026-10-30 08:31 Josiah picked DAM option B: RAISE THE RESERVOIR (~25 above sea behind the dam; needs a terrain+water code change, since all world water is one level). Being built next.
- 2026-10-30 Black Pine Dam BUILT (see zomboid-dam-tech.md). All five NW sites now built.
