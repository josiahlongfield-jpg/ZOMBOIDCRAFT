---
name: zomboid-air-strikes-tech
description: Air strike world events (jet strafe, jet bombing, Apache hunt): AirStrikes.java, path events, low-pass and street-alignment rules, config, sounds
metadata:
  type: project
---

Josiah 2026-10-28 09:27 asked for AI strike runs; picked "zombies + players caught close, NO block damage" (09:28). 09:46-09:47 RULES: runs line up with the street and never hit buildings; jets do LOW passes close over rooftops so the whole run is visible; Apache hovers low between buildings.

Built on branch claude/project-thread-dcbfp2 (ef80d21b + 47b03fb7 fix), in playtest a5b8d083 2026-10-28 10:10, sent to Josiah, untested in game:
- WorldEvents.Kind adds STRAFE, BOMB, HUNT. Event got a `path` float[] (x,y,z,yaw every Event.STEP=0.25 s, Catmull-Rom between; empty = old straight line). Use at(t)/velocity(t)/yaw(t) and Event.place(car, t) (sets bank/nose via the new CarProp.flyby(at, vel, yaw, nose, bank, afterburner) overload). FarFlybys and WorldEventSound use these.
- AirStrikes.java: crowd() picks the biggest zombie crowd within 180 of a player, clear of players (bomb 22, strafe 14, hunt 6). Heading: tries 36 directions, picks the lowest max rooftop (MOTION_BLOCKING heightmap, wing +-8) = down the street; jets drop the strike if it needs >70 above the target. lowPass() follows the rooftops; clear() raises any path point within margin of anything under its wings/rotor, eased +-2 s (heli 2.5 s).
- Damage type zomboidcraft:air_strike (tagged is_explosion + bypasses_cooldown). Zombies in radius: Dismember.blast + lethal damage. Players: falloff, quarter behind cover.
- Client AirStrikeFx: tracers (lightning() under Photon) and bombs drawn as item model zomboidcraft:strike_bomb (stick with item_model).
- Sounds world.strike.cannon / chaingun built by tools/audio/build_strike_sounds.py from Sonniss MK44 30 mm shot (sx627539ff, in sound-raw/sonniss/strike_gun). Bombs use world.explosion(.far).
- Config world.airStrikes, airStrikeShare 0.35, airStrikeCrowd 4, strikeCannonRate 50, strikeChainGunRate 10, strikeBomb{Kill 9,Hurt 7,Damage 30}.
- /zc event strafe|bomb|hunt forces one (hits the ground ahead if no crowd).
Related: [[zomboid-world-events]], [[zomboid-air-lights-tech]], [[zomboid-flight-direction]].
- RULE (crash 2026-10-28 11:05): never build an ItemStack in a static field of a client class registered at entrypoint time ("Components not bound yet"); AirStrikeFx makes its bomb stack on first draw.
- Josiah 11:34: strafes are GUN DIVES (approach from a downward angle, pitch back up while firing so the shells walk a straight line). AirStrikes.gunDive/walkAndPull: level in, push-over (1.2 g) into a 17-23 deg dive aimed at the line's near end, 1.4 s track, fire 1.6 s easing the nose up (rate solved so the aim walks LINE=48 blocks), 3.5 g pull out bottoming at max(16-22, roof lift) over the target, 25 deg climb. Opens fire ~55-90 up, ~220 out. Bombing runs still use lowPass.
- Josiah 20:19 (built f7658c47 playtest): runs were stiff/sudden -> StrikeFlight.java simulates a pilot (load factor + bank through critically damped filters, 3 g/s, 70 deg/s roll), point-mass flight; strafe in ~110 up, 14-17 deg dive, walk shells while easing nose up, bottom 16-22; bomb in ~100 up, 8-10 deg dive, round out level 24-30, stick 1.2 s into the level run; then pull wings-level to 14 deg, roll 60-75 deg climbing turn L/R (pairs split), turn 60-95 deg. Event path STRIDE 6 = x y z yaw(unwrapped) nose bank (NaN nose = derive, the Apache hunt). Offline check: compile StrikeFlight with stub Mth/Vec3/ServerLevel/WorldEvents (scratch sft/). He asked for the heli outside sound to edit: sent /mnt/project-files/zomboid-mod/heli-sound-edit/ (rotor loop is only 0.86 s = the rough loop).
- 2026-10-29 detail rule (everything as detailed as landmarks): strike bomb item model is now a generated Mk 82 (tools/cars/strike_bomb.py, 177 elements; round sections = crossed bars at 0/22.5/45/-22.5, bar width r*tan(pi/16)). Never hand-write a blocky stand-in again.
- Refs (2026-10-29, from REFERENCES): /mnt/project-files/zomboid-mod/refs/aircraft/ (mk82-bombs, control-surfaces, high-g-vapor, strafing-impacts, bomb-blasts, apache-low-town, night-lights; sources.md each; raw Wikimedia hits). Thread paused after requesting them.
