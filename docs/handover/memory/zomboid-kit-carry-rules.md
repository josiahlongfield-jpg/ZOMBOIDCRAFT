---
name: zomboid-kit-carry-rules
description: ZomboidCraft kit rules Josiah set 2026-10-27: no weapons in pockets, long guns on the back sling, drag-drop, shift bindings, bigger bench
metadata:
  type: project
---

Josiah's asks (2026-10-27, UI thread), all built and in playtest:
- Weapons (guns and melee) never go in pockets (inv slots 9-35). Shotguns, rifles, snipers and melee can go on the back (gear SLING). Pistols, revolvers and magnums cannot sling.
- A picked-up weapon goes to a free belt slot, then the sling, then bags (Carry.takeWeapon via an InventoryMixin add() hook). Shift-click quick move follows the same order and never uses pockets.
- True drag and drop: press, drag and release over a slot to place it. Releasing anywhere else, or closing the screen, returns the item to where it came from. A plain click still picks up and places as before (KitScreen pressed/dragFrom/drop()).
- Shift + right click drops the item on the floor (ContainerInput.THROW).
- Bench: the gun stage is the main part of the screen (MID 314x196), and other panels sit around it. Right mouse only turns the gun and never selects. The footer says "RMB drag  Turn the gun".
- Clicking a fitted attachment moves it to the spares list (CI sweep checks every gun and part).

Tech:
- The rules live in Carry.weapon/slings/pocket. SlotMixin refuses pocket slots, and GearSlot SLING mayPlace = Carry.slings.
- The sling draws in WornGearLayer.sling(). Gun models run along Z, so it pre-rotates Y 90° and flips 180° so the muzzle is up over the right shoulder. It sits just outside the pack (packOut).
- To see the player's back in a client gametest, draw the render state on a test Screen with bodyRot 0 (KitUiTest BackView). Vanilla skips the LocalPlayer when the camera is another entity, and the mod's F5 cam is too close.
- Melee sling confirmed on CI 2026-10-27: the axe lies diagonally, head over the right shoulder. Test gotcha: a test Screen must override isPauseScreen() false, or the integrated server stops ticking and the WORN sync never runs.

**Why:** Zomboid-style carrying, so weight and slot choices matter. **How to apply:** keep the pocket and sling rules in any new menu or quick move. See [[zomboid-inventory-ui-direction]] and [[zomboid-ui-systems-tech]].

Status 2026-10-27 13:17: UI thread stopped per Josiah (only LANDSCAPE + photo refs keep working). All UI work is in playtest (branch head c0b13918). Resumed 18:29 (all threads allowed). Axe check done; nothing pending but his in-game feedback.
- 2026-10-27 22:15: WORLD LOOT thread (cmsg_01LvQiYGyHLhwyRNcM6wP1Dp3sPQiPRKoASa3LtUBwRxqS) is building a huge custom item roster. UI asked it for: a public ItemInfo.rarity(stack), an int value, a weight data table, and category tags. UI then switches KitItems over and adds med treatments. The list is in project files zomboid-mod/world-loot/ui-needs.md. Next UI job was offered on a card (holsters/wrist slot recommended).
