---
name: zomboid-park-museum-tech
description: Grayhaven City Park + Museum builder (CityPark/Museum/MuseumHalls/MuseumCells.java, mu_ kit in museum.py), lot, constants, preview coords, checks
metadata:
  type: project
---

Built 2026-10-28 by LANDSCAPE; branch commit c06be6cd, playtest merge 9f4b2b03. Variant "park" in
LandmarkSite (runs before Kit-based variants); Layout gives plan spot "park" a 3x3 lot (213x213).

Files (world/county):
- CityPark extends Grounds (signs() = "mu_sgn_"): lake LX106 LZ150 (ellipse 46x27), island temple
  IX122 IZ150, promenade z 82..87, bandstand BX40 BZ128 (trees kept 14 away: canopy over its steps
  failed headroom), palm house GX 166..196 x GZ 112..140 (ribs every 7 z), boathouse, service yard,
  playground, statue, relief camp, burials, railings, Kit.lawn + Trees.grow.
- Museum: shell x 34..178, z 18..78, floors y 3/11, roof 19; rotunda RX106 RZ50 (r 15..16.6, balcony,
  12 columns, copper dome + coffers + oculus); lanterns; two stair towers with roof huts; portico with
  stepped pediment; 2-wide arched windows; dock door at x178 z58..61; café back door (116, z78).
- MuseumHalls: gems, mammal dioramas, birds, ocean hall (whale hung by cables), rotunda T. rex on a
  plinth, galleries, shipping, Egypt/classical/arms upstairs, entrance with counters and barricade.
- MuseumCells: generated from `python3 tools/city/museum.py cells` (rex/whale skeletons split into
  decor blocks). Regenerate it AND rerun museum.py + photo_textures + gen_blocks if rex()/whale() change.

mu_ kit: tools/city/museum.py (registered in photo_textures.py after civiccentre); sounds must be
GAME_SOUNDS names, loot must exist in loot_table/shop (no "museum"/"jewelry").
Preview: `SitePreview DIR landmark park 213 213 SEED 8`; dump x/z = site + 8, y = site + 13; mesh
/tmp/claude-0/plan/park/mk.sh (m_pk_). Cams: rex 113,17,60->104,19,50; aerial 106,110,280->106,5,110.
Checks seeds 1-3: 0 blocked doors/treads; head-knockers only Kit's slide; one 4-spot "pocket" is the
museum's south window sills (jump only, harmless); orientcheck clean. No park/museum photos in refs yet.
