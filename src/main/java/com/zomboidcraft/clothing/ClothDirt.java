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

    /** Steps of dirt and blood the worn clothes show (0 to 4), from the level each starts at. */
    private static final int[] DIRT_STEPS = {10, 30, 55, 80}, BLOOD_STEPS = {5, 20, 45, 75};

    public static int step(int value, boolean blood) {
        int[] steps = blood ? BLOOD_STEPS : DIRT_STEPS;
        int n = 0;
        while (n < steps.length && value >= steps[n]) n++;
        return n;
    }

    public int dirtStep() {
        return step(dirt, false);
    }

    public int bloodStep() {
        return step(blood, true);
    }

    /** 0 dry, 1 damp, 2 soaking wet (the kit card's words). */
    public int wetStep() {
        return wet >= 60 ? 2 : wet > 0 ? 1 : 0;
    }

    /** What others see: dirt and blood rounded down to their steps, and dry, damp or soaked. */
    public ClothDirt shown() {
        int d = dirtStep(), b = bloodStep(), w = wetStep();
        return new ClothDirt(d == 0 ? 0 : DIRT_STEPS[d - 1], b == 0 ? 0 : BLOOD_STEPS[b - 1], w == 0 ? 0 : w == 1 ? 1 : 60);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }
}
