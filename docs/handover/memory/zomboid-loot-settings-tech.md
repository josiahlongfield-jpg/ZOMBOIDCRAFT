---
name: zomboid-loot-settings-tech
description: LOOT wave 4 stage 6 loot sandbox settings (LootSettings drop hook, categories, rarity factors, pre-looted/diminished, crowd effect, starter kit, LootRespawn) (2026-10-31)
metadata:
  type: project
  modified: 2026-10-31T20:16:10.766Z
---

Built by LOOT on claude/project-thread-jd27hs. The first part reached playtest at d88366af3; the respawn part is commit 1d158ee48.

- **loot/LootSettings**: a LootTableEvents.MODIFY_DROPS hook on zomboidcraft:shop/* and car/* tables. EXEMPT: crusader_kit, museum_armour, kingsbridge_stash. It applies these in order:
  - the removal list (loot.removalList);
  - pre-looted (keeps about 10%) and diminished, driven by maximumLooted/daysUntilMaximumLooted and maximumDiminished/daysUntilMaximumDiminished;
  - the per-category ratio: the 21 loot.<category> fields against DEFAULTS, with categories from data/zomboidcraft/loot_categories.json (written by gen_java.py ZOMBOID_CATS);
  - the rarity factor: C=common, S=normal, R=rare, P=extreme, G=insane;
  - the rollsMultiplier;
  - zombiePopLootEffect, counting walkers within 32.
- **Starter kit** (loot.starterKit) is given on JOIN and AFTER_RESPAWN.
- **loot/LootRespawn**: a level attachment loot_rolled (pos -> table@time). Each 1200 ticks it re-sets the container loot table when:
  - hoursForLootRespawn is above 0;
  - no player has been within 64 (only when seenHours is above 0);
  - the container holds fewer than maxItemsForLootRespawn items;
  - with constructionPreventsLootRespawn on, there is no barricade within 8.
- All of these are wired in SandboxOptions.wires() with owner LOOT.
- RuralLooted, MaximumLootedBuildingRooms and TimeSinceApo are in loot/LootArea (f67fa3bca). Rural means a FARM/ROADSIDE place or outside Plan.inCore/inSprawl. Rooms are only counted for LANDMARK places: footprint/100. TimeSinceApo adds months x30 days to the looted/diminished ramps.

Related: [[zomboid-sandbox-settings]], [[zomboid-pz-review]].

**Trait hooks owed to SURVIVAL (2026-10-31 21:27).** API: survival.character.Traits.has(player, id), from branch onqb2b, not merged at the time. The hooks:
- smoker: stress and unhappiness build without cigarettes, and smoking relieves them.
- weak_stomach: about double the food poisoning chance; iron_gut: about half.
- organized: +30% bag capacity; disorganized: -30%.
- prone_to_illness and resilient: colds.
SURVIVAL edited SkillBookItem (illiterate, readScale). Keep that edit.
**Clothing thread edits (branch 9wvcyv, e2830bc0, 2026-10-31 22:43):**
- Added five crusader helms as G mansion rows; "Black Crusader X" is renamed "Crusader X" in roster.py, LootSpecs and loot_categories.
- story_loot.py SETS entries can be a list, meaning pick one (the crusader_kit helm). Keep both when regenerating.
