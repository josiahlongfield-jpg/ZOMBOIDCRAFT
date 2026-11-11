# Tarkov gear and clothing thread: everything for Endless Night (2026-11-09)

## Code (repo josiahlongfield-jpg/ZOMBOIDCRAFT)
Branch claude/project-thread-wjrmhi, fully merged into playtest at 552352d7e (CI green 2026-11-07).
Commits: a592ea3b9 grid core, 822c6eb72 gear tab + Tarkov slots, 552352d7e screenshot test + workflow.
NOTE: Josiah dropped the Tarkov GRID inventory on 2026-11-08 (purge plan P14: operators get a simpler inventory + Tarkov-style HUD). Carry what's reusable:
- carry/grid/GridShape.java, ItemSize.java (sizes + grid_rot component + config/zomboidcraft/item_sizes.json), Grids.java, GridNet.java (R-turn packet), TarkovSlots.java (tags zomboidcraft:tarkov/{earpiece,armband,armour,special}; tag JSONs in data/zomboidcraft/tags/item/tarkov/)
- carry/GearStore (EARPIECE 15, ARMBAND 16, ARMOUR 17, SPECIAL 18-20), CarrySlots, BagSpec (shape/laidOut/cells, maxSlots), BagContainer, Carry, mixin/SlotMixin + InventoryMixin (pocket rules)
- client/kit/TarkovArt.java (loads Tarkov UI PNGs from config/zomboidcraft/tarkov-ui at runtime, never shipped) — directly reusable for a Tarkov-style HUD
- client/kit/GearPage.java, PackPage.java, KitScreen.java (Gear tab)
- src/gametest/.../GearTabShots.java + .github/workflows/invshots.yml (screenshots to branch shots/inventory)

## Project files (/mnt/project-files/zomboid-mod/tarkov/gear/)
- tarkov_gear.json + build.py: 362 gear items from Paulov.Tarkov.Db (grids/cells, armour class, zones, plates, penalties, slot blocks, rarity/price tiers)
- clothes_raw.json (+ clothes.py logic): 128 named outfits (69 tops / 59 trousers) from customization.json + Ragman suits.json
- catalogue.md: about 328 kept items by category (the boss/Halloween/streamer filter is now lifted, so recheck the left-out list)
- gear-ledger.html (ledger artifact 54yQY2ovySjamUDo56gD1b, 490 rows)
- sheets/: 21 contact-sheet JPGs (armour, plates, rigs, backpacks, helmets, headwear, headsets, face covers, eyewear, armbands, clothing tops/pants/tshirts). Thumbnails are mirrored.
- tarkov-ui/: the 17 short-name UI PNGs (slot silhouettes, cell, slotfill, hslot, hpanel, bg, kg)
- inv-tarkov/{page.html,assets.json}: Tarkov inventory mock-up v3 (artifact 5dJAYHen2tryTazFePaHaK); inv-mockup/: v1
- previews/gear-tab-v1.png, pack-grids-v1.png (in-game shots artifact XBqKLWd87x7CiZEF5f2GMG)

## On Josiah's PC
- additions\tarkov-gear\: Windows.json, gear_bundles.txt, sheets\ (+ thumbs\, _report.json)
- additions\tarkov-ui\: png\ (2,843 Tarkov UI PNGs), ui_index.tsv, exported.tsv, ui_sheet_1-17.jpg, Bender font (4 weights)
- Modrinth profile "MEDIEVAL GLOOM (1)"\config\zomboidcraft\tarkov-ui\: the 17 short-name PNGs the game reads
- Render scripts (UnityPy + own software renderer) lived in the Remote Control scratchpad on his PC, so they may be gone.
- Gear bundles (~6 GB, 777 equipment bundles, 158 tops / 128 pants / 25 tshirts, 160 FP sleeve sets) are in his Tarkov install, not copied anywhere.

## Not done
Gear/clothing export to game models (batches 1-6) never started. Armour model agreed: Tarkov class/plates/durability vs bullets, plus bite/scratch vs zombies. Clothing wear only where Tarkov has it.
Memory: tarkov-gear-clothing-intake.md.
