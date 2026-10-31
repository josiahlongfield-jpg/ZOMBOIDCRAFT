package com.zomboidcraft.client.combat;

import com.zomboidcraft.ModItems;
import com.zomboidcraft.combat.Reloading;
import com.zomboidcraft.client.interact.InteractMenu;
import com.zomboidcraft.client.interact.Interactables;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The reloading bench under the interact key (combat/Reloading): a tap or a hold opens its menu, a line for each
 * cartridge it loads (greyed with what's missing), and picking the bench up.
 */
public final class ReloadingClient {
    private static final double REACH = 4.0;

    private ReloadingClient() {
    }

    public static void register() {
        Interactables.blocks(state -> state.is(Reloading.BENCH));
        InteractMenu.provider(ReloadingClient::options);
    }

    private static BlockPos bench(Minecraft client, LocalPlayer player, HitResult hit) {
        if (!(hit instanceof BlockHitResult b) || hit.getType() != HitResult.Type.BLOCK || client.level == null) return null;
        if (b.getLocation().distanceTo(player.getEyePosition()) > REACH) return null;
        return client.level.getBlockState(b.getBlockPos()).is(Reloading.BENCH) ? b.getBlockPos() : null;
    }

    /** A tap of G on a bench: its menu, as there's no one obvious thing to do there. */
    public static boolean interact(Minecraft client, HitResult hit) {
        if (client.player == null || bench(client, client.player, hit) == null) return false;
        InteractMenu.open(client);
        return true;
    }

    private static void options(Minecraft client, LocalPlayer player, HitResult hit, List<InteractMenu.Option> out) {
        BlockPos pos = bench(client, player, hit);
        if (pos == null) return;
        List<InteractMenu.Option> loads = new ArrayList<>();
        for (int i = 0; i < Reloading.LOADS.size(); i++) {
            Reloading.Load load = Reloading.LOADS.get(i);
            int n = Reloading.batch(player, load);
            String name = load.round().getName(new ItemStack(load.round())).getString();
            if (n > 0) {
                int which = i;
                loads.add(InteractMenu.Option.timed("Load " + n + " " + name, Reloading.ticks(player, n) / 20f,
                        () -> ClientPlayNetworking.send(new Reloading.Use(pos, which))));
            } else {
                loads.add(InteractMenu.Option.no("Load " + name, "Needs " + needs(player, load)));
            }
        }
        out.add(InteractMenu.Option.menu("Reload ammunition", loads));
        out.add(InteractMenu.Option.timed("Pick up the bench", 5, () -> ClientPlayNetworking.send(new Reloading.Use(pos, -1))));
    }

    private static String needs(LocalPlayer player, Reloading.Load load) {
        List<String> parts = new ArrayList<>();
        if (!has(player, load.casing())) {
            parts.add(load.casing().getName(new ItemStack(load.casing())).getString().toLowerCase(java.util.Locale.ROOT));
        }
        if (!has(player, ModItems.PRIMERS)) parts.add("primers");
        if (!has(player, load.head())) {
            parts.add(load.head().getName(new ItemStack(load.head())).getString().toLowerCase(java.util.Locale.ROOT));
        }
        if (!has(player, ModItems.SMOKELESS_POWDER)) parts.add("smokeless powder");
        return parts.isEmpty() ? "more powder" : String.join(", ", parts);
    }

    private static boolean has(LocalPlayer player, net.minecraft.world.item.Item item) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) return true;
        }
        return false;
    }
}
