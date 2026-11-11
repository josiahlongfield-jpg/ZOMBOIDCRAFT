# Project Zomboid B42.21 sandbox options (269)

Source: https://pindrop.gg/project-zomboid/sandbox-settings (fetched 2026-10-31).

- default = the value the game loads by default (its Apocalypse settings, SandboxVars.lua). Presets dropped per Josiah 2026-10-31.
- The page gives only the number of choices for multi-choice options, not the choice names.
- idExactCase false: the page did not print the SandboxVars name; it was read from the page's lowercase search text and recased (prefix ZombieLore./MultiplierConfig., first letter capital), so check the case before relying on it.

## Zombie population

| Option | SandboxVars | Range | Default |
|---|---|---|---|
| Zombie Count | Zombies | 6 choices | Normal |
| Zombie Distribution | Distribution (?) | 2 choices | Urban Focused |
| Voronoi Noise | ZombieVoronoiNoise | On / Off | On |
| Zombie Respawn | ZombieRespawn | 4 choices | None |
| Zombie Migration | ZombieMigrate | On / Off | On |
| Zombie Attraction Multiplier | ZombieAttractionMultiplier | 0 – 100 | 1 |
| Population Multiplier | ZombieConfig.PopulationMultiplier | 0 – 4 | 0.65 |
| Population Start Multiplier | ZombieConfig.PopulationStartMultiplier | 0 – 4 | 1 |
| Population Peak Multiplier | ZombieConfig.PopulationPeakMultiplier | 0 – 4 | 1.5 |
| Population Peak Day | ZombieConfig.PopulationPeakDay | 1 – 365 | 28 |
| Respawn Hours | ZombieConfig.RespawnHours | 0 – 8760 | 0 |
| Respawn Unseen Hours | ZombieConfig.RespawnUnseenHours | 0 – 8760 | 0 |
| Respawn Multiplier | ZombieConfig.RespawnMultiplier | 0 – 1 | 0 |
| Redistribute Hours | ZombieConfig.RedistributeHours | 0 – 8760 | 12 |
| Follow Sound Distance | ZombieConfig.FollowSoundDistance | 10 – 1000 | 100 |
| Rally Group Size | ZombieConfig.RallyGroupSize | 0 – 1000 | 20 |
| Rally Group Size Variance | ZombieConfig.RallyGroupSizeVariance | 0 – 100 | 50 |
| Rally Travel Distance | ZombieConfig.RallyTravelDistance | 5 – 50 | 20 |
| Rally Group Separation | ZombieConfig.RallyGroupSeparation | 5 – 25 | 15 |
| Rally Group Radius | ZombieConfig.RallyGroupRadius | 1 – 10 | 3 |
| Zombie count before deletion | ZombieConfig.ZombiesCountBeforeDelete | 0 – 5000 | 300 |

## Zombie lore

| Option | SandboxVars | Range | Default |
|---|---|---|---|
| Speed | ZombieLore.Speed (?) | 4 choices | Random |
| Random Sprinter Amount | ZombieLore.SprinterPercentage | 0 – 100 | 0 |
| Strength | ZombieLore.Strength (?) | 4 choices | Normal |
| Toughness | ZombieLore.Toughness (?) | 4 choices | Random |
| Transmission | ZombieLore.Transmission (?) | 4 choices | Blood and Saliva |
| Infection Mortality | ZombieLore.Mortality (?) | 7 choices | 2-3 Days |
| Reanimate Time | ZombieLore.Reanimate (?) | 6 choices | 0-1 Minutes |
| Cognition | ZombieLore.Cognition (?) | 4 choices | Basic Navigation |
| Random Door Opening Amount | ZombieLore.DoorOpeningPercentage | 0 – 100 | 0 |
| Crawl Under Vehicle | ZombieLore.CrawlUnderVehicle | 7 choices | Often |
| Memory | ZombieLore.Memory (?) | 6 choices | Normal |
| Sight | ZombieLore.Sight (?) | 5 choices | Random between Normal and Poor |
| Hearing | ZombieLore.Hearing (?) | 5 choices | Random between Normal and Poor |
| New Stealth System | ZombieLore.SpottedLogic | On / Off | On |
| Environmental Attacks | ZombieLore.ThumpNoChasing | On / Off | Off |
| Damage Construction | ZombieLore.ThumpOnConstruction | On / Off | On |
| Day/Night Zombie Speed Effect | ZombieLore.ActiveOnly | 3 choices | Both |
| Zombie House Alarm Triggering | ZombieLore.TriggerHouseAlarm | On / Off | On |
| Drag Down | ZombieLore.ZombiesDragDown | On / Off | On |
| Crawlers Drag Down | ZombieLore.ZombiesCrawlersDragDown | On / Off | Off |
| Zombie Lunge | ZombieLore.ZombiesFenceLunge | On / Off | On |
| Zombie Armor Factor | ZombieLore.ZombiesArmorFactor | 0 – 100 | 2 |
| Maximum Zombie Armor Defense | ZombieLore.ZombiesMaxDefense | 0 – 100 | 85 |
| Chance Of Attached Weapon | ZombieLore.ChanceOfAttachedWeapon | 0 – 100 | 6 |
| Zombie Fall Damage Multiplier | ZombieLore.ZombiesFallDamage | 0 – 100 | 1 |
| Fake Dead Zombie Reanimation | ZombieLore.DisableFakeDead | 3 choices | World Zombies |
| Player Spawn Area | ZombieLore.PlayerSpawnZombieRemoval | 4 choices | Inside the building and around it |
| Zombies To Damage Fences | ZombieLore.FenceThumpersRequired | -1 – 100 | 25 |
| Fence Damage Multiplier | ZombieLore.FenceDamageMultiplier | 0.009999999776482582 – 100 | 1 |

