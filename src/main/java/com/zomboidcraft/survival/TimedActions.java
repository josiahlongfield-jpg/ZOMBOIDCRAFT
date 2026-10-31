package com.zomboidcraft.survival;

import com.zomboidcraft.ZomboidCraft;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Zomboid's timed actions: bandaging, splinting, boarding up and the like take a few seconds, shown as a bar above
 * the belt. Walking off, swinging at something, shooting or being hit stops it (swings and shots cancel it where
 * they happen), and nothing is used up until the bar fills.
 * <p>
 * Zomboid's action queue too (PZ review section 16, Josiah 2026-10-31): starting something while busy lines it up
 * behind what's running, with no limit, and each starts when the one before finishes. Anything that stops the
 * running action (walking off, a swing, a hit) clears the whole queue, as Zomboid's does. The queued labels are
 * synced so the HUD can list what's next.
 */
public final class TimedActions {
    /** What the player's client draws: a label and how many ticks the bar takes. A new {@code id} restarts it. */
    public record View(String label, int ticks, int id) {
        public static final View NONE = new View("", 0, 0);

        public static final StreamCodec<io.netty.buffer.ByteBuf, View> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, View::label,
                ByteBufCodecs.VAR_INT, View::ticks,
                ByteBufCodecs.VAR_INT, View::id,
                View::new);

        public boolean running() {
            return ticks > 0;
        }
    }

    public static final AttachmentType<View> TYPE = AttachmentRegistry.create(
            ZomboidCraft.id("timed_action"),
            builder -> builder.initializer(() -> View.NONE)
                    .syncWith(View.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

    /** What's lined up after the running action, by label, in order (what the HUD lists). */
    public static final AttachmentType<List<String>> QUEUED = AttachmentRegistry.create(
            ZomboidCraft.id("timed_queue"),
            builder -> builder.initializer(List::<String>of)
                    .syncWith(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly()));

    /** How far you can shuffle while working before it counts as walking off. */
    private static final double STRAY = 0.6;

    private static final class Running {
        final String key;
        final int total;
        int left;
        final Vec3 from;
        int hurt;
        final Consumer<ServerPlayer> done;

        Running(String key, int total, Vec3 from, int hurt, Consumer<ServerPlayer> done) {
            this.key = key;
            this.total = total;
            this.left = total;
            this.from = from;
            this.hurt = hurt;
            this.done = done;
        }
    }

    private record Pending(String key, String label, int ticks, Consumer<ServerPlayer> done) {
    }

    private static final Map<UUID, Running> RUNNING = new HashMap<>();
    private static final Map<UUID, java.util.ArrayDeque<Pending>> QUEUE = new HashMap<>();
    private static int next = 1;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(TimedActions::tick);
    }

    /** True while the player is in the middle of something. */
    public static boolean busy(Player player) {
        return RUNNING.containsKey(player.getUUID());
    }

    /** True while the player is doing this particular thing (the key {@link #start} was given). */
    public static boolean doing(Player player, String key) {
        Running r = RUNNING.get(player.getUUID());
        return r != null && r.key.equals(key);
    }

    /**
     * Starts an action, or lines it up behind what's running. {@code key} tells repeats apart (holding the use key
     * mustn't restart the bar or queue it twice); {@code done} runs when the bar fills and should check again that it
     * still can.
     */
    public static void start(ServerPlayer player, String key, String label, int ticks, Consumer<ServerPlayer> done) {
        if (doing(player, key)) return;
        if (player.isCreative()) {
            done.accept(player);
            return;
        }
        ticks = Math.max(1, ticks);
        if (busy(player)) {
            java.util.ArrayDeque<Pending> q = QUEUE.computeIfAbsent(player.getUUID(), u -> new java.util.ArrayDeque<>());
            for (Pending p : q) {
                if (p.key.equals(key)) return;
            }
            q.add(new Pending(key, label, ticks, done));
            syncQueue(player);
            return;
        }
        begin(player, new Pending(key, label, ticks, done));
    }

    private static void begin(ServerPlayer player, Pending p) {
        RUNNING.put(player.getUUID(), new Running(p.key, p.ticks, player.position(), player.hurtTime, p.done));
        player.setAttached(TYPE, new View(p.label, p.ticks, next++));
    }

    /** Whether this is lined up to run (the key {@link #start} was given). */
    public static boolean queued(Player player, String key) {
        java.util.ArrayDeque<Pending> q = QUEUE.get(player.getUUID());
        if (q == null) return false;
        for (Pending p : q) {
            if (p.key.equals(key)) return true;
        }
        return false;
    }

    private static void syncQueue(ServerPlayer player) {
        java.util.ArrayDeque<Pending> q = QUEUE.get(player.getUUID());
        List<String> labels = new ArrayList<>();
        if (q != null) q.forEach(p -> labels.add(p.label));
        player.setAttached(QUEUED, List.copyOf(labels));
    }

    /** Stops what the player is doing and everything lined up after it, saying why when there's a reason worth telling. */
    public static void cancel(ServerPlayer player, String why) {
        boolean queued = QUEUE.remove(player.getUUID()) != null;
        if (queued) player.setAttached(QUEUED, List.of());
        if (RUNNING.remove(player.getUUID()) == null) return;
        player.setAttached(TYPE, View.NONE);
        if (why != null) player.sendOverlayMessage(Component.literal(why).withStyle(ChatFormatting.GRAY));
    }

    private static void tick(MinecraftServer server) {
        if (RUNNING.isEmpty()) return;
        List<Runnable> finished = new ArrayList<>();
        Iterator<Map.Entry<UUID, Running>> it = RUNNING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Running> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            Running r = e.getValue();
            if (player == null || !player.isAlive() || player.isSpectator()) {
                it.remove();
                QUEUE.remove(e.getKey());
                if (player != null) {
                    player.setAttached(TYPE, View.NONE);
                    player.setAttached(QUEUED, List.of());
                }
                continue;
            }
            String why = null;
            Vec3 at = player.position();
            double dx = at.x - r.from.x, dz = at.z - r.from.z;
            if (dx * dx + dz * dz > STRAY * STRAY) why = "You stop to move.";
            else if (player.hurtTime > r.hurt) why = "You're hit and stop.";
            r.hurt = player.hurtTime;
            if (why != null) {
                it.remove();
                if (QUEUE.remove(e.getKey()) != null) player.setAttached(QUEUED, List.of());
                player.setAttached(TYPE, View.NONE);
                player.sendOverlayMessage(Component.literal(why).withStyle(ChatFormatting.GRAY));
                continue;
            }
            if (--r.left > 0) continue;
            it.remove();
            player.setAttached(TYPE, View.NONE);
            finished.add(() -> {
                r.done.accept(player);
                // then the next in line, from where they stand now
                java.util.ArrayDeque<Pending> q = QUEUE.get(player.getUUID());
                if (q != null && !busy(player)) {
                    Pending p = q.poll();
                    if (q.isEmpty()) QUEUE.remove(player.getUUID());
                    if (p != null) begin(player, p);
                    syncQueue(player);
                }
            });
        }
        // after the loop: finishing one thing may start the next
        finished.forEach(Runnable::run);
    }

    private TimedActions() {
    }
}
