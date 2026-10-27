package com.zomboidcraft;

import com.zomboidcraft.world.county.Place;
import com.zomboidcraft.world.county.PlaceType;
import com.zomboidcraft.world.gen.CountyChunkGenerator;
import com.zomboidcraft.zombie.ZombieLook;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Adds survival supplies to every chest and to zombie drops, and the rare marked maps to soldiers and bases. */
public final class Loot {
    public static void register() {
        LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
            String path = table.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
            RandomSource random = context.getRandom();
            if (path.startsWith("chests/")) {
                addChestLoot(random, drops);
            } else if (path.equals("entities/zombie") || path.equals("entities/husk")
                    || path.equals("entities/zombie_villager")) {
                addZombieLoot(random, drops);
                // the soldiers carry the marked maps that lead to the bunkers
                Entity dead = context.getOptional(LootContextParams.THIS_ENTITY);
                if (dead != null && "soldier".equals(dead.getAttached(ZombieLook.OUTFIT)) && random.nextFloat() < 0.015f) {
                    drops.add(new ItemStack(ModItems.BUNKER_MAP));
                }
                if (dead != null && "soldier".equals(dead.getAttached(ZombieLook.OUTFIT))) {
                    // a pilot's or a crew chief's pocket
                    maybe(random, drops, 0.004f, ModItems.ROTARY_WING_MANUAL, 1, 1);
                    maybe(random, drops, 0.003f, ModItems.JET_FLIGHT_MANUAL, 1, 1);
                    maybe(random, drops, 0.003f, ModItems.AIRCRAFT_WEAPONS_MANUAL, 1, 1);
                }
            } else if (path.startsWith("shop/")) {
                Vec3 at = context.getOptional(LootContextParams.ORIGIN);
                boolean guarded = at != null && guarded(context.getLevel(), at);
                if (guarded && random.nextFloat() < 0.03f) {
                    // and so do the lockers and crates of the bases and the naval wharf
                    drops.add(new ItemStack(ModItems.BUNKER_MAP));
                }
                if (guarded) {
                    addAircraftLoot(random, drops);
                } else if (path.equals("shop/home_books") || path.equals("shop/office")) {
                    // somebody's hobby: a flight manual on a bookshelf, rarely
                    maybe(random, drops, 0.006f, ModItems.ROTARY_WING_MANUAL, 1, 1);
                    maybe(random, drops, 0.003f, ModItems.JET_FLIGHT_MANUAL, 1, 1);
                }
            }
        });
    }

    /** True inside a military base or quarantine zone, or the naval wharf with its carrier. */
    private static boolean guarded(ServerLevel level, Vec3 at) {
        if (!(level.getChunkSource().getGenerator() instanceof CountyChunkGenerator)) {
            return false;
        }
        int x = (int) Math.floor(at.x), z = (int) Math.floor(at.z);
        for (Place p : CountyChunkGenerator.gen(level.getSeed()).county().placesNear(x, z, x, z, 0)) {
            if (p.type == PlaceType.MILITARY || p.type == PlaceType.PORT && p.variant.equals("naval")) {
                return true;
            }
        }
        return false;
    }

    private static void addChestLoot(RandomSource random, List<ItemStack> drops) {
        maybe(random, drops, 0.35f, ModItems.BANDAGE, 1, 3);
        maybe(random, drops, 0.30f, ModItems.NAILS, 4, 12);
        maybe(random, drops, 0.10f, ModItems.DUCT_TAPE, 1, 2);
        maybe(random, drops, 0.05f, ModItems.WOOD_GLUE, 1, 1);
        maybe(random, drops, 0.06f, ModItems.HAMMER, 1, 1);
        maybe(random, drops, 0.06f, ModItems.BASEBALL_BAT, 1, 1);
        maybe(random, drops, 0.05f, ModItems.CROWBAR, 1, 1);
        maybe(random, drops, 0.06f, ModItems.KITCHEN_KNIFE, 1, 1);
        maybe(random, drops, 0.03f, ModItems.MACHETE, 1, 1);
        maybe(random, drops, 0.05f, ModItems.SCHOOL_BACKPACK, 1, 1);
        maybe(random, drops, 0.04f, ModItems.FANNY_PACK, 1, 1);
        maybe(random, drops, 0.025f, ModItems.HIKING_BACKPACK, 1, 1);
        maybe(random, drops, 0.02f, ModItems.DUFFEL_BAG, 1, 1);
        maybe(random, drops, 0.008f, ModItems.MILITARY_RUCKSACK, 1, 1);
        maybe(random, drops, 0.006f, ModItems.PLATE_CARRIER, 1, 1);
        if (random.nextFloat() < 0.25f) {
            drops.add(PotionContents.createItemStack(Items.POTION, Potions.WATER));
        }
    }

    /** The bases and the carrier keep what aircraft run on, and now and then the manuals that teach flying them. */
    private static void addAircraftLoot(RandomSource random, List<ItemStack> drops) {
        maybe(random, drops, 0.05f, ModItems.JET_FUEL, 1, 1);
        maybe(random, drops, 0.05f, ModItems.AIRCRAFT_PARTS, 1, 2);
        maybe(random, drops, 0.02f, ModItems.AIRCRAFT_BATTERY, 1, 1);
        maybe(random, drops, 0.008f, ModItems.ROTARY_WING_MANUAL, 1, 1);
        maybe(random, drops, 0.006f, ModItems.JET_FLIGHT_MANUAL, 1, 1);
        maybe(random, drops, 0.005f, ModItems.AIRCRAFT_WEAPONS_MANUAL, 1, 1);
    }

    private static void addZombieLoot(RandomSource random, List<ItemStack> drops) {
        maybe(random, drops, 0.08f, ModItems.NAILS, 1, 3);
        maybe(random, drops, 0.04f, ModItems.BANDAGE, 1, 1);
        maybe(random, drops, 0.01f, ModItems.KITCHEN_KNIFE, 1, 1);
        maybe(random, drops, 0.03f, ModItems.DIGITAL_WATCH, 1, 1);
        maybe(random, drops, 0.015f, ModItems.DUCT_TAPE, 1, 1);
        // the dead still wear what they ran with
        maybe(random, drops, 0.015f, ModItems.SCHOOL_BACKPACK, 1, 1);
        maybe(random, drops, 0.01f, ModItems.FANNY_PACK, 1, 1);
        maybe(random, drops, 0.006f, ModItems.HIKING_BACKPACK, 1, 1);
        if (random.nextFloat() < 0.02f) {
            drops.add(PotionContents.createItemStack(Items.POTION, Potions.WATER));
        }
    }

    private static void maybe(RandomSource random, List<ItemStack> drops, float chance, Item item, int min, int max) {
        if (random.nextFloat() < chance) {
            drops.add(new ItemStack(item, min + random.nextInt(max - min + 1)));
        }
    }

    private Loot() {
    }
}
