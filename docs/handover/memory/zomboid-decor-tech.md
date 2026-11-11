---
name: zomboid-decor-tech
description: LOOT wave 4 stage 5 decor system (decor.py specs, DecorBlock, Comfort, DecorNet, Moving hooks, hand truck); models wait on REFERENCES photos (2026-10-31)
metadata:
  type: project
  modified: 2026-10-31T19:59:08.858Z
---

Built by LOOT on claude/project-thread-jd27hs (commit 5815985c1, CI green 2026-10-31). Not merged to playtest yet because nothing is visible until the models land.

- **tools/items/loot/decor.py** is the 100 pieces from the approved wave 4 doc (6b6d542d). Columns: id | rarity | value | kg | tags | looks | comfort | flags P/B/L/T | play | noise | light | special. It writes data/zomboidcraft/decor.json. Roster rows and models get added per batch, ONLY after the photos for that batch are in refs/items/decor/<id>/.
- **REFERENCES** has the list at refs/decor-photo-list.md, with lighting and neon first. It runs on Josiah's PC and will tell LOOT when a batch lands. Expect a few representative photos for the many-look items (posters, vinyl).
- **com.zomboidcraft.decor**
  - **Decor** loads the specs and registers block decor_<id> only for ids that have a live roster row. It runs registerBlocks() before LootItems.register; LootItems then makes a DecorItem (a BlockItem) for those ids.
  - **DecorBlock** has properties FACING, ON and LOOK (0-15, taken from the item's CUSTOM_DATA "look"). It can't be mined (strength -1) and uses forceSolidOff. Random ticks handle power: a plain light comes on with power, and a powered piece goes off without it. Noise is scheduled every 100 ticks while ON.
  - **Comfort** runs every 200 ticks for each player indoors. It sums decor comfort over the LightSwitch.room() flood (now public), at dy -2..3, capped at 24, and eases boredom, unhappiness and stress.
  - **DecorNet** handles TOGGLE and PLAY (a 10 s TimedAction; play points come off boredom, a third of that off stress).
- **Client:** client/decor/DecorClient puts a dot on usable pieces. Tap G to toggle or play. The G menu has both, chained in CarClient after GeneratorClient.
- **building/Moving hooks** (agreed with BASES 19:44):
  - Moving.decor(spec): small pieces are LOOSE; big ones are FURNITURE, or APPLIANCE if powered.
  - pickUp keeps the look via DecorBlock.stack(state).
  - Hand-truck refusal: Size.LARGE and kg >= building.handTruckKg (40) needs hand_truck in the off hand. It only applies once a hand_truck item is registered.
- **Specials not built yet** (the special column): spots, blacklight, lure, calm, tubes, beacon, letters, step_alarm, sweep, aim_xp, cue/needs_cue, roll, train, needs_tv, prize_toy, tickets, payout, seat, rest_fast, bed/bed_best, haircut (clothing Grooming), drinks_store, stove_heat, muffle, cold_store, massage, chime, glows, days, time, paint, back_road, coloured_light, record/needs_record, vhs, old_broadcast, dress_decoy, decoy, sweets_store.

Related: [[zomboid-power-grid-tech]], [[zomboid-base-customisation-plan]].

**Lighting batch built 2026-11-01 (bad39aaea):**
- 15 pieces with 102 looks: wave5decor.py, with looks(it) per look and table(it) for look 0. Roster section ROSTER['Decor'] (tab household); uses.decor_line writes the tooltip; gen_java names the blocks block.zomboidcraft.decor_<id>.
- decor_models.py writes models/block/decor/<id>__<k>.json plus _on.json (glow mats get light_emission 15), the textures/block/decor/decor*.png sheets, and blockstates/decor_<id>.json (facing south 0/west 90/north 180/east 270; looks repeat past the built count). Run it after editing wave5decor.py (about 4 min).
- Decor.register rolls a random look on loot drops. The item in the hand always shows look 0.
- All 20 lighting pieces (141 looks) are in playtest 1ac0beb53 (Josiah approved 03:43). The textures/block/decor sheets are already ~9.4 MB for 20 pieces; lower the px density before the full 100 if size or performance matters.

**Games and leisure batch (2026-11-01, started on Josiah's "move on to the next" 04:41):** wave5games.py (18 pieces, 99 looks), pulled in by wave5decor.looks via wrap(). Boards, screens, felts and signs are face painters (face_paint uses f.uv, so they survive scaling). Big tables are built at world scale (~0.4 unit/inch): the pool table is ~2 blocks long and only the centre block is solid. gen_loot.look_for now merges a look's own '_px' (fine texels for lettering). Skee-Ball is a brand, so the display name is "Alley Roll Lane" (id skee_ball_lane kept). Never use 'base', 'body', 'tint' as material names in a looks dict (they collide with it() params). Merged to playtest 1e89d7cf3 (Josiah "merge" 06:00); decor_models.px_for now paints placed decor at ~half density (block textures 17 MB for 38 pieces). Next batch: furniture and comfort (18).
Furniture batch (2026-11-02, Josiah: "do the next batch"): wave5furn.py, 18 pieces / 66 looks, commit 7aeff22cb, preview sent; merged to playtest fbe6d0e31 (Josiah "Merge" 01:17). Next offered: wall art and display (16). hand_truck is a plain loot item (no block, decor_models skips it). Only gap in refs for later batches: neon_clock (empty folder); fetch on his PC.

**Wall art batch 2026-11-02 (75cde91fc, in playtest 8246fa1e3, jar sent):** wave5wall.py (15 pieces, 141 looks; neon_clock skipped, no photos). Label params are reserved material names too: base, band, ink, art, art2, word_ink, label (use 'print' for poster art). Decor textures now 36 MB for 70 pieces.

**Music batch 2026-11-02 (415c59976, in playtest, jar sent):** wave5music.py, 14 pieces, 57 looks. Gotcha: Model.seg plane='xz' is really the z-y plane; use wave4.flat() for horizontal bars. Rod axis=2 takes cx=x, cz=y. Textures 39 MB for 84 pieces. Last batch: collectibles (14).
**Collectibles batch 2026-11-02 (1dce29edc, in playtest, jar sent 04:4x):** wave5coll.py, 14 pieces, 52 looks (armour = samurai, from the photos). After it, 98/100 decor in; only neon_clock (needs PC photos) and hand_truck (tool, no block) remain. Textures 40 MB. Preview http.server must run via run_in_background or it dies with the shell.
**Decor specials 2026-11-02 (Josiah "Anything that needs functions"):** decor/DecorUses.java (all special handlers), DecorEntity (container BE for HOLDS: drinks/cold/sweets store, stove fuel, mannequin outfit; searched via loot/Nearby), DecorBlock.Holding + TICKS scheduled ticks (chime hourly, stove burn, vending chill, beacon zombie alarm, decoys lure, blacklight lights posters, stained glass by daylight, disco dust spots). DecorNet Actions TOGGLE PLAY OPEN REST FUEL COOK DRESS ROLL READ NEXT_LOOK. Hooks: Sleep.bedOf (BEST bed = waterbed), ZombieEvents footsteps (rug), NpcWork lit stove, Grooming barber mood, Moving drops contents. Not done: neon tube kit lines, spray paint marks, searchlight beam.
