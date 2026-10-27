package com.zomboidcraft.loot.items;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Keeps spoilage honest. Food rolled from a loot table has been sitting there since the outbreak (day 0 of the world),
 * at the pace of where it was found: fridge milk a fortnight in is long gone, a can of beans is fine. After that, every
 * half-second the clocks of what each player has on them, and of any container they have open, are brought up to
 * date, and things put in a fridge or freezer start keeping.
 */
public final class LootHooks {
    public static void register() {
        LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
            String path = table.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
            long now = context.getLevel().getGameTime();
            int where = where(path);
            RandomSource random = context.getRandom();
            for (ItemStack stack : drops) {
                if (stack.has(Spoil.TYPE)) {
                    continue;
                }
                LootSpec spec = LootItems.spec(stack);
                if (spec == null || spec.food() == null || !spec.food().perishes()) {
                    continue;
                }
                boolean cold = spec.food().is(LootSpec.Food.COLD);
                // a shop or house restocked a little before the end, so not everything is as old as the world
                float age = now / Spoil.DAY * Spoil.pace(where, cold) * (0.55f + random.nextFloat() * 0.45f);
                Spoil spoil = Spoil.start(stack, now, where, age);
                if (spoil != null) {
                    stack.set(Spoil.TYPE, spoil);
                    Spoil.name(stack, spoil);
                }
            }
        });
    }

    /** Called from Carry's half-second tick. */
    public static void tick(ServerPlayer player) {
        long now = player.level().getGameTime();
        Inventory inventory = player.getInventory();
        boolean changed = false;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            changed |= Spoil.update(inventory.getItem(i), now, Spoil.OUT);
        }
        if (changed) {
            inventory.setChanged();
        }
        if (player.containerMenu == player.inventoryMenu) {
            return;
        }
        for (Slot slot : player.containerMenu.slots) {
            Container container = slot.container;
            if (container == inventory) {
                continue;
            }
            int where = container instanceof BlockEntity be ? where(BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getPath()) : Spoil.OUT;
            if (Spoil.update(slot.getItem(), now, where)) {
                slot.setChanged();
            }
        }
    }

    /** Fridge or freezer, by a loot table's or a block's name. */
    static int where(String name) {
        if (name.contains("freezer")) return Spoil.FREEZER;
        if (name.contains("fridge") || name.contains("cooler") || name.contains("refriger")) return Spoil.FRIDGE;
        return Spoil.OUT;
    }

    private LootHooks() {
    }
}
