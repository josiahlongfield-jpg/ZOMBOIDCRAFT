package com.zomboidcraft.client.cars;

import com.zomboidcraft.client.interact.Interactables;
import com.zomboidcraft.client.interact.InteractMenu;
import com.zomboidcraft.loot.items.GasCanItem;
import com.zomboidcraft.world.cars.Fuel;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** The gas pumps: the usable dot on them, and G fills the can in hand or the car parked beside. */
public final class FuelClient {
    private FuelClient() {
    }

    public static void register() {
        Interactables.blocks(Fuel::isPump);
        InteractMenu.provider((client, player, hit, out) -> {
            BlockPos pos = pump(client, hit);
            if (pos != null) {
                boolean can = player.getMainHandItem().getItem() instanceof GasCanItem;
                out.add(InteractMenu.Option.of(can ? "Fill the gas can" : "Fill the car's tank",
                        () -> ClientPlayNetworking.send(new Fuel.Pump(pos))));
            }
        });
    }

    private static BlockPos pump(Minecraft client, HitResult hit) {
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                && Fuel.isPump(client.level.getBlockState(block.getBlockPos())) ? block.getBlockPos() : null;
    }

    /** Works the pump you're looking at, if it is one; whether it was. */
    public static boolean interact(Minecraft client, HitResult hit) {
        BlockPos pos = pump(client, hit);
        if (pos == null) {
            return false;
        }
        ClientPlayNetworking.send(new Fuel.Pump(pos));
        return true;
    }
}
