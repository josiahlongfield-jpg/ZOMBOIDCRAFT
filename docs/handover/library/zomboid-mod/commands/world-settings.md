# World settings: every Zomboid sandbox setting and ours

Zomboid B42.21 has 269 settings in 12 groups (list: pz-sandbox-options.md, from PinDrop, 31 Oct). All of them are on our Sandbox page. Today 10 of Zomboid's already drive the game, 259 are **coming** (kept in the world at Zomboid's Apocalypse value until their system is built), plus 29 settings only we have and 6 of your fixed rules, shown but not changeable.

**No presets**: you dropped them on 31 Oct, since they'd need the world redesigned. Every world starts at the defaults below, changed on the create-world page or later by the host.

**Our default** is Zomboid's Apocalypse value unless you confirmed a change or the owning thread already built ours. † = Zomboid's internal name needs a case check.

## Zombie Population

| Setting | Zomboid id | Zomboid (Apocalypse) | Ours | Status | Owner |
|---|---|---|---|---|---|
| Zombie Count | Zombies | normal | normal | Coming | ENTITY |
| Zombie Distribution † | Distribution | urban_focused | urban_focused | Coming | ENTITY |
| Voronoi Noise | ZombieVoronoiNoise | true | true | Coming | ENTITY |
| Zombie Respawn | ZombieRespawn | none | none | Coming | ENTITY |
| Zombie Migration | ZombieMigrate | true | true | Coming | ENTITY |
| Zombie Attraction Multiplier | ZombieAttractionMultiplier | 1 | 1 | Coming | ENTITY |
| Population Multiplier | ZombieConfig.PopulationMultiplier | 0.65 | 1.0 | Working (`population.density`) | ENTITY |
| Population Start Multiplier | ZombieConfig.PopulationStartMultiplier | 1 | 1 | Coming | ENTITY |
| Population Peak Multiplier | ZombieConfig.PopulationPeakMultiplier | 1.5 | 1.5 | Coming | ENTITY |
| Population Peak Day | ZombieConfig.PopulationPeakDay | 28 | 28 | Coming | ENTITY |
| Respawn Hours | ZombieConfig.RespawnHours | 0 | 0 | Coming | ENTITY |
| Respawn Unseen Hours | ZombieConfig.RespawnUnseenHours | 0 | 0 | Coming | ENTITY |
| Respawn Multiplier | ZombieConfig.RespawnMultiplier | 0 | 0 | Coming | ENTITY |
| Redistribute Hours | ZombieConfig.RedistributeHours | 12 | 12 | Coming | ENTITY |
| Follow Sound Distance | ZombieConfig.FollowSoundDistance | 100 | 100 | Coming | ENTITY |
| Rally Group Size | ZombieConfig.RallyGroupSize | 20 | 20 | Coming | ENTITY |
| Rally Group Size Variance | ZombieConfig.RallyGroupSizeVariance | 50 | 50 | Coming | ENTITY |
| Rally Travel Distance | ZombieConfig.RallyTravelDistance | 20 | 20 | Coming | ENTITY |
| Rally Group Separation | ZombieConfig.RallyGroupSeparation | 15 | 15 | Coming | ENTITY |
| Rally Group Radius | ZombieConfig.RallyGroupRadius | 3 | 3 | Coming | ENTITY |
| Zombie count before deletion | ZombieConfig.ZombiesCountBeforeDelete | 300 | 300 | Coming | ENTITY |
| Empty Houses | zc.emptyHouses | not in Zomboid | 0.5 | Working, ours (`population.emptyHouses`) | ENTITY |
| Gathering Places | zc.hotspots | not in Zomboid | true | Working, ours (`population.hotspots`) | PERFORMANCE |

## Zombie Lore

