package com.zomboidcraft.zombie;

import com.zomboidcraft.ZomboidCraft;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;

/**
 * How big the horde round each player is, for the crowd sounds the client plays: the zombies within
 * {@link #RANGE} blocks, and how many of them are after that player. Counted twice a second and sent
 * to the player only, packed as near * 256 + after.
 */
public final class Hordes {
    public static final double RANGE = 48;
    private static final int EVERY = 10;

    public static final AttachmentType<Integer> COUNT = AttachmentRegistry.create(
            ZomboidCraft.id("horde"),
            builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

    public static int near(int packed) {
        return packed >> 8;
    }

    public static int after(int packed) {
        return packed & 0xFF;
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Hordes::tick);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % EVERY != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int near = 0, after = 0;
            if (!player.isSpectator()) {
                for (Zombie zombie : player.level().getEntitiesOfClass(Zombie.class, new AABB(player.blockPosition()).inflate(RANGE),
                        z -> z.isAlive() && z.distanceToSqr(player) < RANGE * RANGE)) {
                    near++;
                    if (zombie.getTarget() == player) {
                        after++;
                    }
                }
            }
            int packed = Math.min(near, 0xFFFF) << 8 | Math.min(after, 0xFF);
            Integer old = player.getAttached(COUNT);
            if (old == null || old != packed) {
                player.setAttached(COUNT, packed);
            }
        }
    }

    private Hordes() {
    }
}
