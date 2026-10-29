package com.zomboidcraft.npc;

import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A character drawn from a bought mesh (NpcRenderer, assets/zomboidcraft/npc/MODEL.znpc). Which model and which look
 * ("raw", as bought, or "toon", painted like the guns) are synced and saved.
 * <p>
 * Her brain (NpcBrain) makes her a companion: she follows the player who last spoke to her (the interact key toggles
 * follow and wait), keeps clear of zombies and runs from any that get close, picks up a gun she sees lying about and
 * fights zombies with it alongside you. Her gun never runs dry, but she stops to reload each magazine.
 */
public class NpcEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> MODEL = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> LOOK = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    /** Holding her gun up to shoot, and how many shots she's fired (each one kicks her shoulders back on the client). */
    private static final EntityDataAccessor<Boolean> AIMING = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> SHOTS = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    /** Clothes she has off (piece names, comma separated: NpcPieces), and ticks left of what she's saying. */
    private static final EntityDataAccessor<String> HIDDEN = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> TALK = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    /** How long she lies where she fell before she's gone (her death clip runs about six seconds). */
    private static final int DEATH_TICKS = 20 * 8;

    /** Client only: how far her gun is up (0..1) and a shot's kick fading, this tick and last, for the animator. */
    public float aimAnim, aimAnimO, kick, kickO;
    private int shotsSeen;
    /** Client only: the skinned animator's state for her (client NpcSkinAnimator), kept with her. */
    public Object clientAnim;

    final NpcLines lines = new NpcLines();

    boolean following = true;
    UUID leader;
    /** Rounds left in her magazine; she reloads when it's out (reloadLeft ticks). */
    int rounds = -1, reloadLeft;

    public NpcEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 48);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new NpcBrain.Flee(this));
        goalSelector.addGoal(2, new NpcBrain.Shoot(this));
        goalSelector.addGoal(3, new NpcBrain.TakeGun(this));
        goalSelector.addGoal(4, new NpcBrain.Follow(this));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 10f));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MODEL, Npcs.MODELS.get(0));
        builder.define(LOOK, "raw");
        builder.define(AIMING, false);
        builder.define(SHOTS, 0);
        builder.define(HIDDEN, "");
        builder.define(TALK, 0);
    }

    /** The clothes she has off. */
    public java.util.Set<String> hidden() {
        String h = entityData.get(HIDDEN);
        return h.isEmpty() ? java.util.Set.of() : java.util.Set.of(h.split(","));
    }

    void setHidden(java.util.Set<String> pieces) {
        entityData.set(HIDDEN, String.join(",", new java.util.TreeSet<>(pieces)));
    }

    /** Ticks left of the line she's saying (her mouth moves while it's above 0). */
    public int talking() {
        return entityData.get(TALK);
    }

    void talk(int ticks) {
        entityData.set(TALK, ticks);
    }

    /** Says a line of this kind, unless she's said one too lately. */
    void say(NpcLines.Kind kind) {
        lines.say(this, kind);
    }

    /** The name her lines go out under: her name tag's, or "Companion". */
    String speakerName() {
        return hasCustomName() ? getCustomName().getString() : "Companion";
    }

    public String model() {
        return entityData.get(MODEL);
    }

    public String look() {
        return entityData.get(LOOK);
    }

    public void setModel(String model, String look) {
        entityData.set(MODEL, model);
        entityData.set(LOOK, look);
    }

    public boolean aiming() {
        return entityData.get(AIMING);
    }

    void setAiming(boolean aiming) {
        entityData.set(AIMING, aiming);
    }

    public int shots() {
        return entityData.get(SHOTS);
    }

    void shot() {
        entityData.set(SHOTS, shots() + 1);
    }

    /** The interact key on her: follow the one who pressed it, or wait here. Returns whether she now follows. */
    public boolean toggleFollow(Player by) {
        if (following && by.getUUID().equals(leader)) {
            following = false;
            getNavigation().stop();
        } else {
            following = true;
            leader = by.getUUID();
        }
        say(following ? NpcLines.Kind.FOLLOW : NpcLines.Kind.WAIT);
        return following;
    }

    /** Who she follows: the player who last asked, if they're about, else the nearest one. */
    Player leader() {
        if (leader != null && level() instanceof ServerLevel server) {
            Player p = server.getPlayerByUUID(leader);
            if (p != null && p.isAlive() && !p.isSpectator() && p.distanceToSqr(this) < 64 * 64) {
                return p;
            }
        }
        Player near = level().getNearestPlayer(this, 48);
        return near != null && !near.isSpectator() ? near : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && talking() > 0) {
            talk(talking() - 1);
        }
        if (level().isClientSide()) {
            aimAnimO = aimAnim;
            kickO = kick;
            aimAnim += ((aiming() ? 1f : 0f) - aimAnim) * 0.35f;
            int shots = shots();
            kick = shots != shotsSeen ? 1f : kick * 0.55f;
            shotsSeen = shots;
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive()) {
            say(NpcLines.Kind.HURT);
        }
        return hurt;
    }

    /** She lies where she fell while her death plays out, then she's gone. */
    @Override
    protected void tickDeath() {
        deathTime = Math.min(deathTime + 1, DEATH_TICKS + 1);
        if (deathTime >= DEATH_TICKS && !level().isClientSide() && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    /** Her gun drops where she falls. */
    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        net.minecraft.world.item.ItemStack gun = getMainHandItem();
        if (!gun.isEmpty() && level() instanceof ServerLevel server) {
            spawnAtLocation(server, gun.copy());
            setItemSlot(EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY);
        }
        super.die(source);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("npcModel", model());
        output.putString("npcLook", look());
        output.putInt("npcFollowing", following ? 1 : 0);
        output.putString("npcHidden", entityData.get(HIDDEN));
        if (leader != null) {
            output.putString("npcLeader", leader.toString());
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setModel(input.getStringOr("npcModel", Npcs.MODELS.get(0)), input.getStringOr("npcLook", "raw"));
        following = input.getIntOr("npcFollowing", 1) == 1;
        entityData.set(HIDDEN, input.getStringOr("npcHidden", ""));
        String id = input.getStringOr("npcLeader", "");
        try {
            leader = id.isEmpty() ? null : UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            leader = null;
        }
    }
}