| Setting | Zomboid id | Zomboid (Apocalypse) | Ours | Status | Owner |
|---|---|---|---|---|---|
| Speed † | ZombieLore.Speed | random | random | Coming | ENTITY |
| Random Sprinter Amount | ZombieLore.SprinterPercentage | 0 | 0 | Coming | ENTITY |
| Strength † | ZombieLore.Strength | normal | normal | Coming | ENTITY |
| Toughness † | ZombieLore.Toughness | random | random | Coming | ENTITY |
| Transmission † | ZombieLore.Transmission | blood_and_saliva | blood_and_saliva | Coming | ENTITY |
| Infection Mortality † | ZombieLore.Mortality | 2_3_days | 2_3_days | Coming | ENTITY |
| Reanimate Time † | ZombieLore.Reanimate | 0_1_minutes | 0_1_minutes | Coming | ENTITY |
| Cognition † | ZombieLore.Cognition | basic_navigation | basic_navigation | Coming | ENTITY |
| Random Door Opening Amount | ZombieLore.DoorOpeningPercentage | 0 | 0 | Coming | ENTITY |
| Crawl Under Vehicle | ZombieLore.CrawlUnderVehicle | often | often | Coming | ENTITY |
| Memory † | ZombieLore.Memory | normal | normal | Coming | ENTITY |
| Sight † | ZombieLore.Sight | random_between_normal_and_poor | random_between_normal_and_poor | Coming | ENTITY |
| Hearing † | ZombieLore.Hearing | random_between_normal_and_poor | random_between_normal_and_poor | Coming | ENTITY |
| New Stealth System | ZombieLore.SpottedLogic | true | true | Coming | ENTITY |
| Environmental Attacks | ZombieLore.ThumpNoChasing | false | false | Coming | ENTITY |
| Damage Construction | ZombieLore.ThumpOnConstruction | true | true | Coming | ENTITY |
| Day/Night Zombie Speed Effect | ZombieLore.ActiveOnly | both | both | Coming | ENTITY |
| Zombie House Alarm Triggering | ZombieLore.TriggerHouseAlarm | true | true | Coming | ENTITY |
| Drag Down | ZombieLore.ZombiesDragDown | true | true | Coming | ENTITY |
| Crawlers Drag Down | ZombieLore.ZombiesCrawlersDragDown | false | false | Coming | ENTITY |
| Zombie Lunge | ZombieLore.ZombiesFenceLunge | true | true | Coming | ENTITY |
| Zombie Armor Factor | ZombieLore.ZombiesArmorFactor | 2 | 2 | Coming | ENTITY |
| Maximum Zombie Armor Defense | ZombieLore.ZombiesMaxDefense | 85 | 85 | Coming | ENTITY |
| Chance Of Attached Weapon | ZombieLore.ChanceOfAttachedWeapon | 6 | 6 | Coming | ENTITY |
| Zombie Fall Damage Multiplier | ZombieLore.ZombiesFallDamage | 1 | 1 | Coming | ENTITY |
| Fake Dead Zombie Reanimation | ZombieLore.DisableFakeDead | world_zombies | world_zombies | Coming | ENTITY |
| Player Spawn Area | ZombieLore.PlayerSpawnZombieRemoval | inside_the_building_and_around_it | inside_the_building_and_around_it | Coming | ENTITY |
| Zombies To Damage Fences | ZombieLore.FenceThumpersRequired | 25 | 25 | Coming | ENTITY |
| Fence Damage Multiplier | ZombieLore.FenceDamageMultiplier | 1 | 1 | Coming | ENTITY |
| Only Headshots Kill | zc.onlyHeadshotsKill |  | true | Fixed: body and limb shots wear a zombie down, only the head kills it. | your rule |
| Shots Only Flinch | zc.shotsFlinch |  | true | Fixed: guns make zombies flinch, only blunt melee knocks them back. | your rule |
| Crawl Only Without Legs | zc.crawlNeedsBothLegs |  | true | Fixed: a zombie crawls only with both legs gone. | your rule |
| Sprinter Share | zc.sprinterChance | not in Zomboid | 0.15 | Working, ours (`zombies.sprinterChance`) | ENTITY |

## Time and Start

| Setting | Zomboid id | Zomboid (Apocalypse) | Ours | Status | Owner |
|---|---|---|---|---|---|
| Day Length (in real time) | DayLength | 1_hour_30_minutes | 1_hour_30_minutes | Coming | COMMANDS |
| Start Year | StartYear | 1 | 1 | Coming | COMMANDS |
| Start Month | StartMonth | july | july | Coming | COMMANDS |
| Start Day | StartDay | 9 | 9 | Coming | COMMANDS |
| Start Hour | StartTime | 9_am | 9_am | Coming | COMMANDS |
| Day / Night Cycle | DayNightCycle | normal | normal | Coming | WEATHER |
| Months since the Apocalypse | TimeSinceApo | 0 | 0 | Coming | LOOT |
| Darkness during night | NightDarkness | normal | normal | Coming | WEATHER |
| Length of nights | NightLength | normal | normal | Coming | WEATHER |
| Endless Night | zc.endlessNight |  | true | Fixed: the clouds keep the day nearly as dark as the night, and it always rains. | your rule |
| Daylight | zc.endlessDaylight | not in Zomboid | 0.30 | Working, ours (`weather.endlessDaylight`) | WEATHER |
| Night Light | zc.nightLight | not in Zomboid | 0.8 | Working, ours (`weather.nightLight`) | WEATHER |
| Choose Where You Wake | zc.spawnPicker | not in Zomboid | true | Working, ours (`start.picker`) | UI |

