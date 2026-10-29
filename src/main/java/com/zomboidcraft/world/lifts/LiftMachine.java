package com.zomboidcraft.world.lifts;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * The controller in a lift's machine: finds the shaft and its landings under it, then runs the
 * car. Calls from the landings' buttons and the car's own panel go in one list; the car serves them
 * the way it's heading before it turns round, stops at a call it passes if it can still slow for
 * it, opens up, waits, and closes again (not while anyone's standing in the doorway). With no calls
 * it stays where it last stopped. The car itself is drawn and ridden through a {@link LiftCar}
 * kept in step with this, made whenever a player is near.
 */
public class LiftMachine extends BlockEntity {
    private static final int CLOSED = 0, OPENING = 1, OPEN = 2, CLOSING = 3;
    /** Speeding up and slowing down, in blocks a tick a tick; the doors' travel a tick; ticks they stay open. */
    private static final double ACCEL = 0.008;
    private static final float DOOR_STEP = 1 / 18f;
    private static final int HOLD = 80;

    private LiftShaft shaft;
    private int retry;
    /** The machines ticking now, for /zc lift list and call. */
    static final java.util.Set<LiftMachine> LOADED = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private boolean scanned;
    /** The car's floor height, last tick's, its speed (blocks a tick, up positive). */
    double y = Double.NaN, yO = Double.NaN, v;
    /** The landing it's stopped at (-1 between floors), where it's heading, which way it's going. */
    int at = -1, target = -1, dir;
    boolean[] calls = new boolean[0];
    int phase = CLOSED;
    float doors;
    private int hold;
    private LiftCar car;
    private String savedCalls = "";

    public LiftMachine(BlockPos pos, BlockState state) {
        super(Lifts.MACHINE_ENTITY, pos, state);
    }

    public LiftShaft shaft() {
        return shaft;
    }

    // ------------------------------------------------------------------ finding the shaft

    /** Reads the shaft under the machine: the landings where its doors are, down to the pit floor. */
    private LiftShaft scan(ServerLevel level) {
        BlockState own = getBlockState();
        if (!own.hasProperty(LiftMachineBlock.FACING)) {
            return null;
        }
        Direction f = own.getValue(LiftMachineBlock.FACING), r = f.getClockWise(), b = f.getOpposite();
        BlockPos m = getBlockPos();
        List<Integer> ys = new ArrayList<>();
        int lobby = -1;
        int bottom = Math.max(level.getMinY() + 1, m.getY() - 420);
        for (int y = m.getY() - 1; y >= bottom; y--) {
            BlockPos cell = new BlockPos(m.getX(), y, m.getZ());
            BlockState door = level.getBlockState(cell.relative(f));
            if (door.is(Lifts.DOOR) && door.getValue(LiftDoorBlock.PART) == LiftDoorBlock.Part.LOWER) {
                ys.add(y);
                if (door.getValue(LiftDoorBlock.LOBBY)) {
                    lobby = ys.size() - 1;
                }
            }
            BlockState in = level.getBlockState(cell);
            if (!in.isAir() && !in.getCollisionShape(level, cell).isEmpty()) {
                break;      // the pit's floor
            }
        }
        if (ys.size() < 2) {
            return null;
        }
        int lowest = ys.get(ys.size() - 1);
        BlockPos first = new BlockPos(m.getX(), lowest, m.getZ()).relative(f);
        int w = 1;
        while (w < 4 && level.getBlockState(first.relative(r, w)).is(Lifts.DOOR)) {
            w++;
        }
        int d = 1;
        BlockPos back = new BlockPos(m.getX(), lowest + 1, m.getZ());
        while (d < 4 && level.getBlockState(back.relative(b, d)).getCollisionShape(level, back.relative(b, d)).isEmpty()) {
            d++;
        }
        int[] landings = new int[ys.size()];
        for (int i = 0; i < landings.length; i++) {
            landings[i] = ys.get(landings.length - 1 - i);
        }
        // no street floor among them: the bottom one is the base-th floor up (a stair core that starts higher up)
        int base = own.getValue(LiftMachineBlock.BASE);
        return new LiftShaft(m.getX(), m.getY(), m.getZ(), f, w, d, landings, lobby < 0 ? -base : landings.length - 1 - lobby);
    }

    // ------------------------------------------------------------------ running

    void tick(ServerLevel level) {
        LOADED.add(this);
        if (!scanned) {
            if (retry-- > 0) {
                return;
            }
            shaft = scan(level);
            if (shaft == null) {
                retry = 200;   // the chunks round it may not all be here yet: look again in a while
                return;
            }
            scanned = true;
            start(level);
        }
        yO = y;
        switch (phase) {
            case OPENING -> {
                doors = Math.min(1, doors + DOOR_STEP);
                if (doors >= 1) {
                    phase = OPEN;
                    hold = HOLD;
                }
            }
            case OPEN -> {
                if (calls[at]) {
                    answer(level, at);
                    hold = HOLD;
                }
                if (doorway(level)) {
                    hold = Math.max(hold, 20);
                }
                if (--hold <= 0) {
                    phase = CLOSING;
                    sound(level, Lifts.DOORS_SOUND, 0.7f, 0.9f);
                }
            }
            case CLOSING -> {
                if (calls[at] || doorway(level)) {
                    answer(level, at);
                    phase = OPENING;
                    sound(level, Lifts.DOORS_SOUND, 0.7f, 1.05f);
                    break;
                }
                doors = Math.max(0, doors - DOOR_STEP);
                if (doors <= 0) {
                    phase = CLOSED;
                    landingDoors(level, at, false);
                }
            }
            default -> {
                if (at >= 0 && calls[at]) {
                    answer(level, at);
                    open(level);
                    break;
                }
                if (target < 0) {
                    target = next();
                }
                if (target >= 0) {
                    move(level);
                }
            }
        }
        follow(level);
    }

    /** Where it was when the world was saved: on with what it was doing, or to the nearest floor below. */
    private void start(ServerLevel level) {
        int n = shaft.landings.length;
        calls = new boolean[n];
        for (int i = 0; i < n && i < savedCalls.length(); i++) {
            calls[i] = savedCalls.charAt(i) == '1';
        }
        int lo = shaft.landings[0], hi = shaft.landings[n - 1];
        if (Double.isNaN(y) || y < lo - 0.01 || y > hi + 0.01) {
            y = lo;
        }
        at = -1;
        for (int i = 0; i < n; i++) {
            landingDoors(level, i, false);
            if (Math.abs(shaft.landings[i] - y) < 1e-3) {
                y = shaft.landings[i];
                at = i;
            }
            lit(level, i, calls[i]);
        }
        if (at < 0 && !any()) {
            // stopped between floors: down to the one under it
            int below = 0;
            for (int i = 0; i < n; i++) {
                if (shaft.landings[i] <= y) {
                    below = i;
                }
            }
            calls[below] = true;
        }
        yO = y;
        v = 0;
        doors = 0;
        phase = CLOSED;
        target = -1;
    }

    private boolean any() {
        for (boolean c : calls) {
            if (c) {
                return true;
            }
        }
        return false;
    }

    /** The next floor to go to: the nearest call the way it's going, else the nearest the other way; -1 for none. */
    private int next() {
        int up = -1, down = -1;
        for (int i = 0; i < calls.length; i++) {
            if (!calls[i]) {
                continue;
            }
            double ly = shaft.landings[i];
            if (ly > y + 1e-3 && (up < 0 || ly < shaft.landings[up])) {
                up = i;
            } else if (ly < y - 1e-3 && (down < 0 || ly > shaft.landings[down])) {
                down = i;
            } else if (Math.abs(ly - y) <= 1e-3) {
                return i;
            }
        }
        if (dir >= 0) {
            return up >= 0 ? up : down;
        }
        return down >= 0 ? down : up;
    }

    private double topSpeed() {
        int span = shaft.landings[shaft.landings.length - 1] - shaft.landings[0];
        return span > 100 ? 0.32 : span > 40 ? 0.24 : 0.16;
    }

    private void move(ServerLevel level) {
        double ty = shaft.landings[target];
        int s = ty > y ? 1 : -1;
        if (at >= 0) {
            at = -1;
            sound(level, Lifts.MOTOR_SOUND, 0.5f, 0.8f);
        }
        dir = s;
        double speed = Math.abs(v);
        // a call it's passing on the way that it can still stop for comes first
        for (int i = 0; i < calls.length; i++) {
            double ly = shaft.landings[i];
            if (calls[i] && i != target && (ly - y) * s > 0 && (ty - ly) * s > 0 && Math.abs(ly - y) >= speed * speed / (2 * ACCEL) + speed) {
                target = i;
                ty = ly;
            }
        }
        double dist = Math.abs(ty - y);
        speed = Math.min(Math.min(speed + ACCEL, topSpeed()), Math.max(0.02, Math.sqrt(2 * ACCEL * dist)));
        if (dist <= speed + 1e-4) {
            y = ty;
            v = 0;
            at = target;
            target = -1;
            answer(level, at);
            sound(level, Lifts.DING_SOUND, 1f, 1f);
            open(level);
        } else {
            y += s * speed;
            v = s * speed;
            if (level.getGameTime() % 30 == 0) {
                sound(level, Lifts.MOTOR_SOUND, 0.35f, 0.8f + (float) Math.abs(v));
            }
        }
        setChanged();
    }

    private void open(ServerLevel level) {
        phase = OPENING;
        landingDoors(level, at, true);
        sound(level, Lifts.DOORS_SOUND, 0.7f, 1.05f);
        // with no other calls, which way it's heading is up for grabs again
        if (!any()) {
            dir = 0;
        }
    }

    /** A call served: off the list and the button out. */
    private void answer(ServerLevel level, int i) {
        if (calls[i]) {
            calls[i] = false;
            setChanged();
        }
        lit(level, i, false);
    }

    /** Anyone (or anything) standing in the doorway at the floor it's open on. */
    private boolean doorway(ServerLevel level) {
        if (at < 0) {
            return false;
        }
        BlockPos a = shaft.door(at, 0), c = shaft.door(at, shaft.w - 1);
        AABB box = new AABB(Math.min(a.getX(), c.getX()), a.getY(), Math.min(a.getZ(), c.getZ()),
                Math.max(a.getX(), c.getX()) + 1, a.getY() + 2, Math.max(a.getZ(), c.getZ()) + 1).deflate(0.02);
        // standing in the doorway itself, not just brushing it from inside the car or the landing
        return !level.getEntities((Entity) null, box, e -> !(e instanceof LiftCar) && !e.isSpectator() && e.isAlive()
                && box.contains(e.getX(), box.minY + 0.5, e.getZ())).isEmpty();
    }

    /** Opens or shuts a landing's doors (the blocks: while they slide the car draws them). */
    private void landingDoors(ServerLevel level, int i, boolean open) {
        if (i < 0) {
            return;
        }
        for (int a = 0; a < shaft.w; a++) {
            BlockPos p = shaft.door(i, a);
            for (int k = 0; k < 2; k++) {
                BlockPos q = p.above(k);
                BlockState s = level.getBlockState(q);
                if (s.is(Lifts.DOOR) && s.getValue(LiftDoorBlock.OPEN) != open) {
                    level.setBlock(q, s.setValue(LiftDoorBlock.OPEN, open), Block.UPDATE_ALL);
                }
            }
        }
    }

    private void lit(ServerLevel level, int i, boolean on) {
        BlockPos p = shaft.button(i);
        BlockState s = level.getBlockState(p);
        if (s.is(Lifts.BUTTON) && s.getValue(LiftButtonBlock.LIT) != on) {
            level.setBlock(p, s.setValue(LiftButtonBlock.LIT, on), Block.UPDATE_CLIENTS);
        }
    }

    private void sound(ServerLevel level, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, shaft.centreX(), y + 1.4, shaft.centreZ(), sound, SoundSource.BLOCKS, volume, pitch);
    }

    // ------------------------------------------------------------------ calls

    /** A landing's button pressed. */
    void hallCall(ServerLevel level, int i) {
        if (shaft == null || i < 0 || i >= calls.length) {
            return;
        }
        if (at == i && phase != OPENING) {
            if (phase == OPEN) {
                hold = HOLD;
            } else if (phase == CLOSING || phase == CLOSED) {
                if (phase == CLOSED) {
                    open(level);
                } else {
                    phase = OPENING;
                    sound(level, Lifts.DOORS_SOUND, 0.7f, 1.05f);
                }
            }
            return;
        }
        if (at == i) {
            return;
        }
        calls[i] = true;
        lit(level, i, true);
        setChanged();
    }

    /** A floor chosen on the car's panel: -1 holds the doors open, -2 shuts them now. */
    void carCall(ServerLevel level, int i) {
        if (shaft == null) {
            return;
        }
        if (i == -1) {
            hallCall(level, at);
            return;
        }
        if (i == -2) {
            if (phase == OPEN && !doorway(level)) {
                hold = 1;
            }
            return;
        }
        if (i < 0 || i >= calls.length) {
            return;
        }
        if (at == i) {
            hallCall(level, i);
            return;
        }
        calls[i] = true;
        // a floor chosen with the doors open: they shut soon, as a real car's do
        if (phase == OPEN) {
            hold = Math.min(hold, 30);
        }
        setChanged();
    }

    // ------------------------------------------------------------------ the car

    /** Keeps a car entity in step while anyone's near enough to see it. */
    private void follow(ServerLevel level) {
        if (car != null && car.isRemoved()) {
            car = null;
        }
        if (car == null) {
            if (level.getGameTime() % 10 != 0 || !playerNear(level, LiftCar.RANGE)) {
                return;
            }
            car = new LiftCar(Lifts.CAR, level);
            car.follow(this);
            level.addFreshEntity(car);
            return;
        }
        car.follow(this);
    }

    boolean playerNear(ServerLevel level, double range) {
        double cx = shaft.centreX(), cz = shaft.centreZ();
        for (ServerPlayer p : level.players()) {
            double dx = p.getX() - cx, dz = p.getZ() - cz;
            if (dx * dx + dz * dz < range * range) {
                return true;
            }
        }
        return false;
    }

    LiftCar car() {
        return car;
    }

    String callsString() {
        StringBuilder s = new StringBuilder(calls.length);
        for (boolean c : calls) {
            s.append(c ? '1' : '0');
        }
        return s.toString();
    }

    int phase() {
        return phase;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED.remove(this);
    }

    /** One line on the lift for /zc lift list: where it is, its floors, where the car is and what it's doing. */
    String describe() {
        BlockPos m = getBlockPos();
        String where = m.getX() + " " + m.getY() + " " + m.getZ();
        if (shaft == null) {
            return "lift at " + where + ": no shaft found yet (retry in " + Math.max(0, retry) + " ticks)";
        }
        return String.format(java.util.Locale.ROOT, "lift at %s: %d floors, car y %.2f at %d, going %d, phase %d, doors %.2f, calls %s, car entity %s",
                where, shaft.landings.length, y, at, dir, phase, doors, callsString(), car != null && !car.isRemoved() ? "yes" : "no");
    }

    /** Calls the car to its top floor (or the bottom one if it's at the top): /zc lift call. */
    void callFar(ServerLevel level) {
        if (shaft != null) {
            hallCall(level, at == shaft.landings.length - 1 ? 0 : shaft.landings.length - 1);
        }
    }

    // ------------------------------------------------------------------ saving

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!Double.isNaN(y)) {
            output.putFloat("car_y", (float) y);
        }
        output.putString("calls", shaft == null ? savedCalls : callsString());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        float saved = input.getFloatOr("car_y", Float.NaN);
        y = Float.isNaN(saved) ? Double.NaN : saved;
        savedCalls = input.getStringOr("calls", "");
    }
}
