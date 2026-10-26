package com.zomboidcraft.world.weather;

import com.zomboidcraft.ZomboidCraft;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * The weather's own particles, drawn on the client (client/weather/WeatherFx): mist that drifts
 * over the streets, made of particles so it shows under shader packs that ignore the game's fog,
 * litter the wind blows along, and the smoke and glow of fires burning out across the city.
 */
public final class WeatherParticles {
    public static final SimpleParticleType MIST = register("mist");
    public static final SimpleParticleType DEBRIS = register("debris");
    /** Smoke rising from a fire somewhere out in the city, leaning with the wind. */
    public static final SimpleParticleType PLUME = register("plume");
    /** The orange glow at the foot of a fire. */
    public static final SimpleParticleType EMBER = register("ember");

    private static SimpleParticleType register(String name) {
        return Registry.register(BuiltInRegistries.PARTICLE_TYPE, ZomboidCraft.id(name), FabricParticleTypes.simple(true));
    }

    public static void register() {
        // Loading the class registers the types.
    }

    private WeatherParticles() {
    }
}
