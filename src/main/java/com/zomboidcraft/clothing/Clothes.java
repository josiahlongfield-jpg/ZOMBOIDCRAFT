package com.zomboidcraft.clothing;

import static com.zomboidcraft.clothing.ClothingSlot.BELT;
import static com.zomboidcraft.clothing.ClothingSlot.EYES;
import static com.zomboidcraft.clothing.ClothingSlot.FACE;
import static com.zomboidcraft.clothing.ClothingSlot.FEET;
import static com.zomboidcraft.clothing.ClothingSlot.HANDS;
import static com.zomboidcraft.clothing.ClothingSlot.HAT;
import static com.zomboidcraft.clothing.ClothingSlot.JACKET;
import static com.zomboidcraft.clothing.ClothingSlot.LEGS;
import static com.zomboidcraft.clothing.ClothingSlot.NECK;
import static com.zomboidcraft.clothing.ClothingSlot.SHIRT;
import static com.zomboidcraft.clothing.ClothingSlot.VEST;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.survival.Body;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Every wearable and what it does (see {@link Garment}), and what a survivor's clothes add up to. Rows are the
 * clothing of the loot roster (tools/items/loot/roster.py) plus the carrier and mansion wearables; an item that isn't
 * registered yet just isn't found.
 *
 * Protection stacks the way layers do: each covering piece stops its share of what got past the ones over it, so a
 * shirt under a leather jacket is a little better than the jacket alone.
 */
public final class Clothes {
    private static final Map<String, Garment> BY_ID = new LinkedHashMap<>();

