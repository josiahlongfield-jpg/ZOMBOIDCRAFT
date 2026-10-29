package com.zomboidcraft.combat.throwing;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.combat.Downed;
import com.zomboidcraft.combat.ModSounds;
import com.zomboidcraft.zombie.Noise;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A thrown bottle, brick, flare, grenade...: it tumbles through the air, breaks or bounces where it lands, and then
 * does its thing (ThrowKind). Lures are heard by zombies through Noise, so they come to it and not to you.
 */
public class Thrown extends Entity {
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(Thrown.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<org.joml.Quaternionfc> TURN = SynchedEntityData.defineId(Thrown.class, EntityDataSerializers.QUATERNION);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(Thrown.class, EntityDataSerializers.INT);

    /** In the air; lying still; going (burning, banging, smoking); ringing. */
    public static final int FLYING = 0, RESTING = 1, GOING = 2, RINGING = 3;
    private static final double GRAVITY = 0.05, DRAG = 0.99;

    /** The client's sparks, smoke and flare light (client/combat/ThrowEffects sets it). */
    public static java.util.function.Consumer<Thrown> CLIENT_TICK = t -> {
    };

    public final Quaternionf turn = new Quaternionf(), turnO = new Quaternionf();
    private final Vector3f tumble = new Vector3f();
    private UUID owner;
    /** Ticks left on the fuse (-1: none), and ticks into what it's doing. */
    private int fuse = -1, going;
    private int age;

    public Thrown(EntityType<? extends Thrown> type, Level level) {
        super(type, level);
    }

    /** Throws one of stack from the player's hand at speed, with fuse ticks left on it (-1: not lit). */
    public static Thrown launch(ServerLevel level, LivingEntity thrower, ItemStack stack, double speed, int fuse) {
        Thrown t = Throwing.THROWN.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
        if (t == null) {
            return null;
        }
        t.entityData.set(ITEM, stack.copyWithCount(1));
        t.owner = thrower.getUUID();
        t.fuse = fuse;
        Vec3 look = thrower.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-6 ? Vec3.ZERO : right.normalize();
        // out of the right hand, just in front of the eyes
        Vec3 from = thrower.getEyePosition().add(right.scale(0.25)).add(0, -0.15, 0).add(look.scale(0.35));
        t.snapTo(from.x, from.y, from.z, thrower.getYRot(), 0);
        t.setDeltaMovement(look.scale(speed).add(thrower.getDeltaMovement().multiply(1, 0, 1)));
        var r = thrower.getRandom();
        // end over end, as an arm throws it
        t.tumble.set(-0.35f - r.nextFloat() * 0.25f, (r.nextFloat() - 0.5f) * 0.1f, (r.nextFloat() - 0.5f) * 0.1f);
        t.turn.rotationY((float) Math.toRadians(-thrower.getYRot()));
        t.entityData.set(TURN, new Quaternionf(t.turn));
        if (t.kind() == ThrowKind.FLARE) {
            t.entityData.set(PHASE, GOING);     // struck as it's thrown
        }
        level.addFreshEntity(t);
        return t;
    }

    /** One from stack let fall at the thrower's feet, still live with fuse ticks to go (a wind-up abandoned). */
    public static void drop(ServerLevel level, LivingEntity thrower, ItemStack stack, int fuse) {
        Thrown t = launch(level, thrower, stack, 0, fuse);
        if (t != null) {
            t.snapTo(thrower.getX(), thrower.getY() + 0.4, thrower.getZ(), thrower.getYRot(), 0);
            Vec3 look = thrower.getLookAngle();
            t.setDeltaMovement(look.x * 0.12, 0.05, look.z * 0.12);
        }
    }

    public ItemStack item() {
        return entityData.get(ITEM);
    }

    public ThrowKind kind() {
        return item().getItem() instanceof ThrowableItem t ? t.kind() : ThrowKind.BOTTLE;
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ITEM, ItemStack.EMPTY);
        builder.define(TURN, new Quaternionf());
        builder.define(PHASE, FLYING);
    }

