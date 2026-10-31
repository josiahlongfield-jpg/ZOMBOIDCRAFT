package com.zomboidcraft.survival;

import com.zomboidcraft.loot.items.LootItems;
import com.zomboidcraft.loot.items.LootSpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * What each food is worth to the body, Zomboid style (data/zomboidcraft/survival/foods.txt): how much it takes off
 * hunger, and its calories, carbs, fats and proteins, for the whole item. Roster items go by their roster id, and
 * Minecraft's own food by its id (apple, carrot and potato carry a _minecraft suffix where the roster has its own).
 * Anything else edible falls back to a guess from Minecraft's food points.
 */
public final class Foods {
    /** One food: hunger is on Zomboid's 0..1 scale, the rest per whole item. */
    public record Value(float hunger, float kcal, float carbs, float fats, float proteins) {
    }

    private static final Map<String, Value> TABLE = load();

    private static Map<String, Value> load() {
        Map<String, Value> table = new HashMap<>();
        try (InputStream in = Foods.class.getResourceAsStream("/data/zomboidcraft/survival/foods.txt")) {
            if (in == null) return table;
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = reader.readLine()) != null; ) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] f = line.split("\\s+");
                table.put(f[0], new Value(Float.parseFloat(f[1]), Float.parseFloat(f[2]), Float.parseFloat(f[3]),
                        Float.parseFloat(f[4]), Float.parseFloat(f[5])));
            }
        } catch (IOException | RuntimeException e) {
            com.zomboidcraft.ZomboidCraft.LOGGER.error("Couldn't read the food values", e);
        }
        return table;
    }

    /** What eating this stack is worth, or null when it isn't food at all. */
    public static @Nullable Value of(ItemStack stack) {
        LootSpec spec = LootItems.spec(stack);
        if (spec != null && spec.food() != null) {
            Value v = TABLE.get(spec.id());
            if (v != null) return v;
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        Value v = TABLE.get(id + "_minecraft");
        if (v == null) v = TABLE.get(id);
        if (v != null) return v;
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null) return spec != null && spec.food() != null ? guess(spec.food().hunger()) : null;
        return guess(food.nutrition());
    }

    /** Minecraft food points as Zomboid values: a steak's 8 points is about a third of a hungry stomach. */
    private static Value guess(int points) {
        return new Value(points * 0.04f, points * 80f, points * 8f, points * 3f, points * 4f);
    }

    /** "420 kcal, carbs 70 g, fats 3 g, proteins 25 g": the Nutritionist trait's line on a food card. */
    public static String describe(Value v) {
        return String.format(Locale.ROOT, "%d kcal, carbs %d g, fats %d g, proteins %d g",
                Math.round(v.kcal()), Math.round(v.carbs()), Math.round(v.fats()), Math.round(v.proteins()));
    }

    private Foods() {
    }
}
