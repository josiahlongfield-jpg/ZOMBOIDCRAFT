package com.zomboidcraft;

import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.survival.SurvivalEvents;
import com.zomboidcraft.survival.SurvivalStats;
import com.zomboidcraft.world.CityPlacer;
import com.zomboidcraft.zombie.ZombieEvents;
import com.zomboidcraft.zombie.ZombieSpawner;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZomboidCraft implements ModInitializer {
    public static final String MOD_ID = "zomboidcraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ZcConfig.load();
        SurvivalStats.register();
        com.zomboidcraft.survival.Nutrition.register();
        com.zomboidcraft.survival.Sleep.register();
        Skills.register();
        com.zomboidcraft.skills.SkillBooks.register();
        com.zomboidcraft.zombie.ZombieLook.register();
        ModBlocks.register();
        com.zomboidcraft.building.BuildBlocks.register();
        com.zomboidcraft.world.blocks.CityBlocks.register();
        com.zomboidcraft.world.cars.Cars.register();
        com.zomboidcraft.world.cars.CarNet.register();
        com.zomboidcraft.world.cars.Mechanics.register();
        com.zomboidcraft.world.cars.CarHoist.register();
        com.zomboidcraft.world.cars.ShopTool.register();
        com.zomboidcraft.world.cars.CarKeys.register();
        com.zomboidcraft.world.cars.CarSounds.register();
        com.zomboidcraft.world.cars.AirSounds.register();
        com.zomboidcraft.world.cars.AirReadiness.register();
        com.zomboidcraft.world.cars.AirNet.register();
        com.zomboidcraft.world.events.WorldEvents.register();
        com.zomboidcraft.world.events.MetaEvents.register();
        com.zomboidcraft.world.NetherSeal.register();
        com.zomboidcraft.world.files.LabFiles.register();
        com.zomboidcraft.world.Keypads.register();
        com.zomboidcraft.world.Breach.register();
        com.zomboidcraft.world.SporeHaze.register();
        com.zomboidcraft.world.trains.Trains.register();
        com.zomboidcraft.world.trains.TrainCommand.register();
        com.zomboidcraft.world.lifts.Lifts.register();
        com.zomboidcraft.world.ferry.Ferries.register();
        com.zomboidcraft.world.blocks.LightSwitch.register();
        com.zomboidcraft.combat.GunState.register();
        com.zomboidcraft.combat.Attachments.register();
        com.zomboidcraft.combat.GunFinish.register();
        com.zomboidcraft.combat.Repairs.register();
        com.zomboidcraft.combat.Wear.register();
        com.zomboidcraft.combat.SpearItem.register();
        com.zomboidcraft.combat.ModSounds.register();
        com.zomboidcraft.combat.throwing.Throwing.register();
        ModItems.register();
        com.zomboidcraft.loot.items.LootItems.register();
        com.zomboidcraft.clothing.ClothingItems.register();
        com.zomboidcraft.loot.items.LootHooks.register();
        com.zomboidcraft.carry.Carry.register();
        com.zomboidcraft.spawn.SpawnPicker.register();
        com.zomboidcraft.gear.Headgear.register();
        com.zomboidcraft.gear.Radios.register();
        com.zomboidcraft.gear.Broadcasts.register();
        com.zomboidcraft.loot.Nearby.register();
        Loot.register();
        com.zomboidcraft.combat.rolls.Rolls.register();
        com.zomboidcraft.barricade.Barricades.register();
        com.zomboidcraft.building.DigRules.register();
        com.zomboidcraft.building.Builder.register();
        com.zomboidcraft.barricade.CrowbarItem.register();
        SurvivalEvents.register();
        com.zomboidcraft.survival.DeathCard.register();
        com.zomboidcraft.survival.Injuries.register();
        com.zomboidcraft.survival.TimedActions.register();
        ZombieEvents.register();
        com.zomboidcraft.zombie.Noise.register();
        com.zomboidcraft.zombie.Acts.register();
        ZombieSpawner.register();
        com.zomboidcraft.zombie.PerfCommand.register();
        com.zomboidcraft.zombie.population.Population.register();
        com.zomboidcraft.zombie.Hordes.register();
        ZcCommand.register();
        com.zomboidcraft.sandbox.Sandbox.register();
        Cinematic.register();
        com.zomboidcraft.combat.CombatNet.register();
        com.zomboidcraft.combat.Impacts.register();
        com.zomboidcraft.world.map.MapNet.register();
        com.zomboidcraft.combat.Guns.register();
        com.zomboidcraft.combat.Downed.register();
        com.zomboidcraft.combat.Dismember.register();
        com.zomboidcraft.combat.Deaths.register();
        com.zomboidcraft.zombie.Corpses.register();
        com.zomboidcraft.creature.Harvestmen.register();
        com.zomboidcraft.npc.Npcs.register();
        com.zomboidcraft.combat.StuckBolts.register();
        com.zomboidcraft.combat.Stamina.register();
        com.zomboidcraft.combat.GunRaise.register();
        com.zomboidcraft.combat.Grabs.register();
        com.zomboidcraft.combat.CombatEvents.register();
        CityPlacer.register();
        com.zomboidcraft.world.MobFilter.register();
        com.zomboidcraft.world.weather.WeatherDirector.register();
        com.zomboidcraft.world.gen.Worldgen.register();
        com.zomboidcraft.world.gen.CountyCommand.register();
        com.zomboidcraft.world.gen.CountyRails.register();
        LOGGER.info("The dead walk.");
    }
}
