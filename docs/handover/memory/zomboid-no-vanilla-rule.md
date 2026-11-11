---
name: zomboid-no-vanilla-rule
description: RULE (Josiah 2026-11-02 06:33): no vanilla Minecraft items or chests in ZomboidCraft, ever, anywhere
metadata:
  type: feedback
  modified: 2026-11-02T06:37:21.677Z
---

Josiah 2026-11-02 06:33 (LOOT thread): "no vanilla items or chest in the game ever".

**Why:** vanilla items and chests break the Zomboid feel; loot and everything a player can hold should be ours ([[zomboid-world-loot-direction]]).
**How to apply:** never add a minecraft: item to a loot table, drop, recipe result, kit or code path (Items.OAK_PLANKS, Items.POTION water, music discs, meats...). Use our own item (plank, water_bottle, potato, vinyl_record, steak, firewood...). Never place a vanilla chest/barrel/shulker in world gen; use fit_/pk_ containers.

Audit 2026-11-02 (LOOT): shop/car tables clean except 8 music discs; vanilla zombie drop table still runs (rotten flesh etc.); Loot.java adds vanilla water potions; block drop tables give oak_log/stick/oak_planks/dirt/string; code hands out Items.OAK_PLANKS (barricades, Hammer, DigRules, Moving, Harvestman), water potions (BuildBlocks rain barrel, Drinks, NpcWork), Items.POTATO (BuildBlocks farming), vanilla meats (DecorUses/NpcWork cook maps); vanilla crafting recipes are not disabled; vanilla farm animals drop vanilla meat.

Built by LOOT 2026-11-02 (commit 2e3bb1608, branch jd27hs): loot/NoVanilla (last MODIFY_DROPS hook; ours(stack) maps vanilla->ours or EMPTY; is(stack,id); item(id)), loot/items/Cooking (raw our food -> "Cooked X" with custom_data cooked/hot; stoves + companions), mixin/RecipeManagerMixin (drops every minecraft: recipe), building/BenchMenu (crouch-use any *workbench fitting = 3x3 grid), our plank added to #minecraft:planks. Beet crop gives onions (no beet item yet). Other threads: use NoVanilla.ours/item instead of new ItemStack(Items.X).

**Lights (Josiah 2026-11-06 04:28):** "Blocks that emit lights need to be replaced with lights that emit colour in the same way and be bright, lighting blocks cannot be replaced with normal unlit items." A vanilla light block may only be swapped for a mod block that emits at about the same level (14-15). Photon colours light by block id, so mod lights throw only his amber blocklight colour: purple/green froglights stay vanilla on purpose. LANDSCAPE e0083040d: amber ones -> front_lampbox_lit / front_glow_panel (15), ceiling_light (14); campfires -> zs_fire_lit (15) / zs_fire.
