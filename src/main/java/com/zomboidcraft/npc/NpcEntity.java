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
 * <p>
 * Her mind (NpcMind) gives her needs, feelings, traits and a diary: hungry, tired, hurt or frightened she keeps up slower,
 * aims shakier and runs sooner, goes for food and drink she sees lying about, and says how she's doing now and then.
 * Her skills (NpcSkills) grow with practice; given a home and a job (NpcWork) she keeps the place going on her own,
 * by the clock, and sleeps when her day's done. How she feels about you (NpcBond) grows or sours with how you treat her.
 * A skinned companion dresses herself (NpcWardrobe), and asks what you think.
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
    /** What her body's doing (NpcWork.SLEEP, SIT, KNEEL...), and her fear, morale, tiredness and pain (4 bits each), for her poses. */
    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MOOD = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    /** How she feels about the player she's with (NpcBond.packed): trust, affection, attraction and the moods they bring. */
    private static final EntityDataAccessor<Integer> BOND = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    /** How long she lies where she fell before she's gone (her death clip runs about six seconds). */
    private static final int DEATH_TICKS = 20 * 8;

    /** Client only: how far her gun is up (0..1) and a shot's kick fading, this tick and last, for the animator. */
    public float aimAnim, aimAnimO, kick, kickO;
    private int shotsSeen;
    /** Client only: the skinned animator's state for her (client NpcSkinAnimator), kept with her. */
    public Object clientAnim;

    final NpcLines lines = new NpcLines();
    public final NpcMind mind = new NpcMind();
    public final NpcSkills skills = new NpcSkills();
    public final NpcWardrobe wardrobe = new NpcWardrobe();
    public final NpcWork work = new NpcWork();
    public final NpcBond bond = new NpcBond();
    private boolean mindLoaded;

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
        goalSelector.addGoal(3, new NpcWork.Sleep(this));
        goalSelector.addGoal(4, new NpcBrain.TakeGun(this));
        goalSelector.addGoal(5, new NpcBrain.Forage(this));
        goalSelector.addGoal(6, new NpcWork.Work(this));
        goalSelector.addGoal(7, new NpcWork.Evening(this));
        goalSelector.addGoal(8, new NpcBrain.Follow(this));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 10f));
        goalSelector.addGoal(10, new RandomLookAroundGoal(this));
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
        builder.define(ACTION, 0);
        builder.define(MOOD, 0);
        builder.define(BOND, 0);
    }

    /** The clothes she has off. */
    public java.util.Set<String> hidden() {
        String h = entityData.get(HIDDEN);
        return h.isEmpty() ? java.util.Set.of() : java.util.Set.of(h.split(","));
    }

    void setHidden(java.util.Set<String> pieces) {
        entityData.set(HIDDEN, String.join(",", new java.util.TreeSet<>(pieces)));
    }

    /** What her body's doing (NpcWork's SLEEP, SIT, KNEEL...). */
    public int action() {
        return entityData.get(ACTION);
    }

    /** Her fear, morale, tiredness and pain, each 0..15, for her posture on the client. */
    public int moodBits() {
        return entityData.get(MOOD);
    }

    /** How she feels about the player she's with, packed (NpcBond.packed). */
    public int bondBits() {
        return entityData.get(BOND);
    }

    /** Her mood in a word or two, with how she feels about you in it. */
    public String mood() {
        return bond.mood(this, level().getGameTime());
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

    /** A new companion: her traits rolled, and meeting whoever spawned her as her first memory. */
    public void born(Player by) {
        mind.roll(getRandom());
        skills.roll(getRandom());
        mindLoaded = true;
        bond.roll(getRandom(), mind);
        bond.meet(by);
        wardrobe.roll(getRandom());
        if (Npcs.dresses(model())) {
            wardrobe.dress(this, NpcWardrobe.Outfit.WORK);
        }
        mind.remember(this, "Met " + by.getName().getString(), 0.3f, 0.9f);
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
        work.asleep = false;
        if (!following && work.home != null && work.job != NpcWork.Job.NONE) {
            lines.sayLine(this, NpcLines.Kind.WAIT, NpcWork.pick(this, "Back to work, then.", "I'll get on with it.", "Right. Home it is."));
        } else {
            say(following ? NpcLines.Kind.FOLLOW : NpcLines.Kind.WAIT);
        }
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
        if (level() instanceof ServerLevel server && isAlive() && tickCount % 20 == 0) {
            if (!mindLoaded) {
                // an older companion, from before she had a mind
                mind.roll(getRandom());
                skills.roll(getRandom());
                bond.roll(getRandom(), mind);
                wardrobe.roll(getRandom());
                mindLoaded = true;
            }
            boolean wasLonely = mind.get(NpcMind.Need.LONELY) > 50;
            mind.tick(this, server);
            if (!work.asleep) {
                speakMind(wasLonely);
            }
            work.watch(this, server);
            bond.tick(this, server);
            wardrobe.tick(this, server);
            entityData.set(BOND, bond.packed(this, server.getGameTime()) | wardrobe.bits(server.getGameTime()));
            int q = 0, k = 0;
            for (NpcMind.Need n : new NpcMind.Need[] {NpcMind.Need.FEAR, NpcMind.Need.MORALE, NpcMind.Need.TIRED, NpcMind.Need.PAIN}) {
                q |= Math.min(15, Math.round(mind.get(n) * 15f / 100f)) << (4 * k++);
            }
            entityData.set(MOOD, q);
        }
        if (level() instanceof ServerLevel server && bond.poseTicks > 0) {
            // a hug or a kiss: she stays with you, facing you, until it's done
            bond.poseTicks--;
            Player p = bond.player(server);
            if (p != null && isAlive()) {
                getLookControl().setLookAt(p, 30f, 30f);
                if (p.distanceTo(this) > 0.9) getNavigation().moveTo(p, 1.0);
                else getNavigation().stop();
            }
        }
        if (!level().isClientSide()) {
            wardrobe.changing(this);
            entityData.set(ACTION, isAlive() ? (work.asleep ? NpcWork.SLEEP : bond.poseTicks > 0 ? bond.pose
                    : wardrobe.changeTicks > 0 ? NpcWork.KNEEL : work.action) : 0);
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
            // pain wakes her
            work.asleep = false;
            if (source.getEntity() instanceof Player p && bond.about(p)) {
                bond.hurtBy(this);
            }
            say(NpcLines.Kind.HURT);
            mind.jolt(25f);
            if (amount >= 6f || getHealth() < getMaxHealth() * 0.35f) {
                mind.badlyHurt(this);
            }
        }
        return hurt;
    }

    /**
     * Now and then she says how she's doing: the loudest feeling first, a panic straight away, the rest only by chance
     * (more often if she's talkative), each kind with its own long wait (NpcLines).
     */
    private void speakMind(boolean wasLonely) {
        Player leader = leader();
        boolean close = leader != null && leader.distanceTo(this) < 6;
        if (wasLonely && close) {
            say(NpcLines.Kind.MISSED);
            return;
        }
        if (mind.panicking()) {
            say(NpcLines.Kind.PANIC);
            return;
        }
        if (getRandom().nextFloat() > 0.06f * (1f + 0.7f * mind.trait(NpcMind.Trait.TALKATIVE))) {
            return;
        }
        NpcMind.Need loudest = null;
        float most = 0;
        for (NpcMind.Need n : NpcMind.Need.values()) {
            float v = n == NpcMind.Need.MORALE ? 100f - mind.get(n) : mind.get(n);
            if (v > most) {
                most = v;
                loudest = n;
            }
        }
        NpcLines.Kind kind = null;
        if (loudest != null && most > 60) {
            kind = switch (loudest) {
                case HUNGER -> NpcLines.Kind.HUNGRY;
                case THIRST -> NpcLines.Kind.THIRSTY;
                case TIRED -> NpcLines.Kind.TIRED;
                case PAIN -> NpcLines.Kind.PAIN;
                case FEAR -> NpcLines.Kind.SCARED;
                case ADRENALINE -> NpcLines.Kind.WIRED;
                case LONELY -> close ? null : NpcLines.Kind.LONELY;
                case MORALE -> mind.get(NpcMind.Need.MORALE) < 30 ? NpcLines.Kind.LOW : null;
            };
        } else if (mind.get(NpcMind.Need.MORALE) > 78 && close) {
            kind = NpcLines.Kind.GOOD;
        }
        if (kind != null) {
            say(kind);
        }
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
        // and whatever she was carrying
        if (level() instanceof ServerLevel server) {
            for (net.minecraft.world.item.ItemStack s : work.pack.removeAllItems()) {
                spawnAtLocation(server, s);
            }
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
        output.putString("npcMind", mind.save());
        output.putString("npcSkills", skills.save());
        output.putString("npcBond", bond.save());
        output.putString("npcWardrobe", wardrobe.save());
        output.putString("npcJob", work.job.name());
        output.putInt("npcNightShift", work.nightShift ? 1 : 0);
        output.putString("npcSearched", work.saveSearched());
        if (work.home != null) {
            output.putString("npcHome", work.home.getX() + " " + work.home.getY() + " " + work.home.getZ());
        }
        net.minecraft.world.ContainerHelper.saveAllItems(output, work.pack.getItems());
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
        mindLoaded = mind.load(input.getStringOr("npcMind", ""));
        if (!skills.load(input.getStringOr("npcSkills", "")) && mindLoaded) {
            skills.roll(getRandom());
        }
        if (!bond.load(input.getStringOr("npcBond", "")) && mindLoaded) {
            bond.roll(getRandom(), mind);
        }
        if (wardrobe.load(input.getStringOr("npcWardrobe", ""))) {
            if (Npcs.dresses(model())) {
                entityData.set(HIDDEN, String.join(",", wardrobe.hidden()));
            }
        } else if (mindLoaded) {
            wardrobe.roll(getRandom());
        }
        NpcWork.Job job = NpcWork.Job.of(input.getStringOr("npcJob", "NONE"));
        work.job = job == null ? NpcWork.Job.NONE : job;
        work.nightShift = input.getIntOr("npcNightShift", 0) == 1;
        work.loadSearched(input.getStringOr("npcSearched", ""));
        String[] home = input.getStringOr("npcHome", "").split(" ");
        try {
            work.home = home.length == 3 ? new net.minecraft.core.BlockPos(Integer.parseInt(home[0]), Integer.parseInt(home[1]), Integer.parseInt(home[2])) : null;
        } catch (NumberFormatException e) {
            work.home = null;
        }
        net.minecraft.world.ContainerHelper.loadAllItems(input, work.pack.getItems());
        String id = input.getStringOr("npcLeader", "");
        try {
            leader = id.isEmpty() ? null : UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            leader = null;
        }
    }
}
