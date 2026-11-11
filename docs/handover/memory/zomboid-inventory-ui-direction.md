---
name: zomboid-inventory-ui-direction
description: Josiah's ZomboidCraft inventory redesign (field kit look, bags/weight, nearby loot side, weapon bench) and its build status
metadata:
  type: project
  modified: 2026-10-25T06:52:37.412Z
---

Ask (2026-10-25): a new inventory that still shows the character but is far more intricate: backpacks of varying sizes and capacities, duffels you carry, and a weapon tab with an expanded gun view where carried attachments drag and drop on.

Agreed direction:
- Mockup v3 approved ("thats much better"): https://claude.ai/artifact/CJwiGemVvUK1YWHFfuUfkQ. Source is in project files zomboid-mod/inventory-ui/v3/.
- Use Minecraft's own slot grids and clicks, restyled as a moody textured field kit: canvas, steel and webbing panels, masking-tape labels, stencil headers, a blurred rainy city behind, and a cursor torch. Never flat block colours. Josiah: "it needs to be dynamic, moody, on theme".
- Weapon view: only the bare gun body is solid. Each empty slot shows a translucent green ghost part with a green crosshair at its mount point, and fitting a part makes it solid. Drive the weapons thread's attachments component; the weapons thread supplies the slot list, mount points and ghost models.
- Duffels sit in the vanilla offhand (tag zomboidcraft:hand_carried).
- Build order: (1) screen and character, (2) bags, gear and weight, (3) nearby loot side, (4) weapon bench.

Status (branch claude/project-thread-j1d6bj, all in the shared playtest jar, see [[zomboid-playtest-build]]):
- Step 1 confirmed in game: "inventory came up, looks good".
- Step 2: carry/* covers bags, gear slots, pockets and weight; the client side is client/inventory/FieldKitScreen.
- Josiah said bags "dont get equipped". Fixed two ways:
  - Bags go on automatically when picked up (config carry.autoWear).
  - Worn bags render on the player through WornGearLayer and the synced Carry.WORN attachment.
- Step 3 is in loot/Nearby*.java:
  - Pressing E sends an OpenKit packet. The server opens NearbyMenu, which keeps InventoryMenu's slot order and shares the 2x2 grid and bags.
  - The menu shows Floor plus tabs for up to 8 fittings, chests and barrels in reach and in sight. Fittings open it too.
- Steps 2 and 3 have not been tested in game. Josiah is reviewing.
- The sound thread added an item.bag sound event, not wired up yet.
- Step 4 (weapon bench) built 2026-10-25 in client/inventory/WeaponBench.java: a Bench tab on the carried panel swaps gear+carried for a 3D stage (gun drawn as a pinned ItemEntityRenderState through g.entity, ghost item models in items/bench_ghost/). Socket clicks go to NearbyMenu.bench via benchButton. Josiah's feedback: no tags or leader lines (click the crosshair), no auto spin, drag follows the mouse. After that he said "very good" (2026-10-25). UI sounds (ui.tab/click/item_move/wear, moodle heartbeat) go through client/sound/UiSounds.
- 2026-10-27: sling, pocket and drag-drop rules shipped, see [[zomboid-kit-carry-rules]].

**Why:** Zomboid-style intricacy is the point of the mod. **How to apply:** Josiah likes options and mockups before big UI changes. Keep every new jar on the playtest branch.