## Water, Power and World Events

| Setting | Zomboid id | Zomboid (Apocalypse) | Ours | Status | Owner |
|---|---|---|---|---|---|
| Water Shutoff | WaterShut | 0_30_days | 0_30_days | Coming | SURVIVAL |
| Electricity Shutoff | ElecShut | 14_30_days | 14_30_days | Coming | LANDSCAPE |
| Alarm Battery Decay | AlarmDecay | 0_30_days | 0_30_days | Coming | LANDSCAPE |
| Water Shutoff (WaterShutModifier) † | WaterShutModifier | 14 | 14 | Coming | SURVIVAL |
| Electricity Shutoff (ElecShutModifier) † | ElecShutModifier | 14 | 14 | Coming | LANDSCAPE |
| Alarm Battery Decay (AlarmDecayModifier) † | AlarmDecayModifier | 14 | 14 | Coming | LANDSCAPE |
| House Alarms Frequency † | Alarm | sometimes | sometimes | Coming | LANDSCAPE |
| Locked Houses Frequency | LockedHouses | very_often | very_often | Coming | LANDSCAPE |
| Helicopter † | Helicopter | once | once | Coming | ENTITY |
| Meta Event | MetaEvent | sometimes | sometimes | Coming | AUDIO |
| Sleeping Event | SleepingEvent | never | never | Coming | AUDIO |
| Generator Fuel Consumption | GeneratorFuelConsumption | 0.1 | 0.1 | Coming | BASES |
| Generators | GeneratorSpawning | rare | rare | Coming | BASES |
| Fire Spread | FireSpread | true | true | Coming | WEAPONS |
| Generator Working in Exterior | AllowExteriorGenerator | true | true | Coming | BASES |
| Siren Shutoff Hours | SirenShutoffHours | 0 | 0 | Coming | VEHICLES |
| Generator tile range | GeneratorTileRange | 20 | 20 | Coming | BASES |
| Generator vertical range | GeneratorVerticalPowerRange | 3 | 3 | Coming | BASES |
| World Events | zc.worldEvents | not in Zomboid | true | Working, ours (`world.worldEvents`) | AUDIO |
| Time Between World Events | zc.worldEventMinutes | not in Zomboid | 10 | Working, ours (`world.worldEventMinutes`) | AUDIO |

## Loot

