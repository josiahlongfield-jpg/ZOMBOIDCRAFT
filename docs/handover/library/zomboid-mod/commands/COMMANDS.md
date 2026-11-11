# ZomboidCraft commands

Checked against the `playtest` build of 2026-10-26 07:20 UTC. Everything under `/zc` needs operator rights (cheats on in single player). Press Tab after any command to see the choices.

## Anyone can use

| Command | What it does |
|---|---|
| `/skills` | Lists your skills with their level and XP. |
| `/zchud [spartan\|operator\|classic\|reload]` | Picks your HUD: spartan (Halo Reach style), operator (Tarkov style) or classic (the old field kit). `reload` re-reads the HUD art after you copy new files into `config/zomboidcraft/hud/`. With no word it says which HUD is on and whether its art was found. Also in Options > Video Settings > HUD. It's `/zchud`, not `/zc hud`, so it doesn't clash with the server's /zc. |

## Your character (survival testing)

| Command | What it does |
|---|---|
| `/zc stats` | Shows your thirst, fatigue, infection and injuries. |
| `/zc thirst <0-100>` | Sets your thirst. |
| `/zc fatigue <0-100>` | Sets your fatigue. |
| `/zc wounds <0-10>` | Sets your old-style wound count. |
| `/zc injure <part> <kind>` | Gives you an injury to try out. Parts (Zomboid's 17): head, neck, upper_torso, lower_torso, groin, left/right_upper_arm, left/right_forearm, left/right_hand, left/right_thigh, left/right_shin, left/right_foot. The old zone names (torso, left_arm, right_arm, left_leg, right_leg) still work and hit that zone's main part. Kinds: scratch, cut, bite, burn, break. |
| `/zc infect` | Infects you with the zombie virus. |
| `/zc cure` | Removes the infection and heals every injury. |
| `/zc heal` | Heals every injury (leaves infection alone). |
| `/zc reset` | Resets all survival stats to a fresh start. |
| `/zc wakeup` | Forgets where you woke up and opens the spawn picker again, so you can pick a different town, city or friend to wake near (planned county worlds only). |

## Weapons

| Command | What it does |
|---|---|
| `/zc roll <1-5>` | Rerolls the weapon in your hand at that tier, to try out rarities and rolls. |
| `/zc localgun <id>` | Gives you a gun kept only on this computer, from `config/zomboidcraft/guns/<id>.json` + `.zmesh` (the Brita pack), with two full magazines and a stack of its own rounds. It rides on one of the mod's gun items. |
| `/zc localgun part <id>` | *Coming soon, once Josiah approves.* Gives you a sight or grip kept on this computer (`config/zomboidcraft/guns/parts`): 15 sights such as sp_fastfire, sp_hamr and sp_goldeneagle, and 6 Brita grips such as ForeGrip and Grip_TG (Tab suggests the ids). Each fits only the local Brita guns it was placed on in the posing tool. |
| `/zc localgun reload` | Re-reads that folder after you add or change a gun. |

## Vehicles

| Command | What it does |
|---|---|
| `/zc car` | Spawns a random car side-on in front of you, in a random state. |
| `/zc car <model>` | Spawns that model: sedan, sedan_ltd, hatchback, wagon, coupe, sports_coupe, convertible, minivan, suv, jeep, jeep_open, limousine, taxi, police_sedan, police_suv, swat_van, pickup, van, step_van, box_truck, moving_truck, tow_truck, ambulance, ambulance_van, fire_truck, garbage_truck, garbage_truck_rear, city_bus, school_bus, warthog (with a minigun). |
| `/zc car <model> <state>` | Spawns it in a set state: usable (drivable), abandoned, wrecked (won't start) or burnt (can't get in). |
| `/zc car <model> ready` | Spawns it ready to drive: usable look, full repair, full tank, good battery, unlocked, key in the ignition (press C to start). |
| `/zc carparts` | Lists every part slot and its condition on the nearest car within 8 blocks. |
| `/zc carparts <part> <condition>` | Sets a part's condition (0-100); -1 removes the part. For testing. |
| `/zc hoist` | Places a working two-post car lift 4.5 blocks ahead of you, squared to the nearest 90°. |
| `/zc aircraft [f22\|su27\|apache]` | Spawns that aircraft just ahead of you (F-22 if left out; jet_corvid, jet_gyrfalcon, heli_hornbill also work), fully repaired, fuelled and charged, and teaches you its flight manual. G to get in, C to start. *Arriving in the next playtest build.* |

Car keys (not commands): **G** at a door handle gets into that seat, on a free seat moves you there, on the glovebox (from a front seat) searches it, and also searches the boot and gets out. **C** starts or stops the engine from the driver's seat, or hotwires it with no key. **L** toggles the headlights from the driver's seat (on foot it's the gun light). **H** is the horn.

## Zombies

| Command | What it does |
|---|---|
| `/zc zombie [outfit] [walker\|crawler\|legless\|hopper\|holed]` | Spawns one of the new zombies 4 blocks in front of you to look at. Outfits: civilian, hoodie, office, farmer, construction, jogger, police, nurse, security, patient, soldier, survivor, doctor, firefighter, prisoner (random if left out). Walker is the default; hopper has one leg shot off at the knee and hops. Holed has its chest blown open by a point-blank shotgun blast (you can see through it; arriving in the next playtest build). Example: `/zc zombie police legless`. |
| `/zc harvestman` | Spawns a Harvestman a few blocks in front of you; its den is set where it spawns. Needs `performance.harvestman true` (off by default: no Harvestman spawns, any in the world are removed, and this command refuses). |
| `/zc harvestman den` | Builds a Harvestman's den about 6 blocks in front of you (torn-clear room, scrap heaps, nest, claw marks, bodies) with its Harvestman resting inside, missing a leg so you can watch it use the scrap to grow it back. |
| `/zc harvestman clear` | Removes every Harvestman and forgets all dens and hunts. |
| `/zc harvestman info` | How many there are, who is being hunted, and each one's plan, stage, health, den, and current goal with its reason. |
| `/zc harvestman target <player>` | Sets who the hunter is after. `/zc harvestman target clear` ends the hunt. |
| `/zc harvestman do <plan>` | Makes the nearest one take up a plan: wander, perch, ceiling, hide, listen, investigate, stalk, ambush, strike, drag, feed, flee, lair, rest, zombie, glimpse, leave_body, travel, patrol, lie_in_wait (still over a way people pass), forage (hunts zombies to eat), warn (warns the nearest player off its ground), observe (watches you from the dark), shelter (under cover till night), tend (tends its den). |
| `/zc harvestman room` | Builds a closed test room high above you, teleports you in, and has one Harvestman show all 14 of its actions in turn, each named on screen. It never strikes you there. |
| `/zc harvestman room next` | Skips to the next action. |
| `/zc harvestman room stop` | Ends the tour and removes its Harvestman (the room stays). |
| `/zc npc dark_elf [raw\|toon\|both]` | Spawns the dark elf NPC a few blocks ahead, facing you. Both (the default) puts the model as bought on your left and the toon-painted one on your right. |
| `/zc npc <model>` | Spawns any other NPC model, including ones kept only on your own PC in `config/zomboidcraft/npc/<model>.znpc` (e.g. `/zc npc master_chief`); only players who have that file see the model. NPCs are companions: they follow whoever spawned them (G on one toggles follow/wait), flee zombies, and pick up guns within 12 blocks to fight them. |
| `/zc npc woman` | Spawns the Woman companion (only if her local model file `woman.zsk` is in `config/zomboidcraft/npc`). |
| `/zc npc wear <bra\|stockings\|heels\|hair>` | Adds a clothing piece to the companion you're looking at (within 8 blocks), or else the nearest one (within 16). |
| `/zc npc remove <bra\|stockings\|heels\|hair>` | Removes that piece the same way. She puts herself back in order after about 10 seconds. |
| `/zc npc outfit` | Shows what the nearest companion is wearing and why, or for a model with whole outfits, her current one and the list. |
| `/zc npc outfit <work\|skirt\|dress\|dress_boots\|date>` | Puts her straight into that outfit (testing). |
| `/zc npc outfit <look>` (whole-outfit models) | Models that come in whole outfits take their own: Shion has default, open, nojacket, shorts, gym and bare. For those models `/zc npc wear\|remove` takes their extras instead (Shion: hair, horns, strands, harness, pistol, collar, straps, earring, piercing, shoes). Tab lists what the nearest companion has. |
| `/zc npc name <name>` | Renames the companion. |
| `/zc npc mind` | Shows the companion's mood, traits, all eight feelings as bars, her five latest memories, and her job, shift and skills. |
| `/zc npc home` | Makes the spot you stand on her home; chests within 12 blocks are her stores. *Arriving in the next playtest build.* |
| `/zc npc job <guard\|repair\|farm\|cook\|scavenge\|medic\|haul\|tailor\|mechanic\|none>` | Her job at home, done in her work hours when she isn't following you. A tailor patches holed clothes in the stores; a mechanic tops up car fluids at home and swaps worn engine-bay parts (tailor and mechanic arrive once the preview is okayed). |
| `/zc npc shift <day\|night>` | Day: works 06:00-17:00, free till 21:00, then sleeps. Night: works 19:00-05:00, free till 09:00, sleeps 09:00-17:00 (wristwatch time). |
| `/zc npc skills` | Every skill she has and its progress, plus the XP boost from any skill book she's read. |
| `/zc npc skill <skill> <0..10>` | Sets one skill: any player skill name (aiming, first_aid, carpentry, sneaking...) or the short ones (aim, cooking, scavenging, farming). |
| `/zc npc trait <brave\|kind\|talkative\|loyal\|optimist> <-1..1>` | Sets one of her traits. |
| `/zc npc feel <hunger\|thirst\|tired\|pain\|fear\|adrenaline\|lonely\|morale> <0..100>` | Sets one of her feelings, for testing. |
| `/zc npc bond [trust\|affection\|attraction <0-100> \| together \| apart]` | Shows how the nearest companion feels about you (no arguments), or sets trust, affection or attraction, or sets you together or apart. |
| `/zc npc lines` | Reloads the nearest companion's `lines.txt` and lists her moments, how many lines each has, how many are voiced, and any problems in the file. *Arriving in the next playtest build.* |
| `/zc npc say <moment>` | Makes the nearest companion say a line from that moment now, ignoring its conditions, wait and chance (Tab lists her moments). Both only matter for a character with her own `config/zomboidcraft/npc/<model>/lines.txt`. |
| `/zc npc clear` | Removes every NPC. |
| `/zc lore` | Looks at the walker in front of you (within 48 blocks) and prints its rolled traits: pace (shambler, fast shambler, sprinter), strength, toughness, sight, hearing, memory, whether it opens doors, armour share and health. |
| `/zc horde <count> [distance]` | Drops that many zombies (1-2000) in a ring around you, about that far away (4-128 blocks, default 32). Example: `/zc horde 200`. |

## Trailer recording

| Command | What it does |
|---|---|
| `/zc cinematic` | Film-set mode: hides the HUD and green dots (gun stays in hand), zombies ignore you, and you can't be hurt. |
| `/zc cinematic off` | Puts everything back. |
| `/zc cinematic hud clean\|full` | Hides or shows the HUD. |
| `/zc cinematic zombies ghost\|freeze\|normal` | Zombies ignore you, freeze mid-step, or act normally. |
| `/zc cinematic horde [count] [distance]` | A horde ahead that walks toward you (default 40 at 28 blocks). |
| `/zc cinematic lure [radius]` | Zombies in range come to you (default 64). |
| `/zc cinematic time dusk\|night\|midnight\|dawn\|day\|noon\|free` | Sets and holds the time of day (free lets it run). |
| `/zc cinematic weather <kind>\|free` | Sets and holds the weather. |
| `/zc cinematic fly [speed\|normal]` | Slow flying, even in survival (default 0.015). |
| `/zc cinematic smooth on\|off` | Eased mouse look for smooth camera moves. |
| `/zc cinematic help` | Lists these in chat. |

## Performance

| Command | What it does |
|---|---|
| `/zc perf` | Prints server tick time (ms, budget 50), walkers loaded (how many chasing, how many within 64 blocks) and total entities, and turns a hotbar readout on or off (FPS, zombies loaded, how many drawn at each detail level, pieces drawn per frame). Also prints a "Horde savings" line (moves rested, searches skipped, paths shared) and resets those counts. |
| `/zc profile [seconds]` | Samples the render, server and worker threads plus garbage collection and memory for that long (default 30, 5-300), writes `zomboidcraft-profile-<date>.txt` to the game folder and prints a summary. The report includes a "Sent to your client" line (block, section, light and chunk-load updates received). It also prints a "Sodium chunk uploads:" line showing Sodium's upload path. It also shows graphics card memory (free MB, evictions) and Zomboid body texture use. Use it when the frame rate drops for no clear reason, and send Claude the file. |
| `/zc flicker on\|off` | Turns the flickering lamps on dying circuits on or off (config `performance.flickerLamps`, default off because each blink rebuilt a whole city chunk section and lagged). Off keeps lamps steadily lit. |

## Trains

| Command | What it does |
|---|---|
| `/zc train test rail` | Lays a 160x100 test railway loop beside you with two platforms (Union Station, Waterfront) and call buttons; the train pulls in to your platform. |
| `/zc train test subway` | The same loop with a subway train (stops Grayhaven Stadium, Mercy General). |
| `/zc train list` | Every train line, its stops (and which are called), and where each train is, how fast it's going, and how many zombies it carries unseen (they appear when a player is within 80 blocks). |
| `/zc train remove <id>` | Takes a train line away, e.g. `test_rail`. |
| `/zc train here` | Sends the nearest line's train to the stop nearest you. |
| `/zc train ride` | Puts you aboard the first car of the nearest train. |
| `/zc train call <stop>` | Calls a train to a named stop, as if its call button were pressed. |

G on a station call button calls the train; it fires a flare above ground or a siren in the subway, and both draw zombies. Settings are under `trains.*` in `/zc config` (speeds, acceleration, door seconds, cars per train, noise ranges, OTTO chatter).

## World and places (County world type only)

| Command | What it does |
|---|---|
| `/zc places` | Lists the 12 nearest places within 3000 blocks, with variant, distance, direction and coordinates. |
| `/zc places <type>` | Same, for one type or variant: downtown, town, suburb, farm, port, military, roadside, hideout. |
| `/zc goto <type or variant>` | Teleports you to the south gate of the nearest place of that kind within 6000 blocks (beside the hatch for a hideout). Variants work too, e.g. naval (the aircraft carrier wharf), old_city, county_seat, army_base, gas_station, diner, motel. Any named place on the county map also works by its short name (Tab lists them; a miss lists them too): e.g. stadium, union, mercy (hospital), university, mall, airport, shipyard, ravenrock, camp, airfield, ranges, lab, lighthouse, dam, boardwalk, hollis, kingsbridge, seabright. `landmark` goes to the nearest landmark. |
| `/zc bunker` | Teleports you to the way into the nearest bunker and says its state (sealed, looted, breached). |
| `/zc bunker <kind>` | Same, for one kind: tower, ship, shelter, forest. |
| `/zc bunker [kind] door` | Teleports you straight to that bunker's blast door. |
| `/zc station [name]` | Teleports you onto a station platform (nearest stop if no name). Names: union, uptown, pinecrest, fort, riverside, millbrook, dunmore, fairview, ashby, hollow, kings, seabright, airport, foundry, southport, subway_stadium, subway_tower, subway_power, subway_waterfront, subway_union, subway_mercy, and on the Glass Line (underwater tube, new worlds only) glass_mercy and glass_quay. |
| `/zc breach` | Shows the breach under Ridgeview Labs: where it is, whether respawning is on, time to the next surge or flicker, and the last surge day. Find the lab with `/zc places military` (the one marked ridgeview). *Arriving in the next playtest build.* |
| `/zc breach surge` | Sets off a full surge now: cracks flare, a tremor, places cleared before it refill, and one lost Harvestman comes back. |
| `/zc breach flicker` | Sets off a short flicker with no spawns. |
| `/zc bunkermap` | Gives you a marked bunker map; reading it rings a nearby bunker in red on your map (M) with a hint. |
| `/zc lift test [floors]` | Builds a working test lift shaft 3 blocks in front of you, doors facing you, floors 4 apart with a ledge at each (2-40 floors, default 6), to try calling and riding a lift. |
| `/zc lift list` | Lists every loaded lift with its floors, where the car is, and any waiting calls. |
| `/zc lift call` | Sends every loaded lift to its top floor, to check they run. |
| `/zc city [seed]` | Builds the old test city around you (best in a superflat world, takes a minute or two). |

## Weather

| Command | What it does |
|---|---|
| `/zc weather` | Shows the current weather, how long it lasts, its rain, thunder, fog, wind, moon and hail levels, and the air temperature where you stand. |
| `/zc weather <kind>` | Forces a weather: drizzle, rain, downpour, fog, gale, storm, thunderstorm, severe_storm. |

## World events

| Command | What it does |
|---|---|
| `/zc event jet\|heli\|siren\|explosion` | Starts a shared world event around you now: a jet or helicopter flyby, an air-raid siren, or a far-off explosion with smoke. Everyone hears it from where they stand. |
| `/zc event strafe` | A jet (sometimes with a wingman) comes in low down the clearest street and rakes the nearest zombie crowd with its cannon. |
| `/zc event bomb` | One or two jets make a low pass and drop a stick of bombs on the nearest zombie crowd. |
| `/zc event hunt` | The Apache circles and hovers low over the nearest horde for 40-55 seconds firing its chain gun, then leaves. |

Air strikes hit the ground ahead of you if no zombies are about. They kill zombies, hurt players standing right there, and never damage blocks.

Settings: `world.worldEvents` (true/false, random events on or off), `world.worldEventMinutes` (average gap between them; default 10).

## World settings (Sandbox)

Each world keeps its own copy of Zomboid's 269 sandbox settings plus ours (full table: `world-settings.md` in this folder). A new world starts from `config/zomboidcraft.json`; after that, `/zc config` on a world setting changes only the open world.

| Command | What it does |
|---|---|
| `/zc sandbox` | Opens the Sandbox page (until the page is built it points you to the list). |
| `/zc sandbox list [group]` | Lists the 13 groups, or every setting in one group with its value (fixed, coming or your-screen-only marked). Groups: population, lore, time, utilities, loot, nature, character, skills, vehicles, firearms, animals, map, zomboidcraft. |
| `/zc sandbox set <id> <value>` | Changes one world setting, e.g. `/zc sandbox set ClimateCycle endless_rain`. Tab lists ids and choices. |

## Settings

| Command | What it does |
|---|---|
| `/zc config` | Lists every setting and its current value. |
| `/zc config <setting>` | Shows one setting. |
| `/zc config <setting> <value>` | Changes a setting and saves it (true/false or a number). |
| `/zc config reload` | Reloads `config/zomboidcraft.json` after you edit the file by hand. |

Setting groups: zombies, zombieLore, senses, spawning, survival, progression, look, world, population, nature, weather, combat, guns, shotgun, limbs, finishes, digging, building, carry, cases, nightVision, radio, rolls, minigun, companions, trains.

Settings other threads pointed out:

- **Nature** (Zomboid's sandbox Nature page): `nature.climateCycle` (normal, no_weather, endless_rain, endless_storm), `nature.fogCycle` (normal, no_fog, endless_fog), `nature.rain` (very_dry, dry, normal, rainy, very_rainy; drier allows drizzle below weather.minRain, wetter brings more downpours and storms), `nature.temperature` (very_cold to very_hot; matters once body temperature exists), `nature.maxFog` (your screen only: normal, moderate, low, none; zombies still use the real fog), `nature.maxRainFx` (your screen only: normal, moderate, low), `nature.snowOnGround` (placeholder, no snow yet). All default to normal.
- **Fog off**: `weather.fog false` (no thick fog weather) and `weather.rollingFog 0` (no fog rolling in on its own; 0.5 halves it). Both are needed to remove the mod's fog. With `weather.steadyDay` on, daytime haze is set by `weather.dayFog` instead (0 for none).
- **Weather** (on by default unless noted): `weather.endlessNight` (gloomy days so lights stand out; `weather.endlessDaylight`, default 0.30, sets how bright), `weather.mist` (mist banks that show under Photon; now OFF by default; `weather.mistAmount`, default 1.0), `weather.windDebris`, `weather.stormFlashes`, `weather.drips`, `weather.skylineFires`, `weather.stormSurges` (lamps flicker after close strikes), `weather.wetGlints` (rain sparkles in each lamp's colour), `weather.ghostPower` (the dead grid briefly flickers streetlights, signs and windows on every few minutes; `weather.ghostPowerEvery`, default 1.0, scales the wait, 0.5 = twice as often), `weather.stormAlarms` (was weather.alarms; now OFF by default: storms set off car and shop alarms that draw zombies; getting in the car stops it), `weather.steadyDay` (daytime keeps one light level and haze whatever the weather; weather fog only shows at night), `weather.dayFog` (0.2, how hazy the day is under steadyDay).
- **Zombie lore** (`zombieLore.*`): Zomboid's 29 Zombie Lore settings (speed, strength, toughness, senses, memory, doors and more). `/zc config` lists them; choice settings take their option names, any case, with spaces or dashes read as underscores.
- **More or fewer zombies** (`spawning.*`): `nearbyCap` (24, no new groups once this many are within 64 blocks; the main limit), `groupMin` / `groupMax` (3 / 7 per group), `intervalSeconds` (10), `minDistance` / `maxDistance` (32 / 56). Big hordes: nearbyCap 80, groupMin 8, groupMax 16, intervalSeconds 4.
- **Population**: `population.hotspots` (true: hospitals, police stations, malls, fire stations and schools hold extra zombies indoors, x3 / x2.5 / x2 / x1.5 / x1.5), `population.autoCap` (true: the cap on real zombies tunes itself to the server's tick time) with `population.capMin` (120), `population.capMax` (800) and `population.tickTarget` (40 ms).
- **Companions**: `companions.startTrust` (60), `companions.startAffection` (45), `companions.startAttraction` (35) set a new companion's starting bond with you; `companions.startSpread` (10) varies each by up to that much either way. `companions.endlessAmmo` (false; true = her gun never runs dry), `companions.watchShare` (0.25, share of your XP a bonded companion learns by watching you; 0 = off).
- **Performance**: `performance.flickerLamps` (false; see `/zc flicker`), `performance.harvestman` (false; the Harvestman on or off), `performance.nvidiaBufferStorage` (true, your screen only, needs a restart: keeps OpenGL buffer storage on for NVIDIA cards, which Minecraft 26.3 turns off, so Sodium uploads chunks faster; false restores Minecraft's choice), `performance.shadowObjectDistance` (24, your screen only; 0 = no limit: only things within that many blocks of the camera cast Photon shadows, further ones still draw normally), `performance.entityShadows` (false: no creature or object casts a shader shadow; true turns them back on), `performance.shadowZombies` (12, how many of the nearest zombies may cast one when they're on), `performance.fastBodyVertices` (true, your screen only: draws Zomboid bodies, meaning zombies, corpses and survivors, a whole vertex at a time, which saves up to 30% of the frame with 600 zombies; looks the same; false = old way; switches itself off for the session if it ever errors), `performance.parallelBodies` (true, your screen only: Zomboid bodies are skinned on worker threads using half your cores; false does it all on the render thread), `performance.batchBodies` (true, your screen only, needs parallelBodies on: body meshes that share a texture are drawn as one batch per frame instead of one per zombie per mesh), `performance.zombieSoundCap` (48, your screen only: most zombie groans, steps and door blows playing at once, nearest kept, so gunshots always get a sound channel), `performance.farPushDistance` (32 blocks; 0 = off: walkers further than that from every player stop shoving each other apart), `performance.restingWalkers` (true: a walker standing still skips its move), `performance.quietCollisions` (true: walkers skip the empty collision search while no boat, minecart or shulker is loaded), `performance.sharedPaths` (true: a horde heading for one spot shares paths, and far zombies re-plan less for a moving noise).
- **Survival**: `survival.lacerationInfectionChance` (0.25, Zomboid's default: chance a laceration from a zombie infects you). Character creation (SURVIVAL wave 3): `survival.characterCreation` (on: character creation runs in the county on first join and after each death), `survival.freeTraitPoints` (0, from -100 to 100; Zomboid's Free Trait Points), `survival.negativeTraitsPenalty` (none, one_every_3, one_every_2, one_every_trait; Zomboid's Negative Traits Penalty).
- **Zombies**: `look.pzMidZombies` (20, how many of the nearest zombies get the mid-detail Zomboid body), `look.pzMidDistance` (16 blocks, range for that mid detail, shrinks when zoomed through a scope), `look.crowdLightDistance` (48 blocks, after scope zoom; 0 = off: far crowd zombies at least that far away use the zombie packs' lightest level 3 mesh, 25-40% fewer triangles), `look.newZombies` (false brings back the old zombie model), `look.zombieDetailDistance` (how close before every clothing detail shows; default 16), `combat.leglessSpawnChance` (share that spawn with no legs; default 0.03).
- **Limb shooting**: `limbs.enabled`; `limbs.forearm` / `arm` / `shin` / `leg` (damage each limb takes before it tears off); `limbs.shotguns` / `handguns` / `rifles` / `huntingRifles` / `otherGuns` (how hard each gun kind tears); `limbs.hopSpeed`; `limbs.limbSeconds` (how long a torn limb lies on the ground); `limbs.maxLimbs`.
- **Aircraft** (arrive when the flight work merges into playtest): `world.jetTopSpeed` (km/h; default 300, past that the world can't load fast enough), `world.heliTopSpeed` (km/h; default 250), `world.aircraftService` (true: aircraft are found low on fuel, with a flat battery and worn out, and burn fuel; false: every aircraft is ready to fly). Flying needs the matching flight manual (Rotary-Wing or Jet Fighter), otherwise you can only taxi; Creative skips manuals and servicing.
- **Minigun** (`minigun.*`): damage, hipSpread (0.9, spread when not zoomed), aimSpread (0.35, spread while the gunner holds right-click to zoom), range, groupRounds (4 rounds per burst group), groupTicks (5 ticks between groups), loopTicks (25, how often the firing sound restarts; together about 945 rpm), autoRpm (no longer used by the turret), spinUpSeconds, spinDownSeconds, beltSize (300), beltLoadSeconds, tracerEvery, shotNoise.
- **Glass**: `guns.glassHolesSmall` (2, small panes and church glass), `guns.glassHolesWindow` (3, house windows), `guns.glassHolesLarge` (4, big and roof glass), `guns.glassHolesShop` (5, shop and office plate glass), `guns.glassHolesBlock` (6, glass blocks): bullet holes each takes before it shatters.
- **Digging**: `digging.rules` (on/off), `digging.floorDepth` (how far under natural ground you can dig; default 8), `digging.carpentryLevel` / `masonryLevel` / `metalworkingLevel` (skill needed to knock down wood / brick / metal walls; default 2), plus noise, endurance cost and seconds for each kind (see `/zc config` for the exact names).
- **Building**: `building.boardHits` (zombie hits a board takes; default 6), `building.boardHitsPerStrength` (3), `building.metalSheetHits` (32), `building.tripwireNoise` (14), `building.rainBarrelFill` (0.5), `building.timeMultiplier` (how long builds take; 1.0). Also `building.alarmClockNoise` (40, how far zombies hear a ringing alarm clock), `building.alarmClockRingSeconds` (25), `building.keyFobRange` (40, reach of a car key's panic button), `building.carAlarmSeconds` (40), `building.planterGrowDays` (4, in-game days from seed to ripe), `building.planterWaterDays` (1, how long a watering lasts), `building.spikedWallHits` (24, zombie blows a spiked log wall takes), `building.spikeDamage` (2).
- **Cars**: `world.streetCars` (parked cars on city streets), `world.carUsable` / `carAbandoned` / `carWrecked` / `carBurnt` (share of street cars in each state; defaults 0.45 / 0.3 / 0.18 / 0.07), `world.carLoot` (loot in boots and vans). Car keys: `world.carKeyIgnition` (0.1, key left in the ignition), `world.carKeyGlovebox` (0.3, key in the glovebox), `world.carKeyZombie` (0.35, a nearby zombie carries it), `world.carKeyDrop` (0.3, that zombie drops it when killed), `world.hotwireSeconds` (8), `world.hotwireFail` (0.25, chance a hotwire only sparks), `world.hotwireWear` (0.8, how much more often hotwiring fails in a worn car; a wrecked-condition car fails about 85% with the defaults). `world.partWear` (1.0, how fast car parts wear from driving, braking, drops and crashes), `world.carRiderScale` (0.85, how big players are inside a car so limbs stay in the cabin; 1 = full size; aircraft unaffected).

## Keys (not commands)

- **G**: get into a seat, change seat, search the glovebox or boot, get out. *Coming soon:* tap **G** at the back to open/close the boot, or at the front for the bonnet; hold **G** for Open/Close the boot, Search the boot and Open/Close the bonnet. **C**: start/stop engine or hotwire. **L**: headlights (gun light on foot). **H**: horn. **V** (shove) in a car: wind your side window down or up. In the Warthog's gunner spot: hold **left click** to spin up and fire the minigun, **R** loads its belt from your loose 5.56 rounds.
- **Sneak + G** on a companion: talk panel (compliment, flirt, give held item, hug, hold hand, kiss; keys 1-6). **G** on the green dot at her collar: follow/wait. **G** on the dot on a piece she's wearing: ask her to take it off or change it. Sneak + G then "Her outfit...": give your opinion or suggest an outfit.
- **Z**: quick-use wheel. **M**: map.
- **B**: talk on your radio. **N**: flip night vision goggles down or up.
- Rebind any of these under Options > Controls.

## Other options

- Sound volume sliders (zombies, guns, weather and ambience, vehicles, wall muffling) are under Options > Music & Sounds.
