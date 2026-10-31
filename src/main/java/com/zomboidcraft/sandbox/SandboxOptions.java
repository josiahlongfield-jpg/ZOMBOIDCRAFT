package com.zomboidcraft.sandbox;

import static com.zomboidcraft.sandbox.SandboxGroup.*;
import static com.zomboidcraft.sandbox.SandboxOption.of;

import java.util.ArrayList;
import java.util.List;

/**
 * Every setting on the Sandbox page, in page order within each group. Each thread adds the lines for its own system
 * here: Zomboid's id and its B42 Apocalypse value where Zomboid has the setting, the config key it drives (or
 * .coming(value) until it's built), and any preset that differs from the default. Josiah's rule: Zomboid's value is our
 * default unless he has confirmed a change, and a confirmed rule is .fixed(...), shown but not changeable.
 */
final class SandboxOptions {
    static List<SandboxOption> all() {
        List<SandboxOption> o = new ArrayList<>();

        // ---- Zombie Population (ENTITY adds Zomboid's peak day, respawn, migration and rally settings) ----
        o.add(of(POPULATION, "ZombieConfig.PopulationMultiplier", "Population Multiplier")
                .tip("How many zombies the world holds, against the normal count. Indoors and out, every place scales by it.")
                .number("population.density", 0.25, 4).zomboid("1.0").owner("ENTITY").build());
        o.add(of(POPULATION, "zc.emptyHouses", "Empty Houses")
                .tip("Share of houses in towns, suburbs and farms with nobody left in them.")
                .number("population.emptyHouses", 0, 1).owner("ENTITY").build());
        o.add(of(POPULATION, "zc.hotspots", "Gathering Places")
                .tip("Hospitals, police stations, malls, fire stations and schools hold extra dead indoors.")
                .toggle("population.hotspots").owner("PERFORMANCE").build());

        // ---- Zombie Lore (ENTITY adds Zomboid's 29 lore settings with its zombieLore group) ----
        o.add(of(LORE, "zc.onlyHeadshotsKill", "Only Headshots Kill")
                .fixed("true", "Your rule: body and limb shots wear a zombie down, only the head kills it.").build());
        o.add(of(LORE, "zc.shotsFlinch", "Shots Only Flinch")
                .fixed("true", "Your rule: guns make zombies flinch, only blunt melee knocks them back.").build());
        o.add(of(LORE, "zc.sprinterChance", "Sprinter Share")
                .tip("Share of zombies that sprint once they spot you; about one in seven.")
                .number("zombies.sprinterChance", 0, 1).owner("ENTITY").build());
        o.add(of(LORE, "zc.bashDoors", "Zombies Break Doors")
                .tip("Zombies on your trail pound shut doors until they give way.")
                .toggle("zombies.bashDoors").owner("ENTITY").build());
        o.add(of(LORE, "zc.climbWindows", "Zombies Climb Through Windows")
                .tip("Zombies climb in and out through windows whose glass is gone.")
                .toggle("zombies.climbWindows").owner("ENTITY").build());
        o.add(of(LORE, "zc.memorySeconds", "Memory")
                .tip("Seconds a zombie keeps chasing after losing sight of you.")
                .number("senses.memorySeconds", 0, 300).owner("ENTITY").build());
        o.add(of(LORE, "zc.sightRange", "Sight")
                .tip("How far a zombie sees you in full light, in blocks.")
                .number("senses.sightRange", 4, 96).owner("ENTITY").build());
        o.add(of(LORE, "zc.hearing", "Hearing")
                .tip("Scales how far every noise carries to the dead.")
                .number("senses.hearing", 0.1, 4).owner("ENTITY").build());
        o.add(of(LORE, "zc.infection", "Infection")
                .tip("Bites and scratches can infect you.")
                .toggle("survival.infection").owner("SURVIVAL").build());
        o.add(of(LORE, "zc.infectionMinutes", "Infection Time")
                .tip("Minutes from infection to death.")
                .number("survival.infectionMinutes", 1, 600).owner("SURVIVAL").build());
        o.add(of(LORE, "zc.reanimate", "Infected Players Rise")
                .tip("A player who dies infected gets up as a zombie wearing their gear.")
                .toggle("survival.reanimate").owner("SURVIVAL").build());

        // ---- Time and Start ----
        o.add(of(TIME, "zc.endlessNight", "Endless Night")
                .fixed("true", "Your rule: the clouds keep the day nearly as dark as the night, and it always rains.").build());
        o.add(of(TIME, "zc.endlessDaylight", "Daylight")
                .tip("How bright the day gets under endless night, as a share of normal daylight.")
                .number("weather.endlessDaylight", 0.05, 1).owner("WEATHER").build());
        o.add(of(TIME, "NightDarkness", "Night Darkness")
                .tip("Night light as a share of normal; lower is darker.")
                .number("weather.nightLight", 0.1, 1).owner("WEATHER").build());
        o.add(of(TIME, "zc.spawnPicker", "Choose Where You Wake")
                .tip("Pick a town or city and wake inside a building with no dead in it.")
                .toggle("start.picker").owner("UI").build());

        // ---- Water, Power and Events (LANDSCAPE adds the district power failure, SURVIVAL the water) ----
        o.add(of(UTILITIES, "MetaEvent", "World Events")
                .tip("Air-raid sirens, far-off explosions, jets and helicopters crossing the map, heard by everyone.")
                .toggle("world.worldEvents").owner("AUDIO").build());
        o.add(of(UTILITIES, "zc.worldEventMinutes", "Time Between Events")
                .tip("About how many minutes between world events.")
                .number("world.worldEventMinutes", 1, 240).owner("AUDIO").build());

        // ---- Loot (LOOT adds Zomboid's category multipliers and rarity factors) ----
        o.add(of(LOOT, "zc.shopLoot", "Shop Loot")
                .tip("Shop shelves, coolers, cases and stockroom crates hold themed loot.")
                .toggle("world.shopLoot").owner("LOOT").build());
        o.add(of(LOOT, "zc.carLoot", "Car Loot")
                .tip("Car boots and the backs of vans hold loot.")
                .toggle("world.carLoot").owner("LOOT").build());

        // ---- Nature, Weather and Farming (SURVIVAL adds farming, fishing and foraging) ----
        o.add(of(NATURE, "ClimateCycle", "Climate Cycle")
                .choice("nature.climateCycle", "normal", "no_weather", "endless_rain", "endless_storm")
                .zomboid("normal").owner("WEATHER").build());
        o.add(of(NATURE, "FogCycle", "Fog Cycle")
                .choice("nature.fogCycle", "normal", "no_fog", "endless_fog").zomboid("normal").owner("WEATHER").build());
        o.add(of(NATURE, "Rain", "Rain")
                .choice("nature.rain", "very_dry", "dry", "normal", "rainy", "very_rainy").zomboid("normal").owner("WEATHER").build());
        o.add(of(NATURE, "Temperature", "Temperature")
                .tip("Air temperature; it matters once body temperature is on.")
                .choice("nature.temperature", "very_cold", "cold", "normal", "hot", "very_hot").zomboid("normal").owner("WEATHER").build());
        o.add(of(NATURE, "MaxFogIntensity", "Max Fog Intensity")
                .tip("Caps the fog you see. Zombies still go by the real fog.")
                .choice("nature.maxFog", "normal", "moderate", "low", "none").zomboid("normal").owner("WEATHER").client().build());
        o.add(of(NATURE, "MaxRainFxIntensity", "Max Rain Effects Intensity")
                .tip("Caps how much rain you see.")
                .choice("nature.maxRainFx", "normal", "moderate", "low").zomboid("normal").owner("WEATHER").client().build());
        o.add(of(NATURE, "EnableSnowOnGround", "Snow on Ground")
                .tip("There is no snow yet, so this does nothing for now.")
                .toggle("nature.snowOnGround").zomboid("true").owner("WEATHER").build());

        // ---- Character, Health and Food (SURVIVAL adds Zomboid's 28) ----
        o.add(of(CHARACTER, "zc.injuries", "Injuries")
                .tip("Injuries by body part: falls break legs, zombies scratch and bite where they reach.")
                .toggle("survival.injuries").owner("SURVIVAL").build());
        o.add(of(CHARACTER, "zc.bleeding", "Bleeding")
                .toggle("survival.bleeding").owner("SURVIVAL").build());
        o.add(of(CHARACTER, "zc.downed", "Downed in Multiplayer")
                .tip("A killing blow puts you down for a while instead, so a friend can revive you.")
                .toggle("survival.downed").owner("SURVIVAL").build());
        o.add(of(CHARACTER, "zc.deathResets", "Death Resets Everything")
                .fixed("true", "Your rule: death resets all skills and progress.").build());

        // ---- Skills and XP ----
        o.add(of(SKILLS, "XpMultiplier", "XP Multiplier")
                .tip("Multiplies all the XP you earn.")
                .number("progression.xpMultiplier", 0.1, 10).zomboid("1.0").owner("SURVIVAL").build());

        // ---- Vehicles and Fuel (VEHICLES adds the rest) ----
        o.add(of(VEHICLES, "EnableVehicles", "Vehicles")
                .tip("Real cars parked on the streets.")
                .toggle("world.streetCars").zomboid("true").owner("VEHICLES").build());
        o.add(of(VEHICLES, "zc.carUsable", "Cars Left Whole")
                .tip("Share of street cars left whole and drivable.")
                .number("world.carUsable", 0, 1).owner("VEHICLES").build());
        o.add(of(VEHICLES, "zc.carAbandoned", "Cars Abandoned")
                .number("world.carAbandoned", 0, 1).owner("VEHICLES").build());
        o.add(of(VEHICLES, "zc.carWrecked", "Cars Wrecked")
                .number("world.carWrecked", 0, 1).owner("VEHICLES").build());
        o.add(of(VEHICLES, "zc.carBurnt", "Cars Burnt Out")
                .number("world.carBurnt", 0, 1).owner("VEHICLES").build());
        o.add(of(VEHICLES, "zc.aircraftService", "Aircraft Need Servicing")
                .tip("Aircraft are found low on fuel, with a flat battery and worn out, and burn fuel as they fly.")
                .toggle("world.aircraftService").owner("VEHICLES").build());

        // ---- Map and Clean-up ----
        o.add(of(MAP, "zc.corpseMinutes", "Body Removal")
                .tip("Minutes a body lies before it's gone; 0 keeps them for good.")
                .number("corpses.minutes", 0, 10080).owner("ENTITY").build());
        o.add(of(MAP, "zc.maxBodies", "Most Bodies Kept")
                .number("corpses.maxBodies", 0, 5000).owner("PERFORMANCE").build());

        // ---- ZomboidCraft only ----
        o.add(of(ZOMBOIDCRAFT, "zc.stormAlarms", "Storm Alarms")
                .tip("Thunder and wind set off car and shop alarms, and the dead come to the noise.")
                .toggle("weather.stormAlarms").owner("WEATHER").build());
        o.add(of(ZOMBOIDCRAFT, "zc.lightningStrikes", "Lightning Strikes")
                .tip("Strong storms throw real lightning that starts fires.")
                .toggle("weather.lightningStrikes").owner("WEATHER").build());
        o.add(of(ZOMBOIDCRAFT, "zc.hail", "Hail")
                .tip("Hail in severe storms hurts anyone outside without a helmet.")
                .toggle("weather.hail").owner("WEATHER").build());
        o.add(of(ZOMBOIDCRAFT, "zc.windDamage", "Cyclone Winds")
                .tip("Severe storm winds shove you and the dead around and blow in windows.")
                .toggle("weather.windDamage").owner("WEATHER").build());
        o.add(of(ZOMBOIDCRAFT, "zc.airStrikes", "Air Strikes")
                .tip("Jets strafe or bomb a crowd of zombies, or the Apache hunts a horde.")
                .toggle("world.airStrikes").owner("FLIGHT").build());
        o.add(of(ZOMBOIDCRAFT, "zc.breachDays", "Lab Breach Surges")
                .tip("In-game days between breach surges under Ridgeview Labs; 0 turns them off.")
                .number("population.respawnDays", 0, 365).owner("NETHER").build());
        o.add(of(ZOMBOIDCRAFT, "zc.digRules", "Digging Rules")
                .tip("You can only dig a few blocks under the ground, and walls need the right skill to break.")
                .toggle("digging.rules").owner("BASES").build());
        o.add(of(ZOMBOIDCRAFT, "zc.digDepth", "Dig Depth")
                .tip("How many blocks under the natural ground you can dig.")
                .number("digging.floorDepth", 0, 64).owner("BASES").build());
        return o;
    }

    private SandboxOptions() {
    }
}