## Time and start

| Option | SandboxVars | Range | Default |
|---|---|---|---|
| Day Length (in real time) | DayLength | 27 choices | 1 Hour, 30 Minutes |
| Start Year | StartYear | 100 choices | 1 |
| Start Month | StartMonth | 12 choices | July |
| Start Day | StartDay | 31 choices | 9 |
| Start Hour | StartTime | 9 choices | 9 AM |
| Day / Night Cycle | DayNightCycle | 3 choices | Normal |
| Months since the Apocalypse | TimeSinceApo | 13 choices | 0 |
| Darkness during night | NightDarkness | 4 choices | Normal |
| Length of nights | NightLength | 5 choices | Normal |

## Water, power and world events

| Option | SandboxVars | Range | Default |
|---|---|---|---|
| Water Shutoff | WaterShut | 9 choices | 0 - 30 Days |
| Electricity Shutoff | ElecShut | 9 choices | 14 - 30 Days |
| Alarm Battery Decay | AlarmDecay | 6 choices | 0 - 30 Days |
| Water Shutoff (WaterShutModifier) | WaterShutModifier (?) | -1 or more | 14 |
| Electricity Shutoff (ElecShutModifier) | ElecShutModifier (?) | -1 or more | 14 |
| Alarm Battery Decay (AlarmDecayModifier) | AlarmDecayModifier (?) | -1 or more | 14 |
| House Alarms Frequency | Alarm (?) | 6 choices | Sometimes |
| Locked Houses Frequency | LockedHouses | 6 choices | Very Often |
| Helicopter | Helicopter (?) | 4 choices | Once |
| Meta Event | MetaEvent | 3 choices | Sometimes |
| Sleeping Event | SleepingEvent | 3 choices | Never |
| Generator Fuel Consumption | GeneratorFuelConsumption | 0 – 100 | 0.1 |
| Generators | GeneratorSpawning | 7 choices | Rare |
| Fire Spread | FireSpread | On / Off | On |
| Generator Working in Exterior | AllowExteriorGenerator | On / Off | On |
| Siren Shutoff Hours | SirenShutoffHours | 0 – 168 | 0 |
| Generator tile range | GeneratorTileRange | 1 – 100 | 20 |
| Generator vertical range | GeneratorVerticalPowerRange | 1 – 15 | 3 |

## Loot

