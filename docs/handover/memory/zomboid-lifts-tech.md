---
name: zomboid-lifts-tech
description: How ZomboidCraft lifts/escalators work in code (world/lifts), the LiftBuild worldgen contract, paint.py, /zc lift test.
metadata:
  type: project
  modified: 2026-10-27T22:04:57.551Z
---

Code: src/main/java/com/zomboidcraft/world/lifts/ + client/lifts/ (branch claude/project-thread-96uzh5, 2026-10-27). Ask: [[zomboid-lifts-direction]].

- State lives in the LiftMachine block entity (block `lift_machine` at the shaft top over the front-corner cell). It scans down for `lift_door` lower parts at machine+facing to find landings; no entity is placed at worldgen.
- LiftCar entity is spawned/unsaved when a player is within 160 blocks; it syncs Y/V/doors/calls and the client predicts motion. LiftRenderer draws car, leaves, ropes and 7-segment indicators (atlas textures/entity/lift/atlas.png).
- LiftPhysics via EntityLiftMixin carries/clips riders and pushes on escalators (`escalator[facing=ascent,run=up|down]`, old city `tx_esc_step` runs up). ServerLiftFloatingMixin stops the flying kick.
- Worldgen contract (LiftBuild lives in world/city so plain-javac preview tools compile; keep it MC-free): `LiftBuild.shaft(sink, x0, z0, w, d, floorYs, lobby, base, top)` in a doors-face-north local frame (doors at z0-1, button at x0+w, z0-2, y+1). Used by Tower.core, Garage.core (doors only), PackTower (DOUBLE cores + side shaft for SINGLE cores over 10 levels), LiftCommand.
- Lift buttons are G/right-click; G inside a car opens LiftScreen (floor panel). VEHICLES' Interactables green dot should register `Lifts.BUTTON` once it lands in playtest.
- Textures/models/blockstates come from tools/lifts/paint.py (rerun after edits).
- `/zc lift test [floors]` builds a test shaft in front of you; `/zc lift list` describes loaded lifts (LiftMachine.LOADED weak set), `/zc lift call` sends them all to the top. CI server smoke test builds+calls a test lift; county smoke test runs `zc lift list` (2026-10-29: 15 tower lifts scanned fine).
- Landing doors (closed) also call the lift: LiftDoorBlock.useWithoutItem + G via Lifts.Call -> Lifts.pressDoor; dot on closed doors too (2026-10-29, playtest 90500607).
- 2026-10-29 Josiah: G in the car opened the floor panel but picking did nothing. Fixed (playtest 046fffce): car call shortens door hold to 30 ticks; doorway() only counts entities whose centre is in the door cells; LiftScreen also takes up/down(W/S)+Enter; refused Pick logs 'Lift panel press from ... refused' + 'Nothing happens.'. Awaiting his retest.
- 2026-11-01: real-client test src/gametest/.../lifts/LiftRideTest + .github/workflows/liftride.yml (push to 96uzh5 touching lifts; shots+log on branch shots/96uzh5-lift) proves G doors -> call, G panel -> LiftScreen, mouse click + Enter ride. Decay drops rubble/paper/rebar/vines in shafts: LiftMachine.clearOut() empties shaft + door-front cells (non-full blocks, item entities) at first scan; scan uses isCollisionShapeFullBlock. Panel has ONE fixed dot (Josiah); window has Open/Close word buttons, no key hint.
- 2026-11-07: Josiah still saw the floor window ignore the mouse (pointer visible, no hover) though LiftRideTest (now 20 floors) passes on current playtest. Added LiftScreen.tick() polling mouseHandler xpos/ypos + isLeftPressed as a fallback and logs 'Lift window opened/mouse button/click on' to latest.log. Downloading his other mods into CI was blocked by the safety check. 2026-11-08: his hover then worked (polled pointer) but clicks did not; added release of a still-grabbed mouse + attack key as click (no GLFW in 26.3: org.lwjgl.glfw missing). Server logs "Lift panel press from". Next if still broken: ask for his logs/latest.log.
- Sounds are vanilla placeholders in sounds.json (lift.ding/doors/motor/button).
- Previews: tools/lifts/preview.html (three.js port of LiftRenderer + block boxes from tools/city/blocks.json) + shot.js; shots in project files zomboid-mod/previews/lifts/. Photo redo done 2026-10-29 (quads subdivided, atlas repainted from refs/city/lifts).