| Setting | Zomboid id | Zomboid (Apocalypse) | Ours | Status | Owner |
|---|---|---|---|---|---|
| Perishable Food | FoodLootNew | 0.8 | 0.8 | Coming | LOOT |
| Other Literature | LiteratureLootNew | 0.6 | 0.6 | Coming | LOOT |
| Skill Books | SkillBookLoot | 0.6 | 0.6 | Coming | LOOT |
| Recipe Resources | RecipeResourceLoot | 0.6 | 0.6 | Coming | LOOT |
| Medical | MedicalLootNew | 0.6 | 0.6 | Coming | LOOT |
| Survival Essentials | SurvivalGearsLootNew | 0.6 | 0.6 | Coming | LOOT |
| Non-Perishable Food | CannedFoodLootNew | 0.6 | 0.6 | Coming | LOOT |
| Melee Weapons | WeaponLootNew | 0.6 | 0.6 | Coming | LOOT |
| Ranged Weapons | RangedWeaponLootNew | 1.2 | 1.2 | Coming | LOOT |
| Ammo | AmmoLootNew | 0.6 | 0.6 | Coming | LOOT |
| Mechanics | MechanicsLootNew | 0.6 | 0.6 | Coming | LOOT |
| Other | OtherLootNew | 0.8 | 0.8 | Coming | LOOT |
| Clothing | ClothingLootNew | 0.6 | 0.6 | Coming | LOOT |
| Bags | ContainerLootNew | 0.6 | 0.6 | Coming | LOOT |
| Keys | KeyLootNew | 0.4 | 0.4 | Coming | LOOT |
| Media | MediaLootNew | 0.6 | 0.6 | Coming | LOOT |
| Mementos | MementoLootNew | 0.6 | 0.6 | Coming | LOOT |
| Cooking | CookwareLootNew | 0.6 | 0.6 | Coming | LOOT |
| Material | MaterialLootNew | 0.6 | 0.6 | Coming | LOOT |
| Farming | FarmingLootNew | 0.6 | 0.6 | Coming | LOOT |
| Tools | ToolLootNew | 0.6 | 0.6 | Coming | LOOT |
| Rolls Multiplier [!] | RollsMultiplier | 1 | 1 | Coming | LOOT |
| Loot Item Removal List | LootItemRemovalList | (empty) | (empty) | Coming | LOOT |
| Remove Unwanted Story Loot | RemoveStoryLoot | false | false | Coming | LOOT |
| Remove Unwanted Zombie Loot | RemoveZombieLoot | false | false | Coming | LOOT |
| Zombie Population Loot Effect | ZombiePopLootEffect | 0 | 0 | Coming | LOOT |
| Insanely Rare Loot Factor | InsaneLootFactor | 0.05 | 0.05 | Coming | LOOT |
| Extremely Rare Loot Factor | ExtremeLootFactor | 0.2 | 0.2 | Coming | LOOT |
| Rare Loot Factor | RareLootFactor | 0.6 | 0.6 | Coming | LOOT |
| Normal Loot Factor | NormalLootFactor | 1 | 1 | Coming | LOOT |
| Common Loot Factor | CommonLootFactor | 2 | 2 | Coming | LOOT |
| Abundant Loot Factor | AbundantLootFactor | 3 | 3 | Coming | LOOT |
| Starter Kit | StarterKit | false | false | Coming | LOOT |
| Loot Seen Prevent Hours | SeenHoursPreventLootRespawn | 0 | 0 | Coming | LOOT |
| Hours for Loot Respawn | HoursForLootRespawn | 0 | 0 | Coming | LOOT |
| Max Items For Loot Respawn | MaxItemsForLootRespawn | 5 | 5 | Coming | LOOT |
| Construction Prevents Loot Respawn | ConstructionPreventsLootRespawn | true | true | Coming | LOOT |
| Annotated Map Chance | AnnotatedMapChance | sometimes | sometimes | Coming | LOOT |
| Randomized Building Chance | SurvivorHouseChance | rare | rare | Coming | LOOT |
| Randomized Road Stories Chance | VehicleStoryChance | rare | rare | Coming | LOOT |
| Randomized Zone Stories Chance | ZoneStoryChance | rare | rare | Coming | LOOT |
| Maximum Looted Building Rooms | MaximumLootedBuildingRooms | 50 | 50 | Coming | LOOT |
| Maximum Looted Building Chance | MaximumLooted | 25 | 25 | Coming | LOOT |
| Days Until Max Looted Building Chance | DaysUntilMaximumLooted | 90 | 90 | Coming | LOOT |
| Rural Building Looted Chance Multiplier | RuralLooted | 0.5 | 0.5 | Coming | LOOT |
| Maximum Diminished Loot Percentage | MaximumDiminishedLoot | 20 | 20 | Coming | LOOT |
| Days Until Maximum Diminished Loot | DaysUntilMaximumDiminishedLoot | 3650 | 3650 | Coming | LOOT |
| Loot Never Scales With Distance | zc.lootNeverScales |  | true | Fixed: loot is the same however far you are from where you started. | your rule |
| Shop Loot | zc.shopLoot | not in Zomboid | true | Working, ours (`world.shopLoot`) | LOOT |
| Car Loot | zc.carLoot | not in Zomboid | true | Working, ours (`world.carLoot`) | LOOT |

## Nature, Weather and Farming

| Setting | Zomboid id | Zomboid (Apocalypse) | Ours | Status | Owner |
|---|---|---|---|---|---|
| Climate Cycle | ClimateCycle | normal | normal | Working (`nature.climateCycle`) | WEATHER |
| Fog Cycle | FogCycle | normal | normal | Working (`nature.fogCycle`) | WEATHER |
| Temperature | Temperature | normal | normal | Working (`nature.temperature`) | WEATHER |
| Rain | Rain | normal | normal | Working (`nature.rain`) | WEATHER |
| Erosion Speed | ErosionSpeed | slow_200_days | slow_200_days | Coming | NONE (no erosion for now) |
| Erosion Days | ErosionDays | 0 | 0 | Coming | NONE (no erosion for now) |
| Farming Speed † | Farming | normal | normal | Coming | SURVIVAL |
| Compost Time | CompostTime | 2_weeks | 2_weeks | Coming | SURVIVAL |
| Nature's Abundance | NatureAbundance | normal | normal | Coming | SURVIVAL |
| Plant Resilience | PlantResilience | normal | normal | Coming | SURVIVAL |
| Farming's Abundance | PlantAbundance | normal | normal | Coming | SURVIVAL |
| Maximum Fog Intensity | MaxFogIntensity | normal | normal | Working (`nature.maxFog`) | WEATHER |
| Maximum Rain FX Intensity | MaxRainFxIntensity | normal | normal | Working (`nature.maxRainFx`) | WEATHER |
| Snow on Ground | EnableSnowOnGround | true | false | Working (`nature.snowOnGround`) | WEATHER |
| Fishing Abundance | FishAbundance | poor | poor | Coming | SURVIVAL |
| Kill Crops Grown Inside | KillInsideCrops | true | true | Coming | SURVIVAL |
| Plant Growing Seasons | PlantGrowingSeasons | true | true | Coming | SURVIVAL |
| Farms not on Ground Level [!] | PlaceDirtAboveground | false | false | Coming | SURVIVAL |
| Farming Speed (FarmingSpeedNew) † | FarmingSpeedNew | 1 | 1 | Coming | SURVIVAL |
| Farming Abundance | FarmingAmountNew | 1 | 1 | Coming | SURVIVAL |
| Clay chance - Lake | ClayLakeChance | 0.05 | 0.05 | Coming | SURVIVAL |
| Clay chance - River | ClayRiverChance | 0.05 | 0.05 | Coming | SURVIVAL |

