package com.zomboidcraft.creature;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Harvestman (step 1: it lives in the world). A long-legged thing that walks on any block face: floors, walls,
 * ceilings. Its body slides over whatever surface is nearest, kept a leg's height off it (tall on a floor, pressed in
 * close on a wall or ceiling, crouched under a low one), and turns to lie along it; a wall in its way is climbed, and
 * an edge is rounded. Nothing is keyframed: each client works its legs out live from where the body goes
 * (client.creature.HarvestmanRig, the same rules as the approved preview).
 *
 * <p>For now it keeps to a few plans of its own: wander, perch high on a wall, cross a ceiling, hide in the darkest
 * corner it can see, and creep toward a player to watch them. It can't be hurt yet and doesn't attack.
 *
 * <p>The entity's position is the bottom of a small box round its body; the body's centre is {@link #MID} above it.
 */
public class HarvestmanEntity extends Entity {
    /** How it lies: model frame (z forward, y away from the surface it's on) to world. */
    private static final EntityDataAccessor<org.joml.Quaternionfc> TURN = SynchedEntityData.defineId(HarvestmanEntity.class, EntityDataSerializers.QUATERNION);
    /** How far its body is held off the surface it's on, blocks (the legs reach for it). */
    private static final EntityDataAccessor<Float> LIFT = SynchedEntityData.defineId(HarvestmanEntity.class, EntityDataSerializers.FLOAT);

    /** The body's centre above the entity's position (the box is 0.8 tall). */
    public static final double MID = 0.4;
    /** Body height off a floor; off a wall or ceiling; and the least it crouches to. */
    public static final double STAND = 2.5, CLING = 2.05, CROUCH = 0.9;
    /** How far it feels for surfaces round it. */
    private static final double FEEL = 4.5;
    private static final double TURN_RATE = 1.9, ACCEL = 1.8, BRAKE = 2.6;

    public static final int IDLE = 0, WANDER = 1, PERCH = 2, CEILING = 3, HIDE = 4, STALK = 5;
    public static final String[] PLANS = {"idle", "wander", "perch", "ceiling", "hide", "stalk"};
    private static final double[] SPEED = {0, 1.6, 1.9, 1.8, 2.1, 0.9};

    /** Client only: the turn last tick and now, eased toward the synced one; and the legs (HarvestmanRig). */
    public final Quaternionf turn = new Quaternionf(), turnO = new Quaternionf();
    public Object rig;

    // server: the surface it's on and where it's going
    private Vec3 up = new Vec3(0, 1, 0), fwd = new Vec3(0, 0, 1), goal;
    private double speed, fall;
    private int plan = IDLE, planTicks, wait = 20, forced = -1;
    private Vec3 lastPos = Vec3.ZERO;
    private int stuck;

    /** Every direction it feels along: the 26 neighbours of a cube. */
    private static final Vec3[] DIRS;

    static {
        java.util.List<Vec3> dirs = new java.util.ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x != 0 || y != 0 || z != 0) {
                        dirs.add(new Vec3(x, y, z).normalize());
                    }
                }
            }
        }
        DIRS = dirs.toArray(new Vec3[0]);
    }

    public HarvestmanEntity(EntityType<? extends HarvestmanEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public Vec3 centre() {
        return position().add(0, MID, 0);
    }

    public float lift() {
        return entityData.get(LIFT);
    }

    public org.joml.Quaternionfc syncedTurn() {
        return entityData.get(TURN);
    }

    public String planName() {
        return PLANS[plan];
    }

    /** Makes it take up a plan next (a /zc harvestman test). */
    public void force(int plan) {
        forced = plan;
        goal = null;
        wait = 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TURN, new Quaternionf());
        builder.define(LIFT, (float) STAND);
    }

    @Override
    protected InterpolationHandler createInterpolationHandler() {
        return LinearInterpolationHandler.create(this, 3);
    }

    @Override
    public void tick() {
        super.tick();
        turnO.set(turn);
        if (!(level() instanceof ServerLevel level)) {
            turn.slerp(entityData.get(TURN), 0.45f);
            return;
        }
        think(level);
        crawl(level);
    }

    // ------------------------------------------------------------------------------------------------ moving

    private BlockHitResult cast(Vec3 from, Vec3 to) {
        return level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
    }

    private static Vec3 normal(BlockHitResult hit) {
        Direction d = hit.getDirection();
        return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ());
    }

    private static double floorness(Vec3 up) {
        double x = Mth.clamp((up.y - 0.55) / 0.4, 0, 1);
        return x * x * (3 - 2 * x);
    }

    /**
     * One tick of crawling: feel round for surfaces, lean the body's up toward the nearest of them, hold it its
     * height off the one it's on, and slide along it toward the goal; a wall ahead is climbed, and with nothing
     * anywhere near it falls.
     */
    private void crawl(ServerLevel level) {
        double dt = 0.05;
        Vec3 c = centre();
        // feel round: the nearer a surface, the more it says which way is up
        Vec3 sum = Vec3.ZERO, nearN = null, nearP = null;
        double near = 1e9;
        for (Vec3 d : DIRS) {
            BlockHitResult hit = cast(c, c.add(d.scale(FEEL)));
            if (hit.getType() == HitResult.Type.MISS) {
                continue;
            }
            double dist = hit.getLocation().distanceTo(c), w = 1 - dist / FEEL;
            Vec3 n = normal(hit);
            sum = sum.add(n.scale(w * w));
            if (dist < near) {
                near = dist;
                nearN = n;
                nearP = hit.getLocation();
            }
        }
        if (nearN == null) {
            // nothing to hold on to: it drops, righting itself as it goes
            fall = Math.min(fall + 0.08, 3.5);
            up = slerp(up, new Vec3(0, 1, 0), 3 * dt);
            setPos(position().add(0, -fall, 0));
            face(dt, fwd);
            return;
        }
        fall = 0;
        up = slerp(up, sum.lengthSqr() > 1e-6 ? sum.normalize() : nearN, 4 * dt);
        // what it stands on, straight under it, and what's over it
        BlockHitResult below = cast(c, c.subtract(up.scale(6))), above = cast(c, c.add(up.scale(6)));
        double base = below.getType() == HitResult.Type.MISS ? near : below.getLocation().distanceTo(c);
        Vec3 away = below.getType() == HitResult.Type.MISS ? c.subtract(nearP).normalize() : up;
        double want = Mth.lerp(floorness(up), CLING, STAND);
        if (above.getType() != HitResult.Type.MISS) {
            double gap = base + above.getLocation().distanceTo(c);
            want = Math.min(want, Math.max(CROUCH, gap - 0.8));
        }
        entityData.set(LIFT, (float) want);
        // toward the goal, along the surface
        Vec3 dir = fwd;
        double dist = 0;
        if (goal != null) {
            Vec3 to = goal.subtract(c);
            Vec3 t = to.subtract(up.scale(to.dot(up)));
            dist = t.length();
            if (dist > 1e-3) {
                dir = t.scale(1 / dist);
            }
        }
        double top = goal == null || wait > 0 ? 0 : Math.min(SPEED[plan], Math.sqrt(2 * 1.5 * Math.max(0, dist - 0.4)));
        speed = speed < top ? Math.min(top, speed + ACCEL * dt) : Math.max(top, speed - BRAKE * dt);
        // a wall or anything else in the way: go up it (its face soon becomes the surface it's on)
        BlockHitResult ahead = cast(c, c.add(dir.scale(1.6)));
        if (ahead.getType() != HitResult.Type.MISS) {
            dir = dir.add(up.scale(1.4)).normalize();
        }
        Vec3 step = dir.scale(speed * dt).add(away.scale((want - base) * Math.min(1, 5 * dt)));
        // never into a block: stop short of what the step would go through
        if (step.lengthSqr() > 1e-8) {
            BlockHitResult hit = cast(c, c.add(step.normalize().scale(step.length() + 0.6)));
            if (hit.getType() != HitResult.Type.MISS) {
                double room = hit.getLocation().distanceTo(c) - 0.6;
                step = step.normalize().scale(Math.max(0, Math.min(step.length(), room)));
            }
        }
        Vec3 next = c.add(step);
        if (near < 0.55) {
            next = next.add(nearN.scale(0.55 - near));
        }
        setPos(next.subtract(0, MID, 0));
        if (speed > 0.05) {
            face(dt, dir);
        } else if (goal != null && plan == STALK) {
            Player p = level.getNearestPlayer(this, 32);
            if (p != null) {
                face(dt, p.getEyePosition().subtract(c));
            }
        } else {
            face(dt, fwd);
        }
        // stuck: it gives up on that goal
        if (goal != null && speed > 0.3 && position().distanceToSqr(lastPos) < 0.0004) {
            if (++stuck > 60) {
                goal = null;
                stuck = 0;
            }
        } else {
            stuck = 0;
        }
        lastPos = position();
    }

    /** Turns it toward dir within the surface, no faster than it can, and syncs how it lies. */
    private void face(double dt, Vec3 dir) {
        Vec3 want = dir.subtract(up.scale(dir.dot(up)));
        Vec3 f = fwd.subtract(up.scale(fwd.dot(up)));
        if (f.lengthSqr() < 1e-6) {
            f = Math.abs(up.y) < 0.9 ? new Vec3(0, 1, 0).subtract(up.scale(up.y)) : new Vec3(0, 0, 1).subtract(up.scale(up.z));
        }
        f = f.normalize();
        if (want.lengthSqr() > 1e-6) {
            want = want.normalize();
            double a = Math.acos(Mth.clamp(f.dot(want), -1, 1)), max = TURN_RATE * dt;
            f = a <= max ? want : slerp(f, want, max / a);
        }
        fwd = f.subtract(up.scale(f.dot(up))).normalize();
        Vec3 x = up.cross(fwd).normalize();
        Vector3f cx = new Vector3f((float) x.x, (float) x.y, (float) x.z), cy = new Vector3f((float) up.x, (float) up.y, (float) up.z);
        Vector3f cz = new Vector3f().set(cx).cross(cy);
        Quaternionf q = new Quaternionf().setFromNormalized(new Matrix3f(cx, cy, cz));
        entityData.set(TURN, q);
        setYRot((float) Math.toDegrees(Math.atan2(-fwd.x, fwd.z)));
    }

    private static Vec3 slerp(Vec3 a, Vec3 b, double t) {
        t = Mth.clamp(t, 0, 1);
        Vec3 v = a.scale(1 - t).add(b.scale(t));
        return v.lengthSqr() < 1e-8 ? b : v.normalize();
    }

    // ------------------------------------------------------------------------------------------------ plans

    /** Picks what to do next once it's where it was going (or gave up), and waits a while there first. */
    private void think(ServerLevel level) {
        planTicks++;
        if (goal != null) {
            Vec3 to = goal.subtract(centre());
            double along = to.subtract(up.scale(to.dot(up))).length();
            if (along < 0.8 || planTicks > 20 * 25) {
                // there: it stays a while (longest when hidden)
                wait = 20 * switch (plan) {
                    case HIDE -> 8 + random.nextInt(8);
                    case STALK -> 3 + random.nextInt(4);
                    default -> 2 + random.nextInt(5);
                };
                goal = null;
            }
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        Player player = level.getNearestPlayer(this, 24);
        int next = forced >= 0 ? forced : pick(player != null);
        forced = -1;
        Vec3 g = next == STALK && player != null ? stalkSpot(player) : spot(next);
        if (g == null) {
            wait = 20;
            return;
        }
        plan = next;
        goal = g;
        planTicks = 0;
    }

    private int pick(boolean playerNear) {
        if (playerNear && random.nextFloat() < 0.3f) {
            return STALK;
        }
        float r = random.nextFloat();
        return r < 0.35f ? WANDER : r < 0.55f ? PERCH : r < 0.7f ? CEILING : HIDE;
    }

    /** A place for a plan, from surfaces it can see round it. */
    private Vec3 spot(int plan) {
        Vec3 c = centre(), best = null;
        double bestScore = -1e9;
        for (int k = 0; k < 40; k++) {
            Vec3 d = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
            if (d.lengthSqr() < 1e-6) {
                continue;
            }
            d = d.normalize();
            BlockHitResult hit = cast(c, c.add(d.scale(16)));
            if (hit.getType() == HitResult.Type.MISS) {
                continue;
            }
            Vec3 n = normal(hit), p = hit.getLocation();
            double dist = p.distanceTo(c);
            if (dist < 3) {
                continue;
            }
            BlockPos air = hit.getBlockPos().relative(hit.getDirection());
            double score = switch (plan) {
                case WANDER -> n.y > 0.7 ? 1 + random.nextDouble() - Math.abs(dist - 9) * 0.05 : -1e9;
                case PERCH -> Math.abs(n.y) < 0.3 && p.y > c.y + 1 ? (p.y - c.y) * 0.3 + random.nextDouble() : -1e9;
                case CEILING -> n.y < -0.7 ? 1 + random.nextDouble() : -1e9;
                case HIDE -> 15 - level().getMaxLocalRawBrightness(air) + 0.8 * corner(air) - dist * 0.04 + random.nextDouble() * 0.5;
                default -> -1e9;
            };
            if (score > bestScore) {
                bestScore = score;
                best = p.add(n.scale(1.0));
            }
        }
        return bestScore > -1e8 ? best : null;
    }

    /** How shut in an air block is: its solid neighbours. */
    private int corner(BlockPos air) {
        int n = 0;
        for (Direction d : Direction.values()) {
            if (!level().getBlockState(air.relative(d)).getCollisionShape(level(), air.relative(d)).isEmpty()) {
                n++;
            }
        }
        return n;
    }

    /** A floor a few blocks short of a player, on its side of them. */
    private Vec3 stalkSpot(Player player) {
        Vec3 from = player.position(), d = centre().subtract(from);
        d = new Vec3(d.x, 0, d.z);
        d = d.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : d.normalize();
        Vec3 p = from.add(d.scale(4.5)).add(0, 2, 0);
        BlockHitResult hit = cast(p, p.add(0, -8, 0));
        return hit.getType() == HitResult.Type.MISS ? null : hit.getLocation().add(0, 1, 0);
    }

    // ------------------------------------------------------------------------------------------------ the rest

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }
}
