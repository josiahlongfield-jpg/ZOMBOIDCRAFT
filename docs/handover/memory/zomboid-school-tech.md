---
name: zomboid-school-tech
description: How the county school is built (School.java, tools/city/school.py sc_ kit), its grounds kit (playground, court, football field), preview spots
metadata:
  type: reference
---

Built 2026-10-27 in the LANDSCAPE thread (overnight mandate [[zomboid-overnight-mandate]]), commit e0fa0353. No school photos in refs/ yet: text research only.

**Kit**: `python3 tools/city/school.py` rewrites the sc_ entries in blocks.json (325 blocks); photo_textures.py calls school.register() (sc_stencil reads the asphalt photo). Paint: list EXTRA keys starting sc_ (run python with sys.argv=['x'], import photo_textures), then photo_textures.py ... NAMES, then gen_blocks.py. Pic class (draws at 4x, `_pen/_ink` layers so alpha<255 blends instead of punching holes). Colour key a/b/c (blue/maroon/green) picks dado, lockers, pennants, keys, centre circle.

**School.java**: Civic.school(w, d, high) delegates. Z0=12 front wall, ST=5 storeys, gx=x1-15 gym party wall, za=Z0+8 / zb=za+4 corridor walls. Ground front: office 1..10 (principal behind wall x5), lobby 12..17 (za wall opened), nurse 19..22, library 24..; back: classrooms by split(1, gx-9, 8), boys/girls restrooms by the gym. Classroom ends: tidy/fled/barricade/overrun. High school: 2 storeys, stair at corridor z=za+1, x 1..5 (well railed upstairs), pull station moves to zb-1. Gym: court keys sized to the court (kd), circle centred, shelter 70% (cots, supplies, registration, message wall) else cafeteria. sc_cot: head pillow at the model's north, foot = head - step(facing) (opposite of bunk_medbed).

**Grounds** (Kit.java, RoadsideSite): Kit.playground 7x7 on sc_chips (swings bar along x, slide down the east side facing south, merry 2x2, horse, seesaw axis z, monkey bars on the south edge); Kit.sandbox(x,z,w,d); Kit.court gooseneck hoops (post, neck, board in the next cell, chain net under). Football: sc_turf bands, sc_turf_line, sc_turf_rough drifts with gc_ grass (Builders.clearGrass counts sc_turf* as soil), sc_goal_* H goalposts, sc_bleacher_alu facing the field, scoreboard on truss legs. backZ = sd + 7 keeps the bleachers off the back wall. Evacuation cots are sc_cot.

**Previews**: TOWN main_street 300 300 seed 3 (school around dump x 184..227, z 269..318, front faces west); ROADSIDE school 170 150 seed 1 (high school); ROADSIDE school 220 220 seed 4 (football field). Shots in /mnt/project-files/zomboid-mod/previews/school/. The preview renderer shows no cars and rain falls indoors (renderer only).