## Character, Health and Food

| Setting | Zomboid id | Zomboid (Apocalypse) | Ours | Status | Owner |
|---|---|---|---|---|---|
| Stats Decrease | StatsDecrease | normal | normal | Coming | SURVIVAL |
| Nutrition System † | Nutrition | true | true | Coming | SURVIVAL |
| Food Spoilage | FoodRotSpeed | normal | normal | Coming | SURVIVAL |
| Refrigeration Effectiveness | FridgeFactor | normal | normal | Coming | SURVIVAL |
| Endurance Regeneration | EndRegen | normal | normal | Coming | SURVIVAL |
| Free Trait Points | CharacterFreePoints | 0 | 0 | Coming | SURVIVAL |
| Player-built Construction Strength | ConstructionBonusPoints | normal | normal | Coming | SURVIVAL |
| Bone Fracture | BoneFracture | true | true | Coming | SURVIVAL |
| Injury Severity | InjurySeverity | 2 | 2 | Coming | SURVIVAL |
| Time Before Corpse Removal | HoursForCorpseRemoval | 216 | 216 | Coming | ENTITY |
| Decaying Corpse Health Impact | DecayingCorpseHealthImpact | normal | normal | Coming | ENTITY |
| Zombie Health Impact | ZombieHealthImpact | false | false | Coming | SURVIVAL |
| Blood Level | BloodLevel | normal | normal | Coming | SURVIVAL |
| Clothing Degradation | ClothingDegradation | normal | normal | Coming | SURVIVAL |
| Rotten Food Removal | DaysForRottenFoodRemoval | -1 | -1 | Coming | SURVIVAL |
| Melee Movement Disruption | AttackBlockMovements | true | true | Coming | SURVIVAL |
| All Clothing Unlocked | AllClothesUnlocked | false | false | Coming | SURVIVAL |
| Enable 'Tainted Water' tooltip | EnableTaintedWaterText | true | true | Coming | SURVIVAL |
| Weapon Multi Hit | MultiHitZombies | false | false | Coming | SURVIVAL |
| Rear Vulnerability | RearVulnerability | high | high | Coming | SURVIVAL |
| Enable Poisoning | EnablePoisoning | true | true | Coming | SURVIVAL |
| Corpse Maggot Spawn | MaggotSpawn | in_and_around_bodies | in_and_around_bodies | Coming | SURVIVAL |
| Negative Traits Penalty | NegativeTraitsPenalty | none | none | Coming | SURVIVAL |
| Muscle Strain Factor | MuscleStrainFactor | 0.7 | 0.7 | Coming | SURVIVAL |
| Discomfort Factor | DiscomfortFactor | 0.8 | 0.8 | Coming | SURVIVAL |
| Wound Infection Damage Factor | WoundInfectionFactor | 1 | 1 | Coming | SURVIVAL |
| No Black Clothes | NoBlackClothes | true | true | Coming | SURVIVAL |
| Easy Climbing | EasyClimbing | false | false | Coming | SURVIVAL |
| Death Resets Everything | zc.deathResets |  | true | Fixed: death resets all skills and progress; your body rises only if you died infected. | your rule |
| Injuries | zc.injuries | not in Zomboid | true | Working, ours (`survival.injuries`) | SURVIVAL |
| Infection | zc.infection | not in Zomboid | true | Working, ours (`survival.infection`) | SURVIVAL |
| Infection Time | zc.infectionMinutes | not in Zomboid | 40 | Working, ours (`survival.infectionMinutes`) | SURVIVAL |
| Downed in Multiplayer | zc.downed | not in Zomboid | true | Working, ours (`survival.downed`) | SURVIVAL |

