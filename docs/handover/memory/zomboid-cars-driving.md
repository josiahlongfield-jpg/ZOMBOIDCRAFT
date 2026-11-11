---
name: zomboid-cars-driving
description: ZomboidCraft drivable cars: controls rule, sounds, zombie safety, lights, open work
metadata:
  type: project
  modified: 2026-10-26T23:57:00.000Z
---

See [[zomboid-cars-plan]]. Branch claude/project-thread-cilddw; one-jar rule in [[zomboid-playtest-build]].
- RULE (Josiah): right-click is his aim-down-sights button, so cars never react to right-click. The Interact key (G) gets in, searches the boot and gets out; H is the horn. Apply the same rule to any new world interactions.
- Driving (PR #3) and sounds (PR #4) merged: engine = his steady idle pick pitched 0.8-2.0 by speed.
- Zombie safety: zombies stay outside an occupied car and must smash windows (6/4/2 blows usable/abandoned/wrecked) before hurting riders.
- Lights (2026-10-25, he asked for dash lights, headlights and working tail/brake lights): tools/cars/lamps.py tags lamp triangles by the pack's palette into assets/zomboidcraft/cars/lamps.json. Rerun lamps.py after pack.py.
  - Client: CarLamps, CarLights (client light blocks down the beam + glow quads, like RailLights), CarRenderer.
  - Lights come on automatically with the engine. Brake and reversing lights use the input for your own car and the speed for other players' cars.
  - Headlights reach 37 blocks. Maybe later: indicators/hazards (amber 182,92,28).
- Interior kit (2026-10-26, he called it "very nice"): tools/cars/interior.py builds the roughed-up cabin from rays (anchors), paints faces onto a strip under the atlas; wreck.build_variant bakes it in; lit faces = lamps.json kind 5 PANEL. wreck.seats finds back benches (2 or 4 seats). Synced SEATS nibbles; seat 0 drives. Merged to main with PR #4.
- Commands: Josiah (2026-10-26) wants every command sent to the COMMANDS thread with a short description; the car commands (/zc car [model] [state], world.streetCars, world.carUsable/Abandoned/Wrecked/Burnt, world.carLoot) were sent there 07:37. Send any new car command there too.
- Asked 2026-10-26 23:15-23:22: STRICT RULE no generated vehicle inside another (world/county/CarSpacing grid claims; model pinned as "=id"); door-handle highlight only when close and facing; pistol-only while moving; Zomboid-style keys/hotwire; engine and lights toggled inside.
- SHIPPED playtest 15d99ad8 (2026-10-26 23:53), jar sent: all four. tools/cars/cabin.py adds handles/steering/glovebox to models.json (pack.py calls it; buses have no handles, school bus no wheel). Keys: CarKeys.java + CarProp keyWhere/hotwire; C = engine/hotwire (driver), L = headlights in driver's seat (shares the rail key), G targets handle/seat/glovebox (CarClient.Target). CarHands/CarArms/CarSteering draw arms and turn the wheel. Config world.carKey*/hotwire*; sent to COMMANDS. Hotwiring: Josiah chose "anyone, 8 s, may spark" (2026-10-27), no tool or skill gate; he then asked that worse cars fail more: CarProp.carCondition() rolls usable 55-100, abandoned 15-60 into the shared CONDITION once (saved "worn"), fail = hotwireFail + (1-hotwireFail)*hotwireWear*(1-cond) (playtest 193ed55). Flight thread fills CarProp.airEngine and owns aircraft fuel.
- 2026-10-27 (his screenshot: arms covered the view): riders shrink to world.carRiderScale 0.85 in cars, not aircraft (Attributes.SCALE transient modifier in CarProp.fit; seat drop 1.02*scale). RULE (Josiah): driver sits the SAME distance from the wheel in every vehicle: CarModels.seatToWheel puts eye 0.18 m behind the rim's nearest point, 0.28 above its centre, x = wheel x (front passenger moves the same z). Arms grip at 75 deg (near 9/3), slimmer. Van back doors ('rear' kind in wreck.swing) now swing out; repack single models with scratch vandoors/repack.py pattern (pack.build + lamps.build, merge by id). Playtest acddfb9.
- Car items for future fuel/repair hooks (WORLD LOOT thread, 2026-10-27, no behaviour yet): gas_can, diesel_can, car_battery, spare_tire, tire_iron, car_jack, motor_oil, spark_plugs, fan_belt, headlight_bulb, jumper_cables, fuel_siphon etc. Cars keep weather out via world/cars/CarShelter (roof heightmap, indoor sky light, no mist in your car).
