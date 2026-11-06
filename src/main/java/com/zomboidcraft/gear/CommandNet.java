package com.zomboidcraft.gear;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.ModSounds;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The squad's command net (Josiah 2026-11-06, W20 and C1/C2 of the command net plan): the old military channel is
 * now Command 41.25, where Overwatch talks to the squad, Reaper. Every marine wearing a comms headset hears it, and
 * so does anyone carrying a live military or field radio; the two-way radios keep the civilian stations.
 * <p>
 * Its looped script (standing orders, the extraction points, the bunker code) is the "military" station in
 * data/zomboidcraft/broadcasts.json, which {@link Broadcasts} plays to everyone {@link #hears}. What happens in the
 * game is said here as it happens: new hunt board taskings ({@link HuntBoard}), the insertion on day 0, and later
 * the air strike calls. Captions only, as every broadcast is.
 */
public final class CommandNet {
    public static final String STATION = "Command 41.25";
    public static final int COLOUR = 0x8FB07A;
    /** The broadcasts.json station that loops the standing orders. */
    public static final String LOOP_ID = "military";
    /** How many transmissions the hunt board's log strip keeps. */
    private static final int KEEP = 12;

    /** One transmission on the net: what was said and when (game time). */
    public record Said(String text, long time) {
    }

    private record Due(long at, UUID to, String text) {
    }

    private static final Deque<Said> LOG = new ArrayDeque<>();
    private static final List<Due> QUEUE = new ArrayList<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(CommandNet::tick);
    }

    /** Whether this player hears the command net: a comms headset on their helmet, or a live military or field radio on them. */
    public static boolean hears(Player player) {
        if (Headgear.headset(player.getItemBySlot(EquipmentSlot.HEAD)) != null) {
            return true;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof RadioItem radio
                    && (radio.type() == RadioItem.Type.MILITARY || radio.type() == RadioItem.Type.FIELD) && Radios.live(stack)) {
                return true;
            }
        }
        return false;
    }

    /** The last few transmissions, oldest first. */
    public static List<Said> log() {
        return List.copyOf(LOG);
    }

    /** Overwatch says this to everyone on the net, now. */
    public static void say(MinecraftServer server, String text) {
        sayLater(server, 0, null, text);
    }

    /** Overwatch says this after a delay; to one player only when {@code to} is set. */
    public static void sayLater(MinecraftServer server, int ticks, UUID to, String text) {
        QUEUE.add(new Due(server.overworld().getGameTime() + Math.max(0, ticks), to, text));
    }

    private static void tick(MinecraftServer server) {
        if (QUEUE.isEmpty() || !ZcConfig.get().broadcast.enabled) {
            return;
        }
        long now = server.overworld().getGameTime();
        List<Due> due = new ArrayList<>();
        QUEUE.removeIf(d -> {
            if (d.at() > now) {
                return false;
            }
            due.add(d);
            return true;
        });
        for (Due d : due) {
            transmit(server, d.to(), d.text(), now);
        }
    }

    private static void transmit(MinecraftServer server, UUID to, String text, long now) {
        boolean sent = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || to != null && !player.getUUID().equals(to) || !hears(player)) {
                continue;
            }
            ServerPlayNetworking.send(player, new Broadcasts.Caption(STATION, COLOUR, text));
            Radios.fromRadio(player, ModSounds.RADIO_SQUELCH, 0.6f);
            sent = true;
        }
        if (to == null || sent) {
            LOG.addLast(new Said(text, now));
            while (LOG.size() > KEEP) {
                LOG.removeFirst();
            }
            HuntBoard.changed(server);
        }
        ZomboidCraft.LOGGER.debug("Command net: {}", text);
    }

    private CommandNet() {
    }
}