| Option | SandboxVars | Range | Default |
|---|---|---|---|
| Perishable Food | FoodLootNew | 0 – 4 | 0.8 |
| Other Literature | LiteratureLootNew | 0 – 4 | 0.6 |
| Skill Books | SkillBookLoot | 0 – 4 | 0.6 |
| Recipe Resources | RecipeResourceLoot | 0 – 4 | 0.6 |
| Medical | MedicalLootNew | 0 – 4 | 0.6 |
| Survival Essentials | SurvivalGearsLootNew | 0 – 4 | 0.6 |
| Non-Perishable Food | CannedFoodLootNew | 0 – 4 | 0.6 |
| Melee Weapons | WeaponLootNew | 0 – 4 | 0.6 |
| Ranged Weapons | RangedWeaponLootNew | 0 – 4 | 1.2 |
| Ammo | AmmoLootNew | 0 – 4 | 0.6 |
| Mechanics | MechanicsLootNew | 0 – 4 | 0.6 |
| Other | OtherLootNew | 0 – 4 | 0.8 |
| Clothing | ClothingLootNew | 0 – 4 | 0.6 |
| Bags | ContainerLootNew | 0 – 4 | 0.6 |
| Keys | KeyLootNew | 0 – 4 | 0.4 |
| Media | MediaLootNew | 0 – 4 | 0.6 |
| Mementos | MementoLootNew | 0 – 4 | 0.6 |
| Cooking | CookwareLootNew | 0 – 4 | 0.6 |
| Material | MaterialLootNew | 0 – 4 | 0.6 |
| Farming | FarmingLootNew | 0 – 4 | 0.6 |
| Tools | ToolLootNew | 0 – 4 | 0.6 |
| Rolls Multiplier [!] | RollsMultiplier | 0.1 – 100 | 1 |
| Loot Item Removal List | LootItemRemovalList | text | (empty) |
| Remove Unwanted Story Loot | RemoveStoryLoot | On / Off | Off |
| Remove Unwanted Zombie Loot | RemoveZombieLoot | On / Off | Off |
| Zombie Population Loot Effect | ZombiePopLootEffect | 0 – 20 | 0 |
| Insanely Rare Loot Factor | InsaneLootFactor | 0 – 0.2 | 0.05 |
| Extremely Rare Loot Factor | ExtremeLootFactor | 0.05 – 0.6 | 0.2 |
| Rare Loot Factor | RareLootFactor | 0.2 – 1 | 0.6 |
| Normal Loot Factor | NormalLootFactor | 0.6 – 2 | 1 |
| Common Loot Factor | CommonLootFactor | 1 – 3 | 2 |
| Abundant Loot Factor | AbundantLootFactor | 2 – 4 | 3 |
| Starter Kit | StarterKit | On / Off | Off |
| Loot Seen Prevent Hours | SeenHoursPreventLootRespawn | 0 or more | 0 |
| Hours for Loot Respawn | HoursForLootRespawn | 0 or more | 0 |
| Max Items For Loot Respawn | MaxItemsForLootRespawn | 0 or more | 5 |
| Construction Prevents Loot Respawn | ConstructionPreventsLootRespawn | On / Off | On |
| Annotated Map Chance | AnnotatedMapChance | 6 choices | Sometimes |
| Randomized Building Chance | SurvivorHouseChance | 7 choices | Rare |
| Randomized Road Stories Chance | VehicleStoryChance | 7 choices | Rare |
| Randomized Zone Stories Chance | ZoneStoryChance | 7 choices | Rare |
| Maximum Looted Building Rooms | MaximumLootedBuildingRooms | 0 – 200 | 50 |
| Maximum Looted Building Chance | MaximumLooted | 0 – 200 | 25 |
| Days Until Max Looted Building Chance | DaysUntilMaximumLooted | 0 – 3650 | 90 |
| Rural Building Looted Chance Multiplier | RuralLooted | 0 – 2 | 0.5 |
| Maximum Diminished Loot Percentage | MaximumDiminishedLoot | 0 – 100 | 20 |
| Days Until Maximum Diminished Loot | DaysUntilMaximumDiminishedLoot | 0 – 3650 | 3650 |

## Nature, weather and farming

