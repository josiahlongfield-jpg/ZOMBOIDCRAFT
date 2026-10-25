package com.zomboidcraft.combat;

import com.zomboidcraft.ZomboidCraft;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/** The mod's own particles. */
public final class ModParticles {
    /**
     * A speck of dust lit by a laser, glowing whatever the light around it. Drawn only on clients (RailLights),
     * which pass its size, brightness and life in the three speed numbers.
     */
    public static final SimpleParticleType LASER_MOTE = Registry.register(BuiltInRegistries.PARTICLE_TYPE,
            ZomboidCraft.id("laser_mote"), FabricParticleTypes.simple(true));

    public static void register() {
        // loading the class registers the particles
    }

    private ModParticles() {
    }
}
