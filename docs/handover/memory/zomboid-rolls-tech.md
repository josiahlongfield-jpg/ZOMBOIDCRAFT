---
name: zomboid-rolls-tech
description: How ZomboidCraft weapon rarity/tier rolls are built (package combat/rolls, zomboidcraft:roll component, places, config, API shared with the UI thread)
metadata:
  type: project
  modified: 2026-10-25T23:05:47.495Z
---

Step 2 of the loot doc ([[zomboid-loot-rarity-direction]]), built 2026-10-25 on branch `rolls` (from `classics`); SHIPPED in playtest c5fdab2 at 23:43. Preview: https://claude.ai/artifact/T8LZVy8JASLZqNcu3RVWdo (source in the weapons thread scratchpad rollsprev/, template.html + build.py).

- Package `com.zomboidcraft.combat.rolls`: Rarity (fixed per model; `of(stack)` null for non-weapons), Stat, Flaw (append new flaws only, saved as bitmask by ordinal), Tier (`label`), WeaponRoll record = item component `zomboidcraft:roll` (tier, stats 0-100 by Stat ordinal, flaws, place, worked), Rolls (core).
- Where tier comes from: Fabric `LootTableEvents.MODIFY_DROPS`; place from the loot table path (home_guns/basement_guns = gun_cabinet, guns/pawn = gun_shop, car/police = police, car/swat = military, entities/* = dead, etc). Dense horde (20+ zombies within 48) = 50% chance of +1 tier. Items never from a loot table roll "unknown" 1-3 in the Carry tick; CaseItem rolls "case". Never distance-based.
- `Rolls.refresh` writes per-stack ITEM_NAME (rarity colour on a child component), MAX_DAMAGE x DURABILITY, melee ATTRIBUTE_MODIFIERS via `MeleeWeaponItem.attributes(spec, dmg, speed, reach)`.
- Real effects: `Rolls.multiplier(stack, Stat)` used in Guns (damage power, spread, jam with `jamFloor`), CombatClient (recoil), RifleView (sway), CombatEvents (knockdown). `GunType.canJam()` = not manual and not tube-fed.
- Config: ZcConfig `rolls` (tierWeights, per-place "a-b" ranges, dense town, per-stat ranges, flaw chances, flawPenalty 1.3, jamFloor). `/zc roll <1-5>` re-rolls the held weapon.
- API promised to the UI thread (they own all screens incl. the inspect page in the character screen on I, reading this by reflection until `rolls` merges): Rarity.of/label/rgb; Rolls.get/position/band/multiplier/percent/flaws/placeLabel; WeaponRoll fields; Stat.of/label/higherIsBetter; Tier.label. Perks come in step 3 as `Rolls.perks`. UI thread's bench repairs (component zomboidcraft:repaired, Repairs.gain hook) live on branch claude/project-thread-j1d6bj.
- Card tooltip (23:40, Josiah asked designs to match the kit screen, [[zomboid-design-palette]]): items return WeaponCard (TooltipComponent) from getTooltipImage; client/kit/WeaponTip.java draws it with KitDraw; mixin/client/GuiGraphicsExtractorMixin catches setTooltipForNextFrame(Font, List, Optional, int, int, Identifier, boolean) and passes only the card with style zomboidcraft:kit_card (sprites tooltip/kit_card_*). Config look.weaponCards. Tooltip pads content by 3 (TooltipRenderUtil: sprite at x-12, margin 9). Fabric 26.3 callback is ClientTooltipComponentCallback; ClientTooltipComponent uses extractImage/extractText.
- GunFinish.line returns Component.empty() (never null) since 23:20 (branch finish-fix, playtest 81923c5): the kit screen called getString() on it.
- 26.3 names found by probe: LootContext.getOptional(ContextKey) (not getOptionalParameter), getLevel(); Zombie is net.minecraft.world.entity.monster.zombie.Zombie.
See [[zomboid-guns-tech]], [[zomboid-opening-guns-tech]], [[zomboid-playtest-build]].
- WORLD LOOT thread (2026-10-27): owns non-weapon items. WEAPONS keeps gun-adjacent consumables (cleaning kits, oil, parts, reloading supplies, holsters, slings, range bags, ammo boxes) and gives that thread the ids to place. It may add a tag-based fallback to Rarity.of and an ItemCard on WeaponTip/KitDraw, and may use kit.paint/toon.py (no behaviour changes).
