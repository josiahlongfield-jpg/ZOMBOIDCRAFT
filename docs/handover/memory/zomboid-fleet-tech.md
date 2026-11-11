---
name: zomboid-fleet-tech
description: Airport airliners/bizjets (LANDSCAPE 2026-10-30): fighters moved off the airport, tools/city/fleet.py an_ kit (4 walk-in types), AirportGates bridges/stands, jet centre bizjets, preview
metadata:
  type: project
  modified: 2026-10-30T04:44:53.182Z
---

Josiah 2026-10-30 04:12: "military base has jets/helis, take them away from the airport and replace them with airliners of various types, private and commercial; military base needs a long clean runway". Decision card posted (block-built walk-in / flyable bought pack / both; recommended BOTH: blocks now, flyable later) — answer pending at time of writing.
- Done (commits b90af0ab, ac86a0b2; playtest 15e2b8ac): no jet_/heli_ vehicles left in Airport*.java. Guard ramp = private jet centre (AirportHangars.guard: lounge in the ops building, hangars G3 744 / G4 800, GW 44, GDOOR 13, GROOF 17, one bizjet each nose out + stair truck; line of 2 bizjets from LINE_X+20 step 50, nose at LINE_Z+18 rot 2). Caldwell Army Airfield already has the jets/helis and a 1200-long runway ([[zomboid-army-airfield-tech]]).
- fleet.py (an_ kit, 8265 an_s blocks): parametric copy of airliner.py's trijet (Type: R, C, NOSE, TAIL0, LEN, AFT, doors, windows...). Types twinjet (coastal, skyline), widebody (north), turboprop (coastal), bizjet (private, charter); liveries from airport.py AIRLINES, made-up regs. Layouts data/zomboidcraft/airliner/<type>_<livery>.txt with a "#! floor= west= east= skin= length= span=" header; Airliner.plane(name) loads a Plane record (place(placer,x,y,z)). Fittings reuse al_ props. Registered in photo_textures.py after airliner.
- AirportGates (piece "gates", bounds x 30..380, z -340..BZ1): Pier B west wall PX0 108 / east 132; twinjets nose-in at gates 5 and 9 (z -312, -236) rot 1, widebody gate 8 (z -274) rot 3; CZ = gz - d*(3 - skinW); bridge = tunnel along the gate door row then a cab cell at the door, steps up for the widebody's floor 9. Turboprops nose north at x 270/330, z -318 with AirportGates.stairTruck (static, plane frame).
- Preview: /tmp/claude-0/plan/an/go.sh (PLANES="..." to limit meshes) -> m_an_<plane>; airport: plan/ap/go.sh gates -30 345 360 215, jets -730 245 262 122. Sheet /mnt/project-files/zomboid-mod/airport/airliners-preview.jpg.
- If flyable airliners are wanted: needs a bought model pack converted by the AIRCRAFT thread (aircraft.py), no airliner .zcar exists yet.
