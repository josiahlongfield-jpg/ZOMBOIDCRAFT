# Zombie behaviour: inventory for Endless Night

This is the "Build Zomboid zombie behaviour" thread's work. The branch is `claude/project-thread-w8d2gc` in ZOMBOIDCRAFT. All of it is merged into `playtest`; the last merge is 26e3cc63, from 2026-11-07, and no commits are left outside playtest. All paths are under `src/main/java/com/zomboidcraft/` unless they say otherwise.

## What it does
- **Zomboid's 29 Zombie Lore and 21 Zombie Population settings**, plus our own additions:
  - Rolled lore per zombie: speed, strength, toughness, sight, hearing, memory, cognition, drag-down, lunge, armour, attached weapons and so on.
  - Rally groups, migration, herds that walk the roads and turn toward noise, and growth toward a population peak.
- **The dead where they belong:**
  - Outfits chosen by the building kit and county place a zombie spawns in.
  - Fake dead: 1 in 6 on the street, 1 in 10 indoors, 25% in combat zones.
  - Crawlers under parked cars that grab your ankle.
  - Belted drivers in about 1 in 20 abandoned or wrecked cars, which get out through the door you open.
- **Search helicopter:** Zomboid's Once / Sometimes / Often schedule, days 6 to 9 by default. It searches, locks on, and its noise pulls in the dead. It loses you if you stay under a roof for 3 seconds.
- **Trait senses:**
  - Inconspicuous x0.5 and Conspicuous x2 on how far zombies see you.
  - Graceful x0.6 and Clumsy x1.2 on footstep noise.
  - Keen Hearing, Hard of Hearing and Deaf change sound volume on the client.
- **Swarm (Josiah, 2026-11-07):** keep the about 908,000 dead and make it feel like it.
  - Live cap 300 to 1,500, starting at 500, self-tuning against 40 ms ticks.
  - 48 woken per step.
  - Idle zombies fold back to records only past 800 per area.
  - Rallies of 40.
  - 15 herds of 500 to 1,500.
  - Crowd drawn out to 320 blocks, up to 8,000.
  - Loud noises keep waking the dead for 10 seconds.
- **District tally for AUDIO's purge hunt board:** `DistrictDead.share(level, districtId)` returns 0 to 1, or -1 when unknown. It stays unknown until 90% of the district's places have been seeded.

## Files that are wholly ours
- `zombie/Lore.java`, `RallyGoal.java`, `PlaceOutfits.java`, `LyingInWait.java`, `BeltedDead.java`, `TraitSenses.java`
- `zombie/population/Herds.java`, `DistrictDead.java`
- `world/events/SearchHeli.java`
- Gametests in `src/gametest/java/com/zomboidcraft/client/zombie/`: `ZombieLoreTest`, `ZombieHerdTest`, `ZombieDeadTest`, `ZombieHeliTest` and `ZombieSwarmTest`. `ZombieSwarmTest` builds a county world from preset `zomboidcraft:county`, seed 12345, and skips the roster and the spawn picker.
- `.github/workflows/zombieshots.yml` runs one client test, chosen with a sed on `fabric.mod.json`, and pushes the shots to the `shots/w8d2gc` branch.

## Shared files we changed (the parts that need to come over)
- **zombie/:**
  - `Sight` (lore sight, light giving you away, crouched cover, trait factor)
  - `Noise` (lore hearing, `emitEngine`, `emitByDead`, `Population.wake`, `Herds.noise`)
  - `ZombieEvents` (sets up LyingInWait/BeltedDead on load, trait-scaled footsteps)
  - `ZombieSpawner.uniform` (now goes through PlaceOutfits)
  - `NoiseListener`, `ZombieSightGoal`, `InvestigateNoiseGoal`, `WayInGoal`, `BashGoal` (lore memory and cognition, fence crowd)
- **zombie/population/Population.java:**
  - Herd and rally hooks.
  - `setPop()`, which keeps DistrictDead in step.
  - `spawn()`, which applies under-car placement and fake dead.
  - Swarm reach: the seed radius clamp goes up to 20 chunks.
  - The rest of the population engine (Budgets, Census, Spots, CrowdPayload) came from the performance thread and comes over with it.
