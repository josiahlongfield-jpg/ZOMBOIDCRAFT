package com.zomboidcraft.loot;

import java.util.List;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

/**
 * Loot for Josiah's command net: a signal smoke found anywhere is one of the four M18 colours (CUSTOM_DATA "colour",
 * and the same word as its custom model data so it shows that can). AIRCRAFT reads the colour when the smoke pops.
 */
public final class CommandNet {
    public static final List<String> COLOURS = List.of("red", "green", "yellow", "violet");

    public static void register() {
        LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
            for (ItemStack s : drops) {
                if (NoVanilla.is(s, "smoke_canister") && colour(s) == null) {
                    colour(s, COLOURS.get(context.getRandom().nextInt(COLOURS.size())));
                }
            }
        });
    }

    /** The smoke's colour, or null for a plain (pre-colour) one. */
    public static String colour(ItemStack s) {
        CustomData data = s.get(DataComponents.CUSTOM_DATA);
        String c = data == null ? "" : data.copyTag().getStringOr("colour", "");
        return c.isEmpty() ? null : c;
    }

    public static void colour(ItemStack s, String colour) {
        CustomData.update(DataComponents.CUSTOM_DATA, s, tag -> tag.putString("colour", colour));
        s.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(colour), List.of()));
        String word = Character.toUpperCase(colour.charAt(0)) + colour.substring(1);
        s.set(DataComponents.CUSTOM_NAME, Component.literal(word + " Smoke Signal").withStyle(st -> st.withItalic(false)));
    }

    /** A random colour, for a smoke made or given outside a loot table. */
    public static String roll(RandomSource random) {
        return COLOURS.get(random.nextInt(COLOURS.size()));
    }

    private CommandNet() {
    }
}
