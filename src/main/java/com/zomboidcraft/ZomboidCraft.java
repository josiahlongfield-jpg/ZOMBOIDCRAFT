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
        ModItems.register();
        Loot.register();
        com.zomboidcraft.barricade.Barricades.register();
        SurvivalEvents.register();
        ZombieEvents.register();
        ZombieSpawner.register();
        ZcCommand.register();
        CityPlacer.register();
        com.zomboidcraft.world.MobFilter.register();
        LOGGER.info("The dead walk.");
    }
}
