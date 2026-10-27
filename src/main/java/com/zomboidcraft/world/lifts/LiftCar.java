package com.zomboidcraft.world.lifts;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A lift's car as an entity, so players near it get it and the client can draw it (the car, its
 * doors and the floor indicators on every landing) and ride it. The server's {@link LiftMachine}
 * places it each tick and it's never saved (the machine is). The client runs the car on by the
 * speed it's sent, nudged toward where the server has it, so riders go up and down in step.
 */
public class LiftCar extends Entity {
    /** How near a player has to be (across) for the car to be about. */
    public static final double RANGE = 160;

    private static final EntityDataAccessor<String> GEO = SynchedEntityData.defineId(LiftCar.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> LANDINGS = SynchedEntityData.defineId(LiftCar.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> CALLS = SynchedEntityData.defineId(LiftCar.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> Y = SynchedEntityData.defineId(LiftCar.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> V = SynchedEntityData.defineId(LiftCar.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DOORS = SynchedEntityData.defineId(LiftCar.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> AT = SynchedEntityData.defineId(LiftCar.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DIR = SynchedEntityData.defineId(LiftCar.class, EntityDataSerializers.INT);

    private static final List<LiftCar> CLIENT = new ArrayList<>(), SERVER = new ArrayList<>();

    /** Server: the machine that runs it. */
    LiftMachine machine;
    private LiftShaft shaft;
    private String shaftGeo = "", shaftLandings = "";
    /** Client: its own running of the car (floor height), last tick's, and the doors the same way. */
    public double y = Double.NaN, yO = Double.NaN;
    public float doors, doorsO;

    public LiftCar(EntityType<? extends LiftCar> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
    }

    /** The cars loaded on one side. */
    public static List<LiftCar> loaded(boolean client) {
        List<LiftCar> list = client ? CLIENT : SERVER;
        list.removeIf(Entity::isRemoved);
        return list;
    }

    private void track() {
        List<LiftCar> list = level().isClientSide() ? CLIENT : SERVER;
        if (!list.contains(this)) {
            list.add(this);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(GEO, "");
        builder.define(LANDINGS, "");
        builder.define(CALLS, "");
        builder.define(Y, 0f);
        builder.define(V, 0f);
        builder.define(DOORS, 0f);
        builder.define(AT, -1);
        builder.define(DIR, 0);
    }

    /** Its shaft, as this side knows it (null until the client's been sent it). */
    public LiftShaft shaft() {
        if (machine != null) {
            return machine.shaft();
        }
        String geo = entityData.get(GEO), landings = entityData.get(LANDINGS);
        if (shaft == null || !geo.equals(shaftGeo) || !landings.equals(shaftLandings)) {
            shaftGeo = geo;
            shaftLandings = landings;
            shaft = LiftShaft.parse(geo, landings);
        }
        return shaft;
    }

    /** The car's floor height now. */
    public double carY() {
        if (machine != null) {
            return machine.y;
        }
        return Double.isNaN(y) ? entityData.get(Y) : y;
    }

    /** The car's floor height a tick ago, so riders can be carried by what it moved since. */
    public double carYO() {
        if (machine != null) {
            return Double.isNaN(machine.yO) ? machine.y : machine.yO;
        }
        return Double.isNaN(yO) ? carY() : yO;
    }

    public double speed() {
        return machine != null ? machine.v : entityData.get(V);
    }

    public float doorsOpen() {
        return machine != null ? machine.doors : doors;
    }

    /** The landing it's stopped at, or -1. */
    public int at() {
        return entityData.get(AT);
    }

    public int dir() {
        return entityData.get(DIR);
    }

    /** Whether a landing's been called (its button's lit in the car). */
    public boolean called(int i) {
        String c = entityData.get(CALLS);
        return i >= 0 && i < c.length() && c.charAt(i) == '1';
    }

    /** Server: copy the machine onto the entity and stand it in the middle of the car's floor. */
    void follow(LiftMachine m) {
        machine = m;
        LiftShaft s = m.shaft();
        entityData.set(GEO, s.geo());
        entityData.set(LANDINGS, s.landingsString());
        entityData.set(CALLS, m.callsString());
        entityData.set(Y, (float) m.y);
        entityData.set(V, (float) m.v);
        entityData.set(DOORS, m.doors);
        entityData.set(AT, m.at);
        entityData.set(DIR, m.dir);
        setPos(s.centreX(), m.y, s.centreZ());
        track();
    }

    /** Client, at the start of each tick before anything moves: runs the car on by its speed, pulled toward the server's. */
    public void clientAdvance() {
        track();
        double server = entityData.get(Y), speed = entityData.get(V);
        if (Double.isNaN(y)) {
            y = server;
        }
        yO = y;
        double predicted = y + speed;
        double err = server - predicted;
        y = Math.abs(err) > 3 ? server : predicted + err * (Math.abs(speed) < 1e-4 ? 0.35 : 0.12);
        if (Math.abs(y - yO) > 3) {
            yO = y;
        }
        doorsO = doors;
        float want = entityData.get(DOORS);
        doors += Math.max(-0.07f, Math.min(0.07f, want - doors));
        LiftShaft s = shaft();
        if (s != null) {
            setPos(s.centreX(), y, s.centreZ());
        }
    }

    /** The client's car floor between ticks. */
    public double yAt(float partialTick) {
        if (Double.isNaN(y)) {
            return carY();
        }
        return yO + (y - yO) * partialTick;
    }

    @Override
    public void tick() {
        super.tick();
        track();
        if (level() instanceof ServerLevel level) {
            BlockPos at = machine == null ? null : machine.getBlockPos();
            if (machine == null || machine.isRemoved() || machine.car() != this || level.getBlockEntity(at) != machine
                    || !machine.playerNear(level, RANGE + 32)) {
                discard();
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
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
    public boolean canBeCollidedWith(Entity other) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 320 * 320;
    }

    @Override
    public void move(net.minecraft.world.entity.MoverType type, Vec3 delta) {
        // placed, never pushed
    }
}
