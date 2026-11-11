---
name: zomboid-ravenrock-tech
description: Ravenrock Penitentiary (NW zone site 5) build tech: pn_ kit, FrameSite frame, island dock, Waterfront prison slip, ferry bell crossing
metadata:
  type: project
  modified: 2026-10-30T08:15:04.243Z
---

Built 2026-10-30 after Josiah picked "Prison ferry" (07:23). Part of [[zomboid-nw-military-plan]].

- Kit: tools/city/prison.py (pn_ blocks: bars, cell/seg doors, bunks, toilet_sink, catwalk, rails, posts, gates, visit booths, plate shop; pn_sgn_KEY_0/1 signs, pn_name_0..5, pn_spray_*). Registered in photo_textures.py after ranges.
- Site: Ravenrock.java (FrameSite, variant "ravenrock@6600,-1500@..."), frame X -400..359, Z -160..159, tiles 240 wide, level ~79; plus a dock Place (frame x -610..-401, z -32..47, waterSide 3, quay 46) sharing the variant. Yard (Grounds) builds wall, fence, RavenrockCells (2 wings + Center), RavenrockHalls (dining, industries, infirmary, segregation, gatehouse, sally port), rec yard, 7 towers, south-wall breach, checkpoint, officers' village, works, graveyard, landing (cove dug to sea+4, quay, PrisonFerry "RAVENROCK II"/"WARDEN HALE").
- PrisonFerry.java: 34 long, beam 10, black hull, barred cage aft, saloon fwd; build(name), keel sea-3, deck sea+2.
- Mainland slip: Waterfront.slip() at the Waterfront (Grayhaven east shore, faces the island): jetty z 66..68 out to x 90, "RAVENROCK I" alongside, guard pen x 29..35 with booth, boom, floods, signs. gap() covers z 63..70.
- Crossing: block zomboidcraft:ferry_bell (world/ferry/FerryBellBlock + Ferries: payload Ring, G via CarClient chain, client/ferry/FerryClient). Ring = bell sound, 3 s wait, everyone within 5 blocks blinded 2.5 s and teleported to the far landing. Ends from county/FerryRoutes (Waterfront.BELL/LANDING, Ravenrock.bell/landing(level)); verified against the real seed: bells at 3732,67,-960 and 6038,67,-1490.
- Preview: /tmp/claude-0/plan/rr/go.sh NAME RX RZ W D [wside quay] (SitePreview got -Dplevel/-Dwside/-Dquay); mesh x = frame x + RX + 8.
- The plan's causeway still reaches the island's north gate (blown); the ferry is the working way in.
