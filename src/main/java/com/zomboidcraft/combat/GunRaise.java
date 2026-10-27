package com.zomboidcraft.combat;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Whether others see a survivor's gun up, pointing where they look, or at the low ready: up while their camera is
 * over their shoulder and for a while after each shot (guns.raiseAfterShot). Synced to everyone who can see them.
 */
public final class GunRaise {
    /** Bit 0: the camera is over the shoulder; the rest: the game time of the last shot. */
    public static final AttachmentType<Long> TYPE = AttachmentRegistry.create(
            ZomboidCraft.id("gun_raise"),
            builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

    private GunRaise() {
    }

    public static void register() {
        // loading the class registers the attachment
    }

    public static void shoulder(ServerPlayer player, boolean on) {
        long v = player.getAttachedOrElse(TYPE, 0L);
        long next = (v & ~1L) | (on ? 1L : 0L);
        if (next != v) {
            player.setAttached(TYPE, next);
        }
    }

    /** A shot: keeps the gun up. Only resent once a second or so, full auto needn't sync every round. */
    public static void shot(ServerPlayer player) {
        long v = player.getAttachedOrElse(TYPE, 0L);
        long now = player.level().getGameTime();
        if (now - (v >> 1) >= 20) {
            player.setAttached(TYPE, (now << 1) | (v & 1L));
        }
    }

    public static boolean raised(Player player) {
        long v = player.getAttachedOrElse(TYPE, 0L);
        // a shot counts from up to a second earlier than it was sent
        return (v & 1L) != 0
                || player.level().getGameTime() - (v >> 1) < ZcConfig.get().guns.raiseAfterShot * 20 + 20;
    }
}
