---
name: zomboid-bunker-review-tech
description: Bunker review pass (Josiah picked "Review pass, then wave 2" 2026-10-31 22:07): what each kind got, where in Bunker.java, how previews/before shots are made, progress
metadata:
  type: project
  modified: 2026-11-06T02:00:00.000Z
---

LANDSCAPE thread, branch claude/project-thread-d61kbn. Order: tower, quay ship, civil-defence shelter, forest hatch, army keypad; then wave 2 kinds. Before/after sheets go to /mnt/project-files/zomboid-mod/previews/bunker-<kind>-before-after.jpg, one bunker at a time.

- Part 1 TOWER (ae136ff4, in playtest 22:35): corridor width cw=9 under a tower -> opsFloor(r) (columns, consoles facing status boards, mouth0..mouth1 narrow mouth into storage). beams(r) grey steel I-beam every 3 cells in every room except DECON/WASH (new blocks bunk_beam, _tray, _pipes, _duct in tools/city/bunker.py). EXEC panelled + carpet; SERVER trays/cooling/UPS. occupants() at end of build(): bodies (p.vehicle "!body:...") and indoor walkers (negative radius) by state and kind.
- Part 2 SHIP (1c083d50, pushed 22:44): palette() ship branch: crew rooms container_inside + container_floor + container_inside roof; hold rooms ship_hold/deck_steel; boxes() paints ring 2 round each container room facing a hold room with container_<colour>, cargo doors (container_<c>_door[facing]) on the end facing the corridor. ship_frame ribs every 4 z in corridor. Ship rooms 4 deep (length()). Width 29 (was 25); BigShip still fits (SitePreview port container 330 160 seeds 1-24: same ports get a bunker).
- Part 3 ARMY (2da30e58, pushed 22:48; refs bunker/small-military-bunker-entrance, military/armory, camp/barracks-interior): cw=3 for ARMY (FOREST stays 2); corridor() vault branch = bunk_haunch (new block, quarter-round concrete) at row 3 on both walls, bunk_pipes_low row 1, lamp/tray at crown; ARMY corridor palette bare bunk_concrete; uses COMMS(guard room: 2 gun racks, pegboard duty board, cabinet), BUNKS (lockers back to back down middle via r.free), MESS, WASH, PLANT; armoury racks two tiers. Size ~23x47 (was 22x39); CampBlackPine has no other digs near (128,22,y-9).
- PAUSED 2026-10-31 23:56, RESUMED 2026-11-06 00:50 (Josiah: "You may continue"; told him ~7-9 h left, all bunkers).
- Part 4 SHELTER + FOREST (ed2eef81, playtest 425c1fe85 2026-11-06): tools/city/shelter.py cd_ kit (painted walls yellow/green/orange, triple bunks cd_bunk_*, cubbies, crank vents, blue escape hatch, ceiling diffusers) via shelterBunks(); FOREST lived-in rooms fh_boards + floor_boards, fh_bunk_* timber bunks. Kit prefixes must be unique (fr_ would wipe Foundry Row). Vault rechecked vs panic-room photos: no change. Sheets previews/bunker-shelter-before-after.jpg, bunker-forest-before-after.jpg. Review pass DONE; wave 2 in [[zomboid-bunker-wave2-tech]].
- map_colours.txt: never regenerate wholesale (other threads' gaps); insert own lines sorted. heightmap_tags.py is safe to rerun.
- Before shots: old classes compiled from 7a671196's Bunker.java in /tmp/claude-0/bk/oldcls (cloud scratch, gone next session: rebuild by `git show 7a671196:...Bunker.java`).
- Preview: tools/county/BunkerPreview OUT seed cut KIND:STATE (cut 3 looks into rooms); needs -cp ...:src/main/resources. SitePreview needs src/main/resources on the classpath too (county_plan.txt).
- NEVER run tools/city/gen_blocks.py wholesale (wipes ~61k assets). New blocks: subset generator (was /tmp/claude-0/bk/gen/subset.py, rewrite if lost).
- Playtest merge conflicts are usually en_us.json tail (both sides append): keep both, add the comma.

Related: [[zomboid-bunkers-direction]], [[zomboid-bunker-kit-tech]], [[zomboid-bunker-wave1-tech]], [[zomboid-garage-bunker-tech]], [[zomboid-ship-tech]].
