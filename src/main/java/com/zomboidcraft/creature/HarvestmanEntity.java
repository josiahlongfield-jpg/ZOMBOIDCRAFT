package com.zomboidcraft.creature;

import com.zomboidcraft.ZomboidCraft;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
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
    /** What it's looking at (its head snaps toward a noise), and whether it's looking at anything. */
    private static final EntityDataAccessor<org.joml.Vector3fc> LOOK_AT = SynchedEntityData.defineId(HarvestmanEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Boolean> LOOKING = SynchedEntityData.defineId(HarvestmanEntity.class, EntityDataSerializers.BOOLEAN);
    /** The player it's watching from where it waits (that player's game grows uneasy), or -1. */
    private static final EntityDataAccessor<Integer> WATCH = SynchedEntityData.defineId(HarvestmanEntity.class, EntityDataSerializers.INT);
    /** Counts up (times 4, plus the leg) each time a foot slips; the client lurches it toward that leg. */
    private static final EntityDataAccessor<Integer> STUMBLE = SynchedEntityData.defineId(HarvestmanEntity.class, EntityDataSerializers.INT);
    /** Counts up each time it's shot in the eyes (the client throws it back). */
    private static final EntityDataAccessor<Integer> RECOIL = SynchedEntityData.defineId(HarvestmanEntity.class, EntityDataSerializers.INT);
    /** Counts up each time it rages at someone (the client plays its rage). */
    private static final EntityDataAccessor<Integer> RAGE = SynchedEntityData.defineId(HarvestmanEntity.class, EntityDataSerializers.INT);
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
    private static final double TURN_RATE = 1.9, ACCEL = 1.8, BRAKE = 2.6;
    /** Where what it holds has its head: under the point of its body, gripped by the beard. */
    private static final Vec3 GRIP = new Vec3(0, -0.64, 0.74);

    /** Client only: the turn last tick and now, eased toward the synced one; and the legs it draws (HarvestmanRig). */
    public final Quaternionf turn = new Quaternionf(), turnO = new Quaternionf();
    public Object rig;
    /** Client only: its Spider 7 body and the animations it's playing (SpiderDraw). */
    public Object body;
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
    private boolean dropping, attached;
    /** The block face it holds (straight off it), and an outer edge it's swinging round (pivotT 0..1, -1 when not). */
    Vec3 surf = new Vec3(0, 1, 0);
    private Vec3 pivotAt, pivotFrom, pivotTo;
    private double pivotT = -1;
    /** The way it's making for a wall (to get up to a ceiling, or down off one), and until when. */
    private Vec3 wallDir, wallFace;
    /**
     * A goal is on the face it holds when it's no more than this far out from its body (a spot a block off the face,
     * a body lying under it) nor this far beyond it on the far side of the face; otherwise it's on another face.
     */
    static final double REACH_OFF = 2.8, REACH_UP = 0.8;
    private Vec3 lastPos = Vec3.ZERO;
    /** Its way round walls to the goal, and where it's making for along it this tick. */
    final HarvestmanRoute route = new HarvestmanRoute();
    private Vec3 aim;
    private int stuck;
    int stuckTicks;
    /** A pause in its crawl (testing a ledge, peering through a doorway) and what it looks at meanwhile. */
    private int pause;
    private Vec3 pauseLook, lastProbe;
    /** The marks it leaves (HarvestmanSigns). */
    final HarvestmanSigns signs = new HarvestmanSigns(this);

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
        builder.define(LOOK_AT, new Vector3f());
        builder.define(LOOKING, false);
        builder.define(WATCH, -1);
        builder.define(STUMBLE, 0);
        builder.define(RECOIL, 0);
        builder.define(RAGE, 0);
    }

    /** Where it's looking, or null. */
    public Vec3 lookingAt() {
        if (!entityData.get(LOOKING)) {
            return null;
        }
        org.joml.Vector3fc v = entityData.get(LOOK_AT);
        return new Vec3(v.x(), v.y(), v.z());
    }

    /** The entity id of the player it's watching, or -1. */
    public int watchingId() {
        return entityData.get(WATCH);
    }

    public int stumbles() {
        return entityData.get(STUMBLE);
    }

    public int rages() {
        return entityData.get(RAGE);
    }

    /** How long a rage holds it still (its rage played a little quick), and the least time between two. */
    public static final int RAGE_TICKS = 86, RAGE_GAP = 20 * 75;
    private long rageAt = -RAGE_GAP;

    /** Still raging at someone. */
    public boolean raging() {
        return level().getGameTime() - rageAt < RAGE_TICKS;
    }

    /**
     * It stops, faces at, and rages: rears, screams, thrashes its arms (the client plays it). Not if it raged at
     * someone only a little while ago (unless forced), nor while it holds something or is dead.
     */
    public boolean rage(Vec3 at, boolean force) {
        long now = level().getGameTime();
        if (!isAlive() || held != null || dropping || pose() == DEAD || raging() || !force && now - rageAt < RAGE_GAP) {
            return false;
        }
        rageAt = now;
        entityData.set(RAGE, rages() + 1);
        pause = RAGE_TICKS;
        pauseLook = at;
        if (level() instanceof ServerLevel sl) {
            playSound(sl, centre(), HarvestmanSounds.SCREECH, 1.8f, 0.8f);
        }
        return true;
    }

    public int recoils() {
        return entityData.get(RECOIL);
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
        entityData.set(LOOKING, look != null);
        if (look != null && dist2(look, entityData.get(LOOK_AT)) > 0.09) {
            entityData.set(LOOK_AT, new Vector3f((float) look.x, (float) look.y, (float) look.z));
        }
        entityData.set(WATCH, mind.watchingId(level));
        struck.clear();
        struckAt.clear();
        stepLegs(level, false);
        signs.tick(level);
    }

    /**
     * Client only: stands an apparition (one only its viewer sees, HarvestmanGlimpse) on the floor where it is, facing
     * a point: its turn and its feet are set here, since no server moves it.
     */
    public void standPhantom(Vec3 face) {
        Vec3 f = face.subtract(centre()).multiply(1, 0, 1);
        up = new Vec3(0, 1, 0);
        fwd = f.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : f.normalize();
        Vec3 x = up.cross(fwd).normalize();
        Vector3f cx = new Vector3f((float) x.x, (float) x.y, (float) x.z), cy = new Vector3f(0, 1, 0);
        Vector3f cz = new Vector3f().set(cx).cross(cy);
        Quaternionf q = new Quaternionf().setFromNormalized(new Matrix3f(cx, cy, cz));
        entityData.set(TURN, q);
        turn.set(q);
        turnO.set(q);
        setYRot((float) Math.toDegrees(Math.atan2(-fwd.x, fwd.z)));
        yBodyRot = getYRot();
        legs.tick(this, centre(), up, fwd, STAND, 0, false, 0, random);
        for (int i = 0; i < 4; i++) {
            Vec3 ft = legs.feet[i];
            entityData.set(FEET.get(i), new Vector3f((float) ft.x, (float) ft.y, (float) ft.z));
            for (int k = 0; k < 4; k++) {
                feetSeen[i][k] = ft;
            }
        }
    }

    private static double dist2(Vec3 a, org.joml.Vector3fc b) {
        double dx = a.x - b.x(), dy = a.y - b.y(), dz = a.z - b.z();
        return dx * dx + dy * dy + dz * dz;
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
            if (!dead && (lost() >> i & 1) == 0 && legs.landed(i, before[i])) {
                footfall(level, i, f);
            }
        }
    }

    /**
     * A point coming down: it taps on what it lands on (louder on hard floors), and the block answers in its own voice
     * (metal rings, boards creak, glass and gravel crunch), with a puff of its dust. Ice, or a wet floor out in the rain,
     * can skid out from under it: the point slides, it lurches toward that leg and loses its speed.
     */
    private void footfall(ServerLevel level, int i, Vec3 f) {
        Vec3 n = legs.footN[i] == null ? up : legs.footN[i];
        BlockPos on = BlockPos.containing(f.subtract(n.scale(0.1)));
        BlockState st = level.getBlockState(on);
        if (st.isAir()) {
            return;
        }
        boolean sneak = mind.sneaking();
        double spd = legs.speed();
        float hard = st.getDestroySpeed(level, on) > 1.2f ? 1f : 0.45f;
        if (random.nextFloat() < 0.8f) {
            level.playSound(null, f.x, f.y, f.z, HarvestmanSounds.TAP, SoundSource.HOSTILE,
                    (sneak ? 0.25f : 0.6f) * hard, 0.85f + random.nextFloat() * 0.3f);
        }
        // its weight on the block: the block's own step, low and heavy, louder at a run
        SoundType type = st.getSoundType();
        float weight = (sneak ? 0.16f : 0.38f) * (spd > 5 ? 1.7f : 1f);
        level.playSound(null, f.x, f.y, f.z, type.getStepSound(), SoundSource.HOSTILE, type.getVolume() * weight,
                type.getPitch() * (0.6f + random.nextFloat() * 0.12f));
        int puff = spd > 5 ? 6 : sneak ? 1 : 3;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), f.x + n.x * 0.05, f.y + n.y * 0.05, f.z + n.z * 0.05,
                puff, 0.12, 0.04, 0.12, 0.02);
        // footing that gives
        boolean ice = st.getBlock().getFriction() > 0.9f;
        boolean wet = n.y > 0.5 && level.isRainingAt(on.above());
        float chance = ice ? 0.12f : wet ? 0.012f : 0f;
        if (chance > 0 && random.nextFloat() < chance * (spd > 3 ? 1.5f : 1f)) {
            Vec3 out = f.subtract(centre());
            out = out.subtract(up.scale(out.dot(up)));
            out = out.lengthSqr() < 1e-4 ? fwd : out.normalize();
            legs.slip(i, out.scale(0.35 + random.nextDouble() * 0.2));
            speed *= 0.4;
            entityData.set(STUMBLE, ((stumbles() >> 2) + 1) << 2 | i);
            level.playSound(null, f.x, f.y, f.z, HarvestmanSounds.SCRAPE, SoundSource.HOSTILE, 0.35f, 1.5f + random.nextFloat() * 0.2f);
        }
    }

    /** Is it moving with care (not rushing, striking, fleeing or carrying)? Then it tests ledges and doorways first. */
    private boolean careful() {
        int plan = mind.plan;
        return goalSpeed <= 3 && held == null && plan != HarvestmanMind.STRIKE && plan != HarvestmanMind.FLEE
                && plan != HarvestmanMind.ZOMBIE && plan != HarvestmanMind.DRAG;
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
     * One tick of crawling. It holds on to one block face at a time (surf: floor, wall or ceiling), its body its height
     * off it, and slides along it toward the goal. A face ahead (a wall, or the ceiling at the top of a wall) it climbs on
     * to; past an outer edge it swings round on to the face beyond, steps down off a low ledge, or drops off a floor's
     * edge when it means to go down; otherwise it stops there. It only ever moves where its body fits and enough of its
     * legs can plant a point on something solid. With nothing to hold (or letting go of a ceiling) it falls.
     */
    private void crawl(ServerLevel level) {
        double dt = 0.05;
        Vec3 c = centre();
        // nothing under it any more (moved, or what it held was broken away): it finds a hold again, or falls
        if (!attached || !dropping && pivotT < 0 && cast(c, c.subtract(surf.scale(STAND + 3.5))).getType() == HitResult.Type.MISS) {
            attach(c);
        }
        // on a ceiling and wanting the floor: it lets go and drops
        if (wantFloor && surf.y < -0.5 && !dropping && cast(c, c.add(0, -12, 0)).getType() != HitResult.Type.MISS) {
            dropping = true;
            pivotT = -1;
            level.playSound(null, c.x, c.y, c.z, HarvestmanSounds.SCRAPE, SoundSource.HOSTILE, 0.8f, 1.2f);
        }
        if (dropping) {
            fall(level, c, dt);
            return;
        }
        // the way round what's in between: where it makes for now along its route (the goal itself when it's in the clear)
        boolean wasSlow = route.slow;
        aim = route.aim(level, c.subtract(surf.scale(height(c, fwd) - 0.5)), surf, goal, stuck);
        Vec3 dir = fwd;
        double dist = 0;
        if (aim != null) {
            Vec3 to = aim.subtract(c);
            Vec3 t = to.subtract(surf.scale(to.dot(surf)));
            dist = t.length();
            if (dist > 1e-3) {
                dir = t.scale(1 / dist);
            }
        }
        // what it wants is out across from the face it holds (a ceiling over its floor, the floor under its ceiling): it
        // makes for the nearest wall. (Something behind its face, over an edge, it gets to by going round the edge.)
        if (!surf.equals(wallFace)) {
            wallDir = null;     // on a new face: it looks again
        }
        double off = aim == null ? 0 : aim.subtract(c).dot(surf);
        if (aim != null && off > REACH_UP && (dist < 3 || wallDir != null)) {
            Vec3 w = wallward(c, dist > 1e-3 ? dir : fwd);
            if (w != null) {
                dir = w;
                dist = Math.max(dist, 4);
            }
        } else {
            wallDir = null;
        }
        dir = dir.subtract(surf.scale(dir.dot(surf)));
        dir = dir.lengthSqr() < 1e-6 ? fwd : dir.normalize();
        double want = height(c, dir);
        entityData.set(LIFT, (float) want);
        // coming up to a doorway, it stops short and peers through before it goes in (less the bolder it has grown)
        if (route.slow && !wasSlow && pivotT < 0 && aim != null && careful() && random.nextFloat() < 0.8f - 0.45f * (float) mind.bold) {
            pause = 14 + random.nextInt(28);
            pauseLook = c.add(dir.scale(5));
        }
        // a drop coming up on a floor: it stops and reaches a point out over the lip to test it before it trusts it
        if (pause <= 0 && pivotT < 0 && surf.y > 0.5 && aim != null && dist > 1.2 && careful()
                && (lastProbe == null || lastProbe.distanceToSqr(c) > 25)) {
            Vec3 p = c.add(dir.scale(1.5));
            BlockHitResult under = cast(p.add(surf.scale(0.4)), p.subtract(surf.scale(want + 1.6)));
            boolean drop = under.getType() == HitResult.Type.MISS || c.y - want - under.getLocation().y > 1.1;
            if (drop) {
                lastProbe = c;
                pause = 20;
                pauseLook = p.subtract(0, want, 0);
                Vec3 right = up.cross(fwd);
                int leg = dir.dot(right) >= 0 ? 2 : 0;
                Vec3 hip = frame().hip(leg);
                Vec3 lip = c.add(dir.scale(1.25)).subtract(surf.scale(want + 0.35));
                Vec3 touch = under.getType() != HitResult.Type.MISS && under.getLocation().distanceTo(hip) < 3.2 ? under.getLocation() : lip;
                if ((lost() >> leg & 1) == 0) {
                    legs.probe(leg, touch);
                }
            }
        }
        if (pause > 0) {
            pause--;
            speed = 0;
            up = slerp(up, surf, 8 * dt);
            face(dt, look != null ? look.subtract(c) : pauseLook != null ? pauseLook.subtract(c) : dir);
            stuck = 0;
            lastPos = position();
            return;
        }
        // at a burst it gets up to speed and stops again in a few strides, its points biting in
        boolean burst = goal != null && goalSpeed > 5 || speed > 5;
        double stop = burst ? 12 : 3;
        double cap = route.slow ? 0.7 : goalSpeed * mind.speedScale();
        double top = goal == null ? 0 : Math.min(cap, Math.sqrt(2 * stop * Math.max(0, dist - 0.3)));
        speed = speed < top ? Math.min(top, speed + ACCEL * mind.accelScale() * (burst ? 2.5 : 1) * dt)
                : Math.max(top, speed - (burst ? 14 : BRAKE) * dt);
        boolean blocked = false;
        Vec3 next;
        if (pivotT >= 0) {
            // swinging round an outer edge, its body arcing over it
            pivotT = Math.min(1, pivotT + Math.max(0.6, speed) * dt / (want * Math.PI / 2));
            Vec3 n = slerp(pivotFrom, pivotTo, pivotT);
            next = pivotAt.add(n.scale(want));
            if (pivotT >= 1) {
                surf = pivotTo;
                pivotT = -1;
            }
        } else {
            // a face across its way: it climbs on to it (wanting the floor, it won't go up a wall)
            if (speed > 0.01) {
                double cling = CLING;
                BlockHitResult ahead = cast(c, c.add(dir.scale(cling + 0.25 + speed * dt)));
                if (ahead.getType() != HitResult.Type.MISS) {
                    Vec3 n = normal(ahead);
                    if (n.dot(dir) > -0.5 && n.dot(dir) < 0 && n.dot(surf) < 0.5) {
                        // a wall at a slant: it slides along it
                        Vec3 along = dir.subtract(n.scale(dir.dot(n)));
                        if (along.lengthSqr() > 1e-4) {
                            dir = along.normalize();
                        }
                    } else if (n.dot(dir) <= -0.5 && n.dot(surf) < 0.5) {
                        if (route.stay) {
                            // its route goes on along this face (under a lintel, through a gap): it carries on
                        } else if (!(wantFloor && surf.y > 0.5) && fits(c, fwd)) {
                            surf = n;
                            speed *= 0.6;
                            level.playSound(null, c.x, c.y, c.z, HarvestmanSounds.SCRAPE, SoundSource.HOSTILE, 0.35f, 1.3f);
                        } else {
                            blocked = true;
                        }
                    }
                }
            }
            next = blocked ? c : c.add(dir.scale(speed * dt));
            BlockHitResult under = cast(next.add(surf.scale(0.4)), next.subtract(surf.scale(want + 1.6)));
            if (under.getType() == HitResult.Type.MISS) {
                next = overEdge(level, c, next, dir, want);
                if (next == null) {
                    next = c;
                    blocked = true;
                } else if (dropping) {
                    setPos(next.subtract(0, MID, 0));
                    return;
                }
            } else {
                // held its height off what's under it (rising over a step, sinking into a dip), never jerking
                double h = under.getLocation().subtract(next).dot(surf.scale(-1));
                next = next.add(surf.scale(Mth.clamp((want - h) * Math.min(1, 6 * dt), -0.3, 0.3)));
            }
        }
        // only where its body fits and its legs find holds
        if (pivotT < 0 && next != c) {
            HarvestmanBody.Frame at = HarvestmanLegs.flat(next, surf, fwd.subtract(surf.scale(fwd.dot(surf))).normalize());
            int legsLeft = 4 - Integer.bitCount(lost());
            int support = legs.support(this, at, want, lost());
            if (!fits(next, at.z()) || support < Math.max(2, legsLeft - 1)) {
                next = c;
                blocked = true;
                speed = 0;
                // creeping up to an outer edge its legs can't plant past: it swings round on to the face beyond now
                Vec3 p = c.add(dir.scale(0.6));
                if (support < Math.max(2, legsLeft - 1)
                        && cast(p.add(surf.scale(0.4)), p.subtract(surf.scale(want + 1.6))).getType() == HitResult.Type.MISS) {
                    Vec3 r = overEdge(level, c, p, dir, want);
                    if (r != null && dropping) {
                        setPos(r.subtract(0, MID, 0));
                        return;
                    }
                    if (r != null && pivotT == 0) {
                        next = r;
                        blocked = false;
                    }
                }
            }
        }
        setPos(next.subtract(0, MID, 0));
        up = slerp(up, pivotT >= 0 ? slerp(pivotFrom, pivotTo, pivotT) : surf, 8 * dt);
        if (look != null && (speed < 0.3 || mind.watching())) {
            face(dt, look.subtract(c));
        } else if (speed > 0.05) {
            face(dt, dir);
        } else {
            face(dt, fwd);
        }
        // got nowhere for two seconds: it gives up on that goal (and the mind picks again)
        if (goal != null && (blocked || speed > 0.3 && position().distanceToSqr(lastPos) < 0.0004)) {
            stuckTicks++;
            if (++stuck > 40) {
                goal = null;
                stuck = 0;
            }
        } else {
            stuck = 0;
            stuckTicks = 0;
        }
        lastPos = position();
    }

    /**
     * The way to the nearest wall across the face it holds (leaning toward the way it would go anyway), kept until it's
     * on another face so it doesn't dither; null with none in sight.
     */
    private Vec3 wallward(Vec3 c, Vec3 lean) {
        if (wallDir != null) {
            return wallDir;
        }
        Vec3 a = Math.abs(surf.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 e1 = a.subtract(surf.scale(a.dot(surf))).normalize(), e2 = surf.cross(e1);
        Vec3 best = null;
        double bestScore = 1e9;
        for (int k = 0; k < 8; k++) {
            double ang = k * Math.PI / 4;
            Vec3 d = e1.scale(Math.cos(ang)).add(e2.scale(Math.sin(ang)));
            BlockHitResult hit = cast(c, c.add(d.scale(16)));
            if (hit.getType() != HitResult.Type.MISS && normal(hit).dot(d) < -0.5) {
                double score = hit.getLocation().distanceTo(c) - 3 * d.dot(lean);
                if (score < bestScore) {
                    bestScore = score;
                    best = d;
                }
            }
        }
        wallDir = best;
        wallFace = surf;
        return best;
    }

    /** How far its body wants to be off its surface now: lower under a low ceiling, here or just ahead (a doorway). */
    private double height(Vec3 c, Vec3 dir) {
        double want = pose() == FEED || pose() == HOLD ? FEEDING : Mth.lerp(HarvestmanLegs.floorness(surf), CLING, STAND);
        want -= 0.6 * mind.crouch();
        for (Vec3 p : new Vec3[] {c, c.add(dir.scale(1.6))}) {
            BlockHitResult below = cast(p, p.subtract(surf.scale(6))), above = cast(p, p.add(surf.scale(6)));
            if (above.getType() != HitResult.Type.MISS && below.getType() != HitResult.Type.MISS) {
                double gap = below.getLocation().distanceTo(p) + above.getLocation().distanceTo(p);
                want = Math.min(want, Math.max(CROUCH, gap - 0.8));
            }
        }
        // a low gap coming up (a doorway's lintel), felt for down by its feet so it ducks before it gets there, and over
        // its tail while that's still under one
        BlockHitResult base = cast(c, c.subtract(surf.scale(6)));
        if (base.getType() != HitResult.Type.MISS) {
            Vec3 b = base.getLocation().add(surf.scale(0.3));
            for (double k : new double[] {0.9, 1.8, 2.7, -0.7, -1.5}) {
                Vec3 p = b.add((k > 0 ? dir : fwd).scale(k));
                if (cast(b, p).getType() != HitResult.Type.MISS) {
                    continue;
                }
                BlockHitResult over = cast(p, p.add(surf.scale(6)));
                if (over.getType() != HitResult.Type.MISS) {
                    want = Math.min(want, Math.max(CROUCH, over.getLocation().distanceTo(p) + 0.3 - 0.8));
                }
            }
        }
        // a leg or two gone, it sags
        return Math.max(CROUCH, want - 0.25 * Integer.bitCount(lost()));
    }

    /** Finds the face it's holding (the nearest of the six around it), or falls if there's none in reach. */
    private void attach(Vec3 c) {
        double best = STAND + 1.6;
        Vec3 face = null;
        // still in reach of the face it held (stepping down a ledge): it keeps it
        if (attached) {
            BlockHitResult held = cast(c, c.subtract(surf.scale(STAND + 3.5)));
            if (held.getType() != HitResult.Type.MISS && normal(held).equals(surf)) {
                pivotT = -1;
                return;
            }
        }
        for (Direction d : Direction.values()) {
            Vec3 n = new Vec3(d.getStepX(), d.getStepY(), d.getStepZ());
            BlockHitResult hit = cast(c, c.subtract(n.scale(STAND + 1.6)));
            if (hit.getType() != HitResult.Type.MISS && normal(hit).equals(n)) {
                double dd = hit.getLocation().distanceTo(c) - (n.y > 0.5 ? 0.6 : 0);  // the floor first, when it's near
                if (dd < best) {
                    best = dd;
                    face = n;
                }
            }
        }
        attached = true;
        pivotT = -1;
        if (face == null) {
            dropping = true;
        } else {
            surf = face;
            up = face;
        }
    }

    /**
     * Its centre has gone past the edge of the face it holds: the face round the edge, a low step down, or a drop off a
     * floor when it means to go down. Returns where it goes this tick (dropping set for a drop), or null to stay put.
     */
    private Vec3 overEdge(ServerLevel level, Vec3 c, Vec3 next, Vec3 dir, double want) {
        boolean onFloor = surf.y > 0.5;
        if (onFloor) {
            // a ledge of a block or three: it just steps down
            BlockHitResult down = cast(next, next.subtract(0, want + 3.2, 0));
            if (down.getType() != HitResult.Type.MISS && normal(down).y > 0.5) {
                double h = next.y - down.getLocation().y;
                return next.add(0, Mth.clamp(want - h, -0.3, 0.3), 0);
            }
        }
        // the face round the edge: back under it, facing the way it was going
        Vec3 low = next.subtract(surf.scale(want + 0.3));
        BlockHitResult wrap = cast(low, low.subtract(dir.scale(2.2)));
        BlockHitResult held = cast(c, c.subtract(surf.scale(want + 1.6)));
        if (wrap.getType() != HitResult.Type.MISS && held.getType() != HitResult.Type.MISS && normal(wrap).dot(dir) > 0.5
                && !(wantFloor && onFloor && aim != null && aim.y < c.y - 6 && floorBelow(next, 14))) {
            Vec3 n1 = normal(wrap), pf = held.getLocation(), pw = wrap.getLocation();
            // the edge: where the two faces meet, beside it; it swings round a line just past it, so it ends up over the
            // new face, not balanced on the corner
            pivotAt = c.subtract(surf.scale(surf.dot(c.subtract(pf)))).subtract(n1.scale(n1.dot(c.subtract(pw))))
                    .subtract(surf.scale(0.3));
            pivotFrom = surf;
            pivotTo = n1;
            pivotT = 0;
            return pivotAt.add(surf.scale(want));
        }
        // off a floor's edge on purpose, when what it wants is below and there's ground to land on
        // (it springs out clear of the edge, so its whole body drops past it)
        Vec3 out = next.add(dir.scale(1.4));
        if (onFloor && aim != null && aim.y < c.y - 1.5 && floorBelow(out, 16) && fits(out, dir)) {
            dropping = true;
            fwd = dir;
            level.playSound(null, c.x, c.y, c.z, HarvestmanSounds.SCRAPE, SoundSource.HOSTILE, 0.6f, 0.9f);
            return out;
        }
        return null;
    }

    private boolean floorBelow(Vec3 p, double depth) {
        BlockHitResult hit = cast(p, p.subtract(0, depth, 0));
        return hit.getType() != HitResult.Type.MISS && normal(hit).y > 0.5;
    }

    /** Would its hull fit here, nose along fwd? (three boxes down its length) */
    private boolean fits(Vec3 c, Vec3 fwd) {
        for (double z : new double[] {0.5, -0.3, -1.1}) {
            Vec3 p = c.add(fwd.scale(z));
            if (!level().noCollision(this, new AABB(p.x - 0.36, p.y - 0.36, p.z - 0.36, p.x + 0.36, p.y + 0.36, p.z + 0.36))) {
                return false;
            }
        }
        return true;
    }

    /** Falling: straight down, righting itself, until it lands on its feet on a floor. */
    private void fall(ServerLevel level, Vec3 c, double dt) {
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
                surf = new Vec3(0, 1, 0);
                up = surf;
                pivotT = -1;
                level.playSound(null, c.x, c.y, c.z, HarvestmanSounds.TAP, SoundSource.HOSTILE, 1f, 0.6f);
                // its weight coming down: the floor's own thud, and a ring of its dust kicked out
                BlockPos on = BlockPos.containing(floor.getLocation().subtract(0, 0.1, 0));
                BlockState st = level.getBlockState(on);
                if (!st.isAir()) {
                    SoundType type = st.getSoundType();
                    level.playSound(null, c.x, c.y, c.z, type.getFallSound(), SoundSource.HOSTILE, type.getVolume() * 1.2f, type.getPitch() * 0.55f);
                    Vec3 g = floor.getLocation();
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), g.x, g.y + 0.1, g.z, 28, 1.1, 0.05, 1.1, 0.08);
                }
            }
        }
        setPos(position().add(step));
        face(dt, look != null ? look.subtract(c) : fwd);
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
            double a = Math.acos(Mth.clamp(f.dot(want), -1, 1)),
                    max = TURN_RATE * mind.speedScale() * (speed > 5 ? 2.5 : route.slow ? 2 : 1) * dt;
            f = a <= max ? want : slerp(f, want, max / a);
        }
        Vec3 nf = f.subtract(up.scale(f.dot(up))).normalize();
        // it only turns where its body has the room to (its tail doesn't swing into a wall)
        if (pivotT >= 0 || dropping || fits(centre(), nf) || !fits(centre(), fwd)) {
            fwd = nf;
        }
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
        boolean eyes = false;
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
                } else if (zone == HarvestmanBody.EYES_ZONE) {
                    // in the eyes: it takes it hard, and throws itself back
                    toHealth += each * CORE;
                    eyes = true;
                    level.sendParticles(new DustParticleOptions(0xFF2414, 0.8f), at.x, at.y, at.z, 5, 0.03, 0.03, 0.03, 0.1);
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
        if (eyes) {
            entityData.set(RECOIL, recoils() + 1);
            mind.eyeHit(level, source.getEntity());
        }
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
                if (le instanceof ServerPlayer sp) {
                    sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
                }
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
