package com.zomboidcraft.world.trains;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Zombies and trains. The game's path finding knows nothing of a train's floor, so near one the
 * zombies are steered by hand: one after somebody aboard heads for the nearest open door, then in;
 * one aboard goes straight for whoever it's after in the same train.
 */
final class Zombies {
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

    private static boolean aboard(ServerLevel level, TrainEntity train, LivingEntity e) {
        if (e instanceof net.minecraft.server.level.ServerPlayer p) {
            return Trains.riders(level, train).contains(p);
        }
        return ((TrainRider) e).zomboidcraft$train() == train;
    }

    private static void go(Mob mob, Vec3 at) {
        mob.getNavigation().stop();
        mob.getMoveControl().setWantedPosition(at.x, at.y, at.z, 1.0);
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
