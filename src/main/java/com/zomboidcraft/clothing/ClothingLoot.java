package com.zomboidcraft.clothing;

import com.mojang.serialization.Codec;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.survival.Body.Region;
import java.util.List;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * Clothing as it's found. Military pieces roll their camo when they drop (the patterns each one comes in are in
 * {@link Camos}, written by tools/items/gen_clothes.py with the textures), and most clothes have been lived in:
 * Zomboid's finds are often worn, sometimes torn.
 */
public final class ClothingLoot {
    /** The camo a piece was made in, by name ("woodland", "desert"...); the item model picks its texture by it. */
    public static final DataComponentType<String> CAMO = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ZomboidCraft.id("camo"), DataComponentType.<String>builder()
                    .persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());

    public static void register() {
        LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
            RandomSource random = context.getRandom();
            for (ItemStack stack : drops) found(stack, random);
        });
    }

    /** Rolls a found piece's camo and how worn it is; leaves one that already has them alone. */
    public static void found(ItemStack stack, RandomSource random) {
        Garment g = Clothes.of(stack);
        if (g == null) return;
        List<String> camos = Camos.FOR.get(g.id());
        if (camos != null && !camos.isEmpty() && !stack.has(CAMO)) {
            stack.set(CAMO, camos.get(random.nextInt(camos.size())));
        }
        if (stack.has(ClothWear.TYPE) || g.cloth() == Garment.Cloth.NONE) return;
        float roll = random.nextFloat();
        if (roll < 0.4f) return;                                            // as good as new
        int worn = Math.round(g.condition() * (0.1f + random.nextFloat() * 0.4f));
        int holes = 0;
        if (roll > 0.75f && g.cloth() != Garment.Cloth.HARD) {
            // a hole or two where it's taken the most: the forearms, hands and shins
            List<Spot> spots = List.copyOf(g.covers());
            int n = 1 + random.nextInt(2);
            for (int i = 0; i < n && !spots.isEmpty(); i++) {
                Spot s = spots.get(random.nextInt(spots.size()));
                Region[] sides = s.regions();
                holes |= 1 << s.place(sides[random.nextInt(sides.length)]);
            }
        }
        stack.set(ClothWear.TYPE, new ClothWear(Math.min(g.condition() - 1, worn), holes, 0L));
    }

    private ClothingLoot() {
    }
}