    @Override
    public void tick() {
        super.tick();
        turnO.set(turn);
        if (!(level() instanceof ServerLevel server)) {
            turn.set(entityData.get(TURN));
            if (age++ == 0) {
                turnO.set(turn);
            }
            CLIENT_TICK.accept(this);
            return;
        }
        age++;
        if (age > 20 * 60 * 5) {
            discard();
            return;
        }
        if (fuse >= 0 && --fuse <= 0) {
            fuse = -1;
            detonate(server);
            return;
        }
        if (phase() == FLYING) {
            fly(server);
        } else if (onGround() || getDeltaMovement().lengthSqr() > 1e-4) {
            settle();
        }
        if (!isRemoved()) {
            behave(server);
        }
    }

    private void fly(ServerLevel level) {
        Vec3 v = getDeltaMovement().add(0, -GRAVITY, 0).scale(DRAG);
        Vec3 from = position().add(0, getBbHeight() / 2, 0);
        Entity thrower = owner();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(this, from, from.add(v), getBoundingBox().expandTowards(v).inflate(0.3),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && (e != thrower || age > 6), v.lengthSqr() + 1);
        if (hit != null) {
            strike(level, (LivingEntity) hit.getEntity(), v);
            if (isRemoved()) {
                return;
            }
            v = v.scale(-0.2);
        }
        setDeltaMovement(v);
        move(MoverType.SELF, v);
        Vec3 after = getDeltaMovement();
        boolean bumpX = v.x != 0 && after.x == 0, bumpZ = v.z != 0 && after.z == 0, bumpY = v.y < 0 && onGround() || v.y > 0 && after.y == 0;
        if (bumpX || bumpY || bumpZ) {
            double speed = v.length();
            if (kind().shatters()) {
                shatter(level);
                return;
            }
            landed(level, speed);
            double keep = kind().bounce;
            Vec3 next = new Vec3(bumpX ? -v.x * keep : v.x * 0.7, bumpY ? -v.y * keep : v.y, bumpZ ? -v.z * keep : v.z * 0.7);
            if (bumpY) {
                next = new Vec3(next.x * 0.6, next.y, next.z * 0.6);
            }
            setDeltaMovement(next);
            tumble.mul((float) keep);
            if (onGround() && next.lengthSqr() < 0.004) {
                setDeltaMovement(Vec3.ZERO);
                rest();
            }
        }
        spin();
    }

    /** Rolls and slides to a stop after it's come down. */
    private void settle() {
        Vec3 v = getDeltaMovement().add(0, -GRAVITY, 0);
        v = onGround() ? new Vec3(v.x * 0.6, Math.max(v.y, -0.1), v.z * 0.6) : v.scale(DRAG);
        setDeltaMovement(v);
        move(MoverType.SELF, v);
    }

    private void spin() {
        if (tumble.lengthSquared() > 1e-6f) {
            turn.rotateLocalX(tumble.x).rotateLocalY(tumble.y).rotateLocalZ(tumble.z);
            entityData.set(TURN, new Quaternionf(turn));
        }
    }

    /** Comes to rest lying on its side. */
    private void rest() {
        tumble.zero();
        float yaw = random.nextFloat() * (float) Math.PI * 2;
        turn.rotationY(yaw).rotateX((float) Math.PI / 2);
        entityData.set(TURN, new Quaternionf(turn));
        if (phase() == FLYING) {
            entityData.set(PHASE, kind() == ThrowKind.FLARE ? GOING : RESTING);
            going = kind() == ThrowKind.FLARE ? going : 0;
        }
    }

    /** It hit the ground or a wall: the sound of it, which zombies hear. */
    private void landed(ServerLevel level, double speed) {
        if (speed < 0.12) {
            return;
        }
        SoundEvent sound = switch (kind()) {
            case CAN, SMOKE, FLASHBANG, FRAG -> ModSounds.CAN_CLATTER;
            default -> ModSounds.BRICK_THUD;
        };
        float loud = (float) Math.min(1, speed);
        level.playSound(null, getX(), getY(), getZ(), sound, SoundSource.PLAYERS, 0.4f + 0.6f * loud, 0.9f + random.nextFloat() * 0.2f);
        if (kind() == ThrowKind.CAN || kind() == ThrowKind.BRICK) {
            Noise.emit(level, position(), kind().noise() * loud);
        } else {
            Noise.emit(level, position(), Math.min(kind().noise(), 8) * loud);
        }
    }

