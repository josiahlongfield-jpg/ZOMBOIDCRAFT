package com.zomboidcraft.sandbox;

import static com.zomboidcraft.sandbox.SandboxGroup.*;
import static com.zomboidcraft.sandbox.SandboxOption.of;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Every setting on the Sandbox page, in page order within each group.
 *
 * Zomboid's own 269 come from data/zomboidcraft/sandbox/zomboid.json (B42.21's list: id, label, tooltip, range and
 * its Apocalypse value), and start as "coming" at Zomboid's Apocalypse value, Josiah's rule. When a thread builds what
 * one drives it wires it in {@link #wires()}: the config key, and our own choices or range if they differ. Settings
 * only ZomboidCraft has, and Josiah's fixed rules, are in {@link #ours()}.
 */
final class SandboxOptions {
    private static final String ZOMBOID = "/data/zomboidcraft/sandbox/zomboid.json";

    /** One of Zomboid's ZombieConfig numbers wired to its zombiePopulation field. */
    private static void pop(Map<String, UnaryOperator<SandboxOption.Builder>> w, String id, String field, double min, double max) {
        w.put("ZombieConfig." + id, b -> b.number("zombiePopulation." + field, min, max).owner("ENTITY"));
    }

    /** A Zombie Lore choice wired to its zombieLore field. */
    private static void lore(Map<String, UnaryOperator<SandboxOption.Builder>> w, String id, String field, String... choices) {
        w.put("ZombieLore." + id, b -> b.choice("zombieLore." + field, choices).owner("ENTITY"));
    }

    /** Zomboid settings our systems drive, by Zomboid's id. */
    private static Map<String, UnaryOperator<SandboxOption.Builder>> wires() {
        Map<String, UnaryOperator<SandboxOption.Builder>> w = new HashMap<>();
        // Zombie Population (ENTITY)
        w.put("ZombieConfig.PopulationMultiplier", b -> b.number("population.density", 0.25, 4).owner("ENTITY"));
        w.put("Zombies", b -> b.choice("zombiePopulation.count", "insane", "very_high", "high", "normal", "low", "none").owner("ENTITY"));
        w.put("Distribution", b -> b.choice("zombiePopulation.distribution", "urban_focused", "uniform").owner("ENTITY"));
        w.put("ZombieVoronoiNoise", b -> b.toggle("zombiePopulation.voronoiNoise").owner("ENTITY"));
        w.put("ZombieRespawn", b -> b.choice("zombiePopulation.respawn", "none", "low", "normal", "high").owner("ENTITY"));
        w.put("ZombieMigrate", b -> b.toggle("zombiePopulation.migrate").owner("ENTITY"));
        w.put("ZombieAttractionMultiplier", b -> b.number("zombiePopulation.attractionMultiplier", 0, 100).owner("ENTITY"));
        pop(w, "PopulationStartMultiplier", "startMultiplier", 0, 4);
        pop(w, "PopulationPeakMultiplier", "peakMultiplier", 0, 4);
        pop(w, "PopulationPeakDay", "peakDay", 1, 365);
        pop(w, "RespawnHours", "respawnHours", 0, 8760);
        pop(w, "RespawnUnseenHours", "respawnUnseenHours", 0, 8760);
        pop(w, "RespawnMultiplier", "respawnMultiplier", 0, 1);
        pop(w, "RedistributeHours", "redistributeHours", 0, 8760);
        pop(w, "FollowSoundDistance", "followSoundDistance", 10, 1000);
        pop(w, "RallyGroupSize", "rallyGroupSize", 0, 1000);
        pop(w, "RallyGroupSizeVariance", "rallyGroupSizeVariance", 0, 100);
        pop(w, "RallyTravelDistance", "rallyTravelDistance", 5, 50);
        pop(w, "RallyGroupSeparation", "rallyGroupSeparation", 5, 25);
        pop(w, "RallyGroupRadius", "rallyGroupRadius", 1, 10);
        pop(w, "ZombiesCountBeforeDelete", "countBeforeDelete", 0, 5000);
        // Zombie Lore (ENTITY): every one of Zomboid's 29, its own choices in Zomboid's order (ZcConfig.ZombieLore)
        lore(w, "Speed", "speed", "sprinters", "fast_shamblers", "shamblers", "random");
        w.put("ZombieLore.SprinterPercentage", b -> b.number("zombieLore.sprinterPercent", 0, 100).owner("ENTITY"));
        lore(w, "Strength", "strength", "superhuman", "normal", "weak", "random");
        lore(w, "Toughness", "toughness", "tough", "normal", "fragile", "random");
        lore(w, "Transmission", "transmission", "blood_and_saliva", "saliva_only", "everyones_infected", "none");
        lore(w, "Mortality", "mortality", "instant", "seconds_0_30", "minutes_0_1", "hours_0_12", "days_2_3", "weeks_1_2", "never");
        lore(w, "Reanimate", "reanimate", "instant", "seconds_0_30", "minutes_0_1", "hours_0_12", "days_2_3", "weeks_1_2");
        lore(w, "Cognition", "cognition", "navigate_and_use_doors", "navigate", "basic_navigation", "random");
        w.put("ZombieLore.DoorOpeningPercentage", b -> b.number("zombieLore.doorOpeningPercent", 0, 100).owner("ENTITY"));
        lore(w, "CrawlUnderVehicle", "crawlUnderVehicle", "crawlers_only", "extremely_rare", "rare", "sometimes", "often",
                "very_often", "extremely_often");
        lore(w, "Memory", "memory", "long", "normal", "short", "none", "random", "random_normal_none");
        lore(w, "Sight", "sight", "eagle", "normal", "poor", "random", "random_normal_poor");
        lore(w, "Hearing", "hearing", "pinpoint", "normal", "poor", "random", "random_normal_poor");
        w.put("ZombieLore.SpottedLogic", b -> b.toggle("zombieLore.stealthSystem").owner("ENTITY"));
        w.put("ZombieLore.ThumpNoChasing", b -> b.toggle("zombieLore.environmentalAttacks").owner("ENTITY"));
        w.put("ZombieLore.ThumpOnConstruction", b -> b.toggle("zombieLore.damageConstruction").owner("ENTITY"));
        lore(w, "ActiveOnly", "activeOnly", "both", "night", "day");
        w.put("ZombieLore.TriggerHouseAlarm", b -> b.toggle("zombieLore.houseAlarms").owner("ENTITY"));
        w.put("ZombieLore.ZombiesDragDown", b -> b.toggle("zombieLore.dragDown").owner("ENTITY"));
        w.put("ZombieLore.ZombiesCrawlersDragDown", b -> b.toggle("zombieLore.crawlersDragDown").owner("ENTITY"));
        w.put("ZombieLore.ZombiesFenceLunge", b -> b.toggle("zombieLore.lunge").owner("ENTITY"));
        w.put("ZombieLore.ZombiesArmorFactor", b -> b.number("zombieLore.armorFactor", 0, 100).owner("ENTITY"));
        w.put("ZombieLore.ZombiesMaxDefense", b -> b.number("zombieLore.maxDefense", 0, 100).owner("ENTITY"));
        w.put("ZombieLore.ChanceOfAttachedWeapon", b -> b.number("zombieLore.attachedWeaponPercent", 0, 100).owner("ENTITY"));
        w.put("ZombieLore.ZombiesFallDamage", b -> b.number("zombieLore.fallDamage", 0, 100).owner("ENTITY"));
        lore(w, "DisableFakeDead", "fakeDead", "world_zombies", "world_and_combat", "off");
        lore(w, "PlayerSpawnZombieRemoval", "spawnArea", "inside_the_building_and_around_it", "inside_the_building",
                "inside_the_room", "none");
        w.put("ZombieLore.FenceThumpersRequired", b -> b.number("zombieLore.fenceThumpersRequired", -1, 100).owner("ENTITY"));
        w.put("ZombieLore.FenceDamageMultiplier", b -> b.number("zombieLore.fenceDamageMultiplier", 0.01, 100).owner("ENTITY"));
        // Nature (WEATHER): our own choices, Zomboid's endless snow and blizzard wait for snow
        w.put("ClimateCycle", b -> b.choice("nature.climateCycle", "normal", "no_weather", "endless_rain", "endless_storm").owner("WEATHER"));
        w.put("FogCycle", b -> b.choice("nature.fogCycle", "normal", "no_fog", "endless_fog").owner("WEATHER"));
        w.put("Rain", b -> b.choice("nature.rain", "very_dry", "dry", "normal", "rainy", "very_rainy").owner("WEATHER"));
        w.put("Temperature", b -> b.choice("nature.temperature", "very_cold", "cold", "normal", "hot", "very_hot").owner("WEATHER"));
        w.put("MaxFogIntensity", b -> b.choice("nature.maxFog", "normal", "moderate", "low", "none").owner("WEATHER").client());
        w.put("MaxRainFxIntensity", b -> b.choice("nature.maxRainFx", "normal", "moderate", "low").owner("WEATHER").client());
        w.put("EnableSnowOnGround", b -> b.toggle("nature.snowOnGround").owner("WEATHER")
                .tip("There is no snow yet, so this does nothing for now."));
        // Skills (SURVIVAL): one global multiplier for now
        w.put("MultiplierConfig.Global", b -> b.number("progression.xpMultiplier", 0, 1000).owner("SURVIVAL"));
        // Far-off events (AUDIO): Zomboid's three choices
        w.put("MetaEvent", b -> b.choice("world.metaEvents", "never", "sometimes", "often").owner("AUDIO"));
        // Vehicles (VEHICLES)
        w.put("EnableVehicles", b -> b.toggle("world.streetCars").owner("VEHICLES"));
        w.put("CarSpawnRate", b -> b.choice("world.carSpawnRate", "none", "very_low", "low", "normal", "high").owner("VEHICLES")
                .tip("How many parking spots hold a car or truck. Cars at auto shops always stay. New chunks only."));
        w.put("VehicleEasyUse", b -> b.toggle("world.vehicleEasyUse").owner("VEHICLES"));
        w.put("CarGeneralCondition", b -> b.choice("world.carCondition", "very_low", "low", "normal", "high", "very_high").owner("VEHICLES"));
        w.put("CarDamageOnImpact", b -> b.choice("world.carCrashDamage", "very_low", "low", "normal", "high", "very_high").owner("VEHICLES"));
        w.put("ChanceHasGas", b -> b.choice("world.carChanceHasGas", "low", "normal", "high").owner("VEHICLES"));
        w.put("InitialGas", b -> b.choice("world.carInitialGas", "very_low", "low", "normal", "high", "very_high", "full").owner("VEHICLES"));
        w.put("CarGasConsumption", b -> b.number("world.carGasConsumption", 0, 100).owner("VEHICLES"));
        w.put("FuelStationGasInfinite", b -> b.toggle("world.fuelStationGasInfinite").owner("VEHICLES"));
        w.put("FuelStationGasMin", b -> b.number("world.fuelStationGasMin", 0, 1).owner("VEHICLES"));
        w.put("FuelStationGasMax", b -> b.number("world.fuelStationGasMax", 0, 1).owner("VEHICLES"));
        w.put("FuelStationGasEmptyChance", b -> b.number("world.fuelStationEmptyChance", 0, 100).owner("VEHICLES"));
        w.put("LockedCar", b -> b.choice("world.carLocked", "never", "extremely_rare", "rare", "sometimes", "often", "very_often").owner("VEHICLES"));
        w.put("CarAlarm", b -> b.choice("world.carAlarm", "never", "extremely_rare", "rare", "sometimes", "often", "very_often").owner("VEHICLES"));
        w.put("SirenEffectsZombies", b -> b.toggle("world.sirenEffectsZombies").owner("VEHICLES"));
        w.put("SirenShutoffHours", b -> b.number("world.sirenShutoffHours", 0, 168).owner("VEHICLES"));
        w.put("PlayerDamageFromCrash", b -> b.toggle("world.playerDamageFromCrash").owner("VEHICLES"));
        w.put("RecentlySurvivorVehicles", b -> b.choice("world.survivorCars", "none", "low", "normal", "high").owner("VEHICLES"));
        w.put("TrafficJam", b -> b.toggle("world.trafficJams").owner("LANDSCAPE"));
        w.put("DamageToPlayerFromHitByACar", b -> b.number("world.carHitDamage", 1, 4).owner("VEHICLES"));
        // Firearms (WEAPONS): combat/Firearms. Use Damage Chance says who a shot strikes just by being aimed at;
        // jams start at 0 since Josiah cut them on 10-26
        w.put("FirearmUseDamageChance", b -> b.choice("guns.useDamageChance", "both", "zombies", "off").owner("WEAPONS")
                .tip("Shots strike what they're aimed at. Both: zombies and players; Zombies: only zombies; Off: Aiming decides if a shot lands."));
        w.put("FirearmNoiseMultiplier", b -> b.number("guns.noiseMultiplier", 0.2, 2).owner("WEAPONS"));
        w.put("FirearmJamMultiplier", b -> b.number("guns.jamMultiplier", 0, 10).owner("WEAPONS"));
        w.put("FirearmMoodleMultiplier", b -> b.number("guns.moodleMultiplier", 0, 10).owner("WEAPONS"));
        w.put("FirearmWeatherMultiplier", b -> b.number("guns.weatherMultiplier", 0, 10).owner("WEAPONS")
                .tip("How much wind, rain and fog throw your aim off, out under the sky. Kept light, as the rain never stops."));
        w.put("FirearmHeadGearEffect", b -> b.toggle("guns.headgearEffect").owner("WEAPONS"));
        // Fire (WEAPONS, combat/ContainedFire): off keeps a fire to what it was started on and a little round it
        w.put("FireSpread", b -> b.toggle("throwing.fireZomboid").owner("WEAPONS"));
        return w;
    }

    /** Settings only ZomboidCraft has, and Josiah's fixed rules, after Zomboid's in their group. */
    private static List<SandboxOption.Builder> ours() {
        List<SandboxOption.Builder> o = new ArrayList<>();
        o.add(of(POPULATION, "zc.emptyHouses", "Empty Houses")
                .tip("Share of houses in towns, suburbs and farms with nobody left in them.")
                .number("population.emptyHouses", 0, 1).owner("ENTITY"));
        o.add(of(POPULATION, "zc.hotspots", "Gathering Places")
                .tip("Hospitals, police stations, malls, fire stations and schools hold extra dead indoors.")
                .toggle("population.hotspots").owner("PERFORMANCE"));

        o.add(of(LORE, "zc.onlyHeadshotsKill", "Only Headshots Kill")
                .fixed("true", "Your rule: body and limb shots wear a zombie down, only the head kills it."));
        o.add(of(LORE, "zc.shotsFlinch", "Shots Only Flinch")
                .fixed("true", "Your rule: guns make zombies flinch, only blunt melee knocks them back."));
        o.add(of(LORE, "zc.crawlNeedsBothLegs", "Crawl Only Without Legs")
                .fixed("true", "Your rule: a zombie crawls only with both legs gone."));

        o.add(of(TIME, "zc.endlessNight", "Endless Night")
                .fixed("true", "Your rule: the clouds keep the day nearly as dark as the night, and it always rains."));
        o.add(of(TIME, "zc.endlessDaylight", "Daylight")
                .tip("How bright the day gets under endless night, as a share of normal daylight.")
                .number("weather.endlessDaylight", 0.05, 1).owner("WEATHER"));
        o.add(of(TIME, "zc.nightLight", "Night Light")
                .tip("Night light as a share of normal; lower is darker.")
                .number("weather.nightLight", 0.1, 1).owner("WEATHER"));
        o.add(of(TIME, "zc.spawnPicker", "Choose Where You Wake")
                .tip("Pick a town or city and wake inside a building with no dead in it.")
                .toggle("start.picker").owner("UI"));

        o.add(of(UTILITIES, "zc.worldEvents", "World Events")
                .tip("Air-raid sirens, far-off explosions, jets and helicopters crossing the map, heard by everyone.")
                .toggle("world.worldEvents").owner("AUDIO"));
        o.add(of(UTILITIES, "zc.worldEventMinutes", "Time Between World Events")
                .tip("About how many minutes between world events.")
                .number("world.worldEventMinutes", 1, 240).owner("AUDIO"));
        o.add(of(UTILITIES, "zc.broadcasts", "Outbreak Broadcasts")
                .tip("News, the emergency broadcast, the military and survivors on the two-way and military radios, as captions.")
                .toggle("broadcast.enabled").owner("AUDIO"));
        o.add(of(UTILITIES, "zc.broadcastDayOffset", "Broadcasts Start On Day")
                .tip("Days already gone on the broadcast timeline when the world starts: 9 or more and the news is already off air.")
                .number("broadcast.dayOffset", 0, 60).owner("AUDIO"));

        o.add(of(LOOT, "zc.lootNeverScales", "Loot Never Scales With Distance")
                .fixed("true", "Your rule: loot is the same however far you are from where you started."));
        o.add(of(LOOT, "zc.shopLoot", "Shop Loot")
                .tip("Shop shelves, coolers, cases and stockroom crates hold themed loot.")
                .toggle("world.shopLoot").owner("LOOT"));
        o.add(of(LOOT, "zc.carLoot", "Car Loot")
                .tip("Car boots and the backs of vans hold loot.")
                .toggle("world.carLoot").owner("LOOT"));

        o.add(of(CHARACTER, "zc.deathResets", "Death Resets Everything")
                .fixed("true", "Your rule: death resets all skills and progress; your body rises only if you died infected."));
        o.add(of(CHARACTER, "zc.injuries", "Injuries")
                .tip("Injuries by body part: falls break legs, zombies scratch and bite where they reach.")
                .toggle("survival.injuries").owner("SURVIVAL"));
        o.add(of(CHARACTER, "zc.infection", "Infection")
                .tip("Bites and scratches can infect you.")
                .toggle("survival.infection").owner("SURVIVAL"));
        o.add(of(CHARACTER, "zc.infectionMinutes", "Infection Time")
                .tip("Minutes from infection to death.")
                .number("survival.infectionMinutes", 1, 600).owner("SURVIVAL"));
        o.add(of(CHARACTER, "zc.downed", "Downed in Multiplayer")
                .tip("A killing blow puts you down for a while instead, so a friend can revive you.")
                .toggle("survival.downed").owner("SURVIVAL"));

        o.add(of(VEHICLES, "zc.carUsable", "Cars Left Whole")
                .tip("Share of street cars left whole and drivable.")
                .number("world.carUsable", 0, 1).owner("VEHICLES"));
        o.add(of(VEHICLES, "zc.carAbandoned", "Cars Abandoned")
                .number("world.carAbandoned", 0, 1).owner("VEHICLES"));
        o.add(of(VEHICLES, "zc.carWrecked", "Cars Wrecked")
                .number("world.carWrecked", 0, 1).owner("VEHICLES"));
        o.add(of(VEHICLES, "zc.carBurnt", "Cars Burnt Out")
                .number("world.carBurnt", 0, 1).owner("VEHICLES"));
        o.add(of(VEHICLES, "zc.aircraftService", "Aircraft Need Servicing")
                .tip("Aircraft are found low on fuel, with a flat battery and worn out, and burn fuel as they fly.")
                .toggle("world.aircraftService").owner("VEHICLES"));

        o.add(of(MAP, "zc.corpseMinutes", "Body Removal")
                .tip("Minutes a body lies before it's gone; 0 keeps them for good.")
                .number("corpses.minutes", 0, 10080).owner("ENTITY"));
        o.add(of(MAP, "zc.maxBodies", "Most Bodies Kept")
                .number("corpses.maxBodies", 0, 5000).owner("PERFORMANCE"));

        o.add(of(ZOMBOIDCRAFT, "zc.stormAlarms", "Storm Alarms")
                .tip("Thunder and wind set off car and shop alarms, and the dead come to the noise.")
                .toggle("weather.stormAlarms").owner("WEATHER"));
        o.add(of(ZOMBOIDCRAFT, "zc.lightningStrikes", "Lightning Strikes")
                .tip("Strong storms throw real lightning that starts fires.")
                .toggle("weather.lightningStrikes").owner("WEATHER"));
        o.add(of(ZOMBOIDCRAFT, "zc.hail", "Hail")
                .tip("Hail in severe storms hurts anyone outside without a helmet.")
                .toggle("weather.hail").owner("WEATHER"));
        o.add(of(ZOMBOIDCRAFT, "zc.windDamage", "Cyclone Winds")
                .tip("Severe storm winds shove you and the dead around and blow in windows.")
                .toggle("weather.windDamage").owner("WEATHER"));
        o.add(of(ZOMBOIDCRAFT, "zc.airStrikes", "Air Strikes")
                .tip("Jets strafe or bomb a crowd of zombies, or the Apache hunts a horde.")
                .toggle("world.airStrikes").owner("FLIGHT"));
        o.add(of(ZOMBOIDCRAFT, "zc.breachDays", "Lab Breach Surges")
                .tip("In-game days between breach surges under Ridgeview Labs; 0 turns them off.")
                .number("population.respawnDays", 0, 365).owner("NETHER"));
        o.add(of(ZOMBOIDCRAFT, "zc.digRules", "Digging Rules")
                .tip("You can only dig a few blocks under the ground, and walls need the right skill to break.")
                .toggle("digging.rules").owner("BASES"));
        o.add(of(ZOMBOIDCRAFT, "zc.digDepth", "Dig Depth")
                .tip("How many blocks under the natural ground you can dig.")
                .number("digging.floorDepth", 0, 64).owner("BASES"));
        return o;
    }

    static List<SandboxOption> all() {
        Map<SandboxGroup, List<SandboxOption.Builder>> byGroup = new LinkedHashMap<>();
        for (SandboxGroup g : SandboxGroup.values()) {
            byGroup.put(g, new ArrayList<>());
        }
        Map<String, UnaryOperator<SandboxOption.Builder>> wires = wires();
        for (SandboxOption.Builder b : zomboid()) {
            UnaryOperator<SandboxOption.Builder> wire = wires.remove(b.id);
            byGroup.get(b.group).add(wire != null ? wire.apply(b) : b);
        }
        if (!wires.isEmpty()) {
            throw new IllegalStateException("Sandbox wires for settings Zomboid doesn't have: " + wires.keySet());
        }
        for (SandboxOption.Builder b : ours()) {
            byGroup.get(b.group).add(b);
        }
        List<SandboxOption> all = new ArrayList<>();
        byGroup.values().forEach(list -> list.forEach(b -> all.add(b.build())));
        return all;
    }

    /** Zomboid's settings from the B42 list, each coming at its Apocalypse value until something wires it. */
    private static List<SandboxOption.Builder> zomboid() {
        List<SandboxOption.Builder> out = new ArrayList<>();
        try (InputStream in = SandboxOptions.class.getResourceAsStream(ZOMBOID)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + ZOMBOID);
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject root = new Gson().fromJson(reader, JsonObject.class);
                for (JsonElement e : root.getAsJsonArray("options")) {
                    JsonObject o = e.getAsJsonObject();
                    SandboxGroup group = SandboxGroup.byId(o.get("group").getAsString());
                    SandboxOption.Builder b = of(group, o.get("id").getAsString(), o.get("label").getAsString())
                            .tip(o.get("tooltip").getAsString()).owner(o.get("owner").getAsString());
                    String apocalypse = o.get("default").getAsString();
                    switch (o.get("kind").getAsString()) {
                        case "toggle" -> b.kind(SandboxOption.Kind.TOGGLE);
                        case "number" -> b.kind(SandboxOption.Kind.NUMBER)
                                .range(o.get("min").getAsDouble(), o.get("max").getAsDouble());
                        case "choice" -> {
                            List<String> choices = new ArrayList<>();
                            o.getAsJsonArray("choices").forEach(c -> choices.add(c.getAsString()));
                            b.kind(SandboxOption.Kind.CHOICE).choices(choices);
                        }
                        default -> b.kind(SandboxOption.Kind.TEXT);
                    }
                    b.coming(apocalypse).zomboid(apocalypse);
                    out.add(b);
                }
            }
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Couldn't read " + ZOMBOID, ex);
        }
        return out;
    }

    private SandboxOptions() {
    }
}
