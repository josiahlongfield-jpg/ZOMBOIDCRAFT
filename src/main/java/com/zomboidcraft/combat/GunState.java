package com.zomboidcraft.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zomboidcraft.ZomboidCraft;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What's loaded in a gun: whether a magazine is in, how many rounds it holds, and whether the
 * gun has jammed (cleared by working the charging handle with the reload key).
 */
public record GunState(boolean mag, int rounds, boolean jammed) {
    public static final GunState EMPTY_MAG = new GunState(true, 0, false);

    public static final Codec<GunState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("mag", true).forGetter(GunState::mag),
            Codec.INT.optionalFieldOf("rounds", 0).forGetter(GunState::rounds),
            Codec.BOOL.optionalFieldOf("jammed", false).forGetter(GunState::jammed)
    ).apply(i, GunState::new));

    public static final StreamCodec<io.netty.buffer.ByteBuf, GunState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, GunState::mag,
            ByteBufCodecs.VAR_INT, GunState::rounds,
            ByteBufCodecs.BOOL, GunState::jammed,
            GunState::new);

    /** Loaded state of a gun. Changing it doesn't make the gun bob as if it were swapped. */
    public static final DataComponentType<GunState> TYPE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ZomboidCraft.id("gun"), DataComponentType.<GunState>builder()
                    .persistent(CODEC).networkSynchronized(STREAM_CODEC).ignoreSwapAnimation().build());

    /** Rounds in a loose magazine. */
    public static final DataComponentType<Integer> MAG_ROUNDS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ZomboidCraft.id("mag_rounds"), DataComponentType.<Integer>builder()
                    .persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).ignoreSwapAnimation().build());

    /** The gun's side-rail light is switched on (the light key). */
    public static final DataComponentType<Boolean> RAIL_ON = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ZomboidCraft.id("rail_on"), DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).ignoreSwapAnimation().build());

    /** The gun's right-rail laser is switched on (the laser key). */
    public static final DataComponentType<Boolean> LASER_ON = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ZomboidCraft.id("laser_on"), DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).ignoreSwapAnimation().build());

    /** The gun is set to full auto (the fire mode key), on a gun that has the setting. */
    public static final DataComponentType<Boolean> FULL_AUTO = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ZomboidCraft.id("full_auto"), DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).ignoreSwapAnimation().build());

    /** The trigger works the underbarrel grenade launcher rather than the rifle (the fire mode key). */
    public static final DataComponentType<Boolean> LAUNCHER_ON = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ZomboidCraft.id("launcher_on"), DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).ignoreSwapAnimation().build());

    /** A 40mm round is loaded in the underbarrel grenade launcher. */
    public static final DataComponentType<Boolean> LAUNCHER_LOADED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ZomboidCraft.id("launcher_loaded"), DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).ignoreSwapAnimation().build());

    public GunState withRounds(int rounds) {
        return new GunState(mag, rounds, jammed);
    }

    public GunState withJam(boolean jammed) {
        return new GunState(mag, rounds, jammed);
    }

    public static void register() {
        // loading the class registers the components
    }
}