    static {
        // head: hats and helmets
        g("baseball_cap", HAT, "H", 0.0f, 0.05f, 1, 0.1f, 0);
        g("trucker_cap", HAT, "H", 0.0f, 0.05f, 1, 0.1f, 0);
        g("beanie", HAT, "H", 0.02f, 0.1f, 3, 0.05f, 0);
        g("cowboy_hat", HAT, "H", 0.02f, 0.1f, 1, 0.3f, 0);
        g("bucket_hat", HAT, "H", 0.0f, 0.05f, 1, 0.2f, 0);
        g("hard_hat", HAT, "H", 0.4f, 0.5f, 0, 0.2f, 1);
        g("bike_helmet", HAT, "H", 0.3f, 0.4f, 0, 0.05f, 1);
        g("football_helmet", HAT, "H", 0.7f, 0.8f, 1, 0.1f, 2);
        g("motorcycle_helmet", HAT, "H", 0.9f, 0.95f, 2, 0.3f, 2);
        g("police_cap", HAT, "H", 0.0f, 0.05f, 1, 0.1f, 0);
        g("fire_helmet", HAT, "H", 0.6f, 0.7f, 2, 0.3f, 2);
        g("combat_helmet", HAT, "H", 0.6f, 0.7f, 1, 0.1f, 2);
        g("riot_helmet", HAT, "H", 0.85f, 0.9f, 1, 0.3f, 2);
        g("cranial_helmet", HAT, "H", 0.3f, 0.4f, 1, 0.05f, 1);
        g("pilot_helmet", HAT, "H", 0.7f, 0.8f, 1, 0.2f, 2);
        // eyes
        g("welding_goggles", EYES, "", 0f, 0f, 0, 0f, 0);
        g("sunglasses", EYES, "", 0f, 0f, 0, 0f, 0);
        g("aviator_sunglasses", EYES, "", 0f, 0f, 0, 0f, 0);
        g("reading_glasses", EYES, "", 0f, 0f, 0, 0f, 0);
        // face
        g("hockey_mask", FACE, "H", 0.35f, 0.4f, 0, 0f, 0);
        g("gas_mask", FACE, "H", 0.3f, 0.35f, 1, 0f, 0);
        g("bandana", FACE, "H", 0.02f, 0.05f, 1, 0f, 0);
        g("balaclava", FACE, "H", 0.05f, 0.15f, 2, 0f, 0);
        g("surgical_mask_worn", FACE, "", 0f, 0f, 0, 0f, 0);
        // neck
        g("scarf", NECK, "H", 0.05f, 0.1f, 2, 0f, 0);
        g("necktie", NECK, "", 0f, 0f, 0, 0f, 0);
        // shirts: the layer next to the skin
        g("t_shirt", SHIRT, "T", 0.02f, 0.1f, 1, 0f, 0);
        g("band_tshirt", SHIRT, "T", 0.02f, 0.1f, 1, 0f, 0);
        g("tank_top", SHIRT, "T", 0.0f, 0.05f, 0, 0f, 0);
        g("flannel_shirt", SHIRT, "TA", 0.05f, 0.2f, 3, 0.05f, 0);
        g("dress_shirt", SHIRT, "TA", 0.03f, 0.12f, 1, 0f, 0);
        g("polo_shirt", SHIRT, "T", 0.03f, 0.12f, 1, 0f, 0);
        g("hawaiian_shirt", SHIRT, "T", 0.02f, 0.1f, 1, 0f, 0);
        g("turtleneck", SHIRT, "TA", 0.04f, 0.15f, 3, 0f, 0);
        g("scrubs_top", SHIRT, "T", 0.02f, 0.08f, 1, 0f, 0);
        g("dress", SHIRT, "TL", 0.02f, 0.1f, 1, 0f, 0);
        g("wedding_dress", SHIRT, "TAL", 0.05f, 0.2f, 3, 0f, 0);
        g("flight_deck_jersey", SHIRT, "TA", 0.04f, 0.15f, 2, 0f, 0);
        // jackets, coats and anything worn over the shirt
        g("sweater", JACKET, "TA", 0.08f, 0.25f, 5, 0.05f, 0);
        g("hoodie", JACKET, "TA", 0.08f, 0.25f, 4, 0.1f, 0);
        g("sweatshirt", JACKET, "TA", 0.07f, 0.22f, 4, 0.05f, 0);
        g("denim_jacket", JACKET, "TA", 0.2f, 0.4f, 3, 0.15f, 0);
        g("leather_jacket", JACKET, "TA", 0.35f, 0.6f, 4, 0.3f, 1);
        g("bomber_jacket", JACKET, "TA", 0.3f, 0.5f, 5, 0.3f, 1);
        g("varsity_jacket", JACKET, "TA", 0.15f, 0.35f, 4, 0.15f, 0);
        g("windbreaker", JACKET, "TA", 0.05f, 0.2f, 2, 0.5f, 0);
        g("parka", JACKET, "TA", 0.2f, 0.45f, 8, 0.6f, 0);
        g("raincoat", JACKET, "TAL", 0.05f, 0.2f, 2, 0.9f, 0);
        g("trench_coat", JACKET, "TAL", 0.15f, 0.35f, 5, 0.5f, 0);
        g("suit_jacket", JACKET, "TA", 0.1f, 0.25f, 3, 0.1f, 0);
        g("lab_coat", JACKET, "TAL", 0.05f, 0.2f, 2, 0.1f, 0);
        g("police_jacket", JACKET, "TA", 0.25f, 0.45f, 5, 0.4f, 0);
        g("fire_coat", JACKET, "TA", 0.5f, 0.75f, 9, 0.8f, 1);
        g("field_jacket", JACKET, "TA", 0.25f, 0.45f, 5, 0.4f, 0);
        g("ghillie_suit", JACKET, "TAL", 0.15f, 0.35f, 5, 0.3f, 0);
        g("ww2_flight_jacket", JACKET, "TA", 0.4f, 0.65f, 9, 0.4f, 1);
        g("fur_coat", JACKET, "TAL", 0.3f, 0.5f, 9, 0.3f, 0);
        g("bespoke_suit", JACKET, "TAL", 0.12f, 0.3f, 3, 0.1f, 0);
        g("poncho", JACKET, "TA", 0.02f, 0.1f, 1, 0.95f, 0);
        // vests: over the jacket, and armour
        g("hi_vis_vest", VEST, "T", 0.02f, 0.08f, 1, 0f, 0);
        g("flak_vest", VEST, "T", 0.6f, 0.75f, 2, 0f, 4);
        g("police_vest", VEST, "T", 0.7f, 0.8f, 1, 0f, 5);
        g("plate_carrier", VEST, "T", 0.85f, 0.9f, 1, 0f, 7);
        g("survival_vest", VEST, "T", 0.2f, 0.35f, 2, 0f, 1);
        // gloves: they guard the hands you put up against a zombie, so they count for the arms
        g("work_gloves", HANDS, "A", 0.08f, 0.2f, 1, 0f, 0);
        g("leather_gloves", HANDS, "A", 0.1f, 0.25f, 2, 0f, 0);
        g("winter_gloves", HANDS, "A", 0.06f, 0.15f, 3, 0.1f, 0);
        g("rubber_gloves", HANDS, "A", 0.02f, 0.08f, 0, 0.2f, 0);
        g("tactical_gloves", HANDS, "A", 0.15f, 0.3f, 1, 0f, 0);
        g("fire_gloves", HANDS, "A", 0.15f, 0.3f, 3, 0.2f, 0);
        // belts: what they open is in QuickSlots
        g("leather_belt", BELT, "", 0f, 0f, 0, 0f, 0);
        g("tool_belt", BELT, "", 0f, 0f, 0, 0f, 0);
        g("duty_belt", BELT, "", 0f, 0f, 0, 0f, 0);
        // legs
        g("jeans", LEGS, "L", 0.15f, 0.35f, 2, 0.05f, 0);
        g("work_pants", LEGS, "L", 0.15f, 0.35f, 2, 0.1f, 0);
        g("cargo_pants", LEGS, "L", 0.12f, 0.3f, 2, 0.1f, 0);
        g("slacks", LEGS, "L", 0.05f, 0.15f, 1, 0f, 0);
        g("sweatpants", LEGS, "L", 0.04f, 0.12f, 2, 0f, 0);
        g("shorts", LEGS, "L", 0.0f, 0.05f, 0, 0f, 0);
        g("skirt", LEGS, "L", 0.0f, 0.05f, 0, 0f, 0);
        g("overalls", LEGS, "LT", 0.15f, 0.35f, 3, 0.1f, 0);
        g("scrubs_pants", LEGS, "L", 0.02f, 0.08f, 1, 0f, 0);
        g("camo_pants", LEGS, "L", 0.15f, 0.35f, 2, 0.1f, 0);
        g("fire_pants", LEGS, "L", 0.45f, 0.7f, 7, 0.8f, 1);
        g("leather_chaps", LEGS, "L", 0.35f, 0.6f, 3, 0.3f, 1);
        g("g_suit", LEGS, "L", 0.2f, 0.4f, 3, 0.2f, 0);
        // feet: boots guard the shins a little
        g("sneakers", FEET, "", 0f, 0f, 1, 0f, 0);
        g("running_shoes", FEET, "", 0f, 0f, 1, 0f, 0);
        g("dress_shoes", FEET, "", 0f, 0f, 1, 0f, 0);
        g("work_boots", FEET, "L", 0.05f, 0.1f, 2, 0.3f, 1);
        g("cowboy_boots", FEET, "L", 0.05f, 0.1f, 2, 0.2f, 0);
        g("hiking_boots", FEET, "L", 0.04f, 0.08f, 2, 0.4f, 0);
        g("rubber_boots", FEET, "L", 0.06f, 0.12f, 1, 0.8f, 0);
        g("combat_boots", FEET, "L", 0.06f, 0.12f, 2, 0.4f, 1);
        g("slippers", FEET, "", 0f, 0f, 1, 0f, 0);
    }