## Skills, XP and Reading

| Setting | Zomboid id | Zomboid (Apocalypse) | Ours | Status | Owner |
|---|---|---|---|---|---|
| Media List Meta Knowledge | MetaKnowledge | completely_hidden | completely_hidden | Coming | SURVIVAL |
| See Not Known Recipes | SeeNotLearntRecipe | true | true | Coming | SURVIVAL |
| Maximum Media XP Level | LevelForMediaXPCutoff | 3 | 3 | Coming | SURVIVAL |
| Maximum Dismantling XP Level | LevelForDismantleXPCutoff | 0 | 0 | Coming | SURVIVAL |
| Literature Cooldown Days | LiteratureCooldown | 45 | 45 | Coming | SURVIVAL |
| Minutes Per Skill Book Page | MinutesPerPage | 2 | 2 | Coming | SURVIVAL |
| Global Multiplier | MultiplierConfig.Global | 1 | 1.0 | Working (`progression.xpMultiplier`) | SURVIVAL |
| Use Global Multiplier | MultiplierConfig.GlobalToggle | true | true | Coming | SURVIVAL |
| Fitness Multiplier † | MultiplierConfig.Fitness | 1 | 1 | Coming | SURVIVAL |
| Strength Multiplier † | MultiplierConfig.Strength | 1 | 1 | Coming | SURVIVAL |
| Sprinting Multiplier † | MultiplierConfig.Sprinting | 1 | 1 | Coming | SURVIVAL |
| Lightfooted Multiplier † | MultiplierConfig.Lightfoot | 1 | 1 | Coming | SURVIVAL |
| Nimble Multiplier † | MultiplierConfig.Nimble | 1 | 1 | Coming | SURVIVAL |
| Sneaking Multiplier † | MultiplierConfig.Sneak | 1 | 1 | Coming | SURVIVAL |
| Axe Multiplier † | MultiplierConfig.Axe | 1 | 1 | Coming | SURVIVAL |
| Long Blunt Multiplier † | MultiplierConfig.Blunt | 1 | 1 | Coming | SURVIVAL |
| Short Blunt Multiplier | MultiplierConfig.SmallBlunt | 1 | 1 | Coming | SURVIVAL |
| Long Blade Multiplier | MultiplierConfig.LongBlade | 1 | 1 | Coming | SURVIVAL |
| Short Blade Multiplier | MultiplierConfig.SmallBlade | 1 | 1 | Coming | SURVIVAL |
| Spear Multiplier † | MultiplierConfig.Spear | 1 | 1 | Coming | SURVIVAL |
| Maintenance Multiplier † | MultiplierConfig.Maintenance | 1 | 1 | Coming | SURVIVAL |
| Carpentry Multiplier | MultiplierConfig.Woodwork | 1 | 1 | Coming | SURVIVAL |
| Cooking Multiplier † | MultiplierConfig.Cooking | 1 | 1 | Coming | SURVIVAL |
| Agriculture Multiplier | MultiplierConfig.Farming | 1 | 1 | Coming | SURVIVAL |
| First Aid Multiplier | MultiplierConfig.Doctor | 1 | 1 | Coming | SURVIVAL |
| Electrical Multiplier | MultiplierConfig.Electricity | 1 | 1 | Coming | SURVIVAL |
| Welding Multiplier | MultiplierConfig.MetalWelding | 1 | 1 | Coming | SURVIVAL |
| Mechanics Multiplier † | MultiplierConfig.Mechanics | 1 | 1 | Coming | SURVIVAL |
| Tailoring Multiplier † | MultiplierConfig.Tailoring | 1 | 1 | Coming | SURVIVAL |
| Aiming Multiplier † | MultiplierConfig.Aiming | 1 | 1 | Coming | SURVIVAL |
| Reloading Multiplier † | MultiplierConfig.Reloading | 1 | 1 | Coming | SURVIVAL |
| Fishing Multiplier † | MultiplierConfig.Fishing | 1 | 1 | Coming | SURVIVAL |
| Trapping Multiplier † | MultiplierConfig.Trapping | 1 | 1 | Coming | SURVIVAL |
| Foraging Multiplier | MultiplierConfig.PlantScavenging | 1 | 1 | Coming | SURVIVAL |
| Knapping Multiplier | MultiplierConfig.FlintKnapping | 1 | 1 | Coming | SURVIVAL |
| Masonry Multiplier † | MultiplierConfig.Masonry | 1 | 1 | Coming | SURVIVAL |
| Pottery Multiplier † | MultiplierConfig.Pottery | 1 | 1 | Coming | SURVIVAL |
| Carving Multiplier † | MultiplierConfig.Carving | 1 | 1 | Coming | SURVIVAL |
| Animal Care Multiplier | MultiplierConfig.Husbandry | 1 | 1 | Coming | SURVIVAL |
| Tracking Multiplier † | MultiplierConfig.Tracking | 1 | 1 | Coming | SURVIVAL |
| Blacksmithing Multiplier † | MultiplierConfig.Blacksmith | 1 | 1 | Coming | SURVIVAL |
| Butchering Multiplier † | MultiplierConfig.Butchering | 1 | 1 | Coming | SURVIVAL |
| Glassmaking Multiplier † | MultiplierConfig.Glassmaking | 1 | 1 | Coming | SURVIVAL |

