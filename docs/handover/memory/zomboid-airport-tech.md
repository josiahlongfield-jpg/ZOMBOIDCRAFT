---
name: zomboid-airport-tech
description: Grayhaven International Airport build (LANDSCAPE, 2026-10-29): site moved east of I-95, frame/tiles, af_ kit, location card, refs, preview
metadata:
  type: project
  modified: 2026-10-29T19:33:59.055Z
---

- SITE: plan spot (3740,3610), east of I-95 between the city wall and the lake. Josiah CHOSE 'East of I-95, regional hub' at 19:48 (2,000-block runway, ~12 gates, road off I-95, shuttle at st-airport).
- Frame (Airport.java): ROT=0, so world = (RX+ax, RZ+az). Runway 9/27 E-W, cells ax -1000..999, az +-22. Taxiway A az -122; connectors A1-A5 at ax +-985, +-500, 0. Hold line az -76. Apron az -330..-134, ax +-420. Landside az -560..-200 (north, toward the city). X0=-1250, X1=1149.
- Wiring: Layout.fixed special-cases spot "airport" (named airport, airport#1..). LandmarkSite dispatches variant "airport@RX,RZ".
- af_ kit = tools/city/airfield.py. Hold sign "27-9", tile 0 at the higher x when facing north.
- Preview: /tmp/claude-0/plan/ap/go.sh NAME RX RZ W D (SitePreview at 0,0, so pick RX,RZ to frame the tile), then mk.sh NAME x,12,z R; mesh m_ap_NAME.
- Refs: refs/airport/ (14 folders, landed).
- MW2 TERMINAL HOMAGE (Josiah 19:49): he wants the airport to look like Modern Warfare 2's multiplayer map 'Terminal' in detail. Homage: layout/feel only, own block art, made-up names, no game assets. Scope CHOSEN 19:51: main concourse (security to gates) + WEST pier are the homage, with a walk-in airliner at its gate; check-in, claim and the east pier stay a normal regional airport.
- Terminal code: AirportTerminal.java (Grounds) is built once into a cached Voxels in frame coords, then copied per tile.
- Airliner: tools/city/airliner.py (al_ kit, ~1795 blocks, committed d6fc026c) + Airliner.java (stamps data/zomboidcraft/airliner/trijet.txt, a 727-style trijet since 23:00; frame nose z=0 north, x=0 centre, y=0 apron, FLOOR 7, doors L1 z10-11 / L2 z42-43 west side x=-4). Preview: plan/al/go.sh -> mesh m_al_plane; mesh coords = model coords (NOT +34).
- HOMAGE BUILT 2026-10-29 (commit 468a05c6, playtest a27982d8): TerminalHomage.java (Grounds) from AirportTerminal.build(); west block x -140..-41 z -331..-236, lobby x -41..31 z ..-299 (upper floor y12, roof 18), tarmac south of it; plane nose west at NX -38, PZ -250 via p.turned(NX,0,PZ,3), bridge gate 1 room -> L1, rp_slide off R2. Indoor tree must use ap_fir_* (Builders.clearTrees strips tree_* under roofs). Pier B = gates 5-10.
- Kits: rp_ = tools/city/ramp.py (jet bridge, stair truck, tug, dollies, ULDs, belt loader, GPU, chocks, wands); homage shop pieces in ap_ (ap_store_<k>, ap_menu_<k>, sushi belt/case, gondolas, book table, mag rack, tray rack). Preview yard: plan/rp/go.sh -> m_rp_yard. Terminal preview: plan/ap/go.sh term 150 420 301 271 then mk.sh term.
- 2026-10-29 22:43 Josiah sent 12 MW2 Terminal screenshots (saved /mnt/project-files/zomboid-mod/airport/mw2-terminal-shots/). They show: round skylight atrium with tree in stone planter; corner burger kiosk, checkered floor, skylights; wood bar + stools by windows; vending machines, palms, golf cart, paper planes hung; ULD stacks, painted gate number, police line, service bldg with roller door, windsock. He then sent the overhead map (13) and said its labels are only a loose guide, "prioritise building this now" (rework chosen). 23:12 PLANE RULE: the map's "PLANE" inset is only drawn off to the side; the plane slots in beside the tarmac so its "TO PLANE" bridge meets the open section lined up with it, the bridge corridor from the front door runs into the building and connects to the stairs. Refs 14 (tarmac/slide) and 15 (overhead photo) confirm: nose WEST toward the hallway, bridge to the port (south) front door, slide from a starboard (north) mid door onto the tarmac, ULD stacks north of it.
- Surroundings BUILT 2026-10-30: see [[zomboid-airport-grounds-tech]].

Related: [[zomboid-landscape-queue]], [[zomboid-photo-reference-rule]].
