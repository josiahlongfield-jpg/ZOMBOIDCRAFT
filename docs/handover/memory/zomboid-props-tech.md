---
name: zomboid-props-tech
description: How pack and Poly Haven 3D models become ZomboidCraft furniture blocks (tools/props/props.py), swaps into the world (PropSwaps/Cover), stocked store shelves, preview tricks
metadata:
  type: reference
  modified: 2026-10-26T17:16:14.183Z
---

Built 2026-10-26 in the LANDSCAPE thread (overnight mandate). Pack originals and staging: see [[zomboid-nature-packs-tech]].

**Pipeline**: `python3 tools/props/props.py [--only REGEX]` (~2 min for all) then `python3 tools/city/gen_blocks.py /tmp/claude-0/tex`. tools/props/meshio.py reads glTF/GLB, OBJ and binary FBX (with model transforms). Each PROPS entry: src (gltf/obj/fbx/ph(name, node_regex)/grocery), cells, height or width, fill (True or (fx, fz) share of the block), ref (source height that `height` scales, when things stand on top), front (+z/-z/+x/-x: the source side that becomes north), sit back/middle, ymax, shine, sound, title, wear, lift (dark lift, default 0.8), swap, always. Voxelised at 1/16, greedy boxes, per-face atlas pk_<name>[_xyz].png (+_n/_s/_nrm_gl) in tools/props/tex, blocks in blocks.json, layout in data/zomboidcraft/props.json.

**Swaps**: props.py writes world/county/PropSwaps.java: built-in block -> [group, stand-ins...]. county/Cover.java (wraps CountyGen's sink) picks by position hash; pick 0 keeps the built-in, unless the group starts with "!" (cfg always=True: shelves, counters, tills always swap so runs match). Multi-cell props map each cell to a built-in (fridge/home_fridge_top). Loot is copied from the original block.

**Stocked shelves**: stocked(kind, seed) clips SM_Shelf_01 (grocery pack) to one block (both end panels kept), scale so the first board (84.8 cm) lands at y=16; goods from GOODS/THEMES placed in facings, looted gaps, knocked over, dropped on the floor. Shelves stacked two high now use store_shelf_<kind>_top for the upper block (Shops.stack / Shops.top, CityCivic; interiors.py makes the _top blocks). Shops.aisles now builds back-to-back gondola pairs with an aisle kept free at maxX. counter(seed, till) does the same for SM_StoreCounter_01 + SM_Cashier_Machine.

**Caution**: running tools/city/interiors.py strips "loot" from every non-home block and resets store_ specs other threads changed (e.g. store_teller_glass); diff blocks.json against a copy and restore anything not yours.

**Checking**: /tmp/claude-0/pv/props/views.py NAME|'dict(...)' OUT draws the five sides (view 1 = north = front). gallery.py lays every prop out. For real placement: CountyPreview seed 12345 around 0,0 (main_street town; grocery near dump x 5-12 z 85-97, pharmacy x 27-36), set origin [0,40,0]; mesh viewer y = dump y (no origin), x/z = dump + origin; eye = floor + 1.62.
