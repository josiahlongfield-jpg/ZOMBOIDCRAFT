package com.zomboidcraft.world.cars;

import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A piece of a crashed aircraft ({@link AirBreakup}): thrown off tumbling, it falls, bounces and
 * slides to a stop, then burns and smokes for a while and is cleared away after ten minutes. Whatever
 * it lands on in its first flight takes a hit.
 */
public class AirDebris extends Entity {
    private static final EntityDataAccessor<String> MODEL = SynchedEntityData.defineId(AirDebris.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> VARIANT = SynchedEntityData.defineId(AirDebris.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> PIECE = SynchedEntityData.defineId(AirDebris.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<org.joml.Quaternionfc> TURN = SynchedEntityData.defineId(AirDebris.class, EntityDataSerializers.QUATERNION);

    private static final int LIFE = 20 * 60 * 10, BURN = 20 * 40;

    /** How it's turned (model frame, as aircraft are), last tick and now; and its tumble, radians a tick. */
    public final Quaternionf turn = new Quaternionf(), turnO = new Quaternionf();
    private final Vector3f tumble = new Vector3f();
    private int age;

    public AirDebris(EntityType<? extends AirDebris> type, Level level) {
        super(type, level);
    }

    /** Throws piece of the aircraft car, which is turned by q and moving at vel (blocks a tick). */
    public static void throwFrom(ServerLevel level, CarProp car, Quaternionf q, Vec3 vel, String variant) {
        CarModels.Model m = CarModels.get(car.model());
        if (m == null) {
            return;
        }
        List<AirBreakup.Piece> pieces = AirBreakup.pieces(car.model());
        float pivot = car.pivot();
        for (int i = 0; i < pieces.size(); i++) {
            float[] a = pieces.get(i).anchor(m.size);
            Vector3f off = q.transform(new Vector3f(a[0], a[1] - pivot, a[2]));
            AirDebris d = Cars.DEBRIS.create(level, EntitySpawnReason.EVENT);
            if (d == null) {
                return;
            }
            d.entityData.set(MODEL, car.model());
            d.entityData.set(VARIANT, variant);
            d.entityData.set(PIECE, i);
            d.turn.set(q);
            d.turnO.set(q);
            d.entityData.set(TURN, new Quaternionf(q));
            // flung out from the middle on top of the way the aircraft was going
            Vec3 out = new Vec3(off.x, Math.max(0.5, off.y + 1.5), off.z).normalize();
            double kick = 0.25 + level.random.nextDouble() * 0.35;
            d.setDeltaMovement(vel.scale(0.7).add(out.scale(kick)));
            d.tumble.set((level.random.nextFloat() - 0.5f) * 0.5f, (level.random.nextFloat() - 0.5f) * 0.4f, (level.random.nextFloat() - 0.5f) * 0.5f);
            d.snapTo(car.getX() + off.x, car.getY() + pivot + off.y, car.getZ() + off.z, 0, 0);
            level.addFreshEntity(d);
        }
    }

    public String model() {
        return entityData.get(MODEL);
    }

    public String variant() {
        return entityData.get(VARIANT);
    }

    public int piece() {
        return entityData.get(PIECE);
    }

    /** Whether it's still burning, for the client's flames; it smokes for twice as long. */
    public boolean burning() {
        return age < BURN;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(MODEL, "jet_corvid");
        builder.define(VARIANT, "burnt");
        builder.define(PIECE, 0);
        builder.define(TURN, new Quaternionf());
    }

    @Override
    public void tick() {
        super.tick();
        turnO.set(turn);
        if (level() instanceof ServerLevel server) {
            age++;
            if (age > LIFE) {
                discard();
                return;
            }
            Vec3 v = getDeltaMovement().add(0, -0.08, 0);
            boolean wasGround = onGround();
            move(MoverType.SELF, v);
            v = getDeltaMovement();
            if (onGround()) {
                if (!wasGround && v.y < -0.3) {
                    // a bounce, and anyone it comes down on is hit
                    v = new Vec3(v.x * 0.6, -v.y * 0.25, v.z * 0.6);
                    for (LivingEntity hit : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(1.2))) {
                        hit.hurtServer(server, damageSources().fallingBlock(this), 14);
                    }
                } else {
                    v = new Vec3(v.x * 0.7, Math.min(v.y, 0), v.z * 0.7);
                }
                tumble.mul(0.7f);
            } else {
                v = v.scale(0.985);
            }
            if (horizontalCollision) {
                v = new Vec3(-v.x * 0.2, v.y, -v.z * 0.2);
                tumble.mul(0.5f);
            }
            setDeltaMovement(v);
            if (tumble.lengthSquared() > 1e-6f) {
                turn.rotateLocalX(tumble.x).rotateLocalY(tumble.y).rotateLocalZ(tumble.z);
                if (onGround()) {
                    // settle onto its flattest side
                    Vector3f up = turn.transform(new Vector3f(0, 1, 0));
                    turn.premul(new Quaternionf().rotationTo(up, new Vector3f(0, Math.signum(up.y) == 0 ? 1 : Math.signum(up.y), 0)).nlerp(new Quaternionf(), 0.85f));
                }
                entityData.set(TURN, new Quaternionf(turn));
            }
        } else {
            turn.set(entityData.get(TURN));
            if (age++ == 0) {
                turnO.set(turn);
            }
            smoke();
        }
    }

    /** Flames while it burns, then a column of smoke that thins out. */
    private void smoke() {
        var r = level().random;
        boolean flames = tickCount < BURN;
        if (tickCount > BURN * 2 && r.nextInt(4) != 0) {
            return;
        }
        double x = getX() + (r.nextDouble() - 0.5) * 1.5, y = getY() + 0.5 + r.nextDouble(), z = getZ() + (r.nextDouble() - 0.5) * 1.5;
        level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, true, x, y, z, 0, 0.07, 0);
        if (flames && r.nextInt(2) == 0) {
            level().addParticle(ParticleTypes.FLAME, x, y - 0.4, z, 0, 0.03, 0);
            level().addParticle(ParticleTypes.LARGE_SMOKE, x, y, z, 0, 0.05, 0);
        }
    }

    @Override
    protected InterpolationHandler createInterpolationHandler() {
        return LinearInterpolationHandler.create(this, 2);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        entityData.set(MODEL, input.getStringOr("model", "jet_corvid"));
        entityData.set(VARIANT, input.getStringOr("variant", "burnt"));
        entityData.set(PIECE, input.getIntOr("piece", 0));
        age = input.getIntOr("age", 0);
        turn.set(input.getFloatOr("qx", 0), input.getFloatOr("qy", 0), input.getFloatOr("qz", 0), input.getFloatOr("qw", 1));
        turnO.set(turn);
        entityData.set(TURN, new Quaternionf(turn));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putString("model", model());
        output.putString("variant", variant());
        output.putInt("piece", piece());
        output.putInt("age", age);
        output.putFloat("qx", turn.x);
        output.putFloat("qy", turn.y);
        output.putFloat("qz", turn.z);
        output.putFloat("qw", turn.w);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