## Vehicles and Fuel

| Setting | Zomboid id | Zomboid (Apocalypse) | Ours | Status | Owner |
|---|---|---|---|---|---|
| Vehicles | EnableVehicles | true | true | Working (`world.streetCars`) | VEHICLES |
| Vehicle Spawn Rate | CarSpawnRate | low | low | Coming | VEHICLES |
| Easy Use | VehicleEasyUse | false | false | Coming | VEHICLES |
| Initial Gas | InitialGas | low | low | Coming | VEHICLES |
| Infinite Gas Pumps | FuelStationGasInfinite | false | false | Coming | VEHICLES |
| Initial Minimum Gas Pump Amount | FuelStationGasMin | 0 | 0 | Coming | VEHICLES |
| Initial Maximum Gas Pump Amount | FuelStationGasMax | 0.8 | 0.8 | Coming | VEHICLES |
| Initial Gas Pump Empty Chance | FuelStationGasEmptyChance | 20 | 20 | Coming | VEHICLES |
| Locked Vehicle Frequency | LockedCar | sometimes | sometimes | Coming | VEHICLES |
| Gas Consumption | CarGasConsumption | 1 | 1 | Coming | VEHICLES |
| General Condition | CarGeneralCondition | normal | normal | Coming | VEHICLES |
| Car Damage on Impact | CarDamageOnImpact | normal | normal | Coming | VEHICLES |
| Player Damage From Vehicle Impact | DamageToPlayerFromHitByACar | 1 | 1 | Coming | VEHICLES |
| Car Wreck Congestion | TrafficJam | true | true | Coming | VEHICLES |
| Vehicle Alarms Frequency | CarAlarm | rare | rare | Coming | VEHICLES |
| Player Damage from Crash | PlayerDamageFromCrash | true | true | Coming | VEHICLES |
| Chance Has Gas | ChanceHasGas | normal | normal | Coming | VEHICLES |
| Recent Survivor Vehicles | RecentlySurvivorVehicles | low | low | Coming | VEHICLES |
| Vehicle Sirens Attract Zombies | SirenEffectsZombies | true | true | Coming | VEHICLES |
| Cars Left Whole | zc.carUsable | not in Zomboid | 0.45 | Working, ours (`world.carUsable`) | VEHICLES |
| Cars Abandoned | zc.carAbandoned | not in Zomboid | 0.3 | Working, ours (`world.carAbandoned`) | VEHICLES |
| Cars Wrecked | zc.carWrecked | not in Zomboid | 0.18 | Working, ours (`world.carWrecked`) | VEHICLES |
| Cars Burnt Out | zc.carBurnt | not in Zomboid | 0.07 | Working, ours (`world.carBurnt`) | VEHICLES |
| Aircraft Need Servicing | zc.aircraftService | not in Zomboid | true | Working, ours (`world.aircraftService`) | VEHICLES |

## Firearms

| Setting | Zomboid id | Zomboid (Apocalypse) | Ours | Status | Owner |
|---|---|---|---|---|---|
| Firearms Use Damage Chance | FirearmUseDamageChance | zombies_only | zombies_only | Coming | WEAPONS |
| Firearm Noise Multiplier | FirearmNoiseMultiplier | 1 | 1 | Coming | WEAPONS |
| Firearm Jam Multiplier | FirearmJamMultiplier | 1 | 1 | Coming | WEAPONS |
| Firearm Moodle Multiplier | FirearmMoodleMultiplier | 1 | 1 | Coming | WEAPONS |
| Firearm Weather Multiplier | FirearmWeatherMultiplier | 1 | 1 | Coming | WEAPONS |
| Firearm Headgear Effect | FirearmHeadGearEffect | true | true | Coming | WEAPONS |

## Animals and Rats

