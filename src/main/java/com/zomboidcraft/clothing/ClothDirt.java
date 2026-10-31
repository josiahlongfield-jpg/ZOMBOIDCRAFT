package com.zomboidcraft.clothing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zomboidcraft.ZomboidCraft;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * How dirty, bloody and wet a piece of clothing is, Zomboid style, each 0 to 100. Fighting splashes blood on what's
 * outermost, crawling and rain on bare ground grime it, rain and washing soak it. A dirty or bloody piece over a wound
 * makes it likelier to go bad (survival reads {@link Grime#filth}); washing (clothing/Grime) clears it and leaves it
 * wet, and wet clothes dry while worn or carried.
 */
public record ClothDirt(int dirt, int blood, int wet) {
    public static final ClothDirt CLEAN = new ClothDirt(0, 0, 0);

    public static final Codec<ClothDirt> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("dirt", 0).forGetter(ClothDirt::dirt),
            Codec.INT.optionalFieldOf("blood", 0).forGetter(ClothDirt::blood),
            Codec.INT.optionalFieldOf("wet", 0).forGetter(ClothDirt::wet)
    ).apply(i, ClothDirt::new));

    public static final StreamCodec<io.netty.buffer.ByteBuf, ClothDirt> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ClothDirt::dirt,
            ByteBufCodecs.VAR_INT, ClothDirt::blood,
            ByteBufCodecs.VAR_INT, ClothDirt::wet,
            ClothDirt::new);

    public static final DataComponentType<ClothDirt> TYPE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ZomboidCraft.id("cloth_dirt"), DataComponentType.<ClothDirt>builder()
                    .persistent(CODEC).networkSynchronized(STREAM_CODEC).build());

    public static void register() {
        // loads the class, which registers the component
    }

    public static ClothDirt of(ItemStack stack) {
        return stack.getOrDefault(TYPE, CLEAN);
    }

    public ClothDirt add(int dirt, int blood, int wet) {
        return new ClothDirt(clamp(this.dirt + dirt), clamp(this.blood + blood), clamp(this.wet + wet));
    }

    /** Dirt and blood together, 0 to 1: what a wound under it is exposed to. */
    public float filth() {
        return Math.min(1f, (dirt + blood * 1.5f) / 150f);
    }

    public boolean clean() {
        return dirt == 0 && blood == 0;
    }

    /** One word for the kit card, worst first, or "" when clean and dry. */
    public String describe() {
        StringBuilder s = new StringBuilder();
        if (blood >= 50) s.append("Soaked in blood");
        else if (blood >= 15) s.append("Bloody");
        else if (blood > 0) s.append("Spotted with blood");
        if (dirt >= 50) s.append(s.isEmpty() ? "Filthy" : ", filthy");
        else if (dirt >= 15) s.append(s.isEmpty() ? "Dirty" : ", dirty");
        if (wet >= 60) s.append(s.isEmpty() ? "Soaking wet" : ", soaking wet");
        else if (wet > 0) s.append(s.isEmpty() ? "Damp" : ", damp");
        return s.toString();
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }
}
