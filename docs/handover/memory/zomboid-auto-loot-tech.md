---
name: zomboid-auto-loot-tech
description: LOOT wave 4 stage 7 auto shop loot tables (auto_loot.py, part custom data, the 26 part/manual items waiting on REFERENCES photos) (2026-10-31)
metadata:
  type: project
  modified: 2026-10-31T20:56:20.728Z
---

Built by LOOT on claude/project-thread-jd27hs, commit 5d4e0062d (2026-10-31).

- **tools/items/loot/auto_loot.py** writes the 20 shop/auto_*.json tables that LANDSCAPE's autoshop.py fittings name:
  - tools, electric, fluids, books, outfit, special (the service desk), military (the motor pools), scrap (pulled parts of every class at 20-60%, no fluids);
  - {brakes,suspension,exhaust,wheels}_{standard,sport,heavy}.
- There is no auto_keyboard table on purpose: VEHICLES' CarKeys fills the key board, and car_key is never loot.
- **Part entries** carry set_custom_data {car_part:'id', kind:'class', condition:n}, which VEHICLES' Mechanics.held reads. With no condition, a part is new in its box. Universal parts go out as 'standard'; the UNIVERSAL set mirrors Mechanics.universal.
- **Items built 2026-11-01, commit e2d013b72 (branch CI green):** wave5parts.py (ITEMS.update in gen_loot) has the 22 parts, auto_manual_{standard,heavy,sport} ("Laine's Auto Manual: <Mechanics.manualTitle>") and skillbook_mechanics. The roster rows sit after wheel_chock; the parts are in place_loot NOT_PLACED (auto shelves and cars only). The preview is at project files zomboid-mod/loot-previews/car-parts-models.png. It was sent to Josiah, awaiting his OK before the playtest merge.

Related: [[zomboid-car-maintenance-plan]], [[zomboid-landscape-contracts]], [[zomboid-loot-settings-tech]].
