---
name: zomboid-houses-tech
description: ZomboidCraft house interiors, furniture and exterior kits: Interior.java, House.java, furniture.py and exterior.py pipelines, HousePreview, CitySpecs size rule
metadata:
  type: reference
---

(2026-10-25, thread "Build the ZomboidCraft landscape".)

- Interior.java (world/county) plans and furnishes every House: hall-with-strips, row (shotgun, trailer) or studio (cabin) layouts; stairs beside the hall with banisters; two-layer walls so windows sit in curtained niches; walk paths kept clear (putAll reverts a piece that cuts a room's doorways apart). Fronts of fridges, stoves, wardrobes etc. are kept clear (FRONTED set + keep grid); one nightstand per open bed side. It reports hearths() so House can run chimneys up the wall outside; fireplaces prefer the end walls.
- Furniture kit: tools/city/furniture.py writes the home_ specs into blocks.json; exterior kit: tools/city/exterior.py writes the ext_ specs (198: roofs in quarter steps per kind charcoal/grey/brown/green/red/slate/tin_*, eaves with gutters, ridge/crest, under decking, windows ext_win_{frame}_{six|one}_{state}_{lo|hi}, shutters, doors, porch posts/beam/rails, chimney crown, flues, aerial, downspouts, plinth, AC, trash cans). Both painters are registered into photo_textures.py. Paint: photo_textures.py CC0_DIR OUT names. Then gen_blocks.py TEX_DIR, where TEX_DIR must hold ALL city textures (copy repo textures/block/city, overlay the new pngs), because gen_blocks wipes and recopies the folder.
- Box textures (b[6]) may be a per-face dict {up, down, north, south, east, west, side}. ext_ collision boxes are clamped to 0..16, so models may poke outside the cell.
- Ext conventions: base model faces north; wall-mounted pieces have their back at z=16 and facing = out from the wall; roof pieces' facing = the way the slope rises. House.gable() works in a u (along ridge) / v (across) frame.
- Front textures of north-facing boxes are seen un-mirrored: texture column u = 16 - x.
- RULE: CitySpecs.java must stay under Java's 64 KB per method. gen_blocks splits it into part methods by an estimated bytecode budget (40 + 9 per comma per line, 36000 per part); check with javap after big kit additions.
- Preview: tools/county/HousePreview.java OUT seed cut STYLE:COND[:floors]; ROOMS=1 prints room rectangles. Viewer coords: x = house offset + local x, z = local z + 2, ground floor surface y 5. Cut 3 shows the ground floor, cut 7 the upstairs. In the preview the house floor sits one block above the grass; in game y=0 is the ground layer.
- Sizes and lots were raised for the interiors (Homes.size, SuburbSite/TownSite/RoadsideSite lots). Shallow lots drop the porch first; houses under 7 wide or 9 deep become empty yards.

- Basements: county/Cellar.java (built after Interior). The odds roll on the household's own seed (Interior.seed()), so houses without a basement are unchanged. Interior.cellar() makes the way down between plan() and build(): a flight under the stairs in the column away from the hall (entry at sz0+4), or a trapdoor and ladder against a wall. It registers a pseudo-way so furniture keeps a path to it. The cellar floor is at y=-4, the rows are -3..-1, and the joists sit at -1. Kit: tools/city/basement.py (cellar_ blocks). Loot tables basement_food/medicine/guns/supplies are in shop_loot.py. Checker: /tmp/claude-0/stress/CellarCheck.java. Preview: HousePreview cut -2 or STYLE:COND:floors:use; scratchpad celin.sh.
- furniture.py's main() moves the home_ entries to the end of blocks.json. Restore the old order before committing to keep the diff small.

Related: [[zomboid-world-tech]], [[zomboid-building-detail-direction]].