    private static void g(String id, ClothingSlot slot, String covers, float bite, float scratch, int warmth, float water, int armour) {
        BY_ID.put(id, new Garment(id, slot, Garment.parts(covers), bite, scratch, warmth, water, armour));
    }

    /** Every wearable, head to foot in table order. */
    public static List<Garment> all() {
        return List.copyOf(BY_ID.values());
    }

    public static @Nullable Garment byId(String id) {
        return BY_ID.get(id);
    }

    /** What this stack is as clothing, or null if it isn't any. */
    public static @Nullable Garment of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key.getNamespace().equals(ZomboidCraft.MOD_ID) ? BY_ID.get(key.getPath()) : null;
    }

    public static @Nullable ClothingSlot slot(ItemStack stack) {
        Garment g = of(stack);
        return g == null ? null : g.slot();
    }

    /**
     * What everything worn adds up to.
     *
     * @param bite    per body part, the share of bites that get through nothing (0 = no guard)
     * @param scratch the same for scratches
     */
    public record Totals(Map<Body.Region, Float> bite, Map<Body.Region, Float> scratch, int warmth, float water, int armour) {
        /** The average guard over the body's six parts, for a single headline number. */
        public float meanBite() {
            return mean(bite);
        }

        public float meanScratch() {
            return mean(scratch);
        }

        private static float mean(Map<Body.Region, Float> map) {
            float sum = 0;
            for (float v : map.values()) sum += v;
            return sum / Body.Region.values().length;
        }
    }

    /** Totals for your own survivor (server, or your own client while the kit is open). */
    public static Totals totals(Player player) {
        return totals(player, false);
    }

    /** @param shown read what the player is seen wearing (other players, on a client) instead of their own store */
    public static Totals totals(Player player, boolean shown) {
        Map<Body.Region, Float> biteLeft = new EnumMap<>(Body.Region.class), scratchLeft = new EnumMap<>(Body.Region.class);
        for (Body.Region r : Body.Region.values()) {
            biteLeft.put(r, 1f);
            scratchLeft.put(r, 1f);
        }
        int warmth = 0, armour = 0;
        float dry = 1f;
        for (ClothingSlot slot : ClothingSlot.ALL) {
            Garment g = of(shown ? slot.shown(player) : slot.worn(player));
            if (g == null) continue;
            for (Body.Region r : g.covers()) {
                biteLeft.merge(r, 1 - g.bite(), (a, b) -> a * b);
                scratchLeft.merge(r, 1 - g.scratch(), (a, b) -> a * b);
            }
            warmth += g.warmth();
            dry *= 1 - g.water();
            armour += g.armour();
        }
        Map<Body.Region, Float> bite = new EnumMap<>(Body.Region.class), scratch = new EnumMap<>(Body.Region.class);
        for (Body.Region r : Body.Region.values()) {
            bite.put(r, 1 - biteLeft.get(r));
            scratch.put(r, 1 - scratchLeft.get(r));
        }
        return new Totals(Map.copyOf(bite), Map.copyOf(scratch), warmth, 1 - dry, armour);
    }

    private Clothes() {
    }
}
