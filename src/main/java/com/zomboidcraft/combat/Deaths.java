package com.zomboidcraft.combat;

import com.mojang.serialization.Codec;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/**
 * How shots move a walker's body, for every client to draw the same: a living one jerks where it's hit, and a dead one
 * falls the way the killing shot and its own momentum take it, then leaves its body lying there (zombie.Corpse). The
 * client works out the movements (ZHit, ZDeath); the server says what happened.
 *
 * <p>A death is packed in a long, kept on the dying zombie and then on its body: bits 0-2 what it was doing (STILL to
 * DOWN), 3-10 which way the shot went through it (256ths of a turn from its front round toward its left), 11-12 how
 * hard (0 handgun to 3 buckshot), 13 whether anything pushed it at all, 14-17 the part struck plus one (0 none), 18-25
 * a seed, and 32-47 which way its body faced (65536ths of a turn of its yaw).
 */
public final class Deaths {
    /** What a walker was doing when it died. */
    public static final int STILL = 0, SHAMBLE = 1, RUN = 2, LUNGE = 3, HOP = 4, CRAWL = 5, DOWN = 6;
    /**
     * Ticks a dead walker stays itself while it falls, before its body takes its place; the body is laid two ticks
     * before, so it's there the moment the zombie goes. The client's fall settles before this (ZDeath.SETTLED).
     */
    public static final int HOLD = 36;

    public static final AttachmentType<Long> DEATH = AttachmentRegistry.create(
            ZomboidCraft.id("death"),
            builder -> builder
                    .persistent(Codec.LONG)
                    .syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

    /**
     * Shots landing on a living walker, this tick: which one, the parts struck (Dismember's, one bit each), which way
     * the shot went (256ths of a turn of yaw, as Dismember packs a limb's flight) and how hard (0 to 3).
     */
    public record Hit(int entity, int parts, int heading, int force) implements CustomPacketPayload {
        public static final Type<Hit> TYPE = new Type<>(ZomboidCraft.id("body_hit"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Hit> CODEC = new StreamCodec<>() {
            @Override
            public Hit decode(RegistryFriendlyByteBuf buf) {
                return new Hit(buf.readVarInt(), buf.readVarInt(), buf.readUnsignedByte(), buf.readUnsignedByte());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, Hit h) {
                buf.writeVarInt(h.entity);
                buf.writeVarInt(h.parts);
                buf.writeByte(h.heading);
                buf.writeByte(h.force);
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Hit.TYPE, Hit.CODEC);
    }

    /** Shots struck a living walker this tick: everyone who can see it sees it jerk. */
    static void jerk(Zombie zombie, int parts, Vec3 dir, int force) {
        if (!ZcConfig.get().corpses.hitReactions) {
            return;
        }
        Hit hit = new Hit(zombie.getId(), parts, yaw256(dir), force);
        for (ServerPlayer player : PlayerLookup.tracking(zombie)) {
            ServerPlayNetworking.send(player, hit);
        }
    }

    /** A direction's yaw (MC's: 0 south, a quarter turn west) in 256ths of a turn. */
    private static int yaw256(Vec3 dir) {
        return Math.floorMod(Math.round((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)) / 360f * 256f), 256);
    }

    /**
     * A walker just died (server): how it goes down, from what it was doing, and the shot that killed it if it was
     * shot this tick (else the blow, or nothing pushing it at all).
     */
    public static void died(Zombie zombie, DamageSource source) {
        if (zombie.hasAttached(DEATH)) {
            return;
        }
        Vec3 dir = null;
        int force = 0, part = -1;
        Dismember.Shot shot = Dismember.lastShot(zombie);
        if (shot != null) {
            dir = shot.dir();
            force = Dismember.force(shot.gun());
            part = shot.part();
        } else {
            Vec3 from = source.getSourcePosition();
            Entity by = source.getDirectEntity();
            if (from != null) {
                dir = zombie.position().subtract(from).multiply(1, 0, 1);
                if (dir.lengthSqr() < 1e-6) {
                    dir = null;
                }
            }
            if (source.is(DamageTypeTags.IS_EXPLOSION)) {
                force = 3;
            } else if (by instanceof LivingEntity) {
                force = 1;      // a blow from someone stood next to it
                part = 2;
            }
        }
        long d = BodyTrack.deathKind(zombie);
        if (dir != null) {
            float yaw = (float) Math.toRadians(zombie.yBodyRot);
            float h = (float) Math.atan2(-dir.x, dir.z);
            // through the body: turned from its front round toward its left
            int heading = Math.floorMod(Math.round((yaw - h) / (float) (Math.PI * 2) * 256f), 256);
            d |= (long) heading << 3 | (long) force << 11 | 1L << 13;
        }
        d |= (long) (part + 1) << 14;
        d |= (long) (zombie.getRandom().nextInt(256)) << 18;
        d |= (long) Math.floorMod(Math.round(zombie.yBodyRot / 360f * 65536f), 65536) << 32;
        zombie.setAttached(DEATH, d);
    }

    public static int kind(long death) {
        return (int) (death & 7);
    }

    /** Which way the shot went through it: radians from its front round toward its left. */
    public static float heading(long death) {
        return (death >> 3 & 0xFF) / 256f * (float) (Math.PI * 2);
    }

    /** How hard it was pushed: 0 handgun to 3 buckshot, or -1 when nothing pushed it. */
    public static int force(long death) {
        return (death >> 13 & 1) == 0 ? -1 : (int) (death >> 11 & 3);
    }

    /** The part struck (Dismember's), or -1. */
    public static int part(long death) {
        return (int) (death >> 14 & 15) - 1;
    }

    public static int seed(long death) {
        return (int) (death >> 18 & 0xFF);
    }

    /** Which way its body faced (degrees of yaw). */
    public static float yaw(long death) {
        return (death >> 32 & 0xFFFF) / 65536f * 360f;
    }

    private Deaths() {
    }
}
