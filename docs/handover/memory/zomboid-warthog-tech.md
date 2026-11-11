---
name: zomboid-warthog-tech
description: Warthog tech: bake pipeline (warthog_bake.py in bpy, warthog.py packer), .zcar v4 turret parts, Turret.java minigun (gunner seat, belt from 5.56, spin-up), military kind
metadata:
  type: project
  modified: 2026-10-29T07:49:18.689Z
---

Warthog (VEHICLES thread, built 2026-10-29). Josiah supplied a Forza rip zip (halo-warthog-*.zip in project files); nothing of it is committed (see below). He chose "belt feeds from rifle ammo you carry".

**Pipeline** (source never committed):
- Unpack the .blend; label each source triangle in numpy: 0 hull, 1 post (yaw), 2 gun (tilt), 3 barrels (spin), 4-7 wheels (FL, FR, BL, BR).
- `bpyenv/bin/python tools/cars/warthog_bake.py WARTHOG.blend LABELS.npy OUT [3072] [32]`: Forza mats to plain diffuse (black floored to 0.035), decimate per part (hull 50k), cull hidden faces, Smart UV, then a Cycles DIFFUSE bake high->low under a white sky. It then writes `facing` flags: back-only faces flipped, both-sides twinned (the game culls back faces).
- `python3 tools/cars/warthog.py BAKE OUT [--preview DIR]`: copies wheel 0 to the other wheels (mirrored), weathers the four looks (aircraft.weather; burnt keeps the bake's shading and has charred tyres), and writes warthog.zcar + warthog.json into OUT.
- Model space: Blender forward is (1,-1,0)/√2, left (1,1,0)/√2, lifted 0.157 m.

**.zcar v4**: after wheels, int parts; each part is byte kind, then 6 floats (pivot, axis), then mesh. CarAssets.Ready.turret holds them as Rotors. zcar.py reads v4.

**Kept OFF the repo and jar (Halo IP, same call as Master Chief; raised with Josiah 2026-10-29 ~07:55 Josiah chose this 08:01): files in config/zomboidcraft/vehicles (CarModels.LOCAL, warthog.json + warthog.zcar), copies in /mnt/project-files/zomboid-mod/cars/warthog/. warthog.py BAKE OUT writes them.**

**Row**: kind "military" is kept off streets, CityPlacer and random /zc car. Row fields:
- gunner (seat index 2)
- turret {yaw, pitch, muzzle, rest 4.38°, up 50, down 22}

**Java**:
- Turret.java: yaw/pitch maths, gunner seat turns with the post, trigger, loadBelt, tick (spin-up, rate, tracers via AirStrikes.Tracer every 3rd round).
- Guns.mountedRound fires from the muzzle toward what the gunner's eye ray hits, passing through its own hull.
- CarProp synced TURRET_SPIN/TURRET_FIRING/BELT, saved "belt".
- CarNet TRIGGER_ON -7, TRIGGER_OFF -8, BELT -9. CombatClient: the gunner's attack works the trigger; R loads the belt; overlay shows the rounds.
- Config `minigun.*`: damage, spread, spinUp/Down, belt, tracerEvery, rhythm (below).

**Spawns**: Camp.java vehicle rows (1 in 10 picks) and MilitarySite trucks list.

**Installed 2026-10-29 09:47** via folder access (granted C:\Users\josia\AppData\Roaming\ModrinthApp\profiles) first into the empty "ZOMBOIDCRAFT 1.0.0" profiles (wrong: "Unknown car"), then 09:56 into the profile he ACTUALLY plays ZomboidCraft in: "MEDIEVAL GLOOM (1)" (FOLDER name; the Modrinth app shows it as "ZOMBOIDCRAFT". Its mods hold the playtest jar; logs/latest.log shows the zomboidcraft version). Needs a game restart: CarModels loads once. Same route works for other local-only models. 10:05: the row got `handles` (sides + gun from behind; CarClient treats an x=0 handle as reached from the rear), playtest 570b5e13.

**Gunner** (view, hands, zoom, rhythm, flash): see [[zomboid-warthog-gunner-tech]].

Related: [[zomboid-cars-plan]], [[zomboid-glass-tech]], [[zomboid-guns-tech]].
