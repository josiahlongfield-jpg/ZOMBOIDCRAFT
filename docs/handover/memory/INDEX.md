# Memory notes for ZomboidCraft (all 366 notes, verbatim)

Every memory note from the old project is copied here unchanged. Class:
- **CORE**: this project's own work and decisions.
- **SHARED**: rules and systems that apply to this project too (read them).
- **BACKGROUND**: belongs to the other project (Endless Night: Minecraft) or to removed systems; kept so nothing is lost.

`MEMORY.md` is the old project's own index, verbatim. `[[name]]` links point to `name.md` in this folder.

## CORE

| Note | What it holds |
|---|---|
| [minecraft-gloomy-pack](minecraft-gloomy-pack.md) | Josiah's "Medieval Gloom" Minecraft overhaul (PAUSED 2026-10-24) — status, files, setup gotchas |
| [zomboid-access-tech](zomboid-access-tech.md) | How ZomboidCraft keeps stairs, doorways, ladders and decks walkable (Builders post-passes, walkcheck --why, block surface heights, preview sizes, traps fixed) |
| [zomboid-air-lights-tech](zomboid-air-lights-tech.md) | Aircraft nav lights and heli searchlight (AirLights), Photon-safe glow rules, and FarFlybys (flybys drawn past render distance) |
| [zomboid-air-strikes-tech](zomboid-air-strikes-tech.md) | Air strike world events (jet strafe, jet bombing, Apache hunt): AirStrikes.java, path events, low-pass and street-alignment rules, config, sounds |
| [zomboid-air-surfaces-tech](zomboid-air-surfaces-tech.md) | Moving jet control surfaces (surfaces.py + AirSurfaces.java), weathercock flight term, F-22 bezels/ICP and tinted HUD, carrier jet parking rule |
| [zomboid-airport-grounds-tech](zomboid-airport-grounds-tech.md) | Airport buildings round the terminal (tower, fire station, hangars, cargo, fuel farm, landside roads, road off I-95) built 2026-10-30; classes, coords, ah_ kit, preview |
| [zomboid-airport-tech](zomboid-airport-tech.md) | Grayhaven International Airport build (LANDSCAPE, 2026-10-29): site moved east of I-95, frame/tiles, af_ kit, location card, refs, preview |
| [zomboid-arena-tech](zomboid-arena-tech.md) | Harbor Arena (island landmark at 3640,-480) build notes - Arena.java/ArenaRooms.java, ar_ kit in arena.py, preview coords and checks |
| [zomboid-arms-attachments-tech](zomboid-arms-attachments-tech.md) | Foregrips and optics in the arms posing tool (v19, 2026-11-03): part data, how they mount, front hand on a grip, ADS through an optic |
| [zomboid-army-airfield-tech](zomboid-army-airfield-tech.md) | Caldwell Army Airfield build (LANDSCAPE 2026-10-30): ArmyField frame/tiles/gates, ArmyFieldSite, ArmyFieldBase layout, mf_ kit incl. smooth barrel-vault pieces, preview |
| [zomboid-attachment-colours-tech](zomboid-attachment-colours-tech.md) | How attachment colour variants work (tan holo/magnifier/grip/light, dark/camo/green flip irons): zomboidcraft:colour component, model data indices, skin.py selects, loot paint |
| [zomboid-auto-loot-tech](zomboid-auto-loot-tech.md) | LOOT wave 4 stage 7 auto shop loot tables (auto_loot.py, part custom data, the 26 part/manual items waiting on REFERENCES photos) (2026-10-31) |
| [zomboid-ballpark-tech](zomboid-ballpark-tech.md) | Grayhaven Ballpark tech (Ballpark/BallparkRooms/BallparkLz, bp_ kit, preview coords and checks); built 2026-10-28 |
| [zomboid-base-building-tech](zomboid-base-building-tech.md) | ZomboidCraft base building + digging rules code map (building/ package, Build page, Placer, wave 1 blocks, config sections, gitignore trap) |
| [zomboid-base-customisation-plan](zomboid-base-customisation-plan.md) | Skill-gated base customisation (move furniture, knock/add walls, extend bases) plan doc 2026-10-31, picks answered, wave 1 built |
| [zomboid-base-rooms-plan](zomboid-base-rooms-plan.md) | BASES room functions + outposts plan (2026-11-06, marine direction W6/W26): doc MsV98kR3JgUFt54wDVhkFx, calls R1-R6, nothing built until sign-off |
| [zomboid-boardwalk-tech](zomboid-boardwalk-tech.md) | Seabright Boardwalk (coast group site 3) build tech: natural place, shops/arcade/boardwalk/beach + amusement pier (wheel, carousel, swing, booths, wooden coaster), sb_ kit |
| [zomboid-boats-tech](zomboid-boats-tech.md) | Boats are .zcar vehicles (kind "boat") made procedurally by tools/cars/boats.py; how they float, are placed, searched and previewed |
| [zomboid-bought-assets-plan](zomboid-bought-assets-plan.md) | Josiah will buy 3D asset packs (trees first) to replace most of the world; shopping list doc link, intake rules and conversion order |
| [zomboid-broadcast-tech](zomboid-broadcast-tech.md) | Grayhaven Broadcast Center tech (Broadcast/BroadcastHouse/BroadcastTower, tv_ kit, preview coords, checks); built 2026-10-28 |
| [zomboid-broadcasts-plan](zomboid-broadcasts-plan.md) | AUDIO's plan for Zomboid-style outbreak radio broadcasts (PZ review section 15) and far-off meta events (section 14); doc 98TrFVLzMteUKAXQKM6v9Z, merged to playtest 2026-10-31 |
| [zomboid-bunker-kit-tech](zomboid-bunker-kit-tech.md) | How the ZomboidCraft bunker block kit is built and previewed (tools/city/bunker.py, trapdoor + climbable support, catalogue preview loop) |
| [zomboid-bunker-review-tech](zomboid-bunker-review-tech.md) | Bunker review pass (Josiah picked "Review pass, then wave 2" 2026-10-31 22:07): what each kind got, where in Bunker.java, how previews/before shots are made, progress |
| [zomboid-called-strikes-plan](zomboid-called-strikes-plan.md) | Radio-operator called air strikes (W17, marines direction): preview doc, AUDIO/UI hook agreement, A1-A8 approved 12:11; built on claude/project-thread-dcbfp2 (StrikeCalls), preview sent 14:05, merged to playtest 7f9e6db5a 2026-11-06 20:06 on his go; AUDIO+UI pinged. |
| [zomboid-camp-black-pine-tech](zomboid-camp-black-pine-tech.md) | Camp Black Pine build (LANDSCAPE 2026-10-30): CampBlackPine FrameSite frame/zones, smooth quonset huts via ArmyFieldBase.arc, cleared zones vs pines, preview |
| [zomboid-camp-kit-tech](zomboid-camp-kit-tech.md) | Army camp pieces (Camp.java) come from the cp_ kit in tools/city/camp.py; block names, facings, preview yard, what is still vanilla |
| [zomboid-cape-tech](zomboid-cape-tech.md) | Cape Merrow Lighthouse (coast group site 2) build tech: natural place like the Dam, drawn cliff line, cm_ kit, round.py xrail/glazex, preview go.sh |
| [zomboid-car-maintenance-plan](zomboid-car-maintenance-plan.md) | Car maintenance + working two-post hoist (Zomboid-style parts, Mechanics skill), VEHICLES thread, doc Lez5BQYtKQYAehuQYXHosr; code map, worldgen hook, key boards |
| [zomboid-carrier-tech](zomboid-carrier-tech.md) | How the ZomboidCraft aircraft carrier, its aircraft and military zombies are built (Carrier.java, tools/cars/aircraft.py, zcar.py, mesh.py --cars, hull floors, preview coords) |
| [zomboid-cars-driving](zomboid-cars-driving.md) | ZomboidCraft drivable cars: controls rule, sounds, zombie safety, lights, open work |
| [zomboid-cars-plan](zomboid-cars-plan.md) | ZomboidCraft car models: 80s American cars pack, licence rules, pack/wreck tools, boots, hull (thread "Make the cars drivable") |
| [zomboid-cases-tech](zomboid-cases-tech.md) | How ZomboidCraft's rifle case, pistol case and ammo can work (carried bags that come packed), branch cases |
| [zomboid-civic-buildings-tech](zomboid-civic-buildings-tech.md) | Per-building notes for the rebuilt civic set (post office, and next town hall, churches, bank...) plus the shop_loot.py wipe hazard |
| [zomboid-civic-centre-tech](zomboid-civic-centre-tech.md) | Grayhaven Civic Center builder (CivicCentre/CivicTower/CivicChamber.java, Grounds base, cc_ kit), lot, layout constants, preview coords, check results |
| [zomboid-civic-services-tech](zomboid-civic-services-tech.md) | Fire station (and next sheriff, school, theatre) rebuild notes: FireStation.java layout, fs_ kit, fire loot table, truck yaw rule |
| [zomboid-civic-tech](zomboid-civic-tech.md) | Civic kit (tools/city/civic.py, cv_ blocks) and the rebuilt courthouse (world/county/Courthouse.java): tile order, even widths, basement shelter hosts |
| [zomboid-clothing-style](zomboid-clothing-style.md) | Josiah's standing art direction (2026-10-25) for ZomboidCraft clothing and accessories: chunky 3D blocks, Borderlands-like cartoonish but believable |
| [zomboid-cockpit-lights-tech](zomboid-cockpit-lights-tech.md) | How the jet/heli cockpit lights work (cockpit_glow.py, cockpit_pages.py, .zglow, CockpitGlow.java) and how to re-pick regions |
| [zomboid-cockpit-live-tech](zomboid-cockpit-live-tech.md) | How live aircraft cockpits work: CockpitDisplays/CockpitPages screens, conformal HUD, CockpitPanel + Su27Panel sharp panel face, offline harness |
| [zomboid-command-net-loot](zomboid-command-net-loot.md) | LOOT's part of Josiah's command net plan (AUDIO's HuntBoard): C7 resupply crate on escort, C4 coloured smoke + laser designator ids (2026-11-06) |
| [zomboid-command-net-tech](zomboid-command-net-tech.md) | AUDIO command net + hunt board (Tarkov marines direction): CommandNet, HuntBoard, StrikeRadio air strike calls, hooks for UI/LOOT/SURVIVAL/AIRCRAFT, merge status |
| [zomboid-companion-bond-tech](zomboid-companion-bond-tech.md) | Companion step 2: NpcBond opinions (trust/affection/attraction), flirty/romantic/jealous moods, sneak+G talk panel, and the no-sexual-content line (2026-10-30) |
| [zomboid-companion-care-tech](zomboid-companion-care-tech.md) | Companion brain tech (2026-10-31): NpcCare (offers, real ammo, kit, LookAfter/Gather goals), NpcSkills on shared Skill names, learn-by-watching hook, CompanionCareTest film |
| [zomboid-companion-render-tech](zomboid-companion-render-tech.md) | How skinned companions (.zsk) are drawn and what Iris/Photon on MC 26.3 do to custom geometry; the shader see-through bug hunt (2026-10-30) |
| [zomboid-companion-trades-plan](zomboid-companion-trades-plan.md) | PZ review section 18 plan (2026-10-31): companion moves to the player's Zomboid skill list, learns by doing/books/occupation, trades per new system; doc 7CzuWTzRBUNiGB6t6FsmQP answered, building |
| [zomboid-companion-ui](zomboid-companion-ui.md) | Companion (Rebecca) UI page request (Josiah 2026-10-30) built 2026-10-30: CompanionScreen (G) + OrderWheel (middle-click) |
| [zomboid-companion-wardrobe-tech](zomboid-companion-wardrobe-tech.md) | Companion wardrobe (2026-10-30): pack clothes added to woman.zsk, NpcWardrobe outfits she picks herself, opinion/suggest panel, green dot at her waist, topless-reaction ask declined |
| [zomboid-companion-work-tech](zomboid-companion-work-tech.md) | Companion skills, base jobs, day/night routine, sleep and mood posture: classes, rules, commands and the test room (built 2026-10-30) |
| [zomboid-county-dress-tech](zomboid-county-dress-tech.md) | Shared county building dressing (Bldg.java + tools/city/dress.py bd_ kit): kit roofs for gable/hip, flat-roof coping, chimneys, rooftop units, downpipes, lintels; blocks.json order rules |
| [zomboid-county-plan](zomboid-county-plan.md) | ZomboidCraft county master-plan map page (artifact URL, drafts, how to read Josiah's edits, what draft 2 contains) |
| [zomboid-county-preview-tech](zomboid-county-preview-tech.md) | Previewing and checking the planned county offline (CountyPreview at world coords, mesh origin overflow fix, LOD, fog, endless flag, one-of-every-place sweep) |
| [zomboid-dam-tech](zomboid-dam-tech.md) | Black Pine Dam (NW zone site 4) build tech: raised reservoir water model, natural place, Dam.java frame, dm_ kit, real-terrain preview pipeline |
| [zomboid-decor-tech](zomboid-decor-tech.md) | LOOT wave 4 stage 5 decor system (decor.py specs, DecorBlock, Comfort, DecorNet, Moving hooks, hand truck); models wait on REFERENCES photos (2026-10-31) |
| [zomboid-depot-tech](zomboid-depot-tech.md) | Country rail depots (DepotSite.java, rw_dp_ kit in railway.py) at Pinecrest/Millbrook/Fairview/Kingsbridge/Seabright; placement and gotchas |
| [zomboid-detail-kits-tech](zomboid-detail-kits-tech.md) | Kits added in the 2026-10-29 detail pass (freight, trackbed, weave, memorial, gazebo, poles, cellar barrels, ship fairing) and how they're placed |
| [zomboid-detail-pass](zomboid-detail-pass.md) | Detail pass over built places against the reference photos (picked 2026-10-30): what was changed, what was checked unchanged, what is left |
| [zomboid-diner-tech](zomboid-diner-tech.md) | Roadside diners tech (lunch car, brick cafe, truck stop): diner.py dn_ kit, Diner.java layout rules, preview seeds, gotchas found 2026-10-27 |
| [zomboid-farm-kit-tech](zomboid-farm-kit-tech.md) | fm_ farm kit (tools/city/farm.py) used by Farmstead/FarmSite: gambrel roof pieces, bales, rail fences, gates, stalls, yard props, loft ladders; preview yard modes |
| [zomboid-farm-pens-tech](zomboid-farm-pens-tech.md) | Farm pens for chickens and cows (2026-10-31, LANDSCAPE): new fm_ pen pieces, corrals and coops, FarmHerds chunk records and the animal/* block tags for SURVIVAL |
| [zomboid-fleet-tech](zomboid-fleet-tech.md) | Airport airliners/bizjets (LANDSCAPE 2026-10-30): fighters moved off the airport, tools/city/fleet.py an_ kit (4 walk-in types), AirportGates bridges/stands, jet centre bizjets, preview |
| [zomboid-floor-fitout](zomboid-floor-fitout.md) | Every tower floor fitted out by building purpose, hotel towers, slim one-lift core, shared Stairwell (4 clear over every step); BUILT and in playtest 2026-10-28; tech notes + preview loop |
| [zomboid-foundry-tech](zomboid-foundry-tech.md) | Foundry Row (coast and industry site 5, Calder Steel works at 1300,1950): files, frame, fr_ kit, layout constants, preview pipeline |
| [zomboid-garage-bunker-tech](zomboid-garage-bunker-tech.md) | How the downtown tower parking levels and hidden skyscraper bunker are generated, previewed and found in game (Garage.java, park_ kit, /zc bunker) |
| [zomboid-glass-line](zomboid-glass-line.md) | Glass Line underwater subway tube (Mercy General to Eastside Quay under the South Arm): route, depths, kit, world-gen code, stations, leak rules, previews |
| [zomboid-glass-tech](zomboid-glass-tech.md) | Shootable glass tech: GlassShots for building glass, CarGlass per-window car panes (holes at exact u,v, wind down with V), glass_marks texture, preview tool |
| [zomboid-harbour-fort-tech](zomboid-harbour-fort-tech.md) | LANDSCAPE's harbour fort hideout (round sea fort in the bay, ARMY bunker under it): code, kit, location, preview (2026-11-06) |
| [zomboid-harvestman-den-tech](zomboid-harvestman-den-tech.md) | Harvestman den (hd_ block kit, HarvestmanDens carve/bodies/setOut), posed corpses (Corpse.pose SAT/SLUMPED/HUNG, client ZPosed), scrap working in REST, /zc harvestman den (2026-10-28) |
| [zomboid-harvestman-game-tech](zomboid-harvestman-game-tech.md) | How the Harvestman works in game - server legs, hit zones, grab/feed, Mind utility AI, Director hunter/lairs, camo, held view, sounds, /zc harvestman (2026-10-28) |
| [zomboid-harvestman-goals-tech](zomboid-harvestman-goals-tech.md) | Harvestman goals between hunts (2026-10-29): HarvestmanGoals/Territory/Choice, new plans 27-33, territory memory in Director records, encounter decisions |
| [zomboid-harvestman-presence-tech](zomboid-harvestman-presence-tech.md) | Harvestman believability round (2026-10-29) - signs, presence hum/unease/hush, feints, eye shots, caution, wounds; plus the 89-idea full-potential doc |
| [zomboid-harvestman-route-tech](zomboid-harvestman-route-tech.md) | Harvestman route-finding round walls (HarvestmanRoute A*, gates for doorways, crawl hooks stay/slow, Python port to test changes) - 2026-10-28 |
| [zomboid-harvestman-tech](zomboid-harvestman-tech.md) | How the Harvestman preview is built (smooth WebGL mesh kit, round-5 leggy shape constants, test harness), for the in-game model later |
| [zomboid-harvestman-web-room](zomboid-harvestman-web-room.md) | Web page copy of the Harvestman test room (3D Spider 7 acting out all 14 actions), artifact 4dSFQRP3JCkfLJz2ajhpsr, source in project files |
| [zomboid-highway-places](zomboid-highway-places.md) | Highway places Josiah picked 2026-10-31 02:39 (rest area, toll plaza, I-88/I-495 cloverleaf): chosen spots, design, progress |
| [zomboid-hollis-tech](zomboid-hollis-tech.md) | Hollis Island Quarantine Station (coast site 4) build tech, qs_ kit, Seabright ferry route, and the previews gallery rule |
| [zomboid-hospital-tech](zomboid-hospital-tech.md) | Mercy General Hospital builder (Hospital/HospitalDepts/HospitalWard.java, hs_ kit in tools/city/hospital.py), lot, preview coords, access results |
| [zomboid-house-alarms-tech](zomboid-house-alarms-tech.md) | How LANDSCAPE's Zomboid house alarms work (House.alarm placement, ha_ kit, world/gen/HouseAlarms ringing, power/battery, Electrical cut) and the yard arc/hum (2026-10-31) |
| [zomboid-house-stories-tech](zomboid-house-stories-tech.md) | Zomboid's building stories (SurvivorHouseChance / Randomized Building Chance) in county houses: where the code is, the 12 tales, how bodies/walkers and the rate are wired |
| [zomboid-houses-tech](zomboid-houses-tech.md) | ZomboidCraft house interiors, furniture and exterior kits: Interior.java, House.java, furniture.py and exterior.py pipelines, HousePreview, CitySpecs size rule |
| [zomboid-hunt-board](zomboid-hunt-board.md) | Hunt board page on the command radio (UI thread draws it; AUDIO owns entries, AIRCRAFT owns strike status); mock sent 2026-11-06, awaiting Josiah's sign-off |
| [zomboid-landmark-lighting-tech](zomboid-landmark-lighting-tech.md) | Landmark coloured lighting pass (Josiah 2026-11-06): light counts per landmark, airfield runway lights lit, and how Photon colours modded lights (block.properties 10064-10079) |
| [zomboid-landscape-contracts](zomboid-landscape-contracts.md) | The other threads' contracts LANDSCAPE builds against for auto shops, substations, farm pens, house alarms (hoist, loot tables, key board, power, animals, sounds, sandbox) |
| [zomboid-landscape-pause-after-list](zomboid-landscape-pause-after-list.md) | LANDSCAPE pause history: 2026-10-29 pause (lifted 18:59) and the 2026-10-31 10:22 "stop development for now" pause with what was left unmerged |
| [zomboid-landscape-queue](zomboid-landscape-queue.md) | LANDSCAPE thread's queue of county-plan places that still have no builder (2026-10-28), grouped, plus the next pick and refs status |
| [zomboid-landscape-review-plan](zomboid-landscape-review-plan.md) | LANDSCAPE's plan doc for auto shops, world stories, alarms, erosion, farm animals, substations (PZ review s8/9/12/13/14); ANSWERED 2026-10-31 04:32, auto shops first |
| [zomboid-lifts-tech](zomboid-lifts-tech.md) | How ZomboidCraft lifts/escalators work in code (world/lifts), the LiftBuild worldgen contract, paint.py, /zc lift test. |
| [zomboid-living-companion](zomboid-living-companion.md) | "Living" companion plan (needs, memory, opinions, own decisions, optional Claude chat brain, body IK/physics), awaiting Josiah's answers 2026-10-29 |
| [zomboid-loop-bench](zomboid-loop-bench.md) | Loop Bench page where Josiah edits any mod sound on his phone; how to apply his edits; -18 dB means remove |
| [zomboid-loot-settings-tech](zomboid-loot-settings-tech.md) | LOOT wave 4 stage 6 loot sandbox settings (LootSettings drop hook, categories, rarity factors, pre-looted/diminished, crowd effect, starter kit, LootRespawn) (2026-10-31) |
| [zomboid-mall-tech](zomboid-mall-tech.md) | Grayhaven Mall builder (Mall*.java, ml_ kit in tools/city/mall.py), sign tile order, preview coords and access-check results |
| [zomboid-map-tech](zomboid-map-tech.md) | Kit MAP page tech (UI thread, 2026-10-29): whole county drawn on the client from CountyGen, train lines, all players, block colour table |
| [zomboid-market-tech](zomboid-market-tech.md) | Old Market on Grayhaven island (built 2026-10-28): Market.java/MarketHall.java, om_ kit (market.py), lot, preview loop, the access fixes it forced in Flats/Interior |
| [zomboid-nether-origin-idea](zomboid-nether-origin-idea.md) | DECIDED 2026-10-29 23:30: the Nether is where the Harvestman and the infection came from (lab-breach lore + ground-zero zone now, trip later); Nether rebuilt DOOM-inspired |
| [zomboid-nw-military-plan](zomboid-nw-military-plan.md) | LANDSCAPE's northwest military zone (picked 2026-10-30 03:27): five sites, plan coords, refs, proposed order, design notes from photos |
| [zomboid-otto-tech](zomboid-otto-tech.md) | OTTO the robot train conductor: voice pipeline, foul-mouthed personality rule, reaction hooks (boarding, zombies, shots, stop button) |
| [zomboid-packtowers-tech](zomboid-packtowers-tech.md) | How the bought 20-buildings pack becomes ZomboidCraft downtown towers (tools/towers/towers.py, PackTower.java), how to preview and debug their interiors |
| [zomboid-park-museum-tech](zomboid-park-museum-tech.md) | Grayhaven City Park + Museum builder (CityPark/Museum/MuseumHalls/MuseumCells.java, mu_ kit in museum.py), lot, constants, preview coords, checks |
| [zomboid-penthouse-monument](zomboid-penthouse-monument.md) | Josiah 2026-10-29 wants hotel penthouses to be "a substantial monument", OK to redesign hotel tower tops; crown options offered, refs requested |
| [zomboid-power-grid-tech](zomboid-power-grid-tech.md) | LOOT's mains grid (PowerGrid districts, shutoff day, OFF/ON events), generators (place/fuel/connect/fix), Blackout sweep, How to Use Generators magazine (2026-10-31) |
| [zomboid-power-tech](zomboid-power-tech.md) | Harbor Point Generating Station tech (PowerStation/PowerHalls/PowerYards, pw_ kit, preview coords, checks); built 2026-10-28 |
| [zomboid-props-tech](zomboid-props-tech.md) | How pack and Poly Haven 3D models become ZomboidCraft furniture blocks (tools/props/props.py), swaps into the world (PropSwaps/Cover), stocked store shelves, preview tricks |
| [zomboid-quickslots-tech](zomboid-quickslots-tech.md) | How the worn quick slots work in code (QuickSlots.java, GearStore BELT/HOLSTER/SHEATH/WRIST, spill tick, pickup routing, client skip) |
| [zomboid-radios-tech](zomboid-radios-tech.md) | ZomboidCraft radios tech: voice through Simple Voice Chat plugin, Radios snapshot, B talk key + PTT mixin, voice key fixer, headset on helmet rails, filter, sounds, loot |
| [zomboid-rail-tech](zomboid-rail-tech.md) | LANDSCAPE rail/subway world build (Railway.java routing, Track.java, RailGen.java per-column sections, rw_ kit, CountyRails hand-off, previews, route maps), Josiah's 13:12 ask, open items |
| [zomboid-ravenrock-tech](zomboid-ravenrock-tech.md) | Ravenrock Penitentiary (NW zone site 5) build tech: pn_ kit, FrameSite frame, island dock, Waterfront prison slip, ferry bell crossing |
| [zomboid-ridgeview-kit-tech](zomboid-ridgeview-kit-tech.md) | Ridgeview Labs breach lab kits: rl_ (tools/city/lab.py, round ring + plug layouts in data/zomboidcraft/ridgeview) and sg_ red growth (tools/city/spore.py); preview yard pipeline (2026-10-29) |
| [zomboid-road-stories-tech](zomboid-road-stories-tech.md) | How LANDSCAPE's Zomboid road stories (world stories wave 1) are placed, built and hooked to LOOT, VEHICLES and the corpses system (2026-10-31) |
| [zomboid-rolls-tech](zomboid-rolls-tech.md) | How ZomboidCraft weapon rarity/tier rolls are built (package combat/rolls, zomboidcraft:roll component, places, config, API shared with the UI thread) |
| [zomboid-room-lights](zomboid-room-lights.md) | Room light switches and blue/red cube lamps (lx_ kit, LightSwitch, RoomLights post-pass), shipped 2026-10-31; rooms start dark |
| [zomboid-round-kit-tech](zomboid-round-kit-tech.md) | rd_ round-wall kit (tools/city/round.py + Round.java): smooth silo/bin/tank drums, stepped domes and cones; Farmstead silo/grainBin use it; farm preview yard |
| [zomboid-sandbox-settings](zomboid-sandbox-settings.md) | World settings (Sandbox page) work split, from PZ review sections 17 and 20; plan status |
| [zomboid-school-tech](zomboid-school-tech.md) | How the county school is built (School.java, tools/city/school.py sc_ kit), its grounds kit (playground, court, football field), preview spots |
| [zomboid-ship-fitout-tech](zomboid-ship-fitout-tech.md) | Ship fit-out kit (tools/city/shipkit.py, fit_ blocks) used to rebuild carrier/cargo interiors from photos; orientation, lamp variants, wheel UV trick, Carrier.java helpers |
| [zomboid-ship-tech](zomboid-ship-tech.md) | How the massive cargo ships at PORT places are built and previewed (BigShip.java, vessel_ kit, ship bunker, rain Photon mock-up) |
| [zomboid-shipyard-tech](zomboid-shipyard-tech.md) | Pelican Point Shipyard (coast group site 1) build tech: FrameSite tiles + dredged basin, sy_ kit, dock/slip/halls split, preview + leak/orient checks |
| [zomboid-shop-stories-tech](zomboid-shop-stories-tech.md) | LANDSCAPE's shop building stories (Zomboid RB looted/trashed/gun store siege/staff last stand): code, hook, rate, preview (2026-11-06) |
| [zomboid-shopfronts-tech](zomboid-shopfronts-tech.md) | Main street trades (Trades.java) and the shop-front kit (tools/city/fronts.py, front_ blocks) in Storefront.java; how to preview a row of fronts without an http server |
| [zomboid-signals-roads-tech](zomboid-signals-roads-tech.md) | Working traffic signals (Signal phase blocks), every street lamp lit, and the road-network fix (Layout.tieEnds/openGates) from 2026-10-31 |
| [zomboid-stadium-tech](zomboid-stadium-tech.md) | Grayhaven Stadium build (Stadium.java, LandmarkSite, stadium.py st_ kit): bands, ramps, bay kinds, county hook, walk-check fixes, preview commands and cams |
| [zomboid-substations-tech](zomboid-substations-tech.md) | Power districts (PowerGrid) and the 17 substation yards LANDSCAPE built for LOOT's power grid (2026-10-31) |
| [zomboid-survival-contracts](zomboid-survival-contracts.md) | SURVIVAL's contracts with other threads: LANDSCAPE herds/tags, WEATHER Climate, LOOT Mood/Ailments, UI G-menu, COMMANDS sandbox, NPC skills |
| [zomboid-survival-plan](zomboid-survival-plan.md) | SURVIVAL thread plan (2026-10-31): Zomboid needs, 17-part health, cooking/farming/fishing/animals, occupations+traits, water shutoff; split with LOOT; awaiting go |
| [zomboid-survival-tech](zomboid-survival-tech.md) | SURVIVAL wave 1 code map (Nutrition/Foods/Needs/Sleep, food bar held at 17, skills enum, preview test) and gotchas |
| [zomboid-tank-ranges-tech](zomboid-tank-ranges-tech.md) | Caldwell Tank Ranges build (LANDSCAPE 2026-10-30): TankRanges frame on FrameSite, layout coords, rg_ tank hulk kit + Tanks.tank, igloos, preview |
| [zomboid-tents-tech](zomboid-tents-tech.md) | Every tent in the world is built by Tents.java from the tn_ kit (tools/city/tents.py); widths, uses, block naming, preview yard |
| [zomboid-theatre-tech](zomboid-theatre-tech.md) | Theatre (picture house) tech: tools/city/theatre.py tr_ kit, Theatre.java layout and stories, lot placement fix, preview coords. |
| [zomboid-town-station-tech](zomboid-town-station-tech.md) | Town stations at the four street rail stops (tx_ transit kit, TownStation.java, Railway.stations hand-off, Role.STATION) and preview coords; built 2026-10-27 |
| [zomboid-track-contract](zomboid-track-contract.md) | Track/station geometry contract agreed between TRAINS and LANDSCAPE threads (bed, envelope, platforms, grades, call button, Trains.line API), 2026-10-27 |
| [zomboid-trailer-cinematic](zomboid-trailer-cinematic.md) | ZomboidCraft demo trailer plan page and the /zc cinematic filming command (code locations, how it works) |
| [zomboid-trains-tech](zomboid-trains-tech.md) | Trains tech (TRAINS thread): Line/Route/TrainRun/TrainEntity, walk-in car physics mixins, OTTO, call buttons, API for LANDSCAPE, /zc train |
| [zomboid-union-station-tech](zomboid-union-station-tech.md) | Union Station landmark: head house (UnionStation.java) + upstairs el train hall and bus terminal (UnionHall.java), lot frame, constants, tx_ kit, preview and check commands |
| [zomboid-university-tech](zomboid-university-tech.md) | Grayhaven State University builder (University + UniLibrary/UniLecture/UniScience/UniUnion/UniOldMain/UniDorm.java, un_ kit), lot, preview coords, access results |
| [zomboid-vehicle-mods](zomboid-vehicle-mods.md) | Converting Josiah's PZ Workshop vehicle mods (B41+B42) with their sounds into local-only ZomboidCraft cars; plan sent, awaiting go (2026-11-01) |
| [zomboid-waterfront-tech](zomboid-waterfront-tech.md) | Grayhaven Waterfront tech - Waterfront/Terminal/Pier/Boats classes, wf_ kit, Ferris wheel member blocks, preview + checks |
| [zomboid-weapons-review-share](zomboid-weapons-review-share.md) | Weapons thread's share of the Project Zomboid review (2026-10-31): section 6 + Firearms settings; plan doc, his answers, what is built |
| [zomboid-wearable-clothing](zomboid-wearable-clothing.md) | Clothing thread (2026-10-29) plan for 94 ZomboidCraft wearables: loot + worn models, slot layering choice, batch order, refs request |
| [zomboid-world-loot-tech](zomboid-world-loot-tech.md) | LOOT thread tech: roster data file, gen_loot.py item painter, preview viewer, ownership splits agreed with other threads (2026-10-27) |
| [zomboid-world-tech](zomboid-world-tech.md) | ZomboidCraft world/worldgen technical notes: rain-blocking tag rule, county generator layout, offline preview tools |
| [zomboid-zone-stories-tech](zomboid-zone-stories-tech.md) | How LANDSCAPE's Zomboid zone stories (RZS camps, burials, fishing, parties, sandlot) are placed, built and looted (2026-11-06) |

## SHARED

| Note | What it holds |
|---|---|
| [endless-night-switch](endless-night-switch.md) | Josiah 2026-11-09 07:51 STRICT: all Reach/Halo/Tarkov/zombie work moves to Endless Night repo, file store and Modrinth instance |
| [endless-night-voice-accents](endless-night-voice-accents.md) | Josiah's rule on AI voice accents for any voice samples or picks (never Indian accents) |
| [full-detail-previews-rule](full-detail-previews-rule.md) | STRICT (Josiah 2026-11-10): every preview sent to him must be rendered in full detail, never quick flat-shaded check renders |
| [josiah-preferences](josiah-preferences.md) | How Josiah likes to work on game/mod projects |
| [josiah-world-art-direction](josiah-world-art-direction.md) | Josiah's rules for how ZomboidCraft cities/buildings must look (battle-scarred, 3D depth, clutter), from his feedback |
| [minecraft-zomboid-mod](minecraft-zomboid-mod.md) | Josiah's Project-Zomboid-style survival mod for Minecraft (started 2026-10-24) — scope and status |
| [photon-shader-settings](photon-shader-settings.md) | Josiah plays with Photon shaders on; the mod's fog uses vanilla fog distances, which Photon ignores; exact Photon fog values recommended |
| [tarkov-ammo-counter-direction](tarkov-ammo-counter-direction.md) | Josiah's ammo rule (2026-11-08): no magazine items; a hidden capped ammo counter per calibre, every reload puts in a full mag |
| [tarkov-assets-pipeline-tech](tarkov-assets-pipeline-tech.md) | How Tarkov game files are pulled and decoded (UnityPy, Mecanim clips, part assembly, IK hands) for the Tarkov-into-ZomboidCraft pilot (2026-11-06) |
| [tarkov-bench-fitting](tarkov-bench-fitting.md) | Josiah's 2026-11-07 06:10 go-ahead to make ALL pulled Tarkov attachments game ready for bench fitting; how it's built, where files are staged, what's left |
| [tarkov-gear-clothing-intake](tarkov-gear-clothing-intake.md) | Thread "Bring Tarkov gear and clothing into game" (started 2026-11-07 07:32): scope proposal for all Tarkov armour/rigs/packs/helmets/clothing, awaiting Josiah's OK |
| [tarkov-grip-holds-tech](tarkov-grip-holds-tech.md) | Tarkov foregrip/handguard left-hand holds (GripPose) in previews and in game; SV-98 stock fix; Josiah's 2026-11-07 grip rule |
| [tarkov-icons-scope-tech](tarkov-icons-scope-tech.md) | Tarkov gun slot icons (TarkovGunIcon), part icon base, and the scope-lens HUD bug fixed 2026-11-08 |
| [tarkov-melee-only](tarkov-melee-only.md) | Josiah's 2026-11-07 10:37 rule that only Tarkov melee weapons exist (all Minecraft/Zomboid melee goes); Tarkov melee list and the tool-role question |
| [tarkov-operator-ingame-tech](tarkov-operator-ingame-tech.md) | How the Tarkov operator + kitted M4A1 are wired into ZomboidCraft (client/operator, gun file, sound pack, local files) 2026-11-06 |
| [tarkov-reload-sounds-tech](tarkov-reload-sounds-tech.md) | Where Tarkov keeps reload sound events (container bundle StaticData, not AnimationClip events), the 2026-11-08 audit of our 30 guns, and the MP-18 missing-round finding |
| [tarkov-sight-switch-tech](tarkov-sight-switch-tech.md) | Josiah's 2026-11-07 rule that every switchable Tarkov sight (piggyback red dots, 1x/4x modes, variable zoom) works in game, looted ones too; how it is built |
| [tarkov-suppressors-irons](tarkov-suppressors-irons.md) | Josiah's 2026-11-07 10:05 calls - suppressors are quite rare loot (found guns come without cans), every gun must have unsuppressed audio, flip-up irons fold under an optic |
| [tarkov-weapon-batch1](tarkov-weapon-batch1.md) | Josiah's first batch of Tarkov weapons to bring in game (picked 2026-11-06 20:36 from the weapons list doc) and what "game ready" means for them |
| [tarkov-weapon-batch2](tarkov-weapon-batch2.md) | Josiah's second batch of Tarkov guns (ticked 2026-11-07 07:34 in the weapons list doc), to be done exactly like batch 1 |
| [tarkov-weapon-batch3](tarkov-weapon-batch3.md) | Josiah's third batch of Tarkov guns (ticked 2026-11-07 12:23 in the weapons list doc), done like batches 1 and 2 |
| [tarkov-zomboid-scoping](tarkov-zomboid-scoping.md) | Side project (2026-11-06): first-person Project Zomboid using Tarkov guns/gear, offline, co-op with friends; scoping doc and route pick |
| [zomboid-access-rule](zomboid-access-rule.md) | RULE (Josiah 2026-10-27): every stairwell, doorway and roof must be walkable by players and zombies; roof access huts on buildings with stairs |
| [zomboid-aircraft-sounds](zomboid-aircraft-sounds.md) | Aircraft audio for flyable jets/heli (asked by the "Make jets and helicopter flyable" thread): event list, picker URL, sources, how to rebuild |
| [zomboid-approve-mockups-first](zomboid-approve-mockups-first.md) | Josiah 2026-11-02 09:30 - don't build any version into the game until he approves its mock-up/preview |
| [zomboid-arms-posing-history](zomboid-arms-posing-history.md) | Version history and Josiah's rules for the arms posing tool (v1 2026-11-02 to v40 2026-11-06), summarised |
| [zomboid-arms-posing-tool](zomboid-arms-posing-tool.md) | Josiah's web posing tool for survivor arms holding Brita guns (artifact Ur4ZP18eXgMUDALKpCEhyZ): current state, save rules, how to add guns |
| [zomboid-asset-intake](zomboid-asset-intake.md) | What 3D asset packs are already in project files (Poly Haven CC0 props), the buying-list doc, and CGTrader/free-source access notes |
| [zomboid-base-wave2](zomboid-base-wave2.md) | BASES wave 2 wood building status (2026-10-31): 2a pieces, beam rule, door, art refs; NPC Builder/Electrician companion jobs owed |
| [zomboid-basements-bunkers-direction](zomboid-basements-bunkers-direction.md) | Josiah's 2026-10-25 rare house basements template (shipped in playtest 003e25d); bunkers live in zomboid-bunkers-direction |
| [zomboid-blender-skills-ref](zomboid-blender-skills-ref.md) | What we took from github.com/arjun988/blender-skills (Josiah's modelling reference, 2026-11-04) and how it changes modelling work |
| [zomboid-blender-tutorial](zomboid-blender-tutorial.md) | Thread "Learn to add a character in Blender" (2026-10-30): teaching Josiah step by step to add Rebecca (Cyberpunk Edgerunners fan model) as a local-only companion |
| [zomboid-brita-irons-tech](zomboid-brita-irons-tech.md) | How the Brita guns' real-life iron sights are built (irons.py kit, sight line JSON, previews) and their status |
| [zomboid-brita-loader-tech](zomboid-brita-loader-tech.md) | Design for loading Brita guns locally (2026-11-01): component on our anchor gun items, local spec+zmesh in config/zomboidcraft/guns, MeshGunRenderer swaps meshes |
| [zomboid-brita-refined-list](zomboid-brita-refined-list.md) | Josiah's refined Brita in-game gun list (2026-11-06, 128 guns: 14 in game, 26 wave-2 picked, 88 new; no DShK) and the plan for the 88 new ones |
| [zomboid-brita-wave2](zomboid-brita-wave2.md) | Brita wave 2 (the other 27 picked guns): pulled 2026-11-03, review page, open calls, next steps |
| [zomboid-brita-wave3-tech](zomboid-brita-wave3-tech.md) | Brita wave 3 (the 88 new guns of the refined list): conversion pipeline, review page QcWrQW8iUpZ4sysoCx5Vhb, open calls awaiting Josiah |
| [zomboid-brita-weapons](zomboid-brita-weapons.md) | Josiah wants Brita's Weapon Pack (PZ B41 mod) guns + audio in ZomboidCraft; local-only plan, awaiting replace/add pick (2026-10-31) |
| [zomboid-building-detail-direction](zomboid-building-detail-direction.md) | Josiah's 2026-10-25 detail directives: B42-level interiors and exteriors, custom furniture everywhere, no vanilla blocks, push toward realism in a Minecraft build style |
| [zomboid-bullet-impacts](zomboid-bullet-impacts.md) | Surface-specific bullet strike sounds (Josiah 2026-10-27: old hits "cartoonish"): WEAPONS event names, sources, picker status |
| [zomboid-bunker-wave1-tech](zomboid-bunker-wave1-tech.md) | How ZomboidCraft's civil-defence shelters, forest hatches (hideout layer), marked bunker maps and /zc bunker work (Shelter.java, HatchSite.java, Bunkers.java, PaperMaps.readMarked) |
| [zomboid-bunker-wave2-tech](zomboid-bunker-wave2-tech.md) | Bunker wave 2 kinds (bank vault, ghost station, old mine adit, then grain silo, missing floor, harbour fort, container tunnel, offshore ship): where each lives and how it was built |
| [zomboid-bunkers-direction](zomboid-bunkers-direction.md) | Josiah's hidden-bunker direction for ZomboidCraft: mostly urban (skyscrapers, cargo ships, docks), rare map drops from hard places, rolled loot, approved choices |
| [zomboid-camera-tech](zomboid-camera-tech.md) | ZomboidCraft camera system - shared CameraRig/CameraMixin (flight + weapons threads) and the over-the-shoulder third person |
| [zomboid-carrier-direction](zomboid-carrier-direction.md) | Josiah's aircraft-carrier ask for ZomboidCraft (true-scale carrier at its own naval wharf, true-size F-22/F-15-style jets, military interior and zombies) |
| [zomboid-characters-direction](zomboid-characters-direction.md) | Josiah's 2026-10-25 direction for ZomboidCraft zombie and player models: Project Zomboid-style mitten hands, pronounced features, not overly realistic |
| [zomboid-chest-hole](zomboid-chest-hole.md) | Point-blank shotgun chest hole in ZomboidCraft: Josiah's ask (27 Oct 09:08) and how the see-through hole is built and checked |
| [zomboid-clothing-wave2](zomboid-clothing-wave2.md) | Clothing wave 2 (2026-10-31): ~218 more wearables + PZ clothing mechanics; answered, groundwork + camo + military batch 1 built |
| [zomboid-commands-list](zomboid-commands-list.md) | Where the ZomboidCraft command reference lives and the rule that every thread sends new commands to the COMMANDS thread |
| [zomboid-commands-thread](zomboid-commands-thread.md) | RULE - every ZomboidCraft command any thread adds goes to the COMMANDS thread with a brief description |
| [zomboid-consistent-detail-rule](zomboid-consistent-detail-rule.md) | RULE (Josiah 2026-10-29) - every part of the world gets the same intricate detail as the landmarks; no basic block-shape boats, tents, props next to detailed builds |
| [zomboid-corpses-direction](zomboid-corpses-direction.md) | Josiah's 2026-10-27 ask that ZomboidCraft zombies fall to their death per movement and leave a corpse with its shot-off parts missing (no vanilla poof) |
| [zomboid-corpses-tech](zomboid-corpses-tech.md) | How ZomboidCraft death falls, lasting bodies (Corpse entity) and hit jerks are built, plus the offline and preview checks |
| [zomboid-crowd-lod-tech](zomboid-crowd-lod-tech.md) | Far crowd (CrowdFx) cost 2026-11-08: view-cone cull shipped; level-3 crowd mesh SHIPPED (playtest 03871067a, packs installed on his PC 09:34Z), awaiting his profile |
| [zomboid-design-palette](zomboid-design-palette.md) | RULE from Josiah: every ZomboidCraft design (in-game UI and preview pages) matches the kit screen's palette and template; the tokens to use |
| [zomboid-dismember-direction](zomboid-dismember-direction.md) | Josiah's 2026-10-26 ask to shoot limbs off ZomboidCraft zombies (per-gun odds, limb comes off where hit) and the plan proposed in the characters thread |
| [zomboid-dismember-tech](zomboid-dismember-tech.md) | How ZomboidCraft limb shooting works in code (Dismember.java, LIMBS attachment, capsule hit test, Gibs, stumps, hop/stumble/fall) |
| [zomboid-endless-night-switch-loot](zomboid-endless-night-switch-loot.md) | LOOT's view of the 2026-11-09 Endless Night switch: ZOMBOIDCRAFT frozen, LOOT's systems mostly stay behind; Reach work goes to ENDLESS-NIGHT only |
| [zomboid-enemies-direction](zomboid-enemies-direction.md) | Josiah's 2026-10-27 ask for more enemy variety in ZomboidCraft (golem-style mini boss "Brute", special zombie types, dogs, raiders) and the options doc |
| [zomboid-eyes-clear-rule](zomboid-eyes-clear-rule.md) | RULE from Josiah - ZomboidCraft face items rest below the eyes and headwear just above them; only eyewear covers the eyes |
| [zomboid-factory-irons-first-rule](zomboid-factory-irons-first-rule.md) | Josiah 2026-11-06 06:07 - pull reference photos of every gun to check its factory iron sights; model those instead of our pack flip-ups |
| [zomboid-flight-direction](zomboid-flight-direction.md) | Josiah's ask to make the carrier jets and Apache-style helicopter flyable (thread "Make jets and helicopter flyable"): goals, controls plan, ownership |
| [zomboid-floor-purpose-rule](zomboid-floor-purpose-rule.md) | RULE (Josiah 2026-10-27 22:40): building floors must make sense for the building's purpose and location; variety never means random unrelated floor types |
| [zomboid-gear-attachments-direction](zomboid-gear-attachments-direction.md) | Josiah's ask for more ZomboidCraft gear attachments, incl. working radios and comms headsets that mount on the combat helmet; brainstorm status |
| [zomboid-gun-art-style](zomboid-gun-art-style.md) | RULE + tech: every ZomboidCraft gun and attachment is painted in the character (Borderlands-like) style by tools/weapons/toon.py, always like new |
| [zomboid-guns-tech](zomboid-guns-tech.md) | How ZomboidCraft guns are built (GunType, per-gun rigs, bolt guns, preview tooling) and first-person lessons like the Iris bob bypass |
| [zomboid-harvestman-direction](zomboid-harvestman-direction.md) | The Harvestman, Josiah's long-legged corpse-feeding horror creature for dark big buildings (from his two reels, 2026-10-27), and its 3D preview |
| [zomboid-harvestman-wave1-tech](zomboid-harvestman-wave1-tech.md) | Harvestman wave 1 mind games (2026-10-29) - HarvestmanTricks plans, HarvestmanNights (barricades, trophies, body return, patient nights), glimpses only the target sees |
| [zomboid-hitbox-tech](zomboid-hitbox-tech.md) | ZomboidCraft walker hitboxes: BodyCaps tables fitted to the zombie mesh, BodyTrack pose/lag rewind, tools/zombies/hitcaps refitter and offline sheet |
| [zomboid-holsters-spawn-direction](zomboid-holsters-spawn-direction.md) | Josiah's 2026-10-27 ask for worn quick slots (belt, holster, sheath, wrist) and a spawn picker before entering the world; mockup link, plan, open questions |
| [zomboid-hordes-death-base-direction](zomboid-hordes-death-base-direction.md) | Josiah's 2026-10-26 pick of three new ZomboidCraft features (roaming hordes, your body stays after death, base building); plan doc link and status |
| [zomboid-hordes-never-vanish-rule](zomboid-hordes-never-vanish-rule.md) | Josiah 2026-11-04 RULE: a horde a player met must never disappear when they leave and return; vanilla despawn is off for walkers (MobMixin) |
| [zomboid-hud-direction](zomboid-hud-direction.md) | Josiah's ZomboidCraft HUD redesign - chose "field kit that fades" (2026-10-25); rundown doc and open questions |
| [zomboid-injuries](zomboid-injuries.md) | ZomboidCraft body-part injury system (Zomboid-lite), chosen and shipped 2026-10-25; design and where it lives |
| [zomboid-interact-dot](zomboid-interact-dot.md) | ZomboidCraft green interact dot RULE and API: every usable thing gets a small green dot when close and looked at, no "press G" text |
| [zomboid-interact-menu-tech](zomboid-interact-menu-tech.md) | G menu (hold G) provider API, Zomboid action queue in TimedActions, SkillUnlocks for level-up card, DeathCard, SandboxScreen (UI thread, 2026-10-31) |
| [zomboid-inventory-ui-direction](zomboid-inventory-ui-direction.md) | Josiah's ZomboidCraft inventory redesign (field kit look, bags/weight, nearby loot side, weapon bench) and its build status |
| [zomboid-iron-sights-rule](zomboid-iron-sights-rule.md) | RULE (Josiah 2026-11-01 10:22): every future weapon gets its real-life iron sights modelled from reference photos, then the "rough up" paint/bevel pass |
| [zomboid-irons-tech](zomboid-irons-tech.md) | Flip-up iron sights placement (Josiah 2026-10-30): clear of everything, hide lists under optics, crossbow has none, mesh cleanups |
| [zomboid-keep-working-offline](zomboid-keep-working-offline.md) | Josiah 2026-11-11 08:27 (his clock): never stop at a "needs the PC" wall; keep working on everything that can be done in the cloud |
| [zomboid-kit-carry-rules](zomboid-kit-carry-rules.md) | ZomboidCraft kit rules Josiah set 2026-10-27: no weapons in pockets, long guns on the back sling, drag-drop, shift bindings, bigger bench |
| [zomboid-kit-screen-destiny](zomboid-kit-screen-destiny.md) | Josiah's Destiny 2-style kit screen redesign for ZomboidCraft (2026-10-25): mockup link, layout, open questions |
| [zomboid-landscape-direction](zomboid-landscape-direction.md) | Josiah's rules for ZomboidCraft world generation (new landscape, many variants of every place type, match the concept sheets, finite urban county) |
| [zomboid-lifts-direction](zomboid-lifts-direction.md) | Josiah's ask for working escalators and lifts in every tower over 10 stories; power choice "always run" (2026-10-27) |
| [zomboid-loot-rarity-direction](zomboid-loot-rarity-direction.md) | Josiah wants ZomboidCraft to be a believable looter shooter: huge roster in every rarity, tier 1-5 rolls on guns, attachments and gear, learned blueprints, no franchise names |
| [zomboid-mannequin-run](zomboid-mannequin-run.md) | Josiah's first solo Mannequin run (2026-10-31): .blend quirks, weight painting learned, Mixamo rig with his own weights works |
| [zomboid-match-zomboid-rule](zomboid-match-zomboid-rule.md) | RULE (Josiah 2026-10-31): run Project Zomboid's own version of every feature unless he already confirmed a change |
| [zomboid-mesh-attach-tech](zomboid-mesh-attach-tech.md) | How bought guns are laid out from their own meshes (guns.json, attachments.json, skin.py swap, kit.mesh_layout) and the attachment game side (Optic table, magnifier slot, reticles, bipod) |
| [zomboid-mesh-attachments-rule](zomboid-mesh-attachments-rule.md) | RULE (Josiah 2026-10-29 09:17) no old block parts on bought-mesh guns; attachments come only from the guns' own pieces plus the 10-piece attachment pack, swappable |
| [zomboid-mesh-guns-tech](zomboid-mesh-guns-tech.md) | How bought weapon meshes go in game (pack.py .zmesh, zomboidcraft:mesh special item model, skin.py/skins.json, outlines, melee/bolts) |
| [zomboid-mod-build-notes](zomboid-mod-build-notes.md) | How ZomboidCraft is built through GitHub CI from this cloud env, plus Minecraft 26.3 API name gotchas |
| [zomboid-mod-status](zomboid-mod-status.md) | ZomboidCraft current status and priorities (latest release, what's paused, what's next) |
| [zomboid-movement-v2](zomboid-movement-v2.md) | Josiah's 2026-10-26 zombie movement brief (idle sway, whole-body shamble, alert head turn, arms-straight chase, attack, crawl, legless variants) and where the preview lives |
| [zomboid-nature-packs-tech](zomboid-nature-packs-tech.md) | How Josiah's bought tree and grass packs become ZomboidCraft blocks (trees.py, groundcover.py, Cover.java), where the pack originals live, preview tricks |
| [zomboid-nature-settings](zomboid-nature-settings.md) | Zomboid sandbox Nature page settings (ZcConfig.Nature, Climate.java): what WEATHER built, who owns the rest |
| [zomboid-nightvision-tech](zomboid-nightvision-tech.md) | ZomboidCraft night vision tech: headgear item models, Mounted/battery components, N flip, post-effect tube view, params texture, config, loot |
| [zomboid-no-self-resolve-rule](zomboid-no-self-resolve-rule.md) | RULE (Josiah 2026-10-29) - no thread resolves itself unless Josiah authorises it |
| [zomboid-no-self-resolve](zomboid-no-self-resolve.md) | RULE from Josiah 2026-10-29: threads never resolve themselves unless he authorises it |
| [zomboid-no-vanilla-rule](zomboid-no-vanilla-rule.md) | RULE (Josiah 2026-11-02 06:33): no vanilla Minecraft items or chests in ZomboidCraft, ever, anywhere |
| [zomboid-no-wikimedia-rule](zomboid-no-wikimedia-rule.md) | RULE (Josiah 2026-10-31, clarified 04:52) photo refs are fetched only through his PC, never from the cloud container |
| [zomboid-npc-models](zomboid-npc-models.md) | Josiah's bought 3D character models for spawnable NPCs (dark elf TO BE REMOVED per Josiah 2026-11-06, Master Chief declined), raw-vs-toon preview and renderer |
| [zomboid-npc-voice-tech](zomboid-npc-voice-tech.md) | Companion voices (2026-10-31): lines.txt moments, fish.audio voice.py, clips from config folder, jaw by loudness, face_dump.py, /zc npc lines/say |
| [zomboid-old-guns-removed](zomboid-old-guns-removed.md) | Josiah 2026-11-02 07:16: remove ALL our pre-Brita guns, their audio and our attachments; keep only the Intervention-style Heavy Sniper and everything Brita |
| [zomboid-open-plan-rule](zomboid-open-plan-rule.md) | Josiah wants big open floorplans in towers, not corridor mazes; seats must never face a wall; built 2026-10-29 as Floorplan.airy |
| [zomboid-opening-guns-tech](zomboid-opening-guns-tech.md) | How ZomboidCraft's hand-loaded guns work (double-barrel, revolver cylinder, finishes) and their first-person lessons |
| [zomboid-overnight-mandate](zomboid-overnight-mandate.md) | Josiah's standing permission (2026-10-26 14:35) for the LANDSCAPE thread to keep building every aspect of the world, area after area, without asking |
| [zomboid-pc-storage-rule](zomboid-pc-storage-rule.md) | Josiah 2026-11-08 00:09 STRICT: never save or back up anything to OneDrive folders on his PC; use C:\Dev\Zomboidcraft-Desktop\additions instead |
| [zomboid-performance-tech](zomboid-performance-tech.md) | ZomboidCraft horde performance work (ZDetail zombie LOD budget, scope-aware detail, Iris shadow LOD, AI throttles, config performance.*) and what was measured |
| [zomboid-photo-reference-rule](zomboid-photo-reference-rule.md) | RULE - every ZomboidCraft build (houses, churches, factories, bunkers, offices, skyscrapers, ships) starts from real photo references, at maximum detail |
| [zomboid-photon-lpv-haze](zomboid-photon-lpv-haze.md) | Josiah's constant haze 2026-11-06..08: SOLVED, Rainmeter Glass skin blur over the windowed game (F2 clean, F11 fixes); not mod, Photon or DH |
| [zomboid-playtest-build](zomboid-playtest-build.md) | The one-jar rule for ZomboidCraft test builds - shared playtest branch and its fixed download link |
| [zomboid-population-direction](zomboid-population-direction.md) | Josiah's 2026-10-27 rules for ZomboidCraft's zombie population (Zomboid-style density by place, no respawn by default, one shared count, towns full at world gen) |
| [zomboid-population-tech](zomboid-population-tech.md) | ZomboidCraft world population engine (zombie/population: Population chunk records, Census floor-space count, Spots, Budgets) and the shared door/window blows |
| [zomboid-profiler](zomboid-profiler.md) | /zc profile sampling profiler + GPU timers, and what each FPS profile of Josiah's city showed (root causes, fixes) |
| [zomboid-purge-direction](zomboid-purge-direction.md) | CONFIRMED re-steer (Josiah 2026-11-08): "purge the world", Halo Spartans/Tarkov operators vs Covenant-led Zomboid hordes; his answers to P1-P21 |
| [zomboid-pz-car-audio-tech](zomboid-pz-car-audio-tech.md) | How converted PZ cars get Zomboid's own car audio (FMOD bank extraction, local pack, per-model sound sets); state 2026-11-01 |
| [zomboid-pz-install-extract](zomboid-pz-install-extract.md) | Plan for pulling base-game assets from Josiah's local Project Zomboid install (Steam 108600), local-only (2026-10-31) |
| [zomboid-pz-review](zomboid-pz-review.md) | Front-to-back Project Zomboid review doc (2026-10-31): gaps, verdicts, owners, base/vehicle skill ladders, open calls |
| [zomboid-pz-zombie-sounds-tech](zomboid-pz-zombie-sounds-tech.md) | Zomboid zombie audio pack (FSB5 extraction, ZedSounds voices) and the 2026-11-01 shoot-freeze fix (ZSprites removed) |
| [zomboid-pz-zombies-history](zomboid-pz-zombies-history.md) | PZ zombie bodies 2026-11-01 morning: pack install md5s, roughing-up fx, stumps, LOD cap, fx pack parts on his PC |
| [zomboid-pz-zombies-tech](zomboid-pz-zombies-tech.md) | PZ zombie bodies thread (2026-11-01): Zomboid bodies/outfits/clips on zombies, red eyes, Project Real Limbs port; data locations, branch, decisions |
| [zomboid-rail-lights-tech](zomboid-rail-lights-tech.md) | ZomboidCraft gun laser and torch (RailLights, Photon rules), full-auto fire mode, hip-aim solver, gunlook in-game screenshot branch |
| [zomboid-reach-backdrops-direction](zomboid-reach-backdrops-direction.md) | Josiah 2026-11-11 11:57 (his clock): unreachable distance should use the Reach missions' exact painted backdrops (cinematic far ranges) so the whole world has visible continuance, not bland procedural scenery |
| [zomboid-reach-places-direction](zomboid-reach-places-direction.md) | Reach world place decisions: New Alexandria/Exodus landmark towers + ground floors, Lone Wolf by the Pillar of Autumn, PoA walkable |
| [zomboid-reach-realism-direction](zomboid-reach-realism-direction.md) | Josiah 2026-11-09 10:52-10:55: after the playtest fixes, top priority is Reach realism, "indistinguishable detail" from the real game |
| [zomboid-reach-sky-direction](zomboid-reach-sky-direction.md) | Reach world sky/atmosphere (Josiah 2026-11-09): missions' ships in orbit, fog, light, battle effects in the shared world; each region keeps its mission's sky (chosen 04:06) |
| [zomboid-reach-terrain-rule](zomboid-reach-terrain-rule.md) | Reach world RULE (Josiah 2026-11-08 23:31): never flatten levels; keep every cliff, canyon, height change and building level; every drop needs a way back out |
| [zomboid-reach-towers-direction](zomboid-reach-towers-direction.md) | Josiah's 2026-11-10 decisions on New Alexandria tower floors (whole real floors) and mission scenery (every object), and review cadence |
| [zomboid-reach-whole-world-quality-rule](zomboid-reach-whole-world-quality-rule.md) | STRICT (Josiah 2026-11-11 09:49 his clock): every visible, walkable or distant piece of Reach-world land gets real Reach terrain/textures/detail; no white patches, no missing sections |
| [zomboid-reach-world-direction](zomboid-reach-world-direction.md) | Halo Reach world direction (2026-11-08): brand new separate 16 km open world, county world kept; New Alexandria city; endless rain; no digging; corvette |
| [zomboid-refs-collection](zomboid-refs-collection.md) | How the ZomboidCraft reference-photo library under refs/ is collected (Wikimedia script, rate limits, what is filled) |
| [zomboid-refs-endless-night-switch](zomboid-refs-endless-night-switch.md) | REFERENCES storage rule after the 2026-11-09 Endless Night switch (where new Reach-world photo sets go) |
| [zomboid-reload-rules](zomboid-reload-rules.md) | Josiah's rules for every reload animation (2026-11-04): exact start/end, gun may move mid-reload, no finger clipping, flat slaps, full FP arm, right-hand handles |
| [zomboid-reload-wave2](zomboid-reload-wave2.md) | Wave-2 reloads (26 local guns) ported from Josiah's own edited reloads; pipeline files, reach finding, per-gun choices (2026-11-04) |
| [zomboid-reload-wave3](zomboid-reload-wave3.md) | Wave-3 Brita reloads (88 guns) ported from Josiah's 14 edited templates: status, scripts, families, what is left (2026-11-06) |
| [zomboid-reloads-ingame-tech](zomboid-reloads-ingame-tech.md) | How Josiah's edited reloads play in the game (baked clip files, ReloadClips.java), plus the 2026-11-04 local-gun fixes, attachments install and DShK removal |
| [zomboid-reloads-tech](zomboid-reloads-tech.md) | Hand-made reload animations for the 14 local Brita guns (first + third person): tooling, approach, preview location, status |
| [zomboid-render-distance-limit](zomboid-render-distance-limit.md) | Josiah's RTX 4070 (12 GB) with Photon + DH 128: render distance 12 left the card on the edge; set to 8 on 2026-11-04 (his pick) |
| [zomboid-scope-zoom-direction](zomboid-scope-zoom-direction.md) | Josiah's aim-zoom rule (2026-11-02): unmagnified sights get no zoom; magnified scopes want picture-in-picture lens zoom with mild outside blur |
| [zomboid-server-capacity](zomboid-server-capacity.md) | Server capacity plan 2026-11-08 (Josiah asked if a MC server can handle the purge plan): horde savings, CI horde bench numbers, mods to add, hosting options awaiting his pick |
| [zomboid-shion-declined](zomboid-shion-declined.md) | Shion (Overwatch-ripped character pack): local-only, CLOTHED looks only; preview artifact, Mixamo T-pose, pipeline in scratch bpy (2026-10-30) |
| [zomboid-skin-style-reference](zomboid-skin-style-reference.md) | Art-AI body skin workflow: style reference renders of the 3D kits + stylised prompt in project files; first AI skin was too realistic |
| [zomboid-sound-design](zomboid-sound-design.md) | ZomboidCraft sound thread: ownership, pipeline, branch/merge rule, current state and queue |
| [zomboid-sound-history-2](zomboid-sound-history-2.md) | ZomboidCraft sound rounds 15+ (after R14 Gamemaster): new-guns picker, music picker, beds rotation, reload loudness |
| [zomboid-sound-history](zomboid-sound-history.md) | ZomboidCraft sound rounds 1-12: picker artifact URLs and what Josiah picked or rejected each round |
| [zomboid-spawn-select-direction](zomboid-spawn-select-direction.md) | Josiah wants a Zomboid-style chosen spawn: pick a town or city, start inside a zombie-free house or building there (not started) |
| [zomboid-spider7-glow-hits](zomboid-spider7-glow-hits.md) | Spider 7 Harvestman round 3 (2026-10-29): blue/red glow rule, rage skull flare, server-posed bone hitbox, cuttable arms/scythes, idle fixes |
| [zomboid-spider7-model](zomboid-spider7-model.md) | Josiah's royalty-free "Spider 7" creature model (rigged, 30 pre-made animations, 5 skins) and its web preview, 2026-10-29 |
| [zomboid-subway-tube-direction](zomboid-subway-tube-direction.md) | Josiah's ask (2026-10-27 18:41) for a see-through underwater subway tube with an in-world reason; pitch and open decision |
| [zomboid-survivor-clothes-tech](zomboid-survivor-clothes-tech.md) | How the PZ survivor bodies wear Zomboid's own clothes (packs, clothes_map.json, SurvivorClothes), and what's pending (2026-11-01) |
| [zomboid-survivor-fp-tech](zomboid-survivor-fp-tech.md) | First-person survivor arms on guns - facts that broke runs 1-7 (hand frame offset, gun sizes) and the cloud preview harness |
| [zomboid-survivor-lead-rule](zomboid-survivor-lead-rule.md) | RULE (Josiah 2026-11-01 08:06): the Minecraft player model must never drive the Zomboid survivor body; plus render-order facts for survivors on 26.3 |
| [zomboid-tarkov-gun-loot](zomboid-tarkov-gun-loot.md) | LOOT's job (Josiah 2026-11-07 10:20): every Tarkov gun into world loot via Arsenal-twin places in the local places.json; proposed mapping awaiting OK |
| [zomboid-texture-library](zomboid-texture-library.md) | Where ZomboidCraft's CC0 PBR texture library lives, how it is organized, and how to add more |
| [zomboid-thread-resolve-rule](zomboid-thread-resolve-rule.md) | RULE (Josiah 2026-10-29) - no thread resolves itself unless he authorises it |
| [zomboid-throwables-direction](zomboid-throwables-direction.md) | ZomboidCraft throwables (Josiah picked them as next weapons work 2026-10-27 22:17): plan doc, roster, open questions, reference photo request |
| [zomboid-trains-direction](zomboid-trains-direction.md) | Josiah's decisions on driverless walk-in trains, call buttons with flare/siren, robot conductor, and the island downtown with bridges (2026-10-27); ideas doc link and build order |
| [zomboid-ui-pages-tech](zomboid-ui-pages-tech.md) | Kit screen Clothing page and skill books (SkillBooks read action), built by the UI thread 2026-10-29 |
| [zomboid-ui-pz-pieces](zomboid-ui-pz-pieces.md) | UI thread's share of the PZ review (G menu every option, action queue, car parts view, level-up card, death card, Sandbox page); mock-ups artifact and status |
| [zomboid-ui-systems-tech](zomboid-ui-systems-tech.md) | ZomboidCraft timed actions, health/skills/map pages, noise+sight meter, quick-use wheel: where the code lives and rules |
| [zomboid-video-preview-rule](zomboid-video-preview-rule.md) | RULE (Josiah 2026-10-30 13:21): show a video preview of a change before merging/confirming the build |
| [zomboid-vram-tech](zomboid-vram-tech.md) | 2026-11-02 graphics-card-memory root cause of Josiah's freezes (atlas sizes, Stitcher mip padding, DH, zombie texture churn) and fixes |
| [zomboid-warthog-gunner-tech](zomboid-warthog-gunner-tech.md) | Warthog minigun gunner details: eye/grips, hands, zoom, spread ring, fire rhythm matched to the recording, muzzle flash, standing pose |
| [zomboid-warthog-tech](zomboid-warthog-tech.md) | Warthog tech: bake pipeline (warthog_bake.py in bpy, warthog.py packer), .zcar v4 turret parts, Turret.java minigun (gunner seat, belt from 5.56, spin-up), military kind |
| [zomboid-wave1-guns](zomboid-wave1-guns.md) | Weapons wave one (Josiah's pick 2026-10-26): six new guns, their modules, preview page, planned rarity and numbers, in-game wiring status |
| [zomboid-wave1-tech](zomboid-wave1-tech.md) | How the wave-one lever-action, crossbow, .22/SMG suppressors and revolver hammer cock are built |
| [zomboid-wave2-guns](zomboid-wave2-guns.md) | Weapons wave two (Josiah picked it 2026-10-29 04:17): proposed 8-gun list awaiting his approval |
| [zomboid-weapons-direction](zomboid-weapons-direction.md) | Josiah's direction for ZomboidCraft weapons and combat (branch combat, started 2026-10-25) |
| [zomboid-weather-direction](zomboid-weather-direction.md) | Josiah's weather/atmosphere direction for ZomboidCraft: "endless night" days, thin see-through rain; WEATHER/ATMOSPHERE thread |
| [zomboid-window-climb](zomboid-window-climb.md) | Zombies breaking in: door/window banging and climb-through-window animations, Acts sync, WindowClimbGoal, WayInGoal, the split with the performance thread |
| [zomboid-woman-model](zomboid-woman-model.md) | "Woman" character from Josiah's additions folder (replaces the dark elf companion): rig, pieces, preview page, 2026-10-29 |
| [zomboid-world-events](zomboid-world-events.md) | Shared world events (siren, explosions, jet/heli flybys) everyone hears from where they stand: design, files, Josiah's asks |
| [zomboid-world-loot-direction](zomboid-world-loot-direction.md) | Josiah's 2026-10-27 ask for the LOOT thread: replace every vanilla item with custom ones, vast roster with rarities and values, loot on theme with the building/area |
| [zomboid-world-systems-tarkov](zomboid-world-systems-tarkov.md) | SUPERSEDED 2026-11-08 by zomboid-purge-direction; old W1-W26 world-systems calls (marines premise), few still stand |
| [zomboid-zbody-tech](zomboid-zbody-tech.md) | How the new ZomboidCraft zombie bodies are drawn in game (ZBodyMesh/ZBodyLayer/ZMove/ZBodyAnimator, preview export pipeline, legless, /zc zombie) |
| [zomboid-zombie-behaviour-plan](zomboid-zombie-behaviour-plan.md) | ZOMBIE BEHAVIOUR thread (2026-10-31): all 50 Zomboid zombie sandbox settings + migration/rallies/herds, fake dead, outfits by place, search helicopter; plan doc and status |
| [zomboid-zombie-spawning-tech](zomboid-zombie-spawning-tech.md) | Where ZomboidCraft zombies spawn from and the roof/tree perch rule (ZombieSpawner.perched, PathfinderMobMixin) for anyone touching spawns |

## BACKGROUND

| Note | Why background | What it holds |
|---|---|---|
| [endless-night-cortana-body-models](endless-night-cortana-body-models.md) | moved to the Endless Night: Minecraft project | Cortana voice-to-body model comparison (EMAGE vs GestureLSM vs Meta audio2photoreal), runners and his verdict |
| [endless-night-cortana-expression](endless-night-cortana-expression.md) | moved to the Endless Night: Minecraft project | Cortana's voice emotion work (2026-11-10): Josiah chose "directed Heart now + expressive-model samples"; how the delivery marks work |
| [endless-night-cortana-ingame](endless-night-cortana-ingame.md) | moved to the Endless Night: Minecraft project | Cortana helper in-game build for Endless Night: role picked (voice in helmet, hologram on call), branch, vendored NpcVoice, file locations |
| [endless-night-cortana-lipsync](endless-night-cortana-lipsync.md) | moved to the Endless Night: Minecraft project | Cortana talking-samples rounds 4-11 (2026-11-10): visemes, H4 poses, NVIDIA Audio2Face lip-sync and his verdicts |
| [endless-night-cortana-motion](endless-night-cortana-motion.md) | moved to the Endless Night: Minecraft project | Cortana body/gesture work 2026-11-10: body directions, MoMask, keyframe engine, BEAT2 mocap + EMAGE speech-synced samples |
| [endless-night-cortana-wholebody](endless-night-cortana-wholebody.md) | moved to the Endless Night: Minecraft project | Cortana rounds 20-21 (2026-11-11): full TWH fingers + shoulders published v21; round 21 purposeful head/spine/breath/hips/knees layers |
| [endless-night-cortana-zeggs](endless-night-cortana-zeggs.md) | moved to the Endless Night: Minecraft project | Cortana round 17 (2026-11-11): Ubisoft ZeroEGGS voice-to-gesture (woman's mocap, full fingers) replaces GestureLSM; how it runs |
| [endless-night-covenant-enemies](endless-night-covenant-enemies.md) | moved to the Endless Night: Minecraft project | Thread "Bring Covenant enemies into Endless Night" (started 2026-11-09 20:04): options C1-C6 sent, status and answers |
| [endless-night-covenant-vehicles-tech](endless-night-covenant-vehicles-tech.md) | moved to the Endless Night: Minecraft project | How Covenant vehicle packs, Reach overlays, crews/seats and Phantom troop drops are built and rendered (tools + Java classes) |
| [endless-night-forge-stations](endless-night-forge-stations.md) | moved to the Endless Night: Minecraft project | Forge Armory stations (consoles, holotable with Cortana, stocked Halo + Tarkov racks) for Endless Night bases, 2026-11-10 state and file locations |
| [endless-night-gunsmith](endless-night-gunsmith.md) | moved to the Endless Night: Minecraft project | Operator weapon customisation (Gunsmith) in Armory/New Soldier, replaces /localgun; Josiah's CoD reference and open questions (2026-11-09) |
| [endless-night-infected-idea](endless-night-infected-idea.md) | moved to the Endless Night: Minecraft project | Josiah 2026-11-11 asked to add Reach Infection "zombies" as world enemies; what the files hold and the proposal awaiting his answer |
| [endless-night-loadout-command](endless-night-loadout-command.md) | moved to the Endless Night: Minecraft project | Josiah 2026-11-09 10:31 asked for a command to select weapons for Spartans and Tarkov operators; proposed /en loadout design, awaiting his scope pick |
| [endless-night-mcc-menus](endless-night-mcc-menus.md) | moved to the Endless Night: Minecraft project | Josiah 2026-11-10: exact MCC Reach customisation + MCC main menu layout, Bink background videos, Wwise music and UI sounds; where they are |
| [endless-night-new-repo](endless-night-new-repo.md) | moved to the Endless Night: Minecraft project | Josiah 2026-11-09 wants Endless Night in its own new GitHub repo + own Modrinth instance, fully separate from the ZomboidCraft county world; plan doc and status |
| [endless-night-no-minecraft-effects](endless-night-no-minecraft-effects.md) | moved to the Endless Night: Minecraft project | Josiah 2026-11-11 11:49 RULE: remove every Minecraft effect (melee sounds, red hit flash, damage tilt, all MC art/anims); everything must be Reach. Plan doc + picks M1-M5 |
| [endless-night-reach-weapons-ingame](endless-night-reach-weapons-ingame.md) | moved to the Endless Night: Minecraft project | Reach weapons playable in Endless Night: FP zsk packer, files already on Josiah's PC, sounds pulled, SpartanView plan (2026-11-09) |
| [endless-night-spartan-body](endless-night-spartan-body.md) | moved to the Endless Night: Minecraft project | Animated in-game Spartan player body (third person), approved 2026-11-11, owned by the Reach weapons thread; bots thread reuses it |
| [endless-night-unreal-move-scoping](endless-night-unreal-move-scoping.md) | moved to the Endless Night: Minecraft project | 2026-11-11 decision doc scoping a move of Endless Night from Minecraft Fabric to Unreal 5 (vs MCC mod tools); awaiting Josiah's decision |
| [reach-bots-custom-games](reach-bots-custom-games.md) | moved to the Endless Night: Minecraft project | Josiah 2026-11-11 10:10: Reach MP custom games (Slayer, SWAT, Infection) with configurable bots; thread "Add bots to Reach custom games"; building in Endless Night |
| [tarkov-covenant-damage-plan](tarkov-covenant-damage-plan.md) | moved to the Endless Night: Minecraft project | Plan doc (2026-11-09) for how Tarkov weapons damage Covenant enemies; all D1-D8 answered; rounds-to-kill table in doc s6 (2026-11-09) |
| [zomboid-armory-ingame-tech](zomboid-armory-ingame-tech.md) | moved to the Endless Night: Minecraft project | In-game Armory build: spartan.zsk packer, colour masks, pose weapon picks sheet (2026-11-09) |
| [zomboid-character-screens](zomboid-character-screens.md) | moved to the Endless Night: Minecraft project | Endless Night character selection mock-ups (Reach style), how the Noble figures are posed, and HUD-in-game install status |
| [zomboid-cortana-hologram](zomboid-cortana-hologram.md) | moved to the Endless Night: Minecraft project | Halo 4 Cortana hologram shader look, Josiah feedback and latest preview version (v7, 2026-11-08) |
| [zomboid-endless-night-hud](zomboid-endless-night-hud.md) | moved to the Endless Night: Minecraft project | UI thread's Halo/Tarkov HUD + screens proposal for the purge re-steer (2026-11-08); mock + doc with picks H1-H7, awaiting Josiah |
| [zomboid-halo-maps](zomboid-halo-maps.md) | moved to the Endless Night: Minecraft project | Halo MCC maps as LOCAL-ONLY world segments (Josiah 2026-11-08 06:55): where his MCC install is, file formats, Reach focus |
| [zomboid-halo-reach-anim-format](zomboid-halo-reach-anim-format.md) | moved to the Endless Night: Minecraft project | Halo Reach jmad (animation) format 2026-11-08: layout, curve codec 9 rotation + translation keys SOLVED; tools in zomboid-mod/halo/tools |
| [zomboid-halo-reach-format](zomboid-halo-reach-format.md) | moved to the Endless Night: Minecraft project | Halo Reach MCC .map cache format as decoded by our cloud reader (offsets for tags, names, resource pages, BSP collision, instances) |
| [zomboid-halo-swordbase](zomboid-halo-swordbase.md) | moved to the Endless Night: Minecraft project | Reach vehicles delivery + Sword Base level geometry (Lbsp) export, props, preview tooling, and status (2026-11-08) |
| [zomboid-halo-voices](zomboid-halo-voices.md) | moved to the Endless Night: Minecraft project | Where Halo Reach voice audio lives on Josiah's PC (english.fsb) and what each Noble member has; no voice cloning |
| [zomboid-halo-weapons](zomboid-halo-weapons.md) | moved to the Endless Night: Minecraft project | Halo Reach weapons pull status (models, FP sets, missing campaign-only pieces, weapon sounds from sfx.fsb) as of 2026-11-08 |
| [zomboid-halo4-format](zomboid-halo4-format.md) | moved to the Endless Night: Minecraft project | Halo 4 MCC .map cache format as decoded 2026-11-08 (offsets differ from Reach); tools zomboid-mod/halo/tools/h4 |
| [zomboid-main-menu](zomboid-main-menu.md) | moved to the Endless Night: Minecraft project | Endless Night main menu (Reach lobby layout, replaces the MC title screen): approved v3b layout, toggle, landmark slideshow, code + status |
| [zomboid-reach-armory-tech](zomboid-reach-armory-tech.md) | moved to the Endless Night: Minecraft project | Where Reach's armour customisation data lives (pmcg tag in 20_sword_slayer.map), how to render outfits, Haunted helmet, display names |
| [zomboid-reach-copies-tech](zomboid-reach-copies-tech.md) | moved to the Endless Night: Minecraft project | Reach world "copies" feature (cut-out level parts drawn at many places) and the New Alexandria tower fill pipeline (2026-11-10) |
| [zomboid-reach-grand-designs](zomboid-reach-grand-designs.md) | moved to the Endless Night: Minecraft project | My original Reach-style grand designs for the land between missions (13 approved 20:45, placed in Update 15) |
| [zomboid-reach-hud-pull](zomboid-reach-hud-pull.md) | moved to the Endless Night: Minecraft project | Real Halo Reach HUD pulled from MCC files 2026-11-08 (chdt/chgd decoded, v2 pack calibrated to a real MCC screenshot, per-weapon reticles/scopes/zoom); layout rules |
| [zomboid-reach-ingame-gap](zomboid-reach-ingame-gap.md) | moved to the Endless Night: Minecraft project | 2026-11-10 evening: Josiah found the in-game Reach world looks nothing like the previews; causes found with the debug view, fixes, PC-use OK |
| [zomboid-reach-land-tech](zomboid-reach-land-tech.md) | moved to the Endless Night: Minecraft project | Reach world previews 3-5 tooling and the land-between-missions heightmap pass (2026-11-09): scripts, shifts, sea level, next Java steps |
| [zomboid-reach-land-terrain-tech](zomboid-reach-land-terrain-tech.md) | moved to the Endless Night: Minecraft project | How our generated Reach land + horizon wear real Reach terrain (in playtest 2026-11-11, commits cd41b7fcb..dfb9767c0) and the matching cloud preview tools |
| [zomboid-reach-layout-question](zomboid-reach-layout-question.md) | moved to the Endless Night: Minecraft project | 2026-11-11 21:12 his clock: Josiah chose to PACK the Reach missions together so their real Halo land meets (world shrinks, our land only seams) |
| [zomboid-reach-mesh-fix-hold](zomboid-reach-mesh-fix-hold.md) | moved to the Endless Night: Minecraft project | Reach world "nothing here" texture-mip fix + baked terrain hookup, pushed to ENDLESS-NIGHT playtest 2026-11-09 09:40 after Josiah's "push" |
| [zomboid-reach-mp-ingame](zomboid-reach-mp-ingame.md) | moved to the Endless Night: Minecraft project | Bringing Reach MP maps into the in-game world (2026-11-11): which ones already exist as campaign geometry, which need placing, blockers; plus fog/water jar of the same session |
| [zomboid-reach-mp-placement](zomboid-reach-mp-placement.md) | moved to the Endless Night: Minecraft project | Reach MP/Firefight maps worked into mission areas (rules 2026-11-09 20:03-20:05), Update 14 placements, Covenant spawn-site contract |
| [zomboid-reach-packed-build-tech](zomboid-reach-packed-build-tech.md) | moved to the Endless Night: Minecraft project | How the approved tight packed Reach world (2026-11-11) is built: tools, outputs, md5s, what moved and what still has to follow |
| [zomboid-reach-pipeline](zomboid-reach-pipeline.md) | moved to the Endless Night: Minecraft project | How Reach maps are pulled from Josiah's PC, converted in the cloud and shipped back; where outputs live; storage limits |
| [zomboid-reach-priority-order](zomboid-reach-priority-order.md) | moved to the Endless Night: Minecraft project | Josiah's priority order for Endless Night Reach world (2026-11-10 03:14): campaign areas 100% first, then all MP maps, then in-between land/grand designs |
| [zomboid-reach-real-surroundings](zomboid-reach-real-surroundings.md) | moved to the Endless Night: Minecraft project | Bringing each mission's real Halo far land (_000) back round it in the Reach world; WC (m10) valley built 2026-11-11, install steps |
| [zomboid-reach-shader-tech](zomboid-reach-shader-tech.md) | moved to the Endless Night: Minecraft project | Endless Night in-game Halo level shader (ReachShader) - how it works, what data it needs, status 2026-11-09 |
| [zomboid-reach-world-tech](zomboid-reach-world-tech.md) | moved to the Endless Night: Minecraft project | In-game Reach world build (started 2026-11-08 23:35): world type, invisible-wall voxel format, CI test, probe tricks, build-log artifact |
