package com.zomboidcraft.client.power;

import com.zomboidcraft.client.interact.InteractMenu;
import com.zomboidcraft.client.interact.Interactables;
import com.zomboidcraft.loot.items.GasCanItem;
import com.zomboidcraft.power.GeneratorBlock;
import com.zomboidcraft.power.Generators;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** A set-down generator: the green dot, a tap of G turns it on or off, holding G opens its menu. */
public final class GeneratorClient {
    private GeneratorClient() {
    }

    public static void register() {
        Interactables.blocks(Generators::is);
        InteractMenu.provider((client, player, hit, out) -> {
            BlockPos pos = at(client, hit);
            if (pos == null) {
                return;
            }
            BlockState s = client.level.getBlockState(pos);
            boolean on = s.getValue(GeneratorBlock.LIT);
            out.add(InteractMenu.Option.of(on ? "Turn the generator off" : "Turn the generator on", () -> send(pos, Generators.Action.TOGGLE)));
            out.add(InteractMenu.Option.of("Connect it", () -> send(pos, Generators.Action.CONNECT)));
            boolean can = false;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (player.getInventory().getItem(i).getItem() instanceof GasCanItem && GasCanItem.fuel(player.getInventory().getItem(i)) > 0.05f) {
                    can = true;
                    break;
                }
            }
            out.add(can ? InteractMenu.Option.of("Add fuel", () -> send(pos, Generators.Action.FUEL))
                    : InteractMenu.Option.no("Add fuel", "You need a gas can with fuel in it"));
            out.add(InteractMenu.Option.of("Fix it", () -> send(pos, Generators.Action.REPAIR)));
            out.add(InteractMenu.Option.of("Check it", () -> send(pos, Generators.Action.CHECK)));
            out.add(on ? InteractMenu.Option.no("Pick it up", "Turn it off first")
                    : InteractMenu.Option.of("Pick it up", () -> send(pos, Generators.Action.PICK_UP)));
        });
    }

    private static void send(BlockPos pos, Generators.Action action) {
        ClientPlayNetworking.send(new Generators.Use(pos, action.ordinal()));
    }

    private static BlockPos at(Minecraft client, HitResult hit) {
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                && Generators.is(client.level.getBlockState(block.getBlockPos())) ? block.getBlockPos() : null;
    }

    /** A tap of G on a generator: on or off. */
    public static boolean interact(Minecraft client, HitResult hit) {
        BlockPos pos = at(client, hit);
        if (pos == null) {
            return false;
        }
        send(pos, Generators.Action.TOGGLE);
        return true;
    }
}
