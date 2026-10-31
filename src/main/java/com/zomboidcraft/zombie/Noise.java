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
        emit(level, pos, radius, false);
    }

    /** An engine, a car alarm or a generator: Zomboid's Zombie Attraction Multiplier scales how far it carries. */
    public static void emitEngine(ServerLevel level, Vec3 pos, double radius) {
        emit(level, pos, radius * ZcConfig.get().zombiePopulation.attractionMultiplier, false);
    }

    /**
     * A noise the dead make themselves (thumping a door or fence): it draws in the ones further off, but the ones
     * already at it and after something keep after that, rather than turning to the thump at their elbow.
     */
    public static void emitByDead(ServerLevel level, Vec3 pos, double radius) {
        emit(level, pos, radius, true);
    }

    private static void emit(ServerLevel level, Vec3 pos, double radius, boolean byDead) {
        radius *= ZcConfig.get().senses.hearing * WeatherDirector.hearing(level);
        if (radius <= 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(pos) < YOURS * YOURS) {
                player.setAttached(HEARD, new Heard((int) Math.round(radius * 10), ++heard));
            }
        }
        com.zomboidcraft.creature.HarvestmanDirector.noise(level, pos, radius);
        // the dead still waiting as records in earshot come out to it too
        com.zomboidcraft.zombie.population.Population.wake(level, pos, radius);
        // and further off, past where anyone could see them, the herds and the records turn toward a loud one
        com.zomboidcraft.zombie.population.Herds.noise(level, pos, radius);
        // each hears as its lore rolled (zombieLore hearing): keen ones half as far again, poor ones 60%, and the
        // less keen walk to somewhere near the sound rather than the spot itself
        for (Zombie zombie : level.getEntitiesOfClass(Zombie.class, new AABB(pos, pos).inflate(radius * 1.5))) {
            if (!ZombieTypes.isWalker(zombie) || zombie.getTarget() != null) {
                continue;
            }
            double reach = radius * Lore.hearing(zombie);
            double dist2 = zombie.position().distanceToSqr(pos);
            if (byDead && dist2 < ALREADY_THERE * ALREADY_THERE && ((NoiseListener) zombie).zomboidcraft$heardAt() != null) {
                continue;
            }
            if (dist2 <= reach * reach) {
                // close by, anyone can tell where a sound came from; further off the dull of hearing guess
                double spread = dist2 < PINPOINT * PINPOINT ? 0 : Lore.hearingSpread(zombie);
                Vec3 heard = spread <= 0 ? pos : pos.add((zombie.getRandom().nextDouble() * 2 - 1) * spread, 0,
                        (zombie.getRandom().nextDouble() * 2 - 1) * spread);
                ((NoiseListener) zombie).zomboidcraft$hear(heard);
            }
        }
    }

    /** Within this many blocks a zombie already after something ignores the thumping of the others (emitByDead). */
    private static final double ALREADY_THERE = 5;
    /** Within this many blocks a zombie hears exactly where a sound was, whatever its hearing. */
    private static final double PINPOINT = 6;

    private Noise() {
    }
}
