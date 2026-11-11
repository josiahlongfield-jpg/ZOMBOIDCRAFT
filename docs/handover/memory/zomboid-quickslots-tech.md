---
name: zomboid-quickslots-tech
description: How the worn quick slots work in code (QuickSlots.java, GearStore BELT/HOLSTER/SHEATH/WRIST, spill tick, pickup routing, client skip)
metadata:
  type: project
  modified: 2026-10-27T23:12:47.535Z
---

Built 2026-10-27 by the UI thread; IN PLAYTEST since 2026-10-28 ~00:10 (merge 03ab565e). Belt models: tools/items/gen_belts.py, built at body scale (1 unit = 1 skin px, buckle at +z, bottom 0.8 below frame centre) so WornGearLayer draws them round the waist by translation (BELT_DOWN). hip_holster modelled (tools/items/gen_holsters.py, from the duty-belt photo) and in playtest 2026-10-28 00:40. Shoulder/thigh holsters and both sheaths modelled too (in playtest 2026-10-29, in loot). The thigh rig is drawn on the right leg (WornGearLayer.onPart); the knife sheath rides cross-draw on the front-left, the machete behind the left hip (the arm hides the side); hip() takes per-rig x/y/z/size and turns blades handle-up. Add each new model to WornGearLayer.BODY_SCALE, or it is not drawn on the body (a missing model draws as a huge cube).

- `carry/QuickSlots.java` defines the hotbar indexes:
  - 0 HANDS, 1-2 HIP, 3 HOLSTER, 4 SHEATH, 5 BACK (the old sling), 6-7 POUCH, 8 SPARE (never open).
  - `open(player, slot)` decides whether a slot is there; `takes` and `fits` decide what a slot accepts.
  - `order(stack)` and `place(player, stack)` pick where an item goes when you pick it up.
  - `on(player)` is false in creative and when config `carry.quickSlots` is false; then you get the plain hotbar.
- `GearStore` keeps slots 0-2 for bags and adds 3 BELT, 4 HOLSTER, 5 SHEATH, 6 WRIST (SIZE 7).
  - An old save of length 4 with a weapon in index 3 becomes `fromSling`, which `Carry.kit()` moves to hotbar 5.
- The opening rules:
  - Any belt opens HIP.
  - A tool or duty belt also opens POUCH.
  - A hip holster or sheath only works with a belt worn; shoulder and thigh holsters don't need one.
- Item tags `zomboidcraft:quick/hip_weapons`, `quick/knife_sheath` and `quick/machete_sheath` say which melee weapons go where. Add new knives there.
- `Carry.kit()` runs every 10 ticks and skips spectators.
  - It gives the starter leather belt once per life (attachment `kit_started`, not copied on death).
  - On that first run it moves a carried digital watch onto the wrist.
  - It spills anything that no longer fits its quick slot through `inventory.add`, or drops it.
- Mixins:
  - `SlotMixin`: `mayPlace` and `isActive` apply the rules to hotbar slots in every menu.
  - `InventoryMixin`:
    - `getFreeSlot` sends non-weapons to pockets, then bags, then pouch or hip.
    - `add` routes weapons to `QuickSlots.place`.
    - A client-only `setSelectedSlot` makes scrolling skip closed slots. It treats a move to an adjacent slot as a scroll; a number key for a closed slot does nothing.
- `CarrySlots.quick()` handles shift-click moves into the best quick slot. `InventoryMenuMixin` and `NearbyMenu` use it.
- `Carry.WORN` has gear 0-6, then WORN_BACK, WORN_HOLSTER and WORN_SHEATH: what hangs in those slots, empty while it is in your hands. `WornGearLayer.hip()` draws the holstered and sheathed items.
- On the client:
  - BeltHud, QuickWheel and the CharacterPage, PackPage and SuppliesPage show only open slots, labelled with `QuickSlots.label`.
  - WatchHud shows only when a watch is on the wrist.

Related: [[zomboid-holsters-spawn-direction]], [[zomboid-kit-carry-rules]].