    /** A direct hit on someone. */
    private void strike(ServerLevel level, LivingEntity target, Vec3 v) {
        float damage = (float) (kind().hitDamage() * Math.min(1.5, v.length() / ZcConfig.get().throwing.throwSpeed));
        Entity thrower = owner();
        DamageSource source = damageSources().thrown(this, thrower);
        target.hurtServer(level, source, damage);
        if (kind() == ThrowKind.BRICK && target instanceof Zombie zombie
                && position().y + getBbHeight() > zombie.getEyeY() - 0.3) {
            Downed.stagger(zombie, ZcConfig.get().throwing.brickStagger);     // a brick to the head
        }
        if (kind() == ThrowKind.FLARE) {
            target.setRemainingFireTicks(ZcConfig.get().throwing.burnTicks);
        }
        if (kind().shatters()) {
            shatter(level);
        } else {
            landed(level, v.length());
        }
    }

    /** A bottle or molotov breaking: glass everywhere, and a molotov's fuel catching. */
    private void shatter(ServerLevel level) {
        ZcConfig.Throwing c = ZcConfig.get().throwing;
        Vec3 at = position().add(0, 0.1, 0);
        level.playSound(null, at.x, at.y, at.z, kind() == ThrowKind.MOLOTOV ? ModSounds.MOLOTOV_BURST : ModSounds.BOTTLE_SMASH,
                SoundSource.PLAYERS, 1f, 0.9f + random.nextFloat() * 0.2f);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, item().getItem()), at.x, at.y, at.z, 14, 0.2, 0.1, 0.2, 0.15);
        Noise.emit(level, at, kind().noise());
        if (kind() == ThrowKind.MOLOTOV) {
            level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 40, c.molotovRadius / 2, 0.3, c.molotovRadius / 2, 0.05);
            ContainedFire.pool(level, at, c.molotovRadius, c.molotovBurnTicks);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(c.molotovRadius, 1.5, c.molotovRadius))) {
                if (hurts(e)) {
                    e.setRemainingFireTicks(c.burnTicks);
                }
            }
        }
        discard();
    }

    /** What it does once it's down (or, for a flare, from the moment it's struck). */
    private void behave(ServerLevel level) {
        ZcConfig.Throwing c = ZcConfig.get().throwing;
        int phase = phase();
        if (phase == FLYING && kind() != ThrowKind.FLARE) {
            return;
        }
        going++;
        switch (kind()) {
            case CAN, BRICK -> {
                if (going > 30) {
                    becomeItem(level, item());
                }
            }
            case FIRECRACKERS -> {
                if (going > c.firecrackerTicks) {
                    discard();
                } else if (going > 10 && random.nextInt(3) == 0) {
                    level.playSound(null, getX(), getY(), getZ(), ModSounds.FIRECRACKER, SoundSource.PLAYERS, 0.8f + random.nextFloat() * 0.4f,
                            0.8f + random.nextFloat() * 0.5f);
                    level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.1, getZ(), 3, 0.15, 0.05, 0.15, 0.02);
                    setDeltaMovement(getDeltaMovement().add((random.nextDouble() - 0.5) * 0.15, 0.12, (random.nextDouble() - 0.5) * 0.15));
                    if (going % 20 < 3) {
                        Noise.emit(level, position(), kind().noise());
                    }
                }
            }
            case FLARE -> {
                if (going > c.flareTicks) {
                    discard();
                    return;
                }
                if (going == 1) {
                    level.playSound(null, getX(), getY(), getZ(), ModSounds.FLARE_STRIKE, SoundSource.PLAYERS, 0.8f, 1f);
                }
                if (going % 40 == 0) {
                    level.playSound(null, getX(), getY(), getZ(), ModSounds.FLARE_HISS, SoundSource.PLAYERS, 0.6f, 1f);
                    Noise.emit(level, position(), kind().noise());
                }
                if (phase != FLYING && going % 20 == 0) {
                    ContainedFire.spot(level, blockPosition());
                    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.4))) {
                        e.setRemainingFireTicks(Math.max(e.getRemainingFireTicks(), 60));
                    }
                }
            }
            case ALARM_CLOCK -> {
                if (phase == RESTING && going < com.zomboidcraft.building.AlarmClockItem.seconds(item()) * 20) {
                    if (going % 20 == 0) {
                        level.playSound(null, getX(), getY(), getZ(), ModSounds.ALARM_TICK, SoundSource.PLAYERS, 0.5f, 1f);
                    }
                } else if (phase == RESTING) {
                    entityData.set(PHASE, RINGING);
                    going = 0;
                } else if (going > c.alarmRingTicks) {
                    becomeItem(level, item());      // wound down: pick it up and wind it again
                } else if (going % 10 == 1) {
                    level.playSound(null, getX(), getY(), getZ(), ModSounds.ALARM_RING, SoundSource.PLAYERS, 1.2f, 1f);
                    if (going % 20 == 1) {
                        Noise.emit(level, position(), kind().noise());
                    }
                }
            }
            case SMOKE -> {
                if (phase == RESTING && going > 20) {
                    entityData.set(PHASE, GOING);
                    going = 0;
                    level.playSound(null, getX(), getY(), getZ(), ModSounds.SMOKE_POP, SoundSource.PLAYERS, 1f, 1f);
                    Noise.emit(level, position(), kind().noise());
                } else if (phase == GOING) {
                    if (going > c.smokeTicks) {
                        discard();
                    } else {
                        Smoke.hide(level, position().add(0, 1, 0), radius());
                    }
                }
            }
            default -> {
            }
        }
    }

    /** The smoke cloud's size now: it billows out over the first few seconds and thins at the end. */
    public double radius() {
        ZcConfig.Throwing c = ZcConfig.get().throwing;
        if (phase() != GOING || kind() != ThrowKind.SMOKE) {
            return 0;
        }
        double grow = Math.min(1, going / 60.0), fade = Math.min(1, (c.smokeTicks - going) / 80.0);
        return c.smokeRadius * Math.max(0, Math.min(grow, fade));
    }

    /** The fuse ran out: the bang. */
    private void detonate(ServerLevel level) {
        ZcConfig.Throwing c = ZcConfig.get().throwing;
        switch (kind()) {
            case FLASHBANG -> flash(level, c);
            case FRAG -> blast(level, c.fragRadius, c.fragDamage, c.fragShrapnel, c.shrapnelDamage);
            default -> {
            }
        }
        discard();
    }

    private void flash(ServerLevel level, ZcConfig.Throwing c) {
        Vec3 at = position().add(0, 0.3, 0);
        level.playSound(null, at.x, at.y, at.z, ModSounds.FLASHBANG, SoundSource.PLAYERS, 2f, 1f);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 30, 0.1, 0.1, 0.1, 0.35);
        level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 12, 0.3, 0.2, 0.3, 0.05);
        Noise.emit(level, at, kind().noise());
        double r = c.flashbangRadius;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r * 2))) {
            double d = e.getEyePosition().distanceTo(at);
            if (!seen(level, at, e) || !hurts(e)) {
                continue;
            }
            if (e instanceof Zombie zombie && d <= r) {
                Downed.stagger(zombie, c.flashbangStun);
            } else if (e instanceof ServerPlayer player && d <= r * 2) {
                float near = (float) Math.max(0.2, 1 - d / (r * 2));
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, Math.round(c.flashbangBlind * near)));
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, Math.round(c.flashbangBlind * near * 0.6f)));
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                        net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.EAR_RING), SoundSource.PLAYERS,
                        player.getX(), player.getEyeY(), player.getZ(), near, 1f, player.getRandom().nextLong()));
            }
        }
    }

    /**
     * A blast: everyone within radius takes damage falling off toward the edge (a quarter of it behind cover), the
     * nearest are thrown down, a frag's shrapnel reaches further, and fires start across the radius. Blocks are not
     * broken (Josiah 2026-10-27): the fires do the damage, if they're left.
     */
    private void blast(ServerLevel level, double radius, double damage, double shrapnel, double shrapnelDamage) {
        Vec3 at = position().add(0, 0.3, 0);
        level.playSound(null, at.x, at.y, at.z, ModSounds.BLAST, SoundSource.PLAYERS, 4f, 0.9f + random.nextFloat() * 0.15f);
        level.playSound(null, at.x, at.y, at.z, ModSounds.BLAST_FAR, SoundSource.PLAYERS, 8f, 1f);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 30, radius / 3, 0.6, radius / 3, 0.04);
        Noise.emit(level, at, kind().noise());
        Entity thrower = owner();
        DamageSource source = damageSources().explosion(this, thrower);
        double reach = Math.max(radius, shrapnel);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(reach))) {
            if (!hurts(e)) {
                continue;
            }
            Vec3 mid = e.position().add(0, e.getBbHeight() / 2, 0);
            double d = mid.distanceTo(at);
            double cover = seen(level, at, e) ? 1 : 0.25;
            double dealt;
            if (d <= radius) {
                dealt = damage * (1 - 0.7 * d / radius) * cover;
                Vec3 push = mid.subtract(at).normalize().scale(0.9 * (1 - d / radius) * cover);
                e.push(push.x, 0.3 * (1 - d / radius) * cover, push.z);
                if (e instanceof Zombie zombie && cover == 1) {
                    // limbs torn off by the blast (the characters thread's Dismember), then thrown down
                    com.zomboidcraft.combat.Dismember.blast(zombie, at, 1 - d / radius, thrower instanceof ServerPlayer p ? p : null);
                    if (d <= radius * 0.6) {
                        Downed.knockDown(zombie);
                    }
                }
            } else if (d <= shrapnel && random.nextDouble() < 0.7 * (1 - (d - radius) / (shrapnel - radius))) {
                dealt = shrapnelDamage * cover;
            } else {
                continue;
            }
            e.hurtServer(level, source, (float) dealt);
        }
        ContainedFire.blast(level, at, radius);
    }

    /** Whether the blast or flash reaches someone: no friendly fire when it's switched off. */
    private boolean hurts(LivingEntity e) {
        if (!(e instanceof Player) || ZcConfig.get().throwing.friendlyFire) {
            return true;
        }
        return e.getUUID().equals(owner);
    }

    private static boolean seen(ServerLevel level, Vec3 from, LivingEntity e) {
        HitResult hit = level.clip(new ClipContext(from, e.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
        return hit.getType() == HitResult.Type.MISS;
    }

    private void becomeItem(ServerLevel level, ItemStack stack) {
        ItemEntity item = new ItemEntity(level, getX(), getY() + 0.1, getZ(), stack.copyWithCount(1), 0, 0, 0);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
        discard();
    }

    private Entity owner() {
        return owner != null && level() instanceof ServerLevel server ? server.getEntity(owner) : null;
    }

    @Override
    protected InterpolationHandler createInterpolationHandler() {
        return LinearInterpolationHandler.create(this, 2);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        entityData.set(ITEM, input.read("item", ItemStack.CODEC).orElse(ItemStack.EMPTY));
        entityData.set(PHASE, input.getIntOr("phase", FLYING));
        fuse = input.getIntOr("fuse", -1);
        going = input.getIntOr("going", 0);
        age = input.getIntOr("age", 0);
        owner = input.read("owner", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        if (!item().isEmpty()) {
            output.store("item", ItemStack.CODEC, item());
        }
        output.putInt("phase", phase());
        output.putInt("fuse", fuse);
        output.putInt("going", going);
        output.putInt("age", age);
        if (owner != null) {
            output.store("owner", net.minecraft.core.UUIDUtil.CODEC, owner);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    public BlockPos lightPos() {
        return BlockPos.containing(position().add(0, 0.5, 0));
    }
}
