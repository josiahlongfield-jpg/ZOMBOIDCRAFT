---
name: zomboid-carrier-tech
description: How the ZomboidCraft aircraft carrier, its aircraft and military zombies are built (Carrier.java, tools/cars/aircraft.py, zcar.py, mesh.py --cars, hull floors, preview coords)
metadata:
  type: project
  modified: 2026-10-26T04:50:10.399Z
---

Shipped to playtest 2026-10-26 (landscape thread). Direction: [[zomboid-carrier-direction]].

**Carrier** `world/county/Carrier.java`, placed by PortSite for PORT variant "naval" (County picks it). Frame: bow +x, keel line z=0, starboard +z; decks FLIGHT=15, GALLERY=11, HANGAR=2 (relative). Rooms come from a Room enum picker per deck (berthing, mess, galley, armory with gun racks/ammo crates/issue cage, magazine, stores...). Jets parked by `park()` with hangar spots table; `p.vehicle(x, y, z, yaw, "jet_corvid"|"jet_gyrfalcon", seed)`. Blocks are `navy_*` from tools/city/navy.py (gen_blocks pipeline). The deck number "90" on the island and deck is fictional.

**Aircraft** (2026-10-26): `tools/cars/aircraft.py SRC --only corvid|gyrfalcon|hornbill --preview DIR` converts the models Josiah bought (jets.zip in project files; unpack to a scratch dir, NEVER commit the originals): jet_corvid = bought F-22, jet_gyrfalcon = bought Su-27 (decimated to 20k tris by tools/cars/Reduce.java), heli_hornbill = bought AH-64D. tools/cars/seen.js (headless Chromium) orients triangles for the game's back-face culling. Made-up markings (GH, NAVY, 104/211/305), 4 states, ~1.3/2.7/2.0 MB. jets.py was deleted. Su-27 build ~2.5 min (needs node, java). Helipad: Carrier.helipad() at PAD_X=22, PAD_Z=16 (stern quarter to starboard), Apache placed 1.84 m aft so its rotor hub is over the H; per-model chock positions in Carrier.wheels().

**Java**: CarModels.Model.floors/floorAt()/jet(); Cars.hullColumns adds 4 underside values per column and layers() leaves whole blocks under a wing empty (walk under wings). Jets never spawn on streets (ChunkWriter.car/CityPlacer.car filter !jet()), right-click/use always sits you in, engine never runs ("Dead batteries, no ground power"), no horn.

**Military zombies**: ZombieSpawner.uniform() sets ZombieLook.OUTFIT "soldier" for ~85% of spawns standing on navy_ blocks, 80% inside the naval PORT place, 70% on MILITARY places, quarantine = soldier/patient mix. The flight deck (navy_fl*, catapult, cable) is exempt from the "perched" rooftop rule. A navy-specific outfit (deck crew jerseys) would need the characters thread's skins.

**Previews**: `tools/cars/zcar.py` reads .zcar back; `mesh.py --cars` draws a dump's cars (dump car entries are index space, mesh adds meta origin; 7th field is a model pin like "jet_corvid" or "id@state"). Carrier preview: scratchpad ship/pv.sh `cv1 naval 1 430 330`; in that view the flight deck is y=36, hangar floor y=23, armory around (268, 15, 81-95), deck jets around x 305-346 z 79-117. Glass renders as a checker in the viewer. Sheets: /mnt/project-files/zomboid-mod/previews/carrier-standard.jpg and carrier-photon-rain.jpg.

Related: [[zomboid-ship-tech]], [[zomboid-cars-plan]], [[zomboid-world-tech]].

**Cockpits RULE (Josiah 2026-10-26 10:36/10:40):** every bought cockpit keeps ALL its detail ("i bought the packs for that reason"). aircraft.py now: cockpit triangles (spec cockpit= groups, plus tub= boxes for a tub modelled in the hull, as on the F-22) skip decimation, keep source colours (no darkening, little weathering), get their own atlas density (cockpit_px 160/128) on a 2048x2048 atlas. Seat eyes are measured from the model (seat pan + ~0.76, above the glareshield, below the canopy, ~0.15 ahead of the headrest); the old F-22 eye sat behind the headrest.
