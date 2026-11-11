---
name: zomboid-trains-direction
description: Josiah's decisions on driverless walk-in trains, call buttons with flare/siren, robot conductor, and the island downtown with bridges (2026-10-27); ideas doc link and build order
metadata:
  type: project
  modified: 2026-10-27T07:17:19.559Z
---

**The ask (Josiah, 2026-10-27, 06:43-07:15)**
- Working trains on a county railway to every town and major place, with stations scattered around.
- A subway that runs only inside a much bigger downtown, between landmark stops.
- A true-scale football stadium is the first landmark.

**Ideas doc:** https://claude.ai/artifact/DgHbLUEQ1Xz5HgpxqBAS3K (Claude Doc). The section "Your answers" holds his decisions.

**Decisions (07:15)**
- **Driverless:** each train loops its own route, run by a robot conductor. It is our own character (working name OTTO), inspired by the Black Ops 2 TranZit bus driver. Use no franchise names.
- **Walk inside while it moves:** this needs custom player-carrying code, like the Create mod's trains. Car size is about 22 x 4 blocks with 3 blocks of headroom.
- **Call button at every station:**
  - The train skips other stops to come to you, unless someone nearer on the line pressed first.
  - Above ground, the cost is a flare that draws nearby zombies. In the subway it is a siren that pulls zombies down into the station.
- **At a stop:** the doors open by themselves and stay open 30 seconds, then close.
- **Speed changes:** gradual acceleration and braking.
- **Downtown:** a New York style island city, cut off by wide channels and reached by big bridges. One bridge carries the railway. He said to make it "as massive as you need".
- **World size:** I advised against stretching the whole world. The island becomes its own big region and the towns keep their scale. He has not answered that yet.

**Build order (proposed)**
1. Test track with a walk-in train, OTTO, the call button and the doors.
2. Fixed county plan with the island, channels and bridges (see [[zomboid-county-plan]]).
3. The island downtown, stadium first.
4. Subway and railway lines with their stations.
5. The other landmarks.

**Stops (Josiah 07:21):** subway stops are Grayhaven Stadium, Union Station, Mercy General, Waterfront, Broadcast Tower and Power Station. The other landmarks from the ideas doc are scattered around the city (not stops). Lower the train speed slightly so the view from the ride is enjoyed. Subway stops well spread apart, NOT all on the island's outskirts: subway runs through the middle too (Union Station is the ONLY interchange between subway and above-ground railway, central; Waterfront on the island edge). Draw it on the county plan page first so he can move stops. Railway is ELEVATED only inside the island city (like the current elevated line); after the bridge to the mainland it runs at ground level (Josiah 07:23).

**Thread split (Josiah 07:21, "yes make a train thread"):** a separate TRAINS thread builds the vehicle and its systems (walk-in cars, OTTO, call buttons, doors, speed curve, test track). LANDSCAPE keeps the world side: island downtown, channels, bridges (one carries the railway), stadium and landmarks, track beds, tunnels and station shells. TRAINS should send LANDSCAPE the gauge, car size and platform size.
