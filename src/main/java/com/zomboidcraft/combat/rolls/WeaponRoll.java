package com.zomboidcraft.combat.rolls;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zomboidcraft.ZomboidCraft;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * How good this copy of a weapon is, rolled once where it was found (see {@link Rolls}).
 *
 * @param tier   1 (Rough) to 5 (Pristine)
 * @param stats  where each stat landed inside the model's range, 0 to 100, by {@link Stat} ordinal
 * @param flaws  {@link Flaw} bits
 * @param place  where it was found: home, gun_cabinet, gun_shop, police, military, case or unknown
 * @param worked whether its tier was raised at the bench
 */
public record WeaponRoll(int tier, List<Integer> stats, int flaws, String place, boolean worked) {
    public static final Codec<WeaponRoll> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("tier").forGetter(WeaponRoll::tier),
            Codec.INT.listOf().fieldOf("stats").forGetter(WeaponRoll::stats),
            Codec.INT.optionalFieldOf("flaws", 0).forGetter(WeaponRoll::flaws),
            Codec.STRING.optionalFieldOf("place", "unknown").forGetter(WeaponRoll::place),
            Codec.BOOL.optionalFieldOf("worked", false).forGetter(WeaponRoll::worked)
    ).apply(i, WeaponRoll::new));

    public static final StreamCodec<io.netty.buffer.ByteBuf, WeaponRoll> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WeaponRoll::tier,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), WeaponRoll::stats,
            ByteBufCodecs.VAR_INT, WeaponRoll::flaws,
            ByteBufCodecs.STRING_UTF8, WeaponRoll::place,
            ByteBufCodecs.BOOL, WeaponRoll::worked,
            WeaponRoll::new);

    public static final DataComponentType<WeaponRoll> TYPE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ZomboidCraft.id("roll"), DataComponentType.<WeaponRoll>builder()
                    .persistent(CODEC).networkSynchronized(STREAM_CODEC).ignoreSwapAnimation().build());

    /** Where this copy's stat landed, 0 to 1 (the middle for a stat it doesn't roll). */
    public float position(Stat stat) {
        return stat.ordinal() < stats.size() ? Math.clamp(stats.get(stat.ordinal()), 0, 100) / 100f : 0.5f;
    }

    public boolean has(Flaw flaw) {
        return (flaws & flaw.bit()) != 0;
    }

    public static void register() {
        // loading the class registers the component
    }
}
