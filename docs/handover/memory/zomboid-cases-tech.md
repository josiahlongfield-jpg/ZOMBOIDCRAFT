---
name: zomboid-cases-tech
description: How ZomboidCraft's rifle case, pistol case and ammo can work (carried bags that come packed), branch cases
metadata:
  type: project
  modified: 2026-10-25T13:50:00.000Z
---

Built 2026-10-25 on branch `cases` (combat fast-forwarded to it; in playtest b664d83); Josiah chose "Carry them, like Zomboid".
- Models: tools/items/gen_cases.py (reuses gen_bags display/VANILLA/worn_look; held sizes in HELD). Ammo can stencil uses a 5-high bitmap FONT (M is 5 wide).
- They are bags: carry/BagSpec RIFLE_CASE / PISTOL_CASE / AMMO_CAN, Wear.HAND (off hand, opens in the field kit like the duffel), reduction 0. BagSpec has a 7th field Holds {ANYTHING, GUN_GEAR, AMMO}; CarrySlots.BagSlot.mayPlace and Carry.stow respect it. Tag data/zomboidcraft/tags/item/hand_carried.json lists them.
- combat/CaseItem extends BagItem with component zomboidcraft:packed (true on new items). Carry.tick (every 10 ticks) calls CaseItem.unpack on inventory stacks: rifle case = AR/hunting rifle/shotgun stored with mag out, 1-2 mags, loose rounds, chance of a mounted part; pistol case = pistol, 1 mag, rounds; can = 3-4 ammo stacks of 1-2 calibres. Odds in ZcConfig.cases (/zc config).
- Loot: tools/city/shop_loot.py (guns, pawn, storage, farm rare) and tools/cars/car_loot.py (police, swat, trunk rare).
- Not done: military footlocker (too big to carry; maybe a searchable chest later); third-person hang of held cases unchecked.
- Preview: https://claude.ai/artifact/JfAGDXcErswEnvCQndH1WJ (drag-to-spin frames rendered with the scratch item viewer at fov 30, 3x supersampled).
