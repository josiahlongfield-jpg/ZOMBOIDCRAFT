package com.zomboidcraft.client.decor;

import com.zomboidcraft.client.interact.InteractMenu;
import com.zomboidcraft.client.interact.Interactables;
import com.zomboidcraft.decor.DecorBlock;
import com.zomboidcraft.decor.DecorNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Decor you can use: the green dot, a tap of G switches it on or off (or plays it), and the G menu has both. Picking
 * it up is the moving menu's (building/Moving).
 */
public final class DecorClient {
    private DecorClient() {
    }

    private static boolean usable(BlockState s) {
        return s.getBlock() instanceof DecorBlock d && (d.spec.toggle() || d.spec.play() > 0 || !extras(d, s).isEmpty());
    }

    /** What else a piece offers (decor/DecorUses), as menu lines: [label, action]. */
    private static java.util.List<Object[]> extras(DecorBlock d, BlockState s) {
        java.util.List<Object[]> out = new java.util.ArrayList<>();
        String sp = d.spec.special();
        if (sp == null) {
            return out;
        }
        switch (sp) {
            case "seat" -> out.add(new Object[] {"Sit and rest", DecorNet.Action.REST});
            case "rest_fast" -> out.add(new Object[] {"Put your feet up", DecorNet.Action.REST});
            case "massage" -> out.add(new Object[] {"Have a massage", DecorNet.Action.REST});
            case "haircut" -> {
                out.add(new Object[] {"Sit and rest", DecorNet.Action.REST});
                out.add(new Object[] {"Hair and beard...", null});
            }
            case "drinks_store", "cold_store", "sweets_store" -> out.add(new Object[] {"Look inside", DecorNet.Action.OPEN});
            case "stove_heat" -> {
                out.add(new Object[] {"Add fuel", DecorNet.Action.FUEL});
                if (s.getValue(DecorBlock.ON)) out.add(new Object[] {"Cook food", DecorNet.Action.COOK});
            }
            case "dress_decoy" -> out.add(new Object[] {s.getValue(DecorBlock.ON) ? "Take the clothes off" : "Dress it", DecorNet.Action.DRESS});
            case "roll" -> out.add(new Object[] {"Roll it as a distraction", DecorNet.Action.ROLL});
            case "days" -> out.add(new Object[] {"Check the calendar", DecorNet.Action.READ});
            case "chime", "time" -> out.add(new Object[] {"Check the time", DecorNet.Action.READ});
            case "back_road" -> out.add(new Object[] {"Study the map", DecorNet.Action.READ});
            case "letters" -> out.add(new Object[] {"Change the letter", DecorNet.Action.NEXT_LOOK});
            default -> {
            }
        }
        return out;
    }

    public static void register() {
        Interactables.blocks(DecorClient::usable);
        InteractMenu.provider((client, player, hit, out) -> {
            BlockPos pos = at(client, hit);
            if (pos == null) {
                return;
            }
            BlockState s = client.level.getBlockState(pos);
            DecorBlock d = (DecorBlock) s.getBlock();
            if (d.spec.toggle()) {
                boolean on = s.getValue(DecorBlock.ON);
                out.add(InteractMenu.Option.of(on ? "Switch it off" : "Switch it on", () -> send(pos, DecorNet.Action.TOGGLE)));
            }
            if (d.spec.play() > 0) {
                out.add(InteractMenu.Option.of(playLabel(d), () -> send(pos, DecorNet.Action.PLAY)));
            }
            for (Object[] e : extras(d, s)) {
                DecorNet.Action action = (DecorNet.Action) e[1];
                out.add(InteractMenu.Option.of((String) e[0], action == null
                        ? () -> client.gui.setScreen(new com.zomboidcraft.client.kit.GroomingScreen(null))
                        : () -> send(pos, action)));
            }
        });
    }

    private static String playLabel(DecorBlock d) {
        String sp = d.spec.special() == null ? "" : d.spec.special();
        return switch (sp) {
            case "train" -> "Train on it";
            case "aim_xp" -> "Throw some darts";
            case "vhs" -> "Watch a tape";
            case "needs_record" -> "Put a record on";
            case "old_broadcast" -> "Play an old tape";
            case "payout" -> "Put a coin in";
            default -> "Play";
        };
    }

    private static void send(BlockPos pos, DecorNet.Action action) {
        ClientPlayNetworking.send(new DecorNet.Use(pos, action.ordinal()));
    }

    private static BlockPos at(Minecraft client, HitResult hit) {
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                && usable(client.level.getBlockState(block.getBlockPos())) ? block.getBlockPos() : null;
    }

    /** A tap of G: switch it, or play it when it doesn't switch. */
    public static boolean interact(Minecraft client, HitResult hit) {
        BlockPos pos = at(client, hit);
        if (pos == null) {
            return false;
        }
        BlockState s = client.level.getBlockState(pos);
        DecorBlock d = (DecorBlock) s.getBlock();
        if (d.spec.toggle()) {
            send(pos, DecorNet.Action.TOGGLE);
        } else if (d.spec.play() > 0) {
            send(pos, DecorNet.Action.PLAY);
        } else {
            java.util.List<Object[]> ex = extras(d, s);
            if (ex.isEmpty() || ex.get(0)[1] == null) {
                return false;
            }
            send(pos, (DecorNet.Action) ex.get(0)[1]);
        }
        return true;
    }
}
