---
name: zomboid-civic-tech
description: Civic kit (tools/city/civic.py, cv_ blocks) and the rebuilt courthouse (world/county/Courthouse.java): tile order, even widths, basement shelter hosts
metadata:
  type: reference
  modified: 2026-10-26T19:19:11.499Z
---

Built 2026-10-26 in the LANDSCAPE thread from refs/town/courthouse photos (Kentucky courthouses).

**Kit**: `python3 tools/city/civic.py` rewrites the cv_ entries in blocks.json (indent=1 json). Textures: civic.register() in photo_textures.py; paint with `photo_textures.py /mnt/project-files/zomboid-mod/cc0 /tmp/claude-0/tex <names>` then `gen_blocks.py /tmp/claude-0/tex`. Has: columns/caps/bases (white, stone), cornice, rakes, balustrade, pier, steps, water table, belt, arched window heads (cv_arch_{red,buff,stone}_{1,l,r}), oculus (+ _l/_r pair), 4x4 clock dial (cv_dial[_lit]_0..15), copper dome pieces, louvres, bell, hip corners, mosaic/terrazzo/wainscot/plaster/oak panel, judge's bench, bar rail, pews, seal, flags, portraits, plaques (PLAQUES dict), EXIT signs, iron fence, globe lamps, flagpole, cell bars. Frieze lettering cv_frieze_{8,10,12,14}_i.

**Multi-block pictures**: on a north-facing face, tile 0 (the picture's left) goes at the HIGHEST x (viewer faces south, east is on their left); on a west face tile 0 at lowest z, on an east face at highest z, on a south face at lowest x. Same for arch/oculus _l/_r pairs. Boards.fascia follows the same rule.

**Centred pieces**: buildings are an even number of blocks wide (Courthouse.build drops an odd column), so doors, columns and tower centre on a block line. *_c variants (cv_seal_c, cv_finial_c, cv_bell_c) are the model shifted +8 px: a facing=south _c piece goes in the cell east of the line; an unrotated one in the cell north-west of the corner.

**Courthouse.java**: BASE=-3 basement (jail cells, boiler with stair up to the records vault, civil-defence stores, deed archive), F1=2 main floor (mosaic hall, offices, cross passage to side doors with stoops, vault + restrooms, grand stair), F2=8 courtroom + chambers/jury room, TOP=14. Hip roof with deck balustrade; portico gable runs back into the hip (roofGable to z0-2+rows). Shelter hosts carry a floor: TownSite.Host(..., floor) and CityCivic.courtFloor pass Courthouse.BASE to Shelter.dig, so the fallout shelter's stair comes up in the basement. Civic.courthouse(w,d) just delegates. CityCivic needs cd >= 20.

**Preview**: SitePreview TOWN county_seat 300 300 SEED; courthouse frieze at dump y 26, lot origin found by scanning for cv_frieze. Crop with /tmp/claude-0/pv/tw/crop.py (set origin [0,0,0]) and site_shot.sh. Related: [[zomboid-packtowers-tech]], [[zomboid-building-detail-direction]].

**Library.java** (2026-10-26, refs/town/library: Gilroy, Bayliss, stacks photos): BASE=-3 basement, F1=2, TOP=9; z0=6, zb = back wall behind the loan desk (5-6 rows of stacks/office behind). Classic (portico, cv_lfrieze_8, hip to a glazed lantern over the laylight) or cottage 30% (Bayliss bay with gable and bargeboards, end chimneys). Kit adds cv_stack (axis), cv_catalog, cv_circ_desk, cv_table(_lamp(_lit)), cv_globe_stand, cv_grille, cv_skylight, cv_book_drop(_tipped). plaque() auto-fits its text (fit()); keep plaque words to 6-7 letters per line. GOTCHA: TownSite's yard()/Kit.lawn fills any air at y=0, so lay the lawn BEFORE a building with a basement.
