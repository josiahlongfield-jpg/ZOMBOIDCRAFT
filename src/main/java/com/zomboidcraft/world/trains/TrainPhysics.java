package com.zomboidcraft.world.trains;

import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Walking about inside a moving train. Each tick, before anything aboard moves itself, it's carried
 * along with its car (turned with it on a curve); then its own movement is stopped by the car's
 * floor, walls, doors, seats and roof, worked out in the car's frame so a car on a bend is as solid
 * as one on a straight. Whoever moves an entity does this: the client for its own player, the
 * server for everything else. Leaving a moving car (jumping off the roof, say) keeps the train's speed.
 */
public final class TrainPhysics {
    private static final double EPS = 1e-7;

    /** Other players and mobs aboard, on your game: how far each walked in its car this tick (by entity id). */
    public static final java.util.Map<Integer, Float> REMOTE_STEPS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * How far a living thing walked this tick, for its legs: what it moved, less what the train
     * carried it, so someone standing in a moving car stands still rather than running on the spot.
     */
    public static float walkDistance(LivingEntity e, float moved) {
        if (e.level().isClientSide()) {
            Float step = REMOTE_STEPS.get(e.getId());
            if (step != null) {
                return step;
            }
        }
        double[] carried = ((TrainRider) e).zomboidcraft$carried();
        if (carried[0] == 0 && carried[1] == 0) {
            return moved;
        }
        return (float) Math.hypot(e.getX() - e.xo - carried[0], e.getZ() - e.zo - carried[1]);
    }

    /** Whether this side moves this entity: your own player on your game, every non-player on the server. */
    public static boolean handles(Entity e) {
        if (e instanceof TrainEntity || e.isPassenger() || e instanceof Projectile || e.isSpectator()) {
            return false;
        }
        return e.level().isClientSide() ? e instanceof Player p && p.isLocalPlayer() : !(e instanceof Player);
    }

    /** Called at the start of every entity's tick: carries it with the car it's in, and notices boarding and leaving. */
    public static void carry(Entity e) {
        TrainRider rider = (TrainRider) e;
        TrainEntity train = rider.zomboidcraft$train();
        double[] carried = rider.zomboidcraft$carried();
        carried[0] = 0;
        carried[1] = 0;
        if (train == null && TrainEntity.loaded(e.level().isClientSide()).isEmpty()) {
            return;
        }
        if (!handles(e)) {
            if (train != null) {
                rider.zomboidcraft$ride(null, 0, 0);
            }
            return;
        }
        Line line = train == null ? null : train.line();
        if (train != null && (train.isRemoved() || line == null || train.level() != e.level())) {
            rider.zomboidcraft$ride(null, 0, 0);
            train = null;
        }
        if (train != null) {
            double now = train.s();
            int car = rider.zomboidcraft$car();
            TrainCar.Pose from = TrainCar.pose(line.route, rider.zomboidcraft$trainS(), car), to = TrainCar.pose(line.route, now, car);
            Vec3 local = from.toLocal(e.position());
            Vec3 at = to.toWorld(local.x, local.y, local.z);
            Vec3 moved = at.subtract(e.position());
            if (moved.lengthSqr() > 1e-10) {
                e.setPos(at);
                carried[0] = moved.x;
                carried[1] = moved.z;
                float turn = Mth.wrapDegrees(to.yawDegrees() - from.yawDegrees());
                if (turn != 0) {
                    e.setYRot(e.getYRot() + turn);
                    if (e instanceof LivingEntity living) {
                        living.yHeadRot += turn;
                        living.yBodyRot += turn;
                    }
                }
            }
            int in = carAt(train, line, now, e.position());
            if (in < 0) {
                // stepped or fell off: keep the train's way
                e.setDeltaMovement(e.getDeltaMovement().add(moved));
                rider.zomboidcraft$ride(null, 0, 0);
            } else {
                rider.zomboidcraft$ride(train, in, now);
            }
            return;
        }
        for (TrainEntity t : TrainEntity.loaded(e.level().isClientSide())) {
            Line l = t.line();
            if (l == null || t.level() != e.level() || t.distanceToSqr(e) > sq(l.cars * TrainCar.PITCH / 2 + 8)) {
                continue;
            }
            int in = carAt(t, l, t.s(), e.position());
            if (in >= 0) {
                rider.zomboidcraft$ride(t, in, t.s());
                return;
            }
        }
    }

    /** Which car of this train a point (feet) is in or on top of, or -1. */
    public static int carAt(TrainEntity train, Line line, double s, Vec3 feet) {
        for (int car = 0; car < line.cars; car++) {
            TrainCar.Pose pose = TrainCar.pose(line.route, s, car);
            if (sq(pose.x() - feet.x) + sq(pose.z() - feet.z) > 13 * 13) {
                continue;
            }
            if (TrainCar.aboard(pose.toLocal(feet), car == 0, car == line.cars - 1)) {
                return car;
            }
        }
        return -1;
    }