- **combat/:**
  - `Grabs.seize(player, zombie, fromGround)`, ankle holds.
  - `Downed.isHeld` and `standUp`, used by lying-in-wait and belted zombies.
  - `Dismember` (`makeLegless`).
- **world/cars/CarProp.java:**
  - Belted passengers: `canAddPassenger`, and `getIn` calls `BeltedDead.doorOpened`.
  - `seatSpots()`, `seatNext()`, `fit()` for any LivingEntity.
  - Synced `SEARCH_AIM` with `aimSearchlight()`.
  - Client `AirLights.aim()` draws the searchlight.
- **`ZcConfig`** groups: `zombieLore.*`, `zombiePopulation.*`, `population.*` (the swarm defaults) and `searchHeli.*`. The config migration is `defaultsVersion` 19 (swarm) and earlier.
- **`sandbox/SandboxOptions.java`:**
  - `wires()` for every ZombieConfig.* and ZombieLore.* setting, plus "Helicopter".
  - In `ours()`: `zc.fakeDeadStreet`, `zc.fakeDeadIndoor`, `zc.fakeDeadCombat` and `zc.beltedDead`.
- **`mixin/client/SoundEngineMixin.java`** (client): the hearing traits.
- **Infection hooks** in `survival/SurvivalEvents` and `SurvivalStats`. The transmission and mortality lore is read there.

## What it depends on (bring it over, or stub it)
- **County:** `world/county/` (County, Place, PlaceType, Plan + `data/zomboidcraft/county_plan.txt`, Districts), `world/gen/CountyChunkGenerator`, CountyGen, PowerDistricts.
  - PlaceOutfits, Herds (roads), Census, DistrictDead and the swarm test all use it.
  - Without a county, Budgets falls back to the wild rate and herds don't start.
- **Building kits:** PlaceOutfits looks for these block-id prefixes: `hs_` hospital, `sh_` police (not `sh_cmu`), `fs_` fire, `sc_` school, `ml_` mall.
- **Cars:** CarProp, Cars, CarModels. Belted drivers, under-car crawlers and the helicopter (`heli_hornbill` model, flyby) all need them.
- **Others:**
  - survival `character/Traits` (trait ids in `data/zomboidcraft/character/traits.json`)
  - WeatherDirector (sight and hearing scaling)
  - HarvestmanDirector.noise
  - throwing/Smoke
  - LightItem and Attachments (whether you're showing a light)
  - Skills (Sneaking)
  - SpawnPicker clear zones (`Population.keepClear`)
  - ZombieLook outfits and the zbody renderer for how they look

## Data, docs and tools
- Plan doc: https://claude.ai/artifact/97P1rAN54jfpHhCRx7j5m7, with Josiah's answers in it.
- The 269 Zomboid sandbox options: `/mnt/project-files/zomboid-mod/commands/pz-sandbox-options.json`. Generated `data/zomboidcraft/sandbox/zomboid.json` comes from `tools/sandbox/gen_sandbox.py`.
- Previews in `/mnt/project-files/zomboid-mod/zombie-behaviour/`: wave1–4 mp4s, `swarm-downtown.mp4`, and `trait-senses.patch` (already applied).
- Tools in the same folder under `tools/`:
  - `CountDead.java` totals the county's dead. Run `javac -d out -sourcepath src/main/java CountDead.java`, then `java -cp out:src/main/resources CountDead <seed>`. Seed 12345 gives about 907,700.
  - `mkv5.py` stitches screenshots into a captioned mp4.
- Memory: `zomboid-zombie-behaviour-plan.md`, `zomboid-population-tech.md`, `zomboid-hordes-never-vanish-rule.md`.

## Rules Josiah set that come with it
- 908,000 dead stays. He wants to feel swamped by massive hordes.
- Harder than Zomboid is fine, as long as the server holds up.
- No zombies or herds on the Map.
- A horde must never vanish. Walkers only ever fold back into records, never get deleted, and they are never made persistent.
- Belted dead are our addition, and he approved them.

## Open
- AUDIO's HuntBoard isn't in playtest yet. When it is, add `HuntBoard.walkers = DistrictDead::share` in `DistrictDead.register()`.
- One risk from the swarm test: dead already chasing you never fold back. On the slow CI machine about 600 stayed up and the server ran about 50 ms ticks.
