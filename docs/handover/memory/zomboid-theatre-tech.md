---
name: zomboid-theatre-tech
description: Theatre (picture house) tech: tools/city/theatre.py tr_ kit, Theatre.java layout and stories, lot placement fix, preview coords.
metadata:
  type: project
  modified: 2026-10-27T03:30:00.000Z
---

Built 2026-10-27 in the LANDSCAPE thread under the overnight mandate ([[zomboid-overnight-mandate]]). No theatre photos were in refs/ yet, so it follows text research only (1930s-40s small-town picture house). Redo if photos land ([[zomboid-photo-reference-rule]]).

- Kit: `python3 tools/city/theatre.py` rewrites the tr_ entries in blocks.json; registered in photo_textures.py after school. ~327 blocks: marquee board tr_mq_{a,a_on,b,c}_0..29 (15x2, tile col c at local x = 14 - c on a north face), end signs tr_mq_side_0..3 (2x2), tr_mq_box(_on) soffit, tr_bulbs(_on) axis, blade tr_blade_cap/0..5/foot (letters on east/west faces), terracotta tr_tc + weathered _b/_chip/_crack/_rust/_soot, panel/cornice/pilaster, crest, posters, booth, terrazzo name, lobby (carpet, plaster = red damask, wainscot, frieze, candy cases, backbar, popcorn, soda, podium, sconce, chandelier, lobby cards, signs, tr_stair), seats (two per block, sitter faces the facing; tr_seat_aisle has its lit end on the base east, _l on the base west), house (tr_ceiling coffers, prosc band 11x2 with masks, tr_prosc_col, curtains/tie/hem/torn, valance, screen 9x4 and screen_lit, tr_masking black velour, stage, apron with footlights), booth (projector lo/hi, port, rewind, cans, reel, film), tr_loft/tr_loft_carpet (floor up, coffers down).
- Marquee light: only every third lit board tile and every other end tile carry block light (the preview overexposed at night with all of them).
- Theatre.java (Civic.theatre delegates): 15 wide, front z=0 facing north, marquee z -3..-1 over the sidewalk (street lamps stand at z=-4), recess x 5..9, ZL=3 lobby front wall, lobby z 4..6 (candy counter down the east side, ladder to the booth in the west corner), ZA=7, cross aisle z=8, rows from z=9, floor dug down (yLow -1..-3), proscenium zp = d-4/-5/-6, screen at d-2. Stories: LAST (lit, film burned on the screen), CLOSED (board b, seats up, curtains shut, notices), HOLDOUT (board c, barricades, camp on the stage); overrun adds bodies.
- Placement fix: TownSite main_street used to put the theatre where the alley side street's sidewalk corner cut its lot to 18 while the build assumed 22 (it overran into free ground that got grassed). Now it sits at along(main, x2, gz) - 17..22 and uses theatre.d() - 2. main_street lot 24 (d 22, 7 rows), county_seat lot 28 (d 26, 10 rows).
- Board text: BOARDS in theatre.py, "_" marks a fallen letter placed by hand. RULE: random letter drops must go through paint_textures.rude_gaps (school letter board and signs.py channel signs use it); a random drop once turned COUNTY into a rude word.
- Preview gotchas: an unknown block name (e.g. a texture name like tr_gilt_col, or "barricade", the player plank block that isn't in blocks.json) makes mesh.py/rshot hang, so run palchk.py first and wrap shoot.sh in timeout. Night shots need dark=1&ev=-1.5 or the marquee blooms out.
- Seeds on main_street: 3 LAST lit (x 86..100), 2/5 CLOSED (x 85..99), 9 HOLDOUT (x 87..101), 1/4 LAST unlit, 6-8 LAST lit. Previews in /mnt/project-files/zomboid-mod/previews/theatre/.
- Preview: TOWN main_street 300 300 3: theatre at dump x 86..100, front z 161 (dump x = 100 - lx, z = 161 - lz, y = 12 + ly). Cutaway crops with ymax 22 for the house.
