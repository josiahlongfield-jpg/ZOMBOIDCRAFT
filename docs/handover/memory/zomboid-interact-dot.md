---
name: zomboid-interact-dot
description: ZomboidCraft green interact dot RULE and API: every usable thing gets a small green dot when close and looked at, no "press G" text
metadata:
  type: project
---

Josiah 2026-10-27 21:50 (VEHICLES thread): make the green door/glovebox highlight smaller and put the same dot on EVERY button or thing you can interact with in the world, shown only when you're close and looking right at it; it replaces on-screen "press G" text.

- Code: src/client/java/com/zomboidcraft/client/interact/Interactables.java (branch claude/project-thread-cilddw, commit 6614af18). REACH 2.0 blocks (eye to crosshair hit), SIZE 0.025 m core (glow 2.2x).
- Generic blocks lit automatically: Container block entities, anything with a menu, DoorBlock (handle height), TrapDoor, FenceGate, Button, Lever.
- Other threads register: `Interactables.blocks(state -> state.is(MY_BLOCK))` for blocks, `Interactables.finder((mc, player) -> new Interactables.Spot(worldVec3, 0))` for non-block spots, `drawsOwn(() -> bool)` if they draw their own (cars do, in CarRenderer, so the dot rides the car).
- Trains (call button, stop button) and car boot already hooked. Lifts thread told to hook Lifts.BUTTON.
- RULE: any new interactable must register a dot and must NOT add "[G] ..." overlay hints.
- Previews: /mnt/project-files/zomboid-mod/cars/green-dot-sizes.png (scratch dots/dots.py).

- 2026-10-29: Josiah saw NO dots in game (Photon). Photon drops beacon-beam pixels under alpha 0.5, so the glow dot vanished. Fix 2d8cff0e: draw dots with Interactables.type(shaders) — under shaders a solid air_dot.png disc (beaconBeam translucent=false) — and CarRenderer uses CarLights.shaderPackOn() for its dot (state.shaders is only set for lit cars). Waiting on his in-game check.
