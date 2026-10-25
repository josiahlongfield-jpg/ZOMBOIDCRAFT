package com.zomboidcraft.zombie;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.weather.WeatherDirector;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Zombies hunt by sound: anything loud draws every idle zombie in earshot. */
public final class Noise {
    public static final double WALK = 5;
    public static final double SPRINT = 14;
    public static final double ATTACK = 10;
    public static final double BREAK_BLOCK = 12;

    /** Who made it hear: within this of a noise, it's yours, and your HUD's noise meter shows how far it carried. */
    private static final double YOURS = 5;

    /** The last noise a player made, in tenths of a block of earshot; a new {@code id} means a new noise. */
    public record Heard(int tenths, int id) {
        public static final StreamCodec<io.netty.buffer.ByteBuf, Heard> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Heard::tenths, ByteBufCodecs.VAR_INT, Heard::id, Heard::new);
    }

    public static final AttachmentType<Heard> HEARD = AttachmentRegistry.create(
            ZomboidCraft.id("heard"),
            builder -> builder.initializer(() -> new Heard(0, 0))
                    .syncWith(Heard.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

    private static int heard;

    public static void register() {
        // loading the class registers the attachment
    }

    public static void emit(ServerLevel level, Vec3 pos, double radius) {
        radius *= ZcConfig.get().senses.hearing * WeatherDirector.hearing(level);
        if (radius <= 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(pos) < YOURS * YOURS) {
                player.setAttached(HEARD, new Heard((int) Math.round(radius * 10), ++heard));
            }
        }
        double radiusSqr = radius * radius;
        for (Zombie zombie : level.getEntitiesOfClass(Zombie.class, new AABB(pos, pos).inflate(radius))) {
            if (ZombieTypes.isWalker(zombie) && zombie.getTarget() == null
                    && zombie.position().distanceToSqr(pos) <= radiusSqr) {
                ((NoiseListener) zombie).zomboidcraft$hear(pos);
            }
        }
    }

    private Noise() {
    }
}
