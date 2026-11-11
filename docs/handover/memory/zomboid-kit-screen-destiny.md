---
name: zomboid-kit-screen-destiny
description: Josiah's Destiny 2-style kit screen redesign for ZomboidCraft (2026-10-25): mockup link, layout, open questions
metadata:
  type: project
---

Josiah 2026-10-25 21:51: "revisit the UI", modelled on Destiny 2's character screen (armour and weapons) and its next page of materials. He likes that system.

Mockup v1 (Design canvas, 3 interactive boards): https://claude.ai/artifact/CZDDdHmbKxTU6o4PVRSeZf. Sources and the three.js renderer are in project files zomboid-mod/inventory-ui/destiny-v1/. The renderer draws the mod's real Blockbench models to PNG with Playwright; clothing and supplies that aren't items yet are procedural box models.
- Character: weapon slots on the left (Sling/long gun, Holster/sidearm, Sheath/melee), Belt quick items below them. Clothing on the right (Head, Torso, Hands, Legs, Feet), then the Carry row (Vest, Back, Waist, Wrist). The survivor figure is in the centre, with a stat strip on top (bite/scratch guard, warmth, noise, load, speed). Hovering shows a Destiny-style card (rarity band, tier pips, perk icons, stat bars, condition, lore). Clicking a slot opens a 3x3 flyout of carried alternatives, and clicking one equips it.
- Inspect: the hunting rifle at large size, rolled perk columns (barrel/action/sights/stock/trait, two per column at tier 3) that change the stats live, attachments, and finish blueprints.
- Supplies: categories (medical, food, ammo, materials, gun parts, tools), an item grid, a detail panel, materials totals at the top right, and storage use per container.
- Rarity uses the approved names and colours: Common (bone), Scarce (olive), Rare (steel blue), Prized (oxblood), Grail (brass). No franchise names anywhere in the mockup. See [[zomboid-loot-rarity-direction]].
- DECIDED 2026-10-25 22:28: these become extra pages BESIDE the field kit. Tab opens Character/Supplies (Q/E to flip pages); E still opens the field kit ([[zomboid-inventory-ui-direction]]) for bag grids, nearby containers and the bench. Health and Skills tabs are shown but not designed yet.
- APPROVED 2026-10-25 22:30; SHIPPED in playtest 23:03 (merge 75ed9a2), untested in game yet; waiting on Josiah's feedback.
- Josiah 22:32: the weapon inspect page doubles as the weapon BENCH: every attachment carried anywhere (pockets, bags, belt) is listed automatically to fit or swap; plus a tab inside it for quality, condition and repairs. Josiah 22:46 chose "Zomboid kits plus salvage": duct tape + wood glue (melee), gun oil + spare gun parts (guns), or strip a duplicate of the same weapon; each repair gives back less (component zomboidcraft:repaired, x0.8 per repair); new Maintenance skill scales it (0.6 + 0.06/level).

Code (2026-10-25): client/kit/ (KitScreen on key I over player.inventoryMenu, CharacterPage, SuppliesPage, BenchPage, KitCard, KitItems, KitDraw); server combat/Bench.java (button ids: BENCH 1000 cursor, TAKE_OFF 500k, FIT 1M, REPAIR 100M) used by both NearbyMenu and InventoryMenuMixin.clickMenuButton; combat/Repairs.java. Rarity/tier read the weapons thread's combat.rolls classes by reflection (KitItems.look), falling back to Common/unrolled until they merge.

**How to apply:** the old WeaponBench inside the field kit stays until the new bench replaces it; keep the two using one shared server-side fit/unfit path.

Josiah 23:25 approved building SIX more systems, previews first (boards Health/Skills/Map/Hud/Wheel on the same canvas, published 23:40): Health page (treat wounds from the body diagram), timed action bars (bandage/splint/barricade, cancelled by moving/swinging/hits), Map page on key M (explored fog, pins Safehouse/Loot/Danger/Note, readable loot maps reveal areas), Skills page (levels, XP, real effects; plus new: Aiming 5 steadier walking aim, Blunt/Blade 5 cheaper swings, First Aid 8 faster splints), noise+sight meter on the HUD, quick-use wheel (hold Z over the 9 belt slots).

UPDATE 2026-10-26 08:10: Josiah: "the ui thats bound to E shouldnt exist, its meant to be replaced by the new one we made". Done (commit 6e6abcb): E (and I) send OpenKit -> server NearbyMenu -> KitScreen<M> (now generic; registered for Nearby.MENU). New PACK page (PackPage.java: bag tabs, pockets, belt+off hand, floor/container tabs + Take all, 2x2 craft; shift = QUICK_MOVE). FieldKitScreen and old WeaponBench deleted. Pages flip with Q/R (E closes). Footer "Pages"/"Close" hints are clickable (screen.pagesAndClose). Health page draws the real player (skin + gear, still, facing out) with InjuryGlowLayer. He also asked that every button actually responds.
