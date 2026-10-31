package com.zomboidcraft.world.events;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.zombie.Noise;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Zomboid's meta events: every so often, something happens out of sight. A burst of gunfire or a scream comes from a
 * real spot 80 to 250 blocks from one of the players, everyone in earshot hears it from there, and the dead round
 * that spot hear it too and head that way. The sandbox's Meta Event setting (world.metaEvents) picks never, sometimes
 * or often. No animal sounds (Josiah), so Zomboid's dogs and wolves are left out; house and car alarms come from the
 * houses and cars that have them (LANDSCAPE, VEHICLES).
 *
 * Each one is kept in {@link #recent()} so the scene left behind (casings and bodies after gunfire, a forced door after
 * a scream) can be placed by whoever dresses the world (LOOT).
 */
public final class MetaEvents {
    public enum Kind { GUNFIRE, SCREAM }

    /** What happened where, and when (game time). */
    public record Happened(Kind kind, ServerLevel level, Vec3 pos, long time) {
    }

    public static final SoundEvent GUNFIRE = register("world.meta.gunfire", 320f);
    public static final SoundEvent SCREAM = register("world.meta.scream", 220f);

    private static final Deque<Happened> RECENT = new ArrayDeque<>();
    private static long next = -1;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(MetaEvents::tick);
    }

    /** The last few events, newest last. */
    public static List<Happened> recent() {
        return List.copyOf(RECENT);
    }

    /** Minutes between events, roughly, for each setting: Zomboid's are rare enough to stop you in your tracks. */
    private static int gap(String setting, RandomSource random) {
        double minutes = "often".equals(setting) ? 4 + random.nextDouble() * 6 : 12 + random.nextDouble() * 18;
        return (int) Math.round(minutes * 60 * 20);
    }

    private static void tick(MinecraftServer server) {
        String setting = ZcConfig.get().world.metaEvents;
        if ("never".equals(setting)) {
            return;
        }
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        RandomSource random = level.getRandom();
        if (next < 0 || next > now + gap("sometimes", random) * 2L) {
            next = now + gap(setting, random);
        }
        if (now < next) {
            return;
        }
        next = now + gap(setting, random);
        List<ServerPlayer> players = level.players().stream().filter(p -> !p.isSpectator()).toList();
        if (players.isEmpty()) {
            return;
        }
        fire(level, players.get(random.nextInt(players.size())), random.nextInt(10) < 7 ? Kind.GUNFIRE : Kind.SCREAM);
    }

    /** Sets one off near a player. */
    public static void fire(ServerLevel level, ServerPlayer near, Kind kind) {
        RandomSource random = level.getRandom();
        double a = random.nextDouble() * Math.PI * 2, d = 80 + random.nextDouble() * 170;
        double x = near.getX() + Math.cos(a) * d, z = near.getZ() + Math.sin(a) * d;
        double y = WorldEvents.surface(level, x, z, near.getY()) + 1.5;
        Vec3 pos = new Vec3(x, y, z);
        level.playSound(null, x, y, z, kind == Kind.GUNFIRE ? GUNFIRE : SCREAM, SoundSource.AMBIENT,
                1f, 0.95f + random.nextFloat() * 0.1f);
        // the dead round the spot come to see
        Noise.emit(level, pos, kind == Kind.GUNFIRE ? 96 : 48);
        RECENT.addLast(new Happened(kind, level, pos, level.getGameTime()));
        while (RECENT.size() > 16) {
            RECENT.removeFirst();
        }
        ZomboidCraft.LOGGER.debug("Meta event {} at {}", kind, pos);
    }

    private static SoundEvent register(String name, float range) {
        Identifier id = ZomboidCraft.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createFixedRangeEvent(id, range));
    }

    private MetaEvents() {
    }
}
