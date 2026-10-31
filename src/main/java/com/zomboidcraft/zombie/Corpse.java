package com.zomboidcraft.zombie;

import com.zomboidcraft.ZcConfig;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A dead walker's body, lying where it fell: in its clothes, without whatever was shot off it (a burst head too), in
 * the pose its fall ended in (the client works that out from the death, combat.Deaths, as it did for the fall). It
 * stands at the zombie's feet, as the zombie did, so the body is drawn exactly where the falling zombie was. It does
 * nothing but lie there (and drop if what it lies on goes); it can't be hit or pushed. Saved with the world, so the
 * dead stay where they fell (Corpses keeps the count down).
 */
public class Corpse extends Entity {
    private static final EntityDataAccessor<String> OUTFIT = SynchedEntityData.defineId(Corpse.class, EntityDataSerializers.STRING);
    /** Limbs shot off (Dismember's cut bits and HEAD_GONE). */
    private static final EntityDataAccessor<Integer> LIMBS = SynchedEntityData.defineId(Corpse.class, EntityDataSerializers.INT);
    /** Bits 0-2 the limb lost before it rose (ZombieLook.missingLimb), bit 3 legless. */
    private static final EntityDataAccessor<Integer> LOOK = SynchedEntityData.defineId(Corpse.class, EntityDataSerializers.INT);
    /** The death (Deaths' long), low and high halves. */
    private static final EntityDataAccessor<Integer> DEATH_LO = SynchedEntityData.defineId(Corpse.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DEATH_HI = SynchedEntityData.defineId(Corpse.class, EntityDataSerializers.INT);
    /** The zombie's own per-body number (its sways, stumps), and its size. */
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(Corpse.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(Corpse.class, EntityDataSerializers.FLOAT);
    /**
     * The zombie it was (its entity id) while that is still falling; the client draws the body only once that's
     * gone. Not saved.
     */
    private static final EntityDataAccessor<Integer> FROM = SynchedEntityData.defineId(Corpse.class, EntityDataSerializers.INT);
    /** How it was left: where its fall ended, or set out by a Harvestman (sat up, slumped in a doorway, hung). */
    private static final EntityDataAccessor<Integer> POSE = SynchedEntityData.defineId(Corpse.class, EntityDataSerializers.INT);

    public static final int FALLEN = 0, SAT = 1, SLUMPED = 2, HUNG = 3;

    /** The game time it was laid, so the oldest go first. */
    long laidAt;

    public Corpse(EntityType<? extends Corpse> type, Level level) {
        super(type, level);
    }

    void copy(net.minecraft.world.entity.monster.zombie.Zombie zombie, long death) {
        entityData.set(OUTFIT, ZombieLook.outfit(zombie));
        entityData.set(LIMBS, com.zomboidcraft.combat.Dismember.limbs(zombie)
                & (com.zomboidcraft.combat.Dismember.CUTS | com.zomboidcraft.combat.Dismember.MARKS));
        entityData.set(LOOK, ZombieLook.missingLimb(zombie) | (com.zomboidcraft.combat.Downed.isLegless(zombie) ? 8 : 0));
        entityData.set(DEATH_LO, (int) death);
        entityData.set(DEATH_HI, (int) (death >>> 32));
        entityData.set(VARIANT, (int) zombie.getUUID().getLeastSignificantBits());
        entityData.set(SIZE, zombie.getScale());
        entityData.set(FROM, zombie.getId());
        laidAt = zombie.level().getGameTime();
    }

    /** Dresses a body that never walked in this world (a Harvestman's den): its outfit, what's missing, its fall. */
    void dress(String outfit, int limbs, long death, int variant) {
        entityData.set(OUTFIT, outfit);
        entityData.set(LIMBS, limbs);
        entityData.set(DEATH_LO, (int) death);
        entityData.set(DEATH_HI, (int) (death >>> 32));
        entityData.set(VARIANT, variant);
    }

    public String outfit() {
        return entityData.get(OUTFIT);
    }

    public int limbs() {
        return entityData.get(LIMBS);
    }

    public int missingLimb() {
        return entityData.get(LOOK) & 7;
    }

    public boolean legless() {
        return (entityData.get(LOOK) & 8) != 0;
    }

    public long death() {
        return (long) entityData.get(DEATH_HI) << 32 | entityData.get(DEATH_LO) & 0xFFFFFFFFL;
    }

    public int variant() {
        return entityData.get(VARIANT);
    }

    public float size() {
        return entityData.get(SIZE);
    }

    public int pose() {
        return entityData.get(POSE);
    }

    /** Sets it out as a Harvestman leaves it (a hung body stays where it's put, off the floor). */
    public void pose(int pose) {
        entityData.set(POSE, pose);
    }

    /** Never the oldest to go and never times out (a body a Harvestman set out to be found). */
    public void keep() {
        laidAt = level().getGameTime() + 1_000_000_000L;
    }

    /** The zombie it was, while that's still falling (its entity id), or -1. */
    public int from() {
        return entityData.get(FROM);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OUTFIT, "civilian");
        builder.define(LIMBS, 0);
        builder.define(LOOK, 0);
        builder.define(DEATH_LO, 0);
        builder.define(DEATH_HI, 0);
        builder.define(VARIANT, 0);
        builder.define(SIZE, 1f);
        builder.define(FROM, -1);
        builder.define(POSE, FALLEN);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        double minutes = ZcConfig.get().corpses.minutes;
        if (minutes > 0 && tickCount % 20 == 0 && level.getGameTime() - laidAt > minutes * 1200) {
            discard();
            return;
        }
        // drops if what it lay on goes (looked at now and then once it's down)
        Vec3 v = getDeltaMovement();
        if (pose() == HUNG) {
            // hangs from its cable
        } else if (!onGround() || v.y != 0 || tickCount % 20 == 0) {
            v = new Vec3(0, Math.max(-1.5, v.y - 0.08) * 0.98, 0);
            move(MoverType.SELF, v);
            setDeltaMovement(onGround() ? Vec3.ZERO : v);
        }
        if (tickCount > 10 && entityData.get(FROM) != -1) {
            entityData.set(FROM, -1);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        entityData.set(OUTFIT, input.getStringOr("outfit", "civilian"));
        entityData.set(LIMBS, input.getIntOr("limbs", 0));
        entityData.set(LOOK, input.getIntOr("look", 0));
        entityData.set(DEATH_LO, input.getIntOr("death", 0));
        entityData.set(DEATH_HI, input.getIntOr("deathHigh", 0));
        entityData.set(VARIANT, input.getIntOr("variant", 0));
        entityData.set(SIZE, input.getFloatOr("size", 1f));
        laidAt = input.getIntOr("laidAt", 0) & 0xFFFFFFFFL;
        entityData.set(POSE, input.getIntOr("pose", FALLEN));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putString("outfit", outfit());
        output.putInt("limbs", limbs());
        output.putInt("look", entityData.get(LOOK));
        output.putInt("death", entityData.get(DEATH_LO));
        output.putInt("deathHigh", entityData.get(DEATH_HI));
        output.putInt("variant", variant());
        output.putFloat("size", size());
        output.putInt("laidAt", (int) laidAt);
        output.putInt("pose", pose());
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

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double far = ZcConfig.get().corpses.drawDistance;
        return distance < far * far;
    }
}
