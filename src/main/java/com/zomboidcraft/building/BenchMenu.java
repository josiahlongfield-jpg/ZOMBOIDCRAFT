package com.zomboidcraft.building;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;

/**
 * Working at a workbench (a garage's or a farm's, or one moved home): the 3x3 grid our bigger recipes need. Minecraft's
 * crafting table is gone with every other vanilla item (Josiah 2026-11-02), so this is the only one.
 */
public final class BenchMenu extends CraftingMenu {
    private final BlockPos bench;

    private BenchMenu(int id, Inventory inventory, ContainerLevelAccess access, BlockPos bench) {
        super(id, inventory, access);
        this.bench = bench;
    }

    public static void open(ServerPlayer player, BlockPos pos) {
        ContainerLevelAccess access = ContainerLevelAccess.create(player.level(), pos);
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new BenchMenu(id, inv, access, pos.immutable()),
                Component.literal("Workbench")));
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(bench.getX() + 0.5, bench.getY() + 0.5, bench.getZ() + 0.5) <= 64;
    }
}
