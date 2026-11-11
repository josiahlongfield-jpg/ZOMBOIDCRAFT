---
name: zomboid-market-tech
description: Old Market on Grayhaven island (built 2026-10-28): Market.java/MarketHall.java, om_ kit (market.py), lot, preview loop, the access fixes it forced in Flats/Interior
metadata:
  type: project
---

Old Market (plan line `place market landmark new 3850 -2150`), 3x2 landmark lot 213x133 (about x 3774..3986, z -2226..-2094). LANDSCAPE thread, built 2026-10-28. No ref photos yet: redo when city/market photos land ([[zomboid-photo-reference-rule]]).

- Market.java (extends Grounds): two Storefront rows of shops (north row drawn through p.turned(x+bw-1,0,NF,2), south via p.shifted), lanes with carts, umbrellas, string lights (om_lights axis z), plazas (statue, flagpoles, granite fountain), stall keepers' barricade at the east end.
- MarketHall.java: Greek-revival granite hall HX0..HX1 x HZ0..HZ1, porticoes with pediment (om_rake), copper dome on a drum with clocks over the centre (oct() test), stalls per bay from TRADES, barter post under the dome (wire walls, gate east), upstairs long room (office, banquet, cots, stores, kitchen) with a well under the dome, stairs at WST/EST via Grounds.stair.
- Kit: tools/city/market.py (om_, 185 blocks), registered in photo_textures.py. After any kit change run market.py, photo_textures with market.textures(), then gen_blocks (else palcheck shows missing blockstates). Never name a sign key "stairs": headroom.py counts it as a tread (key "up" says STAIRS).
- Preview: /tmp/claude-0/plan/market/go.sh SEED (SitePreview landmark market 213 133); mesh mk.sh; dump coords = lot + 8.
- Access fixes made here, shared with towns: Flats.porches puts the door lamp at py+3 (at head height it blocked the one-board walk next to the rail); Interior.body() keeps corpses off path cells (a body inside a door lifts you into the lintel).
