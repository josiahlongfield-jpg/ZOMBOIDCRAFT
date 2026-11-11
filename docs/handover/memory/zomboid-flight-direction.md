---
name: zomboid-flight-direction
description: Josiah's ask to make the carrier jets and Apache-style helicopter flyable (thread "Make jets and helicopter flyable"): goals, controls plan, ownership
metadata:
  type: project
  modified: 2026-10-26T23:18:25.067Z
---

Josiah 2026-10-26 23:11: flyable jets + heli, "detailed sound, dynamic controls, believable flight". Branch claude/project-thread-dcbfp2, one-jar rule [[zomboid-playtest-build]].

Controls: G gets in (never right-click), engine key C, jets mouse-aim (jet turns toward the view), W/S throttle, A/D roll, Space brake/airbrake, Left Alt free look. Heli: Space/Ctrl collective, W/S nose, A/D bank, mouse turns, H level lock. Top speeds are config (world.jetTopSpeed/heliTopSpeed, 300 km/h default). Sneak doesn't dismount aircraft.
Earning (Josiah 23:32 picked BOTH): Piloting skill + flight manuals, and fuel/battery/parts serviced before first flight (AirReadiness; world.aircraftService). Crashes explode, kill the crew, break the airframe (AirBreakup/AirDebris).
Owners: AUDIO thread cmsg_01LvQiYGyHLhwyRNcM6wP1DpT4W6GGH1BJNG9xCN2FfkG2 owns the .ogg files ([[zomboid-aircraft-sounds]]); VEHICLES thread owns cars; WEAPONS shares CameraRig (aircraft priority 10).
HUDs: F-22 style, Su-27 ILS-31, Apache IHADSS; seats at the design eye point. Cockpits: [[zomboid-cockpit-live-tech]].
Josiah 03:21 2026-10-27: real surface movement per jet, F-22 HUD/interior detail, easier jets, Apache flown from the FRONT seat (seat 0 now front), level lock for hover/height hold. 03:46: every carrier jet faces the runway/bow and can be moved. Built: [[zomboid-air-surfaces-tech]]. AUDIO heli round 2 (heli.idle, rotor outside with Doppler) wired in AirSound.
04:57 he said jets still very hard to fly + asked for a spawn command: jet flight assist on by default (Flight.assisted, flies toward the aim and banks itself, auto rotate + climb-out; H toggles in jets, heli H stays level lock) and /zc aircraft [f22|su27|apache] (ready to fly + manual), playtest d0823f8 05:18.
06:12 he said take-off stalls/dives, uneven acceleration, cockpit dark on some F-22s, deck carts/zombies explode you, shrink pilots. Causes: creative skips cantStart so carrier jets ran with a flat battery (dark) and 0-12% fuel (flame-out). Fixes 8013f402: creative start calls readyToFly; CarProp.plough knocks 1-high clutter aside on the ground (client removes, server move() override destroys first so the vehicle move isn't refused); ground wall crash only above max(40, 0.45 top) m/s; strike() runs zombies down; navy_catapult/shuttle give 14 m/s2 at throttle>0.8; carRiderScale now applies in aircraft.
AUDIO (wh9qba 261c20ae) added CarProp.flyby(at, vel, afterburner) + private flyby flag for WorldEvents flybys: server tick() returns after super.tick(), use() refuses, not saved. Keep it when reworking tick()/use(). Also CarProp.quiet (client static Predicate, AUDIO b7f6b72d): AirSound.keep() drops layers for unmanned flybys playing a whole recording.
Lights (08:29 nav lights + heli searchlight, Photon-safe glow) and far flybys: [[zomboid-air-lights-tech]].
Interact dot (22:00, [[zomboid-interact-dot]]): FlightClient.cockpitDot finder, on the nearest free seat when looked at within 3.5, 8211c064. Open: G vignette, weapons, arresting wires.
