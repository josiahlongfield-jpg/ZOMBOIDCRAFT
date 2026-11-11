---
name: zomboid-airport-grounds-tech
description: Airport buildings round the terminal (tower, fire station, hangars, cargo, fuel farm, landside roads, road off I-95) built 2026-10-30; classes, coords, ah_ kit, preview
metadata:
  type: project
  modified: 2026-10-30T02:12:54.357Z
---

Built 2026-10-30 (LANDSCAPE, Josiah 01:18 "you can build on and around that"), branch commits 7d12c1e5..6e33d4df, playtest merge 31b128f7.

- Each building group is its own Grounds class built once in the airport frame and cached: AirportSite.piece(name, salt, builder) (LRU 6), copied per tile when the tile overlaps the class's BX0..BZ1. Landside is copied FIRST so buildings win.
- AirportGrounds: control tower at TX 200, TZ -372 (round shaft, Round kit "twr"/"twd"/rd_tglaze, square spiral stair 2 wide, cab CAB 38, base offices).
- AirportFire: ARFF station x -640..-563, z -205..-178, 5 bays facing the taxiway, crew block, roof watch room, hose tower, foam tank, car park behind.
- AirportHangars: H1 x 440..519 z -252..-171 (trijet nose-in via Airliner.place unturned, stair truck at L1, scaffold tail dock), lean-to east, H2 relief depot x 546..601, T-hangars from x 622. Doors = sliding leaves on 2 tracks (doors()).
- AirportCargo: cargo shed x -900..-760 z -232..-176, fuel farm bund x -780..-700 z -330..-275 (Harbour.tank/bund/pipeRack reused), gate on the NORTH fence.
- AirportLandside: access road az Airport.ACCESS_Z (-475) from the west fence gap (AirportSite.fence skips it) to the hangars; kerb road z -416..-405, legs x +-198, lot, shuttle shelters/stop, taxi rank, spurs to each building, path to the tower.
- Layout.airportRoad(): COUNTY road from the gate (world 2489,3136) to I-95 (2414,3134); landmark tiles get no spur otherwise.
- ah_ kit = tools/city/airside.py (bay doors, ARFF/cargo/fuel fascia signs, hangar leaves + numbers, track, scaffold, spare engine, guide sign, shelter, stop). corrugated_blue is RUSTED; use corrugated_blue_b for blue.
- Bldg.flat(..., h>0) adds a roofKit of HVAC units; hangars/cargo use h=0 + their own parapet.
- Preview: plan/ap/go.sh NAME RX RZ W D (mesh x = ax+RX, z = az+RZ, ground y 13), mk.sh; e.g. hang -430 260 300 130, fire 664 242 128 112, cargo 910 340 240 210, land 260 500 521 140, gate 1262 505 170 70.
- AIR GUARD RAMP (Josiah 02:42 "fighter jets, helicopters in hangars and outside, clean runway", built 2026-10-30, commit 6f3ce9f8): AirportHangars.guard(), BX1 990. Hangars 3/4 at x 744/794 (40 wide, door 10 high, pocketDoors clear the whole 32 opening), 2 jets each at x0+11.5/+28.5; flight line of 4 jets from LINE_X 858 at z -158 (kept EAST of the hangar exits); helipads 906/936/966 z -200, heli_hornbill hub offset +1.84 along its nose; hangar jets and pad 1 heli pinned "@usable" (else state rolls random and may be wrecked), line jets random; ops building x 848..879. Runway threshold bars only outside |az|>HALF_W now. Aircraft ids jet_corvid, jet_gyrfalcon, heli_hornbill (AIRCRAFT thread owns them). mesh.py --cars draws vehicles in previews.
- Sheets: surroundings-preview.jpg, guard-ramp-preview.jpg (in zomboid-mod/airport/).
- Not built yet (could offer): parking garage, ARFF crash tender vehicle (only the city fire_truck exists), GA light planes, shuttle stop at st-airport (1500,3700 is ~870 west of I-95).

Related: [[zomboid-airport-tech]], [[zomboid-landscape-queue]].
