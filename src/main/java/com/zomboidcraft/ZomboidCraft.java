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
        Skills.register();
        com.zomboidcraft.zombie.ZombieLook.register();
        ModBlocks.register();
        com.zomboidcraft.world.blocks.CityBlocks.register();
        com.zomboidcraft.world.cars.Cars.register();
        com.zomboidcraft.world.cars.CarNet.register();
        com.zomboidcraft.combat.GunState.register();
        com.zomboidcraft.combat.Attachments.register();
        com.zomboidcraft.combat.ModSounds.register();
        com.zomboidcraft.combat.ModParticles.register();
        ModItems.register();
        com.zomboidcraft.carry.Carry.register();
        com.zomboidcraft.loot.Nearby.register();
        Loot.register();
        com.zomboidcraft.barricade.Barricades.register();
        SurvivalEvents.register();
        ZombieEvents.register();
        ZombieSpawner.register();
        com.zomboidcraft.zombie.Hordes.register();
        ZcCommand.register();
        com.zomboidcraft.combat.CombatNet.register();
        com.zomboidcraft.combat.Guns.register();
        com.zomboidcraft.combat.Downed.register();
        com.zomboidcraft.combat.Stamina.register();
        com.zomboidcraft.combat.Grabs.register();
        com.zomboidcraft.combat.CombatEvents.register();
        CityPlacer.register();
        com.zomboidcraft.world.MobFilter.register();
        com.zomboidcraft.world.weather.WeatherDirector.register();
        com.zomboidcraft.world.gen.Worldgen.register();
        com.zomboidcraft.world.gen.CountyCommand.register();
        LOGGER.info("The dead walk.");
    }
}
