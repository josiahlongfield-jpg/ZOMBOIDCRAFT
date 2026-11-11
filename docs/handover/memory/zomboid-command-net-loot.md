---
name: zomboid-command-net-loot
description: LOOT's part of Josiah's command net plan (AUDIO's HuntBoard): C7 resupply crate on escort, C4 coloured smoke + laser designator ids (2026-11-06)
metadata:
  type: project
---

Agreed with AUDIO (branch wh9qba, HuntBoard) on 2026-11-06:
- **C7 resupply.** AUDIO fires HuntBoard.ESCORTED (server, survivor, extraction pos). LOOT listens and puts a pk_military_crate fitting at the pos (or the nearest free ground within 3), using the new table shop/resupply: ammo, medical, food, water, and a rare laser designator. Not built yet: waiting for AUDIO's event.
- **C4 strike items**, which AIRCRAFT reads:
  - zomboidcraft:smoke_canister (the existing "Smoke Signal", R) with CUSTOM_DATA colour red/green/yellow/violet as looks;
  - zomboidcraft:laser_designator (new, R, mil/gun, about 1.6 kg).
  WEAPONS' smoke_grenade is the grey thrown screen, a different item.
- Photos were asked of REFERENCES on 2026-11-06 (refs/items/military/laser_designator, smoke_canister). Models come after they land.
- **Lab data drive (LOOT owns):** zomboidcraft:lab_research_drive is one quest item in a fixed stash in the Ridgeview lab, never rolled as loot. On the first pickup, call HuntBoard.objectiveDone(server, "lab_data"). The event is on wh9qba 910c899b7: HuntBoard.ESCORTED.register((server, survivor, extraction BlockPos) -> ...). Build both once HuntBoard is in playtest. REFERENCES queue pc_spec49 has the designator, smoke and drive photos.
- The dam objective is not LOOT's; AUDIO is asking SURVIVAL.

- **AIRCRAFT hooks (branch dcbfp2 bc7296a, world/events/StrikeCalls):** when a coloured smoke_canister pops, call StrikeCalls.smoke(serverLevel, Vec3 at, colour, burnTicks) once (it draws the column; concealment stays LOOT's). laser_designator use() calls player.startUsingItem(hand) on both sides, getUseDuration 72000, anim NONE (like the FieldGearItem rangefinder). Build these once dcbfp2 is in playtest.
- **Marine boredom (SURVIVAL, 2026-11-06):** once onqb2b is in playtest, scale survival/Mood idle boredom gain (around line 91) by Needs.statsSpeed() for everyone and x0.5 for marines (MarineData.marine(occupation), occupation "marine:<role>"). Stress and unhappiness follow statsSpeed too.

- **Models built 2026-11-06 (commit 4f08ebe5c, branch green, not in playtest):** designator/data_drive builders in wave4mil, smoke_can in wave3 (SMOKE_COLOURS). Loot items can have looks: ITEMS key name__look gets its own mesh, and mesh.item_json selects it by the custom_model_data string at index 0 (gen_loot calls it too). loot/CommandNet rolls the smoke colour on any loot drop (CUSTOM_DATA colour + CUSTOM_MODEL_DATA string + name). Preview: loot-previews/command-net-items.png. NEVER run gen_loot --only on a sheet group: it rewrites the whole sheet with only those items. Run it in full (~10 min).

Related: [[zomboid-no-vanilla-rule]], [[zomboid-photo-reference-rule]].
