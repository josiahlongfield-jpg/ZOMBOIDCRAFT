---
name: zomboid-car-maintenance-plan
description: Car maintenance + working two-post hoist (Zomboid-style parts, Mechanics skill), VEHICLES thread, doc Lez5BQYtKQYAehuQYXHosr; code map, worldgen hook, key boards
metadata:
  type: project
---

Josiah asked 2026-10-31 03:14 for Zomboid-style vehicle maintenance plus WORKING car hoists (Zomboid has none), auto shops in every town, skill-gated unlocks. Send a video preview before merging.

- Plan doc https://claude.ai/artifact/Lez5BQYtKQYAehuQYXHosr. Answers (03:48): hand pump also RAISES; 3 classes, class-locked (no cross-fit); Mechanics lost on death; no parts-list changes for now. PZ review 7rkGg3jdUDUZuHf1QeEvVE sec 12 + sec 20 'Vehicles and fuel' (19 settings) are VEHICLES' to build (settings keys go to COMMANDS).
- Split: VEHICLES = parts, skill, screen, hoist, shop tools. LOOT = part/tool items (CUSTOM_DATA car_part/condition/kind). LANDSCAPE = auto shops (bays 4x7, 4.5 headroom). UI draws the x-ray car figure inside MechanicsScreen (their branch j1d6bj, merges after mine).
- Code: CarParts (slots, roll, drive, faults), Mechanics (jobs, why() gates, manuals), MechanicsScreen, CarProp PARTS string + LIFT float (hoisted/wheelsFree/underReach), CarHoist entity car_hoist (notches 0/0.5/1.25/1.85, posts at local x +-1.7, control box on +x post).
- Hoist (Josiah 04:56): car only needs to be in the bay's general box (|x|<1.4, |z|<2.8, within 40 deg either way); arms slide + turn it to dead centre so pads always touch it, lifts once settled.
- Worldgen: BlockSink.hoist(x,y,z,yaw,notch,carModels,seed) travels as vehicle "!hoist:n;models"; only ChunkWriter places it. Customer cars: state suffix "#customer" -> key on the shop board via CarKeys.board(level,pos,container) (48-block reach), called once by LANDSCAPE's as_keyboard.
- Preview film: .github/workflows/hoistfilm.yml + gametest HoistFilmTest -> branch shots/cilddw (frames + hoist.mp4); screenshots are named NNNN_hoist-NNNN.png. Video at /mnt/project-files/zomboid-mod/previews/vehicles-hoist.mp4.
- Hoist sizing (Josiah 05:08-05:43): fits vans + military (MAX_LENGTH 7.6, MAX_WIDTH 3.0, no trailers/wagons); arms reach to each model's lift points; posts POST_H 4.9 so every vehicle gets the same headroom (needs 6 blocks inside).
- Wheels: tools/cars/wheels.py bakes [cx, cz, r] per model into models.json "wheels" (rerun after repacking cars). CarModels.Model.wheel(corner)/axleMid()/halfBase(). Hoist centres the AXLES between the posts (spot()).
- Shop tools (Josiah 05:43 "build all 4"): ShopTool entity shop_tool, kinds JACK (one corner, CarProp JACK/JACK_LIFT, jacked cars don't drive, lets tyre jobs on that wheel), CRANE (at car nose; required for engine out/in), TYRE_MACHINE (Mech 2, +35% tyre once), WASHER (Mech 4, +15% part once). Worldgen BlockSink.shopTool(x,y,z,yaw,kind,seed) -> "!tool:kind". /zc shoptool <kind>. Jack/crane worked from local -z, machines from +z.
- Vehicle settings (Josiah 06:37 'yes'): build all 20 of Zomboid's Vehicles-page settings the Zomboid way at Zomboid defaults, incl. new car fuel+dash gauge, working pumps (power or generator), door locks+hotwire, alarms, sirens, survivor cars, crash injuries. Plan doc https://claude.ai/artifact/WpTFknZM22vWNNa6p388LB. Wire via SandboxOptions.wires() owner VEHICLES. BUILT 07:10 on branch: Fuel.java (pumps, file zomboidcraft_pumps.json, LOOT GasCanItem + PowerGrid + rubber_hose), CarProp fuel/locks/alarm/siren(double-tap H)/crashHurt/survivor(); LOOT owns car/survivor table; LANDSCAPE owns TrafficJam (world.trafficJams); AUDIO making car.siren/car.alarm (Josiah picks). All waves merged to playtest (f986ee6f0) after his OK 07:41; jar sent. AUDIO car.siren (6 s loop, replayed every 120 ticks) and car.alarm (4 s, every 79) wired, in playtest 0d8cfb428. Fuel film: fuelfilm.yml -> shots/cilddw-fuel.

Related: [[zomboid-cars-plan]], [[zomboid-warthog-tech]], [[zomboid-pz-review]].

- 2026-10-31 later: poses "@wrecked^roof"/"^side" (CarProp.POSE, Cars.hullColumns pose) for LANDSCAPE road stories; traits hooked (speed_demon, sunday_driver, burglar, amateur_mechanic via survival.character.Traits.has); hotwire now gated Electrical 1 + Mechanics 2 (Zomboid) except Burglar, Josiah OK'd the gate 23:07 ("Okay good"). LANDSCAPE added CarPush (car_push). In playtest 6a8b01c5f.
- PZ VEHICLE MODS (Josiah 23:12, via coordinator): convert his subscribed Workshop vehicle mods (Steam content/108600) to .zcar, LOCAL-ONLY like the Warthog (config/zomboidcraft/vehicles, never repo/jar). He dropped the ripped-assets rule for personal use (22:53). Status: asked him which mods; PC was offline 23:15. Next: list workshop folder via device, plan doc, then convert. Bring each mod's SOUNDS too (Josiah 23:43): engine/horn/siren/doors as a local resource pack in his Modrinth profile, like Brita audio ([[zomboid-brita-weapons]]). Mods span B41 and B42 layouts (converter must read both). Josiah will say when his PC is connected (23:45).

Vehicle mods conversion: see [[zomboid-vehicle-mods]].
