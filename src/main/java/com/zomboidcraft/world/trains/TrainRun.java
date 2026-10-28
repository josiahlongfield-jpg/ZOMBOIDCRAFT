package com.zomboidcraft.world.trains;

import com.zomboidcraft.ZcConfig;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

/**
 * One train running its line, on the server: where its front car is, how fast it goes, where it's
 * headed and its doors. It runs its own route, stopping at every stop; a pressed call button sends
 * it straight to that stop, past all the others (the nearest call ahead first). Speed changes
 * smoothly both ways: the push or pull itself builds up and eases off, never a jolt.
 */
public final class TrainRun {
    public enum Phase { RUN, DWELL }

    public final Line line;
    public final int index;
    /** Distance along the line of the front car's middle, and speed in blocks a tick, and its change. */
    public double s, v, a;
    public Phase phase = Phase.RUN;
    /** Ticks left standing at a stop. */
    public int dwell;
    /** How open the doors are, 0-1, and on which side (+1, -1, or 0 for both). */
    public float doors;
    public int doorSide = 1;
    /** The stop it's headed for (-1 none), whether that's an express run to a call, the stop it's standing at. */
    public int target = -1, atStop = -1;
    public boolean express;
    public UUID entity;
    /** OTTO's bookkeeping: ticks since he last said anything, the last crossing he honked for, the stop he announced. */
    int quiet;
    double honkedAt = -1;
    int announced = -1;
    /** Who's aboard as he last counted, and how long till he'll react again to riders, to zombies about, to being shot at. */
    final java.util.Set<UUID> aboard = new java.util.HashSet<>();
    int riderCooldown, zombieCooldown, shotCooldown, shots;
    /** A stop button's been pressed: pull up at haltAt (wherever that is) and open both sides. */
    boolean halting;
    double haltAt;
    /** How long this stand at a stop (or a halt) is, all told. */
    int dwellTotal;
    /** Zombies riding while nobody's near enough to see them (they're made real as players come close). */
    public int passengers;
    /** Seconds the doors stay open after a stop button halt. */
    static final int HALT_SECONDS = 12;

    public TrainRun(Line line, int index, double s) {
        this.line = line;
        this.index = index;
        this.s = line.route.wrapS(s);
    }

    public double speedPerSecond() {
        return v * 20;
    }

    /** The train's length from the front car's middle back to the last car's tail. */
    public double tail() {
        return (line.cars - 1) * TrainCar.PITCH + TrainCar.HALF;
    }

    public TrainCar.Pose pose(int car) {
        return TrainCar.pose(line.route, s, car);
    }

    void tick(ServerLevel level, ZcConfig.Trains cfg) {
        Otto.watch(level, this);
        Route route = line.route;
        if (phase == Phase.DWELL) {
            dwell(level, cfg);
            return;
        }
        double cruise = (line.kind == Line.Kind.SUBWAY ? cfg.subwaySpeed : cfg.railSpeed) / 20;
        double accel = Math.max(0.05, cfg.acceleration) / 400, brake = Math.max(0.05, cfg.braking) / 400;
        double plan = brake * 0.8;   // plan stops on less than full braking, so there's always some in hand
        int was = target;
        pickTarget();
        if (target >= 0 && target != was && express) {
            Otto.express(level, this, line.stops.get(target));
        }
        if (express) {
            cruise = Math.max(cruise, Math.min(cruise * cfg.expressSpeed, 2.0));    // called: flat out to whoever rang, 40 b/s at most
        }
        double dist = target < 0 ? Double.MAX_VALUE : distTo(target);
        // a stop button: pull up short, braking harder than for a station
        boolean haltFirst = false;
        if (halting) {
            double hd = route.ahead(s, haltAt);
            if (hd > route.length() - 8) {
                hd = 0;
            }
            if (hd < dist) {
                dist = hd;
                haltFirst = true;
                plan = brake * 1.6;
                brake *= 2;
            }
        }
        double want = cruise;
        if (target >= 0 || haltFirst) {
            want = Math.min(want, Math.sqrt(2 * plan * Math.max(0, dist - 0.02)));
            if (dist > 0.03) {
                want = Math.max(want, Math.min(0.02, dist * 0.25));   // creep the last bit rather than stall short
            }
        }
        // keep well back from the train ahead on the same line
        for (TrainRun other : line.trains) {
            if (other != this) {
                double gap = route.ahead(s + TrainCar.HALF, other.s - other.tail());
                if (gap < route.length() / 2) {
                    want = Math.min(want, Math.sqrt(2 * plan * Math.max(0, gap - 30)));
                }
            }
        }
        // curves: the slowest bend under the train now, and any coming up within braking distance
        double grip = route.slowest(s - tail(), tail() + TrainCar.HALF) / 20;
        double reach = v * v / (2 * plan) + 24;
        for (double d = TrainCar.HALF; d < reach; d += 4) {
            double lim = route.limit(s + d) / 20;
            grip = Math.min(grip, Math.sqrt(lim * lim + 2 * plan * Math.max(0, d - TrainCar.HALF)));
        }
        want = Math.min(want, Math.max(grip, 0.08));
        double aWant = Mth.clamp((want - v) * 0.12, -brake, accel);
        // the push builds and eases over about a second and a half; harder when it must stop short
        double jerk = accel / 30;
        if (aWant < a && v > Math.sqrt(2 * brake * Math.max(0, dist)) * 0.95) {
            jerk *= 4;
        }
        a += Mth.clamp(aWant - a, -jerk * (aWant < a ? 2 : 1), jerk);
        v = Math.max(0, v + a);
        if (v == 0 && a < 0) {
            a = 0;
        }
        if (haltFirst && (dist <= v + 0.01 && v < 0.08 || dist < 0.03)) {
            s = route.wrapS(haltAt);
            halt(level);
            return;
        }
        if (target >= 0 && (dist <= v + 0.01 && v < 0.08 || dist < 0.03)) {
            s = line.frontAt(line.stops.get(target));
            arrive(level, cfg);
            return;
        }
        s = route.wrapS(s + v);
        Otto.running(level, this, cfg);
    }

