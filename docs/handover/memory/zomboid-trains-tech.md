---
name: zomboid-trains-tech
description: Trains tech (TRAINS thread): Line/Route/TrainRun/TrainEntity, walk-in car physics mixins, OTTO, call buttons, API for LANDSCAPE, /zc train
metadata:
  type: project
  modified: 2026-10-27T07:53:31.287Z
---

Code: src/main/java/com/zomboidcraft/world/trains/ + client/trains/. Built 2026-10-27 on branch claude/project-thread-q2g5bz.

- Server sim: Trains.tick runs each Line's TrainRun (jerk-limited accel, braking curve, curve speed, headway). TrainEntity = unsaved view entity; client predicts s from synced S/V.
- Car frame: +z forward, yaw = atan2(-dx, dz); TrainCar.Pose toWorld/toLocal; renderer rotates by -yaw. Car 22 long, 4 wide, pitch 22.6, doors at z -7/0/7, floor +1 above bed.
- Walk-in: EntityTrainMixin carries riders (Entity.tick HEAD) and clips Entity.collide against car boxes; Ride/Riders packets sync local coords; ServerFloatingMixin stops the fly kick.
- Calls: block zomboidcraft:train_call_button[facing], G (interact) sends Call; nearest stop within 48. Flare above ground / siren in subway (Beacons) make Noise. Train skips to the nearest call ahead; doors 30 s (config trains.doorSeconds).
- OTTO = robot conductor (Otto.java), voiced lines; no franchise names.
- API for LANDSCAPE: Trains.line / lineExact(level, id, RAIL|SUBWAY, name, closed-loop points y=bed top, StopSpecs), Trains.crossings, Trains.remove; saved in world/zomboidcraft_trains.json; rails rendered by TrackRenderer. See [[zomboid-track-contract]].
- Config trains.*: railSpeed 60, subwaySpeed 56 (Josiah 2026-10-28 doubled twice; accel 3, braking 3.5, curveGrip 4, express x1.3 capped 80 b/s; playtest round 14, the branch file says 13 because mist took 13 in playtest), railCars 3 (Josiah 23:17), subwayCars 3; change defaults via ZcConfig.upgrade rounds.
- /zc train test|list|remove|here|ride|call.
- Train sounds are vanilla stand-ins.
- Related: [[zomboid-trains-direction]], OTTO voice/lines/reactions in [[zomboid-otto-tech]].
- CI smoke test lays test_rail and greps "(stopped)".
- Indoors (2026-10-27): LevelTrainMixin raises client getHeight to the car roof (rain/snow), ClientLevelTrainMixin clamps light in a car ; Ambience.roofed=1.
- Lamps: TrainMesh.lamp() tube lamps + cabLamp over OTTO; quads carry near/near2 lamps; TrainRenderer.lampOn: 30% (and cab) fail in 1.5 s spells. GLOW quads draw in a glow pass (beaconBeam under shaders via CarLights.shaderPackOn, eyes() otherwise for the eyes only).
- 18:50 2026-10-27: stop buttons (TrainCar.STOP_BUTTONS, StopRequest packet, TrainRun.requestStop/halt, doorSide 0); speeds 10/9 (defaults round 9); no sleepers on rw_street_bed. County lines arrive via lineExact.
- Zombie riders (2026-10-28): Zombies.riders: TrainRun.passengers count (saved), real within 80 of a player in unwatched cars, folds back past 100 (before Population folds at 112); Zombies.stopped trades the count at unwatched stops; idle wander on/off by UUID hash. Config zombieRiderChance/zombieRidersMax.
- `git commit -a` skips new files.
- Spacing (2026-10-28): only the nearest train behind a call answers it (TrainRun.answers); a train closer than 60% of its share of the loop holds at a stop up to 90 s. Station boards (StationMaps): own 5x7 quad font, labels placed greedily, Positions every 5 ticks with v, extrapolated.
- Detail pass (2026-10-29, Josiah's same-detail-everywhere rule): TrackRenderer piece() = ballast crown/shoulders, skewed/rotten sleepers, plates+clips, foot/web/head rails, joints every 13, weeds, subway third rail (THIRD 1.3), fine parts within FINE 64; TrainMesh bogie()/underfloor()/roofGear()/fittings()/interiorDetail(); atlas row 6 = close button + track tiles. Offline preview: javac TrainMesh+TrainCar+Line+Route+OttoModel with Vec3/TrainRun stubs, dump quads, PIL render.
- Refs for the next train detail pass landed 2026-10-29: refs/rail/details/ (bogies, underframe, roofs, doors-ends, track-closeups, street-running) and refs/subway/ (car-interior-details, drivers-cab, third-rail, help-points, station-map-boards). Redo what's off when resumed.