    /** Called with an entity's movement before the world stops it: the train cars stop it first. */
    public static Vec3 clip(Entity e, Vec3 move) {
        if (move.lengthSqr() < 1e-12 || !handles(e)) {
            return move;
        }
        List<TrainEntity> trains = TrainEntity.loaded(e.level().isClientSide());
        if (trains.isEmpty()) {
            return move;
        }
        AABB box = e.getBoundingBox();
        for (TrainEntity t : trains) {
            Line line = t.line();
            if (line == null || t.level() != e.level() || t.distanceToSqr(e) > sq(line.cars * TrainCar.PITCH / 2 + 16)) {
                continue;
            }
            double s = t.s();
            boolean open = t.doorsOpen() > 0.8f && t.speed() < 0.01;
            int side = t.doorSide();
            for (int car = 0; car < line.cars; car++) {
                TrainCar.Pose pose = TrainCar.pose(line.route, s, car);
                if (sq(pose.x() - e.getX()) + sq(pose.z() - e.getZ()) > 14 * 14) {
                    continue;
                }
                List<double[]> solid = TrainCar.boxes(car, line.cars, open && side >= 0, open && side <= 0);
                move = clipCar(e, box, move, pose, solid);
            }
        }
        return move;
    }

    private static Vec3 clipCar(Entity e, AABB box, Vec3 move, TrainCar.Pose pose, List<double[]> solid) {
        Vec3 centre = pose.toLocal(new Vec3((box.minX + box.maxX) / 2, box.minY, (box.minZ + box.maxZ) / 2));
        double hw = (box.maxX - box.minX) / 2, h = box.maxY - box.minY;
        double[] eb = {centre.x - hw, centre.y, centre.z - hw, centre.x + hw, centre.y + h, centre.z + hw};
        Vec3 lm = pose.dirToLocal(move);
        double mx = lm.x, my = lm.y, mz = lm.z;
        if (eb[3] + Math.max(mx, 0) < -2.2 || eb[0] + Math.min(mx, 0) > 2.2
                || eb[5] + Math.max(mz, 0) < -TrainCar.PITCH / 2 - 0.2 || eb[2] + Math.min(mz, 0) > TrainCar.PITCH / 2 + 0.2
                || eb[4] + Math.max(my, 0) < -1.1 || eb[1] + Math.min(my, 0) > TrainCar.ROOF + 0.1) {
            return move;
        }
        double[] got = slide(solid, eb.clone(), mx, my, mz);
        boolean blocked = Math.abs(got[0] - mx) > EPS || Math.abs(got[2] - mz) > EPS;
        if (blocked && (e.onGround() || my < 0 && got[1] > my) && e.maxUpStep() > 0) {
            // step up onto a seat or over the floor's lip, the way the game steps up blocks
            double[] b = eb.clone();
            double up = axis(solid, b, 1, e.maxUpStep());
            shift(b, 1, up);
            double sx = axis(solid, b, 0, mx);
            shift(b, 0, sx);
            double sz = axis(solid, b, 2, mz);
            shift(b, 2, sz);
            double down = axis(solid, b, 1, -up + Math.min(my, 0));
            if (sx * sx + sz * sz > got[0] * got[0] + got[2] * got[2] + 1e-6) {
                got = new double[] {sx, up + down, sz};
            }
        }
        if (Math.abs(got[0] - mx) < EPS && Math.abs(got[1] - my) < EPS && Math.abs(got[2] - mz) < EPS) {
            return move;
        }
        return pose.dirToWorld(new Vec3(got[0], got[1], got[2]));
    }

    /** Moves a box up/down first, then along the longer horizontal way, then the other, stopping at solids. */
    private static double[] slide(List<double[]> solid, double[] b, double mx, double my, double mz) {
        double y = axis(solid, b, 1, my);
        shift(b, 1, y);
        double x, z;
        if (Math.abs(mx) >= Math.abs(mz)) {
            x = axis(solid, b, 0, mx);
            shift(b, 0, x);
            z = axis(solid, b, 2, mz);
        } else {
            z = axis(solid, b, 2, mz);
            shift(b, 2, z);
            x = axis(solid, b, 0, mx);
        }
        return new double[] {x, y, z};
    }

    /** How far box b can move along one axis (0 x, 1 y, 2 z) toward m before it meets a solid. */
    private static double axis(List<double[]> solid, double[] b, int axis, double m) {
        if (m == 0) {
            return 0;
        }
        int o1 = (axis + 1) % 3, o2 = (axis + 2) % 3;
        for (double[] s : solid) {
            if (b[o1 + 3] <= s[o1] + EPS || b[o1] >= s[o1 + 3] - EPS || b[o2 + 3] <= s[o2] + EPS || b[o2] >= s[o2 + 3] - EPS) {
                continue;
            }
            if (m > 0 && b[axis + 3] <= s[axis] + EPS) {
                m = Math.min(m, s[axis] - b[axis + 3]);
            } else if (m < 0 && b[axis] >= s[axis + 3] - EPS) {
                m = Math.max(m, s[axis + 3] - b[axis]);
            }
        }
        return m;
    }

    private static void shift(double[] b, int axis, double d) {
        b[axis] += d;
        b[axis + 3] += d;
    }

    private static double sq(double v) {
        return v * v;
    }

    private TrainPhysics() {
    }
}