    /**
     * How far ahead stop i is. The stop it's pulling in to stays its stop right to the end, even a
     * hair past it, so it never gives up on a stop in the last block and runs on.
     */
    private double distTo(int i) {
        double d = line.route.ahead(s, line.frontAt(line.stops.get(i)));
        return i == target && d > line.route.length() - 8 ? 0 : d;
    }

    /** The stop to head for: the nearest call ahead if anyone's called, otherwise the next stop. */
    private void pickTarget() {
        int best = -1;
        double bestD = Double.MAX_VALUE;
        boolean calls = !line.calls.isEmpty();
        for (int i = 0; i < line.stops.size(); i++) {
            if (calls && !line.calls.contains(i)) {
                continue;
            }
            double d = distTo(i);
            // a stop it's only just left (or is standing at) isn't one to head for; the one it's coming in to is
            if ((d > 0.5 || i == target) && d < bestD) {
                bestD = d;
                best = i;
            }
        }
        target = best;
        express = calls && best >= 0;
    }

    private void arrive(ServerLevel level, ZcConfig.Trains cfg) {
        phase = Phase.DWELL;
        v = 0;
        a = 0;
        atStop = target;
        Line.Stop stop = line.stops.get(atStop);
        doorSide = stop.side();
        halting = false;
        dwell = dwellTotal = cfg.doorSeconds * 20 + DOOR_OPEN + DOOR_CLOSE + LEAVE;
        boolean called = line.calls.remove(Integer.valueOf(atStop));
        Zombies.stopped(level, this);
        Beacons.answered(line, atStop);
        Otto.arrived(level, this, stop, called);
        TrainSounds.chime(level, this);
    }

    static final int DOOR_OPEN = 20, DOOR_CLOSE = 30, LEAVE = 30;

    /**
     * Somebody aboard pressed a stop button. On the move: pull up as soon as it can (hard braking)
     * and open both sides for a bit. Standing with the doors closing: open them back up a while.
     */
    void requestStop(ServerLevel level, net.minecraft.server.level.ServerPlayer by) {
        if (phase == Phase.DWELL) {
            if (dwell < DOOR_CLOSE + LEAVE + 100) {
                dwell = DOOR_CLOSE + LEAVE + 100;
                TrainSounds.doors(level, this, true);
                Otto.doorsHeld(level, this, by);
            }
            return;
        }
        if (halting) {
            return;
        }
        ZcConfig.Trains cfg = ZcConfig.get().trains;
        double plan = Math.max(0.05, cfg.braking) / 400 * 1.6;
        halting = true;
        haltAt = line.route.wrapS(s + Math.max(1.5, v * v / (2 * plan) + v * 10));
        Otto.stopRequested(level, this, by);
    }

    /** Pulled up for a stop button, between stations: both sides open. */
    private void halt(ServerLevel level) {
        phase = Phase.DWELL;
        v = 0;
        a = 0;
        halting = false;
        atStop = -1;
        doorSide = 0;
        dwell = dwellTotal = HALT_SECONDS * 20 + DOOR_OPEN + DOOR_CLOSE + LEAVE;
        Otto.halted(level, this);
        TrainSounds.chime(level, this);
    }

    private void dwell(ServerLevel level, ZcConfig.Trains cfg) {
        // somebody's calling from up the line: cut the stop short
        if (!line.calls.isEmpty() && !line.calls.contains(atStop) && dwell > DOOR_CLOSE + LEAVE + 100) {
            dwell = DOOR_CLOSE + LEAVE + 100;
            Otto.cutShort(level, this);
        }
        int elapsed = dwellTotal - dwell;
        if (elapsed == 2) {
            TrainSounds.doors(level, this, true);
        }
        if (elapsed < DOOR_OPEN) {
            doors = Math.min(1, doors + 1f / DOOR_OPEN);
        } else if (dwell > DOOR_CLOSE + LEAVE) {
            doors = Math.min(1, doors + 1f / DOOR_OPEN);    // (opens back up if a button held them)
            if (dwell == DOOR_CLOSE + LEAVE + 70) {
                Otto.closing(level, this);
                TrainSounds.chime(level, this);
            }
        } else if (dwell > LEAVE) {
            if (dwell == DOOR_CLOSE + LEAVE) {
                TrainSounds.doors(level, this, false);
            }
            doors = Math.max(0, doors - 1f / DOOR_CLOSE);
        } else {
            doors = 0;
            if (dwell == 12) {
                TrainSounds.horn(level, this, 1);
            }
        }
        if (--dwell <= 0) {
            phase = Phase.RUN;
            doors = 0;
            atStop = -1;
            target = -1;
            Otto.departed(level, this);
        }
    }
}