| Setting | Zomboid id | Zomboid (Apocalypse) | Ours | Status | Owner |
|---|---|---|---|---|---|
| Stats Reduction Speed | AnimalStatsModifier | normal | normal | Coming | SURVIVAL |
| Meta Stats Reduction Speed | AnimalMetaStatsModifier | normal | normal | Coming | SURVIVAL |
| Pregnancy Time | AnimalPregnancyTime | normal | normal | Coming | SURVIVAL |
| Aging Modifier Speed | AnimalAgeModifier | normal | normal | Coming | SURVIVAL |
| Milk Increase Speed | AnimalMilkIncModifier | normal | normal | Coming | SURVIVAL |
| Wool Increase Speed | AnimalWoolIncModifier | normal | normal | Coming | SURVIVAL |
| Animal Spawn Chance | AnimalRanchChance | often | often | Coming | SURVIVAL |
| Grass Regrowth time | AnimalGrassRegrowTime | 240 | 240 | Coming | SURVIVAL |
| Meta Predator | AnimalMetaPredator | false | false | Coming | SURVIVAL |
| Breeding Season | AnimalMatingSeason | true | true | Coming | SURVIVAL |
| Egg Hatch Time | AnimalEggHatch | normal | normal | Coming | SURVIVAL |
| Animals Attract Zombies | AnimalSoundAttractZombies | true | true | Coming | SURVIVAL |
| Animal Tracks Chance | AnimalTrackChance | sometimes | sometimes | Coming | SURVIVAL |
| Animal Paths Chance | AnimalPathChance | sometimes | sometimes | Coming | SURVIVAL |
| Maximum Vermin Index | MaximumRatIndex | 25 | 25 | Coming | SURVIVAL |
| Days Until Maximum Vermin Index | DaysUntilMaximumRatIndex | 90 | 90 | Coming | SURVIVAL |

## Map, Basements and Clean-up

| Setting | Zomboid id | Zomboid (Apocalypse) | Ours | Status | Owner |
|---|---|---|---|---|---|
| World Item Removal List | WorldItemRemovalList | (empty) | (empty) | Coming | LANDSCAPE |
| Hours for Removal List | HoursForWorldItemRemoval | 24 | 24 | Coming | LANDSCAPE |
| Removal List as Whitelist | ItemRemovalListBlacklistToggle | false | false | Coming | LANDSCAPE |
| Light Bulb Lifespan | LightBulbLifespan | 2 | 2 | Coming | BASES |
| Blood Splat Lifespan Days | BloodSplatLifespanDays | 0 | 0 | Coming | LANDSCAPE |
| Maximum Fire Fuel Hours | MaximumFireFuelHours | 8 | 8 | Coming | LANDSCAPE |
| Basement Spawn Frequency | Basement.SpawnFrequency | sometimes | sometimes | Coming | LANDSCAPE |
| Allow Mini-Map | Map.AllowMiniMap | false | false | Coming | UI |
| Allow World Map | Map.AllowWorldMap | true | true | Coming | UI |
| All Known On Start | Map.MapAllKnown | false | false | Coming | UI |
| Light Needed To Read Map | Map.MapNeedsLight | true | true | Coming | UI |
| Body Removal | zc.corpseMinutes | not in Zomboid | 0 | Working, ours (`corpses.minutes`) | ENTITY |
| Most Bodies Kept | zc.maxBodies | not in Zomboid | 300 | Working, ours (`corpses.maxBodies`) | PERFORMANCE |

## ZomboidCraft Only

| Setting | Zomboid id | Zomboid (Apocalypse) | Ours | Status | Owner |
|---|---|---|---|---|---|
| Storm Alarms | zc.stormAlarms | not in Zomboid | false | Working, ours (`weather.stormAlarms`) | WEATHER |
| Lightning Strikes | zc.lightningStrikes | not in Zomboid | true | Working, ours (`weather.lightningStrikes`) | WEATHER |
| Hail | zc.hail | not in Zomboid | true | Working, ours (`weather.hail`) | WEATHER |
| Cyclone Winds | zc.windDamage | not in Zomboid | true | Working, ours (`weather.windDamage`) | WEATHER |
| Air Strikes | zc.airStrikes | not in Zomboid | true | Working, ours (`world.airStrikes`) | FLIGHT |
| Lab Breach Surges | zc.breachDays | not in Zomboid | 0 | Working, ours (`population.respawnDays`) | NETHER |
| Digging Rules | zc.digRules | not in Zomboid | true | Working, ours (`digging.rules`) | BASES |
| Dig Depth | zc.digDepth | not in Zomboid | 8 | Working, ours (`digging.floorDepth`) | BASES |