| Option | SandboxVars | Range | Default |
|---|---|---|---|
| Climate Cycle | ClimateCycle | 6 choices | Normal |
| Fog Cycle | FogCycle | 3 choices | Normal |
| Temperature | Temperature (?) | 5 choices | Normal |
| Rain | Rain (?) | 5 choices | Normal |
| Erosion Speed | ErosionSpeed | 5 choices | Slow (200 Days) |
| Erosion Days | ErosionDays | -1 – 36500 | 0 |
| Farming Speed | Farming (?) | 5 choices | Normal |
| Compost Time | CompostTime | 8 choices | 2 Weeks |
| Nature's Abundance | NatureAbundance | 5 choices | Normal |
| Plant Resilience | PlantResilience | 5 choices | Normal |
| Farming's Abundance | PlantAbundance | 5 choices | Normal |
| Maximum Fog Intensity | MaxFogIntensity | 4 choices | Normal |
| Maximum Rain FX Intensity | MaxRainFxIntensity | 3 choices | Normal |
| Snow on Ground | EnableSnowOnGround | On / Off | On |
| Fishing Abundance | FishAbundance | 5 choices | Poor |
| Kill Crops Grown Inside | KillInsideCrops | On / Off | On |
| Plant Growing Seasons | PlantGrowingSeasons | On / Off | On |
| Farms not on Ground Level [!] | PlaceDirtAboveground | On / Off | Off |
| Farming Speed (FarmingSpeedNew) | FarmingSpeedNew (?) | 0.1 – 100 | 1 |
| Farming Abundance | FarmingAmountNew | 0.1 – 10 | 1 |
| Clay chance - Lake | ClayLakeChance | 0 – 1 | 0.05 |
| Clay chance - River | ClayRiverChance | 0 – 1 | 0.05 |

## Character, health and food

| Option | SandboxVars | Range | Default |
|---|---|---|---|
| Stats Decrease | StatsDecrease | 5 choices | Normal |
| Nutrition System | Nutrition (?) | On / Off | On |
| Food Spoilage | FoodRotSpeed | 5 choices | Normal |
| Refrigeration Effectiveness | FridgeFactor | 6 choices | Normal |
| Endurance Regeneration | EndRegen | 5 choices | Normal |
| Free Trait Points | CharacterFreePoints | -100 – 100 | 0 |
| Player-built Construction Strength | ConstructionBonusPoints | 5 choices | Normal |
| Bone Fracture | BoneFracture | On / Off | On |
| Injury Severity | InjurySeverity |  | 2 |
| Time Before Corpse Removal | HoursForCorpseRemoval | -1 or more | 216 |
| Decaying Corpse Health Impact | DecayingCorpseHealthImpact | 5 choices | Normal |
| Zombie Health Impact | ZombieHealthImpact | On / Off | Off |
| Blood Level | BloodLevel | 5 choices | Normal |
| Clothing Degradation | ClothingDegradation | 4 choices | Normal |
| Rotten Food Removal | DaysForRottenFoodRemoval | -1 or more | -1 |
| Melee Movement Disruption | AttackBlockMovements | On / Off | On |
| All Clothing Unlocked | AllClothesUnlocked | On / Off | Off |
| Enable 'Tainted Water' tooltip | EnableTaintedWaterText | On / Off | On |
| Weapon Multi Hit | MultiHitZombies | On / Off | Off |
| Rear Vulnerability | RearVulnerability | 3 choices | High |
| Enable Poisoning | EnablePoisoning | 3 choices | True |
| Corpse Maggot Spawn | MaggotSpawn | 3 choices | In and Around Bodies |
| Negative Traits Penalty | NegativeTraitsPenalty | 4 choices | None |
| Muscle Strain Factor | MuscleStrainFactor | 0 – 10 | 0.7 |
| Discomfort Factor | DiscomfortFactor | 0 – 10 | 0.8 |
| Wound Infection Damage Factor | WoundInfectionFactor | 0 – 10 | 1 |
| No Black Clothes | NoBlackClothes | On / Off | On |
| Easy Climbing | EasyClimbing | On / Off | Off |

## Skills, XP and reading

