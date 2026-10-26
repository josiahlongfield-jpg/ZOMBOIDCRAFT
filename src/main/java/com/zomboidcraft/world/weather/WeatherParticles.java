package com.zomboidcraft.world.weather;

import com.zomboidcraft.ZomboidCraft;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * The weather's own particles, drawn on the client (client/weather/WeatherFx): mist that drifts
 * over the streets, made of particles so it shows under shader packs that ignore the game's fog,
 * and litter the wind blows along.
 */
public final class WeatherParticles {
    public static final SimpleParticleType MIST = register("mist");
    public static final SimpleParticleType DEBRIS = register("debris");

    private static SimpleParticleType register(String name) {
        return Registry.register(BuiltInRegistries.PARTICLE_TYPE, ZomboidCraft.id(name), FabricParticleTypes.simple(true));
    }

    public static void register() {
        // Loading the class registers the types.
    }

    private WeatherParticles() {
    }
}
