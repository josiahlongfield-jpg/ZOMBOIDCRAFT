---
name: zomboid-civic-services-tech
description: Fire station (and next sheriff, school, theatre) rebuild notes: FireStation.java layout, fs_ kit, fire loot table, truck yaw rule
metadata:
  type: reference
  modified: 2026-10-26T23:10:00.000Z
---

Follows [[zomboid-civic-buildings-tech]] (LANDSCAPE thread). No photos existed for these (refs folders empty), so they are built from text research.

**FireStation.java + tools/city/firestation.py** (commit 659b3ab4): fs_ kit 89 blocks. Civic.fireStation(w, d) delegates; TownSite lots 18x26 and 19x26 plus kit.treeless zones so street trees keep off the apron. S2=6 (upper floor), TOP=11, Z0=6 (front wall), WX=10 (fire wall between the two bays at x<10 and the side wing). Bays bx=1+5i, 4 wide; doors open/rammed/shut; engines via p.vehicle(bx+2.0, 1, z0+6.5, 180, "fire_truck") (yaw 180 = nose north, out of the door: model nose is +z, Props.yaw north=180). Wing: watch room, chief's office, kitchen; upstairs bunk room, captain, showers, lounge, laundry; hose tower at the back corner (tx0=x1-3, tz0=z1-3, tt=TOP+8) with siren on ext_mast_foot. Gear racks and lockers use loot "fire" = loot_table/shop/fire.json (includes two_way_radio and aa_battery, which exist only on playtest, fine because jars build from playtest).

Preview tip: cars are entities, so shoot.sh renders never show them; check yaw in code.

**Sheriff.java + tools/city/sheriff.py** (shipped 2026-10-27): sh_ kit 99 blocks; Civic.sheriff(w, d) delegates; TownSite lots 20x30/20x32 with treeless zones. Z0=8, TOP=6, EY=9 (raised lettered entry, letters tile 0 at x=9-i), bands za=z0+7, zb=z0+14. Rooms: lobby (window x7), dispatch, sally port (roller door x15-17), office, squad, booking, three cells (bars at zb+2), armory, interview; lattice radio tower on the roof. Gun racks/lockers/evidence use loot "police" (shop/police.json). sh_cmu_tan has OUTSIDE_KINDS soot/moss/stain/crack plus custom pocks (bullet craters) round the door. Previews: previews/sheriff/.