| Option | SandboxVars | Range | Default |
|---|---|---|---|
| Media List Meta Knowledge | MetaKnowledge | 3 choices | Completely hidden |
| See Not Known Recipes | SeeNotLearntRecipe | On / Off | On |
| Maximum Media XP Level | LevelForMediaXPCutoff | 0 – 10 | 3 |
| Maximum Dismantling XP Level | LevelForDismantleXPCutoff | 0 – 10 | 0 |
| Literature Cooldown Days | LiteratureCooldown | 1 – 365 | 45 |
| Minutes Per Skill Book Page | MinutesPerPage | 0 – 60 | 2 |
| Global Multiplier | MultiplierConfig.Global (?) | 0 – 1000 | 1 |
| Use Global Multiplier | MultiplierConfig.GlobalToggle | On / Off | On |
| Fitness Multiplier | MultiplierConfig.Fitness (?) | 0 – 1000 | 1 |
| Strength Multiplier | MultiplierConfig.Strength (?) | 0 – 1000 | 1 |
| Sprinting Multiplier | MultiplierConfig.Sprinting (?) | 0 – 1000 | 1 |
| Lightfooted Multiplier | MultiplierConfig.Lightfoot (?) | 0 – 1000 | 1 |
| Nimble Multiplier | MultiplierConfig.Nimble (?) | 0 – 1000 | 1 |
| Sneaking Multiplier | MultiplierConfig.Sneak (?) | 0 – 1000 | 1 |
| Axe Multiplier | MultiplierConfig.Axe (?) | 0 – 1000 | 1 |
| Long Blunt Multiplier | MultiplierConfig.Blunt (?) | 0 – 1000 | 1 |
| Short Blunt Multiplier | MultiplierConfig.SmallBlunt | 0 – 1000 | 1 |
| Long Blade Multiplier | MultiplierConfig.LongBlade | 0 – 1000 | 1 |
| Short Blade Multiplier | MultiplierConfig.SmallBlade | 0 – 1000 | 1 |
| Spear Multiplier | MultiplierConfig.Spear (?) | 0 – 1000 | 1 |
| Maintenance Multiplier | MultiplierConfig.Maintenance (?) | 0 – 1000 | 1 |
| Carpentry Multiplier | MultiplierConfig.Woodwork | 0 – 1000 | 1 |
| Cooking Multiplier | MultiplierConfig.Cooking (?) | 0 – 1000 | 1 |
| Agriculture Multiplier | MultiplierConfig.Farming | 0 – 1000 | 1 |
| First Aid Multiplier | MultiplierConfig.Doctor | 0 – 1000 | 1 |
| Electrical Multiplier | MultiplierConfig.Electricity | 0 – 1000 | 1 |
| Welding Multiplier | MultiplierConfig.MetalWelding | 0 – 1000 | 1 |
| Mechanics Multiplier | MultiplierConfig.Mechanics (?) | 0 – 1000 | 1 |
| Tailoring Multiplier | MultiplierConfig.Tailoring (?) | 0 – 1000 | 1 |
| Aiming Multiplier | MultiplierConfig.Aiming (?) | 0 – 1000 | 1 |
| Reloading Multiplier | MultiplierConfig.Reloading (?) | 0 – 1000 | 1 |
| Fishing Multiplier | MultiplierConfig.Fishing (?) | 0 – 1000 | 1 |
| Trapping Multiplier | MultiplierConfig.Trapping (?) | 0 – 1000 | 1 |
| Foraging Multiplier | MultiplierConfig.PlantScavenging | 0 – 1000 | 1 |
| Knapping Multiplier | MultiplierConfig.FlintKnapping | 0 – 1000 | 1 |
| Masonry Multiplier | MultiplierConfig.Masonry (?) | 0 – 1000 | 1 |
| Pottery Multiplier | MultiplierConfig.Pottery (?) | 0 – 1000 | 1 |
| Carving Multiplier | MultiplierConfig.Carving (?) | 0 – 1000 | 1 |
| Animal Care Multiplier | MultiplierConfig.Husbandry | 0 – 1000 | 1 |
| Tracking Multiplier | MultiplierConfig.Tracking (?) | 0 – 1000 | 1 |
| Blacksmithing Multiplier | MultiplierConfig.Blacksmith (?) | 0 – 1000 | 1 |
| Butchering Multiplier | MultiplierConfig.Butchering (?) | 0 – 1000 | 1 |
| Glassmaking Multiplier | MultiplierConfig.Glassmaking (?) | 0 – 1000 | 1 |

## Vehicles and fuel

