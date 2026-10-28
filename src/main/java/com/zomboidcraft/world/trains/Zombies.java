package com.zomboidcraft.world.trains;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.zombie.ZombieLook;
import com.zomboidcraft.zombie.ZombieSpawner;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Zombies and trains. The game's path finding knows nothing of a train's floor, so near one the
 * zombies are steered by hand: one after somebody aboard heads for the nearest open door, then in;
 * one aboard goes straight for whoever it's after in the same train.
 *
 * <p>And zombies ride the trains. Far from everyone, a train's dead are just a count (its
 * passengers), changing at each stop as some shamble off and others in; as a player comes near,
 * they're made real in the cars nobody's standing in, and when everyone's far again they go back to
 * being a count. With players about, idle zombies by an open door wander aboard and idle ones aboard
 * wander off.
 */
final class Zombies {
    /** Players this near the train make its passengers real; once none is this far, they fold back into a count. */
    private static final double REAL = 80, FOLD = 100;

    /** Every second: make a train's riding dead real near players, or fold them back to a count away from them. */
    static void riders(ServerLevel level, TrainRun run, TrainEntity train) {
        if (level.getGameTime() % 20 != 7) {
            return;
        }
        ZcConfig.Trains cfg = ZcConfig.get().trains;
        Line line = run.line;
        TrainCar.Pose mid = run.pose((line.cars - 1) / 2);
        Vec3 at = new Vec3(mid.x(), mid.y(), mid.z());
        double nearest = Double.MAX_VALUE;
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator()) {
                nearest = Math.min(nearest, p.position().distanceTo(at));
            }
        }
        double r = line.cars * TrainCar.PITCH / 2 + 4;
        List<Zombie> aboard = level.getEntitiesOfClass(Zombie.class,
                new AABB(at.x - r, at.y - 3, at.z - r, at.x + r, at.y + 6, at.z + r),
                z -> z.isAlive() && ((TrainRider) z).zomboidcraft$train() == train);
        if (nearest > FOLD) {
            for (Zombie z : aboard) {
                if (!z.isPersistenceRequired() && !z.hasCustomName()) {
                    z.discard();
                    run.passengers++;
                }
            }
            return;
        }
        if (nearest > REAL || run.passengers <= 0) {
            return;
        }
        // into the cars nobody's in (OTTO's cab excepted), never right in front of anyone
        RandomSource random = level.getRandom();
        int count = aboard.size();
        for (int tries = 0; run.passengers > 0 && count < cfg.zombieRidersMax && tries < 12; tries++) {
            int car = random.nextInt(line.cars);
            TrainCar.Pose pose = run.pose(car);
            double zEnd = car == 0 ? TrainCar.CAB - 1 : TrainCar.HALF - 1.5;
            double lx = (random.nextDouble() * 2 - 1) * 1.1, lz = -TrainCar.HALF + 1.5 + random.nextDouble() * (zEnd + TrainCar.HALF - 1.5);
            Vec3 spot = pose.toWorld(lx, 0.02, lz);
            boolean seen = false;
            for (ServerPlayer p : level.players()) {
                seen |= p.position().distanceToSqr(spot) < 12 * 12;
            }
            if (seen || !level.hasChunkAt(BlockPos.containing(spot))) {
                continue;
            }
            Zombie z = EntityTypes.ZOMBIE.spawn(level, BlockPos.containing(spot), EntitySpawnReason.EVENT);
            if (z == null) {
                continue;
            }
            String look = ZombieSpawner.uniform(level, z.blockPosition(), random);
            if (look != null) {
                z.setAttached(ZombieLook.OUTFIT, look);
            }
            z.setPos(spot.x, spot.y, spot.z);
            z.setYRot(random.nextFloat() * 360);
            z.setYHeadRot(z.getYRot());
            ((TrainRider) z).zomboidcraft$ride(train, car, train.s());
            run.passengers--;
            count++;
        }
    }

    /** Pulled in to a stop with nobody about: a few of the riding dead get off, a few from the platform get on. */
    static void stopped(ServerLevel level, TrainRun run) {
        ZcConfig.Trains cfg = ZcConfig.get().trains;
        TrainCar.Pose mid = run.pose((run.line.cars - 1) / 2);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(mid.x(), mid.y(), mid.z()) < REAL * REAL) {
                return;     // they're real here, and get on and off by themselves
            }
        }
        RandomSource random = level.getRandom();
        int off = run.passengers > 0 ? random.nextInt(run.passengers + 1) / 2 : 0;
        int on = random.nextInt(4) == 0 ? 0 : random.nextInt(3);
        run.passengers = Math.max(0, Math.min(cfg.zombieRidersMax, run.passengers - off + on));
    }

    static void steer(ServerLevel level, TrainRun run, TrainEntity train) {
        if (level.getGameTime() % 2 != 0) {
            return;
        }
        Line line = run.line;
        TrainCar.Pose mid = run.pose((line.cars - 1) / 2);
        double r = line.cars * TrainCar.PITCH / 2 + 14;
        AABB box = new AABB(mid.x() - r, mid.y() - 4, mid.z() - r, mid.x() + r, mid.y() + 7, mid.z() + r);
        boolean open = run.doors > 0.8f && run.v < 0.01;
        for (Zombie z : level.getEntitiesOfClass(Zombie.class, box, Zombie::isAlive)) {
            LivingEntity target = z.getTarget();
            if (target == null || !target.isAlive()) {
                if (open && run.dwell > TrainRun.DOOR_CLOSE + TrainRun.LEAVE + 40) {
                    wander(level, run, train, z);
                }
                continue;
            }
            boolean zAboard = ((TrainRider) z).zomboidcraft$train() == train;
            boolean tAboard = aboard(level, train, target);
            if (zAboard && (tAboard || z.distanceToSqr(target) < 24 * 24)) {
                go(z, target.position());
            } else if (!zAboard && tAboard && open) {
                Vec3 door = nearestDoor(run, z.position());
                if (door != null && z.position().distanceToSqr(door) < 16 * 16) {
                    TrainCar.Pose pose = run.pose(carOf(run, door));
                    Vec3 local = pose.toLocal(z.position());
                    Vec3 doorLocal = pose.toLocal(door);
                    // line up outside the door, then step in
                    boolean lined = Math.abs(local.z - doorLocal.z) < 0.6;
                    go(z, lined ? pose.toWorld(doorLocal.x * 0.3, 0, doorLocal.z) : door);
                }
            }
        }
    }

    /**
     * An idle zombie while the doors stand open: one in four by the door shuffles aboard, one in three
     * aboard shuffles off (which ones is fixed for each zombie, so they don't dither).
     */
    private static void wander(ServerLevel level, TrainRun run, TrainEntity train, Zombie z) {
        boolean zAboard = ((TrainRider) z).zomboidcraft$train() == train;
        int pick = Math.floorMod(z.getUUID().hashCode(), 12);
        Vec3 door = nearestDoor(run, z.position());
        if (door == null) {
            return;
        }
        TrainCar.Pose pose = run.pose(carOf(run, door));
        Vec3 doorLocal = pose.toLocal(door);
        if (!zAboard && pick < 3 && z.position().distanceToSqr(door) < 10 * 10) {
            Vec3 local = pose.toLocal(z.position());
            boolean lined = Math.abs(local.z - doorLocal.z) < 0.6;
            go(z, lined ? pose.toWorld(doorLocal.x * 0.3, 0, doorLocal.z) : door, 0.6);
        } else if (zAboard && pick >= 8) {
            Vec3 local = pose.toLocal(z.position());
            boolean lined = Math.abs(local.z - doorLocal.z) < 0.6;
            // line up with the door inside, then out past it
            go(z, lined ? pose.toWorld(doorLocal.x * 1.8, 0, doorLocal.z) : pose.toWorld(doorLocal.x * 0.3, 0, doorLocal.z), 0.6);
        }
    }

    private static boolean aboard(ServerLevel level, TrainEntity train, LivingEntity e) {
        if (e instanceof net.minecraft.server.level.ServerPlayer p) {
            return Trains.riders(level, train).contains(p);
        }
        return ((TrainRider) e).zomboidcraft$train() == train;
    }

    private static void go(Mob mob, Vec3 at) {
        go(mob, at, 1.0);
    }

    private static void go(Mob mob, Vec3 at, double speed) {
        mob.getNavigation().stop();
        mob.getMoveControl().setWantedPosition(at.x, at.y, at.z, speed);
    }

    /** The nearest doorway on the open side (just outside it), or null. */
    private static Vec3 nearestDoor(TrainRun run, Vec3 from) {
        Vec3 best = null;
        double bestD = Double.MAX_VALUE;
        for (int car = 0; car < run.line.cars; car++) {
            TrainCar.Pose pose = run.pose(car);
            for (int side = -1; side <= 1; side += 2) {
                if (run.doorSide != 0 && run.doorSide != side) {
                    continue;
                }
                for (double d : TrainCar.DOORS) {
                    Vec3 at = pose.toWorld(side * (TrainCar.HALF_WIDTH + 0.6), 0, d);
                    double dist = at.distanceToSqr(from);
                    if (dist < bestD) {
                        bestD = dist;
                        best = at;
                    }
                }
            }
        }
        return best;
    }

    private static int carOf(TrainRun run, Vec3 at) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int car = 0; car < run.line.cars; car++) {
            TrainCar.Pose p = run.pose(car);
            double d = (p.x() - at.x) * (p.x() - at.x) + (p.z() - at.z) * (p.z() - at.z);
            if (d < bestD) {
                bestD = d;
                best = car;
            }
        }
        return best;
    }

    private Zombies() {
    }
}
