package com.zomboidcraft.world.trains;

import com.zomboidcraft.ZomboidCraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * The trains' sounds. For now each one stands on a vanilla or car sound (see sounds.json, "type":
 * "event"), so recordings can replace them later without touching the code.
 */
public final class TrainSounds {
    public static final SoundEvent HORN = register("train.horn", 192f);
    public static final SoundEvent CHIME = register("train.chime", 32f);
    public static final SoundEvent DOORS = register("train.doors", 24f);
    public static final SoundEvent OTTO = register("train.otto", 24f);
    public static final SoundEvent ROLLING = register("train.rolling", 48f);
    public static final SoundEvent FLARE = register("train.flare", 96f);

    static void register() {
        // loading the class registers the sounds
    }

    private static SoundEvent register(String name, float range) {
        Identifier id = ZomboidCraft.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createFixedRangeEvent(id, range));
    }

    /** The horn from the front of the train: once, or a long-short pair. */
    static void horn(ServerLevel level, TrainRun run, int blasts) {
        TrainCar.Pose front = run.pose(0);
        var at = front.toWorld(0, 2, TrainCar.HALF);
        level.playSound(null, at.x, at.y, at.z, HORN, SoundSource.NEUTRAL, 3f, blasts > 1 ? 0.95f : 1f);
        if (blasts > 1) {
            level.playSound(null, at.x, at.y, at.z, HORN, SoundSource.NEUTRAL, 2f, 1.12f);
        }
    }

    /** The two-note chime in every car: arriving and doors closing. */
    static void chime(ServerLevel level, TrainRun run) {
        for (int car = 0; car < run.line.cars; car++) {
            var at = run.pose(car).toWorld(0, 2.6, 0);
            level.playSound(null, at.x, at.y, at.z, CHIME, SoundSource.NEUTRAL, 1f, 1.19f);
            level.playSound(null, at.x, at.y, at.z, CHIME, SoundSource.NEUTRAL, 0.8f, 0.94f);
        }
    }

    /** The doors sliding, at each door on the side that opens. */
    static void doors(ServerLevel level, TrainRun run, boolean open) {
        for (int car = 0; car < run.line.cars; car++) {
            TrainCar.Pose pose = run.pose(car);
            for (double d : TrainCar.DOORS) {
                double x = run.doorSide == 0 ? 0 : run.doorSide * TrainCar.HALF_WIDTH;
                var at = pose.toWorld(x, 1.2, d);
                level.playSound(null, at.x, at.y, at.z, DOORS, SoundSource.NEUTRAL, 0.7f, open ? 1f : 0.85f);
            }
        }
    }

    /** OTTO's chirps in the cab and through each car's speakers. */
    static void otto(ServerLevel level, TrainRun run) {
        for (int car = 0; car < run.line.cars; car++) {
            var at = run.pose(car).toWorld(0, 2.7, car == 0 ? TrainCar.CAB + 1 : 0);
            float pitch = 0.9f + level.getRandom().nextFloat() * 0.6f;
            level.playSound(null, at.x, at.y, at.z, OTTO, SoundSource.NEUTRAL, 0.6f, pitch);
        }
    }

    private TrainSounds() {
    }
}