| Option | SandboxVars | Range | Default |
|---|---|---|---|
| Vehicles | EnableVehicles | On / Off | On |
| Vehicle Spawn Rate | CarSpawnRate | 5 choices | Low |
| Easy Use | VehicleEasyUse | On / Off | Off |
| Initial Gas | InitialGas | 6 choices | Low |
| Infinite Gas Pumps | FuelStationGasInfinite | On / Off | Off |
| Initial Minimum Gas Pump Amount | FuelStationGasMin | 0 – 1 | 0 |
| Initial Maximum Gas Pump Amount | FuelStationGasMax | 0 – 1 | 0.8 |
| Initial Gas Pump Empty Chance | FuelStationGasEmptyChance | 0 – 100 | 20 |
| Locked Vehicle Frequency | LockedCar | 6 choices | Sometimes |
| Gas Consumption | CarGasConsumption | 0 – 100 | 1 |
| General Condition | CarGeneralCondition | 5 choices | Normal |
| Car Damage on Impact | CarDamageOnImpact | 5 choices | Normal |
| Player Damage From Vehicle Impact | DamageToPlayerFromHitByACar |  | 1 |
| Car Wreck Congestion | TrafficJam | On / Off | On |
| Vehicle Alarms Frequency | CarAlarm | 6 choices | Rare |
| Player Damage from Crash | PlayerDamageFromCrash | On / Off | On |
| Chance Has Gas | ChanceHasGas | 3 choices | Normal |
| Recent Survivor Vehicles | RecentlySurvivorVehicles | 4 choices | Low |
| Vehicle Sirens Attract Zombies | SirenEffectsZombies | On / Off | On |

## Firearms

| Option | SandboxVars | Range | Default |
|---|---|---|---|
| Firearms Use Damage Chance | FirearmUseDamageChance | 3 choices | Zombies only |
| Firearm Noise Multiplier | FirearmNoiseMultiplier | 0.2 – 2 | 1 |
| Firearm Jam Multiplier | FirearmJamMultiplier | 0 – 10 | 1 |
| Firearm Moodle Multiplier | FirearmMoodleMultiplier | 0 – 10 | 1 |
| Firearm Weather Multiplier | FirearmWeatherMultiplier | 0 – 10 | 1 |
| Firearm Headgear Effect | FirearmHeadGearEffect | On / Off | On |

## Animals and rats

| Option | SandboxVars | Range | Default |
|---|---|---|---|
| Stats Reduction Speed | AnimalStatsModifier | 6 choices | Normal |
| Meta Stats Reduction Speed | AnimalMetaStatsModifier | 6 choices | Normal |
| Pregnancy Time | AnimalPregnancyTime | 6 choices | Normal |
| Aging Modifier Speed | AnimalAgeModifier | 6 choices | Normal |
| Milk Increase Speed | AnimalMilkIncModifier | 6 choices | Normal |
| Wool Increase Speed | AnimalWoolIncModifier | 6 choices | Normal |
| Animal Spawn Chance | AnimalRanchChance | 7 choices | Often |
| Grass Regrowth time | AnimalGrassRegrowTime | 1 – 9999 | 240 |
| Meta Predator | AnimalMetaPredator | On / Off | Off |
| Breeding Season | AnimalMatingSeason | On / Off | On |
| Egg Hatch Time | AnimalEggHatch | 6 choices | Normal |
| Animals Attract Zombies | AnimalSoundAttractZombies | On / Off | On |
| Animal Tracks Chance | AnimalTrackChance | 6 choices | Sometimes |
| Animal Paths Chance | AnimalPathChance | 6 choices | Sometimes |
| Maximum Vermin Index | MaximumRatIndex | 0 – 50 | 25 |
| Days Until Maximum Vermin Index | DaysUntilMaximumRatIndex | 0 – 365 | 90 |

## Map, basements and clean-up

| Option | SandboxVars | Range | Default |
|---|---|---|---|
| World Item Removal List | WorldItemRemovalList | text | 1 items |
| Hours for Removal List | HoursForWorldItemRemoval | 0 or more | 24 |
| Removal List as Whitelist | ItemRemovalListBlacklistToggle | On / Off | Off |
| Light Bulb Lifespan | LightBulbLifespan | 0 – 1000 | 2 |
| Blood Splat Lifespan Days | BloodSplatLifespanDays | 0 – 365 | 0 |
| Maximum Fire Fuel Hours | MaximumFireFuelHours | 1 – 168 | 8 |
| Basement Spawn Frequency | Basement.SpawnFrequency | 7 choices | Sometimes |
| Allow Mini-Map | Map.AllowMiniMap | On / Off | Off |
| Allow World Map | Map.AllowWorldMap | On / Off | On |
| All Known On Start | Map.MapAllKnown | On / Off | Off |
| Light Needed To Read Map | Map.MapNeedsLight | On / Off | On |

`(?)` = name not printed on the page, recased, check it.
