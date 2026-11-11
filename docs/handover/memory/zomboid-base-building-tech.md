---
name: zomboid-base-building-tech
description: ZomboidCraft base building + digging rules code map (building/ package, Build page, Placer, wave 1 blocks, config sections, gitignore trap)
metadata:
  type: reference
  modified: 2026-10-26T12:05:36.997Z
---

Code (2026-10-26, branch claude/project-thread-j1d6bj):
- building/DigRules.java + mixin BlockStateDigMixin (BlockStateBase.getDestroyProgress HEAD): dig floor (vanilla blocks <= generator ground - floorDepth, server computes, client gets a synced FLOOR attachment), walls = full CityBlock without block entity, tier by SoundType WOOD/STONE/METAL; furniture strips with its tool (XP path). Config section `digging`.
- building/Build.java enum (BOARD, METAL_SHEET, TRIPWIRE, RAIN_BARREL, SANDBAGS, WELD_SHEET): needs, tool, skill, ticks, Where (OPENING/GROUND/MAKE), spot() shared by client preview and server. Build.isWindow/isDoor also used by HammerItem (city windows: path starts window / ext_win_ / contains _window).
- building/Materials.java counts/takes from inventory + worn bags (Carry.Bags).
- building/Builder.java: Request payload -> TimedActions -> finish (materials, tool wear, XP, noise).
- building/BuildBlocks.java: metal_sheet_cover (DAMAGE 0-7), can_tripwire (rattle + Noise), rain_barrel (LEVEL 0-8, random tick in rain, bottle), sandbag_wall. No block items. Loot tables give materials back. Solid ones are in minecraft blocks_motion tags.
- BarricadeBlock now has DAMAGE 0-7; BashGoal wears boards/sheets by eighths (config `building` boardHits, metalSheetHits); hammer on a damaged barricade patches it with one plank.
- Client: kit/BuildPage.java (Page.BUILD after Bench), client/building/Placer.java (START tick eats attack/use clicks; green/red ghost via entityTranslucent + bench ghost texture).
- Art: tools/items/gen_scrap.py (rubble_chunks, scrap_metal, propane_torch), tools/items/gen_builds.py (materials + block models + blockstates + block loot).
- TRAP: .gitignore has `build/`, so no folder may be named build (java package or assets). Use building/.
- KitUiTest runs digProbe (server) and buildTest (placer + left click + timed action) on CI.
Wave 2 (2026-10-27, playtest 10f69661): ALARM_CLOCK (GROUND, clock item timer in CUSTOM_DATA "timer", block rings via scheduled ticks + RINGS countdown), CAR_ALARM (Where.CAR: Placer aims at a CarProp, Request.entity carries its id; CarProp.rigAlarm/stopAlarm), key fob = CarNet.FOB (-5) sent by G with CAR_KEY in hand when no car is targeted, PLANTER (CROP/AGE/WET, seed packets carrot_seeds/seed_potatoes/beet_seeds, vanilla produce), SPIKED_WALL (cactus-style inset collision + BashGoal via BuildBlocks.bashDamage). Build.skill may be null. Art: tools/items/gen_builds2.py. KitUiTest.wave2Test probes all three blocks.
See [[zomboid-hordes-death-base-direction]], [[zomboid-ui-systems-tech]].
