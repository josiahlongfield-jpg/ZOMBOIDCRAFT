package com.zomboidcraft.survival;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.zombie.ZombieTypes;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;

/**
 * Zomboid's Panic, 0-100: the dead in sight push it up (more of them, closer, faster), a zombie's hands on you
 * spikes it, and it ebbs away once none are in view. It throws your aim off (Injuries.armShake) and, at its worst,
 * makes your swing wild. Survivors steady with time: each day you've lived takes a little off how hard the sight of
 * them hits, as in Zomboid. Occupation and traits (Brave, Cowardly, Desensitized) scale it in wave 3.
 * <p>
 * Not saved: you wake into a new session calm. Synced to the player's own client for the moodle.
 */
public final class Panic {
    public static final AttachmentType<Integer> TYPE = AttachmentRegistry.create(
            ZomboidCraft.id("panic"),
            builder -> builder.initializer(() -> 0).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

    /** How far you notice them, in blocks. */
    private static final double SIGHT = 14;

    public static void register() {
        // loading the class registers the attachment
    }

    public static int get(Player player) {
        return player.getAttachedOrElse(TYPE, 0);
    }

    private static void set(ServerPlayer player, int value) {
        value = Math.max(0, Math.min(100, value));
        if (value != get(player)) player.setAttached(TYPE, value);
    }

    /** Zomboid's moodle stages: 1 slight panic, 2 panic, 3 strong panic, 4 extreme panic. */
    public static int stage(int panic) {
        return panic >= 85 ? 4 : panic >= 60 ? 3 : panic >= 35 ? 2 : panic >= 15 ? 1 : 0;
    }

    /** A zombie got its hands on you. */
    public static void hit(ServerPlayer player) {
        set(player, get(player) + Math.round(18 * steel(player)));
    }

    /** Once a second (SurvivalEvents.tickPlayer). */
    static void tick(ServerPlayer player, int second) {
        if (Sleep.asleep(player)) {
            set(player, get(player) - 5);
            return;
        }
        float seen = 0;
        for (Zombie z : player.level().getEntitiesOfClass(Zombie.class, player.getBoundingBox().inflate(SIGHT), ZombieTypes::isWalker)) {
            if (!z.isAlive() || !player.hasLineOfSight(z)) continue;
            double d = Math.sqrt(z.distanceToSqr(player));
            // close ones count most; one shambling at the edge of sight barely registers
            seen += (float) (d < 3 ? 3 : d < 6 ? 1.6 : 0.6);
        }
        int now = get(player);
        if (seen > 0) {
            // a crowd frightens more, but not without end
            float rise = Math.min(14, 1.5f * seen) * steel(player);
            set(player, now + Math.max(1, Math.round(rise)));
        } else if (now > 0) {
            set(player, now - (now > 60 ? 2 : 3));
        }
        if (seen > 0 && stage(get(player)) >= 3) Mood.add(player, Mood.Stat.STRESS, second % 3 == 0 ? 1 : 0);
    }

    /**
     * How hard fear lands on you (1 at the start): Zomboid takes about 2% off a day you've survived, to a floor of a
     * fifth. Days come from the player's own play time.
     */
    static float steel(ServerPlayer player) {
        long ticks = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.TIME_SINCE_DEATH));
        double days = ticks / 20.0 / (Needs.hourSeconds() * 24);
        return (float) Math.max(0.2, 1 - 0.02 * days);
    }

    /** Panic throws your aim: added to Injuries.armShake. */
    public static float shake(Player player) {
        return switch (stage(get(player))) {
            case 4 -> 0.45f;
            case 3 -> 0.3f;
            case 2 -> 0.15f;
            case 1 -> 0.05f;
            default -> 0f;
        };
    }

    private Panic() {
    }
}
