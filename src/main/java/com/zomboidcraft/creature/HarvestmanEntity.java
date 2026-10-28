package com.zomboidcraft.creature;

import com.zomboidcraft.ZomboidCraft;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
 * The Harvestman. A long-legged armoured thing that walks on any block face: floors, walls, ceilings. Its body slides
 * over whatever surface is nearest, kept a leg's height off it, and turns to lie along it; a wall in its way is climbed,
 * an edge is rounded, and it can let go of a ceiling to drop. Its legs are worked out here on the server each tick
 * (HarvestmanLegs) and sent to every client, which bends them (HarvestmanBody's knee rule) and draws them.
 *
 * <p>It is very hard to kill: 1,200 health behind armour plates that let a tenth of a bullet through; the red core
 * under its belly takes double; each leg takes 250 before it's shot off at the knee. It thinks for itself
 * (HarvestmanMind): what it does next comes from how hungry, restless, curious and wary it is, what it hears and what it
 * sees, and the one player it hunts (HarvestmanDirector). It grabs its prey from behind or from above, drags it
 * somewhere dark and feeds; the one held can struggle free or be shot free.
 *
 * <p>The entity's position is the bottom of a small box round its body; the body's centre is {@link #MID} above it.
 */
public class HarvestmanEntity extends Mob {
    public static final ResourceKey<DamageType> DRAINED = ResourceKey.create(Registries.DAMAGE_TYPE, ZomboidCraft.id("harvestman"));

    private static final EntityDataAccessor<org.joml.Quaternionfc> TURN = SynchedEntityData.defineId(HarvestmanEntity.class, EntityDataSerializers.QUATERNION);
    private static final EntityDataAccessor<Float> LIFT = SynchedEntityData.defineId(HarvestmanEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<org.joml.Vector3fc> FOOT0 = SynchedEntityData.defineId(HarvestmanEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<org.joml.Vector3fc> FOOT1 = SynchedEntityData.defineId(HarvestmanEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<org.joml.Vector3fc> FOOT2 = SynchedEntityData.defineId(HarvestmanEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<org.joml.Vector3fc> FOOT3 = SynchedEntityData.defineId(HarvestmanEntity.class, EntityDataSerializers.VECTOR3);
    /** Legs shot off at the knee (bits by leg). */
    private static final EntityDataAccessor<Integer> LOST = SynchedEntityData.defineId(HarvestmanEntity.class, EntityDataSerializers.INT);
    /** What its body is doing: HUNT, HOLD (scythes closed on something), FEED (and glowing), or DEAD. */
    private static final EntityDataAccessor<Integer> POSE = SynchedEntityData.defineId(HarvestmanEntity.class, EntityDataSerializers.INT);
    /** How far it's reared up, 0..1. */
    private static final EntityDataAccessor<Float> REAR = SynchedEntityData.defineId(HarvestmanEntity.class, EntityDataSerializers.FLOAT);
    /** Counts up each time it's hit (the client jolts it). */
    private static final EntityDataAccessor<Integer> FLINCH = SynchedEntityData.defineId(HarvestmanEntity.class, EntityDataSerializers.INT);
    /** How far it has darkened its red to hide, 0 (red showing) .. 1 (all black but its eyes). */
    private static final EntityDataAccessor<Float> CAMO = SynchedEntityData.defineId(HarvestmanEntity.class, EntityDataSerializers.FLOAT);
    private static final List<EntityDataAccessor<org.joml.Vector3fc>> FEET = List.of(FOOT0, FOOT1, FOOT2, FOOT3);

    public static final int HUNT = 0, HOLD = 1, FEED = 2, DEAD = 3;

    /** The body's centre above the entity's position (the box is 0.8 tall). */
    public static final double MID = 0.4;
    /** Body height off a floor; off a wall or ceiling; the least it crouches to; feeding; lying dead. */
    public static final double STAND = 2.5, CLING = 2.05, CROUCH = 0.9, FEEDING = 2.41, LYING = 0.45;
    public static final double MAX_HEALTH = 1200, LEG_HEALTH = 250;
    /** What a shot does to it: through its plates, into the red core (a leg takes it all). */
    public static final double PLATES = 0.1, CORE = 2.0;
    /** How long a feed takes to kill; jumps it takes to struggle free; damage that makes it drop what it holds. */
    public static final int FEED_TICKS = 200, STRUGGLE = 14;
    public static final double SHAKE_OFF = 120;
    private static final double FEEL = 4.5;
    private static final double TURN_RATE = 1.9, ACCEL = 1.8, BRAKE = 2.6;
    /** Where what it holds has its head: under the point of its body, gripped by the beard. */
    private static final Vec3 GRIP = new Vec3(0, -0.64, 0.74);

    /** Client only: the turn last tick and now, eased toward the synced one; and the legs it draws (HarvestmanRig). */
    public final Quaternionf turn = new Quaternionf(), turnO = new Quaternionf();
    public Object rig;
    /** Client only: the feet as they came in, the last few ticks (the body's drawn position runs a little behind). */
    public final Vec3[][] feetSeen = new Vec3[4][4];
    public int feetSeenAt;

    public final HarvestmanMind mind = new HarvestmanMind(this);
    final HarvestmanLegs legs = new HarvestmanLegs();

    // server: the surface it's on and where it's going (the mind sets the goal)
    Vec3 up = new Vec3(0, 1, 0), fwd = new Vec3(0, 0, 1);
    Vec3 goal, look;
    double goalSpeed, speed, fall, rear, rearWant;
    /** It wants to be on the floor (to strike or feed): it lets go of a ceiling, and comes down walls. */
    boolean wantFloor;
    private boolean dropping;
    private Vec3 lastPos = Vec3.ZERO;
    private int stuck;
    int stuckTicks;

    // its legs' health, and the shots that struck it this tick (zone and leg, and where)
    final double[] legHealth = {LEG_HEALTH, LEG_HEALTH, LEG_HEALTH, LEG_HEALTH};
    private final List<int[]> struck = new ArrayList<>();
    private final List<Vec3> struckAt = new ArrayList<>();

    // what it holds
    Entity held;
    int holdTicks, feedTicks, struggle;
    double heldDamage;
    private boolean lastJump;

    public HarvestmanEntity(EntityType<? extends HarvestmanEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 64);
    }

    // ------------------------------------------------------------------------------------------------ what's synced

    public Vec3 centre() {
        return position().add(0, MID, 0);
    }

    public float lift() {
        return entityData.get(LIFT);
    }

    public org.joml.Quaternionfc syncedTurn() {
        return entityData.get(TURN);
    }

    public Vec3 foot(int i) {
        org.joml.Vector3fc v = entityData.get(FEET.get(i));
        return new Vec3(v.x(), v.y(), v.z());
    }

    public int lost() {
        return entityData.get(LOST);
    }

    public int pose() {
        return entityData.get(POSE);
    }

    public float rear() {
        return entityData.get(REAR);
    }

    public float camo() {
        return entityData.get(CAMO);
    }

    public int flinches() {
        return entityData.get(FLINCH);
    }

    public String planName() {
        return mind.planName();
    }

    public Entity held() {
        return held;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TURN, new Quaternionf());
        builder.define(LIFT, (float) STAND);
        for (EntityDataAccessor<org.joml.Vector3fc> f : FEET) {
            builder.define(f, new Vector3f());
        }
        builder.define(LOST, 0);
        builder.define(POSE, HUNT);
        builder.define(REAR, 0f);
        builder.define(FLINCH, 0);
        builder.define(CAMO, 1f);
    }

    // ------------------------------------------------------------------------------------------------ each tick

    @Override
    public void tick() {
        super.tick();
        turnO.set(turn);
        if (!(level() instanceof ServerLevel level)) {
            turn.slerp(entityData.get(TURN), 0.45f);
            // what came in this tick, kept a few ticks so the feet can be drawn as far behind as the body is
            feetSeenAt = (feetSeenAt + 1) & 3;
            for (int i = 0; i < 4; i++) {
                feetSeen[i][feetSeenAt] = foot(i);
            }
            return;
        }
        if (isDeadOrDying()) {
            return;     // tickDeath lays it down
        }
        mind.tick(level);
        crawl(level);
        holding(level);
        rear = Mth.approach((float) rear, (float) rearWant, 0.12f);
        entityData.set(REAR, (float) rear);
        // it governs its colour: black to roam and hunt unseen, the red let up to feed or to frighten
        entityData.set(CAMO, Mth.approach(camo(), (float) mind.camo(), 0.06f));
        struck.clear();
        struckAt.clear();
        stepLegs(level, false);
    }

    @Override
    public void aiStep() {
        // it moves itself (crawl): nothing pushes it, and nothing the vanilla body does moves it
        setDeltaMovement(Vec3.ZERO);
        super.aiStep();
        setDeltaMovement(Vec3.ZERO);
    }

    private void stepLegs(ServerLevel level, boolean dead) {
        double[] before = legs.lift.clone();
        legs.tick(this, centre(), up, fwd, entityData.get(LIFT), rear, dead, lost(), random);
        for (int i = 0; i < 4; i++) {
            Vec3 f = legs.feet[i];
            entityData.set(FEET.get(i), new Vector3f((float) f.x, (float) f.y, (float) f.z));
            // a point coming down taps on what it lands on, louder on hard floors
            if (!dead && (lost() >> i & 1) == 0 && legs.landed(i, before[i]) && random.nextFloat() < 0.8f) {
                BlockPos on = BlockPos.containing(f.subtract(legs.footN[i].scale(0.1)));
                float hard = level.getBlockState(on).getDestroySpeed(level, on) > 1.2f ? 1f : 0.45f;
                level.playSound(null, f.x, f.y, f.z, HarvestmanSounds.TAP, SoundSource.HOSTILE,
                        (mind.sneaking() ? 0.25f : 0.6f) * hard, 0.85f + random.nextFloat() * 0.3f);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ moving

    private BlockHitResult cast(Vec3 from, Vec3 to) {
        return level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
    }

    private static Vec3 normal(BlockHitResult hit) {
        Direction d = hit.getDirection();
        return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ());
    }

    /** Is there a clear line from its body to this point? */
    public boolean canSee(Vec3 p) {
        return cast(centre(), p).getType() == HitResult.Type.MISS;
    }

    /**
     * One tick of crawling: feel round for surfaces, lean the body's up toward the nearest of them, hold it its
     * height off the one it's on, and slide along it toward the goal; a wall ahead is climbed, and with nothing
     * anywhere near (or letting go of a ceiling) it falls.
     */
    private void crawl(ServerLevel level) {
        double dt = 0.05;
        Vec3 c = centre();
        Vec3 sum = Vec3.ZERO, nearN = null, nearP = null;
        double near = 1e9;
        for (Vec3 d : HarvestmanMind.DIRS) {
            BlockHitResult hit = cast(c, c.add(d.scale(FEEL)));
            if (hit.getType() == HitResult.Type.MISS) {
                continue;
            }
            double dist = hit.getLocation().distanceTo(c), w = 1 - dist / FEEL;
            Vec3 n = normal(hit);
            // wanting the floor, it pays the floor more mind than the walls and ceiling
            if (wantFloor) {
                w *= n.y > 0.5 ? 2.5 : n.y < -0.5 ? 0.15 : 0.6;
            }
            sum = sum.add(n.scale(w * w));
            if (dist < near) {
                near = dist;
                nearN = n;
                nearP = hit.getLocation();
            }
        }
        // on a ceiling and wanting the floor: it lets go and drops
        if (wantFloor && up.y < -0.5 && !dropping && cast(c, c.add(0, -12, 0)).getType() != HitResult.Type.MISS) {
            dropping = true;
            level.playSound(null, c.x, c.y, c.z, HarvestmanSounds.SCRAPE, SoundSource.HOSTILE, 0.8f, 1.2f);
        }
        if (dropping || nearN == null) {
            fall = Math.min(fall + 0.08, 3.5);
            up = slerp(up, new Vec3(0, 1, 0), 6 * dt);
            Vec3 step = new Vec3(0, -fall, 0);
            BlockHitResult floor = cast(c, c.add(0, -fall - STAND, 0));
            if (floor.getType() != HitResult.Type.MISS) {
                double room = c.y - floor.getLocation().y - STAND;
                if (room <= fall) {
                    // landed: on its feet, braced
                    step = new Vec3(0, -Math.max(0, room), 0);
                    dropping = false;
                    fall = 0;
                    up = new Vec3(0, 1, 0);
                    level.playSound(null, c.x, c.y, c.z, HarvestmanSounds.TAP, SoundSource.HOSTILE, 1f, 0.6f);
                }
            }
            setPos(position().add(step));
            face(dt, look != null ? look.subtract(c) : fwd);
            return;
        }
        fall = 0;
        up = slerp(up, sum.lengthSqr() > 1e-6 ? sum.normalize() : nearN, 4 * dt);
        BlockHitResult below = cast(c, c.subtract(up.scale(6))), above = cast(c, c.add(up.scale(6)));
        double base = below.getType() == HitResult.Type.MISS ? near : below.getLocation().distanceTo(c);
        Vec3 away = below.getType() == HitResult.Type.MISS ? c.subtract(nearP).normalize() : up;
        double want = pose() == FEED || pose() == HOLD ? FEEDING : Mth.lerp(HarvestmanLegs.floorness(up), CLING, STAND);
        want -= 0.6 * mind.crouch();
        if (above.getType() != HitResult.Type.MISS) {
            double gap = base + above.getLocation().distanceTo(c);
            want = Math.min(want, Math.max(CROUCH, gap - 0.8));
        }
        // a leg or two gone, it sags
        want = Math.max(CROUCH, want - 0.25 * Integer.bitCount(lost()));
        entityData.set(LIFT, (float) want);
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
        double top = goal == null ? 0 : Math.min(goalSpeed * mind.speedScale(), Math.sqrt(2 * 3 * Math.max(0, dist - 0.3)));
        speed = speed < top ? Math.min(top, speed + ACCEL * mind.accelScale() * dt) : Math.max(top, speed - BRAKE * dt);
        BlockHitResult ahead = cast(c, c.add(dir.scale(1.6)));
        if (ahead.getType() != HitResult.Type.MISS && !(wantFloor && normal(ahead).y > 0.5)) {
            dir = dir.add(up.scale(1.4)).normalize();
        }
        Vec3 step = dir.scale(speed * dt).add(away.scale((want - base) * Math.min(1, 5 * dt)));
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
        if (look != null && (speed < 0.3 || mind.watching())) {
            face(dt, look.subtract(c));
        } else if (speed > 0.05) {
            face(dt, dir);
        } else {
            face(dt, fwd);
        }
        if (goal != null && speed > 0.3 && position().distanceToSqr(lastPos) < 0.0004) {
            stuckTicks++;
            if (++stuck > 60) {
                goal = null;
                stuck = 0;
            }
        } else {
            stuck = 0;
            stuckTicks = 0;
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
            double a = Math.acos(Mth.clamp(f.dot(want), -1, 1)), max = TURN_RATE * mind.speedScale() * dt;
            f = a <= max ? want : slerp(f, want, max / a);
        }
        fwd = f.subtract(up.scale(f.dot(up))).normalize();
        Vec3 x = up.cross(fwd).normalize();
        Vector3f cx = new Vector3f((float) x.x, (float) x.y, (float) x.z), cy = new Vector3f((float) up.x, (float) up.y, (float) up.z);
        Vector3f cz = new Vector3f().set(cx).cross(cy);
        entityData.set(TURN, new Quaternionf().setFromNormalized(new Matrix3f(cx, cy, cz)));
        setYRot((float) Math.toDegrees(Math.atan2(-fwd.x, fwd.z)));
        setYHeadRot(getYRot());
        yBodyRot = getYRot();
    }

    static Vec3 slerp(Vec3 a, Vec3 b, double t) {
        t = Mth.clamp(t, 0, 1);
        Vec3 v = a.scale(1 - t).add(b.scale(t));
        return v.lengthSqr() < 1e-8 ? b : v.normalize();
    }

    /** Its body frame now, from the synced turn (the same on the server and every client). */
    public HarvestmanBody.Frame frame() {
        org.joml.Quaternionfc q = entityData.get(TURN);
        Vector3f x = q.transform(new Vector3f(1, 0, 0)), y = q.transform(new Vector3f(0, 1, 0)), z = q.transform(new Vector3f(0, 0, 1));
        return new HarvestmanBody.Frame(centre(), new Vec3(x.x, x.y, x.z), new Vec3(y.x, y.y, y.z), new Vec3(z.x, z.y, z.z));
    }

    public Vec3 up() {
        return up;
    }

    public Vec3 forward() {
        return fwd;
    }

    // ------------------------------------------------------------------------------------------------ being shot

    /** A shot struck it (Dismember.shot, before the hit lands): which part, and where. */
    public void struck(HarvestmanBody.Strike s) {
        struck.add(new int[] {s.zone(), s.leg()});
        struckAt.add(s.at());
    }

    /** Where a shot strikes it now: the hull, the core under its belly, or a leg as it stands. */
    public HarvestmanBody.Strike strike(Vec3 eye, Vec3 dir, double range) {
        if (!isAlive()) {
            return null;
        }
        HarvestmanBody.Frame body = frame();
        HarvestmanBody.Bent[] bent = new HarvestmanBody.Bent[4];
        for (int i = 0; i < 4; i++) {
            Vec3 fn = legs.footN[i] == null ? up : legs.footN[i];
            bent[i] = (lost() >> i & 1) != 0 ? dangle(body, up, i) : HarvestmanBody.bend(body, up, i, foot(i), fn);
        }
        return HarvestmanBody.strike(body, bent, lost(), eye, dir, range);
    }

    /** A leg shot off at the knee: the thigh hangs out and down from its hip. */
    public static HarvestmanBody.Bent dangle(HarvestmanBody.Frame body, Vec3 up, int i) {
        Vec3 H = body.hip(i), out = H.subtract(body.o());
        out = out.subtract(up.scale(out.dot(up))).normalize();
        double a = HarvestmanBody.leg(i).a();
        Vec3 K = H.add(out.scale(0.8 * a)).subtract(up.scale(0.6 * a));
        return new HarvestmanBody.Bent(H, K, K, out.cross(up).normalize());
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isDeadOrDying() || isInvulnerableTo(level, source)) {
            return false;
        }
        double toHealth = 0;
        if (!struck.isEmpty()) {
            // a gunshot's pellets, summed: what each was before the gun took some off a low hit, then what each did
            double factors = 0;
            for (Vec3 at : struckAt) {
                factors += at.y < getY() + getBbHeight() * 0.35 ? 0.7 : 1;
            }
            double each = amount / Math.max(1e-3, factors);
            for (int k = 0; k < struck.size(); k++) {
                int zone = struck.get(k)[0], leg = struck.get(k)[1];
                Vec3 at = struckAt.get(k);
                if (zone == HarvestmanBody.LEG && leg >= 0) {
                    hurtLeg(level, leg, each, at);
                } else if (zone == HarvestmanBody.CORE) {
                    toHealth += each * CORE;
                    level.sendParticles(new DustParticleOptions(0xB01010, 1.2f), at.x, at.y, at.z, 6, 0.05, 0.05, 0.05, 0.1);
                    playSound(level, at, HarvestmanSounds.HURT, 0.6f, 1.3f);
                } else {
                    toHealth += each * PLATES;
                    level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 5, 0.05, 0.05, 0.05, 0.25);
                    playSound(level, at, HarvestmanSounds.CLANG, 0.9f, 0.9f + random.nextFloat() * 0.3f);
                }
            }
            struck.clear();
            struckAt.clear();
        } else {
            // anything else meets the plates; a blast gets through better
            boolean blast = source.is(DamageTypeTags.IS_EXPLOSION);
            toHealth = amount * (blast ? 0.4 : PLATES);
            if (!blast) {
                playSound(level, centre(), HarvestmanSounds.CLANG, 0.8f, 0.8f);
            }
        }
        toHealth *= mind.toughnessScale();
        entityData.set(FLINCH, flinches() + 1);
        heldDamage += toHealth;
        mind.hurt(level, source.getEntity(), toHealth);
        if (toHealth <= 0) {
            return true;
        }
        return super.hurtServer(level, source, (float) toHealth);
    }

    private void hurtLeg(ServerLevel level, int leg, double dmg, Vec3 at) {
        if ((lost() >> leg & 1) != 0) {
            return;
        }
        legHealth[leg] -= dmg;
        heldDamage += dmg * 0.5;
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 4, 0.04, 0.04, 0.04, 0.2);
        playSound(level, at, HarvestmanSounds.CLANG, 0.6f, 1.2f + random.nextFloat() * 0.2f);
        if (legHealth[leg] <= 0) {
            loseLeg(level, leg);
        }
    }

    /** A leg shot off at the knee. */
    void loseLeg(ServerLevel level, int leg) {
        entityData.set(LOST, lost() | 1 << leg);
        legHealth[leg] = 0;
        Vec3 k = centre();
        playSound(level, k, HarvestmanSounds.LEG_OFF, 1.5f, 0.9f + random.nextFloat() * 0.2f);
        level.sendParticles(new DustParticleOptions(0x900808, 1.6f), k.x, k.y, k.z, 20, 0.6, 0.3, 0.6, 0.1);
        mind.lostLeg(level);
    }

    /** A leg already gone when it comes out of the dark (HarvestmanDirector). */
    void loseLegQuietly(int leg) {
        entityData.set(LOST, lost() | 1 << leg);
        legHealth[leg] = 0;
    }

    /** A leg grows back (resting in its lair). */
    void regrow(int leg) {
        entityData.set(LOST, lost() & ~(1 << leg));
        legHealth[leg] = LEG_HEALTH;
    }

    private void playSound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    @Override
    public void knockback(double strength, double x, double z) {
        // nothing moves it
    }

    @Override
    protected void tickDeath() {
        // it doesn't vanish: it lies where it fell, legs splayed, for a long while
        deathTime = Math.min(deathTime + 1, 100_000);
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        entityData.set(CAMO, Mth.approach(camo(), 0.85f, 0.01f));
        if (deathTime == 1) {
            release();
            entityData.set(POSE, DEAD);
            playSound(level, centre(), HarvestmanSounds.DEATH, 2f, 1f);
        }
        // sinks to the floor as its legs give way, and drops off a wall or ceiling
        BlockHitResult floor = cast(centre(), centre().add(0, -40, 0));
        double ground = floor.getType() == HitResult.Type.MISS ? centre().y - LYING : floor.getLocation().y;
        up = slerp(up, new Vec3(0, 1, 0), 0.1);
        double y = centre().y + Math.max(-0.5, (ground + LYING - centre().y) * 0.15);
        setPos(getX(), y - MID, getZ());
        entityData.set(LIFT, (float) Math.max(LYING, centre().y - ground));
        face(0.05, fwd);
        stepLegs(level, true);
        if (deathTime > 20 * 60 * 20) {
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            HarvestmanDirector.died(level, this, source.getEntity());
        }
    }

    @Override
    public boolean isPickable() {
        return isAlive();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96 * 96;
    }

    // ------------------------------------------------------------------------------------------------ holding prey

    /** Where what it holds hangs: its head under the point of its body. */
    public Vec3 gripPoint() {
        HarvestmanBody.Frame f = frame();
        return f.at(GRIP.x, GRIP.y, GRIP.z);
    }

    /** It closes its scythes on something within reach of its grip: true if it got it. */
    boolean grab(ServerLevel level, LivingEntity prey) {
        if (held != null || prey.isPassenger() || !prey.isAlive()) {
            return false;
        }
        held = prey;
        holdTicks = 0;
        feedTicks = 0;
        struggle = 0;
        heldDamage = 0;
        entityData.set(POSE, HOLD);
        if (prey instanceof Player p) {
            p.startRiding(this);
            p.hurtServer(level, level.damageSources().source(DRAINED, this), 4f);
        }
        Vec3 g = gripPoint();
        playSound(level, g, HarvestmanSounds.SCREECH, 2f, 1f);
        level.sendParticles(new DustParticleOptions(0x900808, 1.2f), g.x, g.y - 0.5, g.z, 12, 0.2, 0.3, 0.2, 0.05);
        return true;
    }

    /** It lets go of what it holds. */
    void release() {
        Entity was = held;
        held = null;
        feedTicks = 0;
        if (was != null) {
            if (was.getVehicle() == this) {
                was.stopRiding();
            }
            if (was instanceof Mob m && !(was instanceof HarvestmanEntity)) {
                m.setNoAi(false);
            }
        }
        if (isAlive()) {
            entityData.set(POSE, HUNT);
        }
    }

    public boolean feeding() {
        return pose() == FEED;
    }

    /** It starts feeding on what it holds. */
    void startFeeding() {
        if (held != null) {
            entityData.set(POSE, FEED);
            feedTicks = 0;
        }
    }

    /** What it holds is kept at its grip; a held player struggles (jump) and is drained once it feeds. */
    private void holding(ServerLevel level) {
        if (held == null) {
            return;
        }
        if (!held.isAlive() || held.isRemoved() || held.level() != level
                || held instanceof Player p && (p.isSpectator() || p.isCreative() && !mind.forcedHunt())) {
            release();
            return;
        }
        holdTicks++;
        if (!(held instanceof Player)) {
            // a zombie is just carried
            Vec3 g = gripPoint();
            held.setPos(g.x, g.y - 1.6, g.z);
            if (held instanceof Mob m) {
                m.setNoAi(true);
            }
        } else if (held.getVehicle() != this) {
            held.startRiding(this);
        }
        if (held instanceof ServerPlayer sp) {
            boolean jump = sp.getLastClientInput().jump();
            if (jump && !lastJump) {
                struggle++;
            }
            lastJump = jump;
        }
        // struggled free, or shot hard enough to drop it
        if (struggle >= STRUGGLE + mind.stage() * 2 || heldDamage >= SHAKE_OFF) {
            Vec3 c = centre();
            playSound(level, c, HarvestmanSounds.HURT, 1.4f, 0.8f);
            Entity was = held;
            release();
            if (was instanceof LivingEntity le) {
                Vec3 push = was.position().subtract(c);
                push = new Vec3(push.x, 0, push.z);
                push = push.lengthSqr() < 1e-4 ? fwd.scale(0.6) : push.normalize().scale(0.6);
                le.setDeltaMovement(push.x, 0.3, push.z);
                le.hurtMarked = true;
            }
            mind.lostPrey(level);
            return;
        }
        if (feeding()) {
            feedTicks++;
            if (feedTicks % 20 == 0) {
                Vec3 g = gripPoint();
                playSound(level, g, HarvestmanSounds.FEED, 1f, 0.9f + random.nextFloat() * 0.2f);
                level.sendParticles(new DustParticleOptions(0x700606, 1f), g.x, g.y - 0.2, g.z, 4, 0.1, 0.1, 0.1, 0.02);
            }
            if (held instanceof LivingEntity le) {
                // drained to death over the feed, whatever its armour
                if (feedTicks % 10 == 0) {
                    le.hurtServer(level, level.damageSources().source(DRAINED, this), le.getMaxHealth() / (FEED_TICKS / 10f));
                }
                if (!le.isAlive() || feedTicks >= FEED_TICKS + 40) {
                    if (le.isAlive()) {
                        le.hurtServer(level, level.damageSources().source(DRAINED, this), 1000f);
                    }
                    Entity was = held;
                    release();
                    mind.fed(level, was);
                }
            }
        }
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return passenger == held && getPassengers().isEmpty();
    }

    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        // hung by the head under the point of its body, back to it; eyes where its head is gripped
        Vec3 g = gripPoint();
        move.accept(passenger, g.x, g.y - passenger.getEyeHeight() + 0.05, g.z);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (passenger == held && level() instanceof ServerLevel) {
            held = null;
            if (isAlive()) {
                entityData.set(POSE, HUNT);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ saving

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("hmLost", lost());
        for (int i = 0; i < 4; i++) {
            output.putDouble("hmLeg" + i, legHealth[i]);
        }
        mind.save(output);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(LOST, input.getIntOr("hmLost", 0));
        for (int i = 0; i < 4; i++) {
            legHealth[i] = input.getDoubleOr("hmLeg" + i, LEG_HEALTH);
        }
        mind.load(input);
    }
}
