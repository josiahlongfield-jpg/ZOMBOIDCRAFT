package com.zomboidcraft.world.trains;

import java.util.ArrayList;
import java.util.List;
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
 * A whole train as one entity, so players near it get it and the client can draw it: placed by its
 * {@link TrainRun} on the server each tick while the chunk it's in is running, and never saved (the
 * run is). The client doesn't wait for position updates: it runs the train along the same track
 * itself from the speed it's sent, nudged toward where the server says it is, so the cars move
 * smoothly and riders move with them in step.
 */
public class TrainEntity extends Entity {
    private static final EntityDataAccessor<String> LINE = SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> INDEX = SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> S = SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> V = SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DOORS = SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> SIDE = SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.INT);
    /** Counts up each time OTTO speaks, so the client can animate him talking. */
    private static final EntityDataAccessor<Integer> TALK = SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.INT);

    private static final List<TrainEntity> CLIENT = new ArrayList<>(), SERVER = new ArrayList<>();

    /** Server: the run this stands for. */
    TrainRun run;
    /** Client: its own running of the train, last tick's, and the doors the same way. */
    public double s = Double.NaN, sO;
    public float doors, doorsO;
    private int lastTalk;
    /** Client: ticks since OTTO last spoke (for his talking animation). */
    public int talking = 1000;

    public TrainEntity(EntityType<? extends TrainEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
    }

    /** The trains loaded on one side (client or server). */
    public static List<TrainEntity> loaded(boolean client) {
        List<TrainEntity> list = client ? CLIENT : SERVER;
        list.removeIf(Entity::isRemoved);
        return list;
    }

    void track() {
        List<TrainEntity> list = level().isClientSide() ? CLIENT : SERVER;
        if (!list.contains(this)) {
            list.add(this);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(LINE, "");
        builder.define(INDEX, 0);
        builder.define(S, 0f);
        builder.define(V, 0f);
        builder.define(DOORS, 0f);
        builder.define(SIDE, 1);
        builder.define(TALK, 0);
    }

    public String lineId() {
        return entityData.get(LINE);
    }

    public int index() {
        return entityData.get(INDEX);
    }

    /** The line this runs, as this side knows it. */
    public Line line() {
        return Trains.lines(level().isClientSide()).get(lineId());
    }

    private double posesAt = Double.NaN;
    private TrainCar.Pose[] poses;
    private Line posesLine;

    /** Every car's pose where the train is now, worked out once per move (the weather asks often). */
    public TrainCar.Pose[] carPoses() {
        Line line = line();
        if (line == null) {
            return null;
        }
        double now = s();
        if (poses == null || posesLine != line || now != posesAt) {
            TrainCar.Pose[] p = new TrainCar.Pose[line.cars];
            for (int car = 0; car < line.cars; car++) {
                p[car] = TrainCar.pose(line.route, now, car);
            }
            poses = p;
            posesLine = line;
            posesAt = now;
        }
        return poses;
    }

    /** Distance along the line of the front car's middle: the server's run, or the client's own. */
    public double s() {
        if (run != null) {
            return run.s;
        }
        return Double.isNaN(s) ? entityData.get(S) : s;
    }

    /** Speed in blocks a tick. */
    public double speed() {
        return run != null ? run.v : entityData.get(V);
    }

    public float doorsOpen() {
        return run != null ? run.doors : doors;
    }

    /** Which side's doors open: +1 the cars' +x side, -1 the other, 0 both. */
    public int doorSide() {
        return entityData.get(SIDE);
    }

    public TrainCar.Pose pose(int car, double sFront) {
        Line line = line();
        return line == null ? null : TrainCar.pose(line.route, sFront, car);
    }

    public int cars() {
        Line line = line();
        return line == null ? 0 : line.cars;
    }

    /** Server: copy the run onto the entity and stand it at the train's middle. */
    void follow(TrainRun run) {
        this.run = run;
        entityData.set(LINE, run.line.id);
        entityData.set(INDEX, run.index);
        entityData.set(S, (float) run.s);
        entityData.set(V, (float) run.v);
        entityData.set(DOORS, run.doors);
        entityData.set(SIDE, run.doorSide);
        TrainCar.Pose mid = TrainCar.pose(run.line.route, run.s, (run.line.cars - 1) / 2);
        setPos(mid.x(), mid.y(), mid.z());
        setYRot(mid.yawDegrees());
        track();
    }

    void talked() {
        entityData.set(TALK, entityData.get(TALK) + 1);
    }

    /**
     * Client, at the start of each tick before anything else moves: runs the train on by the speed
     * it was sent, pulled a little toward where the server has it.
     */
    public void clientAdvance() {
        track();
        Line line = line();
        if (line == null) {
            return;
        }
        Route route = line.route;
        double server = entityData.get(S), speed = entityData.get(V);
        if (Double.isNaN(s)) {
            s = server;
        }
        sO = s;
        double predicted = s + speed;
        double err = route.diff(predicted, server);
        s = route.wrapS(Math.abs(err) > 12 ? server : predicted + err * (speed < 0.01 ? 0.25 : 0.08));
        if (Math.abs(route.diff(sO, s)) > 12) {
            sO = s;     // jumped: don't sweep the cars across the map
        }
        doorsO = doors;
        float want = entityData.get(DOORS);
        doors += Math.max(-0.06f, Math.min(0.06f, want - doors));
        int talk = entityData.get(TALK);
        talking = talk != lastTalk ? 0 : talking + 1;
        lastTalk = talk;
        TrainCar.Pose mid = TrainCar.pose(route, s, (line.cars - 1) / 2);
        setPos(mid.x(), mid.y(), mid.z());
        setYRot(mid.yawDegrees());
    }

    /** The client's front-car position between ticks. */
    public double sAt(float partialTick) {
        Line line = line();
        if (line == null || Double.isNaN(s)) {
            return s();
        }
        return line.route.wrapS(sO + line.route.diff(sO, s) * partialTick);
    }

    @Override
    public void tick() {
        super.tick();
        track();
        if (level() instanceof ServerLevel && (run == null || !Trains.running(run))) {
            discard();
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
        return distance < 256 * 256;
    }

    @Override
    public void move(net.minecraft.world.entity.MoverType type, Vec3 delta) {
        // placed, never pushed
    }
}
