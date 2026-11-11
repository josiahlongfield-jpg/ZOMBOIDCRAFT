---
name: zomboid-shopfronts-tech
description: Main street trades (Trades.java) and the shop-front kit (tools/city/fronts.py, front_ blocks) in Storefront.java; how to preview a row of fronts without an http server
metadata:
  type: project
  modified: 2026-10-26T13:36:05.870Z
---

Built 2026-10-26 in the LANDSCAPE thread (batch 1 of Josiah's "all the buildings" ask, main street first).

- world/county/Trades.java fits out the main-street trades that aren't shelf shops: bank (teller line with glass, rope queue, check desk, vault with deposit boxes when d >= 14), barber, tavern (bar, back bar, booths when wide, pool table, jukebox), hotel (lobby; inside stair + rooms upstairs via Flats.stairwell's Upstairs callback when w >= 9 and d >= 14 and floors > 1, else back porches with rooms(y, d - 1)), realty, VFW, vacant. Back rooms come from Shops.backRooms (partition at d - 5). Shops clears the cell in front of each partition door, so never put furniture at z = d - 6 in front of a possible door.
- tools/city/fronts.py writes the front_ blocks (47): corbels per brick colour, stone and wood cornices + brackets (paints white/brown/green/black), hood_arch/hood_flat, bay_corbel, pediment_l/r + finial, datestone_<year>, vitrolite_<black|green|cream>, canopy_metal(+edge_red/teal, rod), coping_metal (no rotate, lips on all faces), gallery_<red|green>(+_edge, post_base/post/post_top), canopy_wood(+edge), trim_/cap_<white|brown>, lampbox_dead. gen_blocks clamps front_ collision like ext_. Base model faces north = out from the wall, placed in the cell in front of the facade.
- Storefront.java uses only mod blocks now. False fronts get Bldg.roofGable (ext_roof_<kind> courses: eave, under, crest/ridge). ShopLights.lamp's dead lamp is front_lampbox_dead; lit lamps stay froglights on purpose (Photon colours light by block id).
- Preview: scratch tool /tmp/claude-0/sfp/.../ShopRowPreview.java (row of fronts, args OUT seed cutY) and a Playwright script that serves files through page.route instead of an http server (scratchpad rshot.js). A background python http.server was refused by the auto-mode classifier on 2026-10-26, so use the routed script.
- Photo refs: main-street-exterior has 8 photos; the research thread queued bank-lobby, barber, tavern-bar, hotel-lobby, hotel-room, vfw-legion-hall and real-estate-office interiors. The trade interiors above were built before those photos arrived: redo against them when they land. Civic photos are queued after them for batch 2, in refs/town/ courthouse, church-exterior, church-interior, post-office, library, fire-station, sheriff-office, school, cinema-theatre.

Related: [[zomboid-houses-tech]], [[zomboid-building-detail-direction]], [[zomboid-photo-reference-rule]], [[zomboid-playtest-build]].
