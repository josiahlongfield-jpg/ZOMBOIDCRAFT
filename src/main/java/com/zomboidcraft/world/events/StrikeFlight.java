package com.zomboidcraft.world.events;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * How a jet flies its air strike (Josiah 2026-10-28 20:19: smooth and natural, in a little higher on a mild dive,
 * nose up to just over the rooftops to strafe or bomb, then pull up and break away banking left or right).
 * <p>
 * A pilot flies it, not a drawn line: the jet is flown through its load factor and bank angle, both eased the way
 * a fly-by-wire jet eases the stick (no step anywhere, so its nose, wings and control surfaces move smoothly), and
 * it goes where the lift and gravity take it. Its run is planned in its own frame (u along the street past the
 * target, w to the right, height over the target), flown once from a little way out, then slid along the street so
 * the guns or the bombs land where they should; the level flight before it is the same anywhere.
 * <ul>
 * <li>Strafe: in level, pushed over into a dive of fourteen degrees or so, the cannon opens up with the nose on the
 * near end of the line through the crowd, and the pilot eases the nose up so the shells walk straight along it to
 * the far end (the nose rising is what walks them); then a hard pull up just over the roofs.</li>
 * <li>Bomb: in level, a gentle dive of eight or nine degrees, rounded out to level over the roofs, the stick let go
 * from there; then the pull up.</li>
 * <li>The break: wings level until the nose is well up, then rolled hard into a climbing turn one way or the other
 * (a pair split), rolled out a hundred degrees or so later and climbing gently away.</li>
 * </ul>
 */
final class StrikeFlight {
    /** How far the shells walk along the street (blocks) and for how long the cannon fires (s). */
    static final double LINE = 48, FIRE = 1.6;
    /** How far apart the bombs of a stick land (blocks). */
    static final double STICK = 7;
    private static final double DT = 0.05, G = 9.81;
    /** How far out the run starts and ends, either side of the target (blocks). */
    private static final double REACH = 1800;

    /** A planned run: its path (WorldEvents.Event, x y z yaw nose bank), when its cannon fires, when its bombs go. */
    record Plan(float[] path, double fireFrom, double fireTo, double[] release) {
    }

    private enum Phase { LEVEL, DIVE, FIRE, ROUND, FLAT, PULL, BREAK, OUT }

    private StrikeFlight() {
    }

    /** The angle of attack at this load factor (radians): the nose rides a little above where it's going, more as it pulls. */
    static double aoa(double n) {
        return Math.toRadians(Math.min(14, 1.5 + 2.2 * Math.max(n, 0)));
    }

    // ------------------------------------------------------------------ the jet

    /** The jet as flown: where it is, which way it's going, how fast, and the load factor and bank it's holding. */
    private static final class Jet {
        double u, w, h, psi, gam, v, vt, n = 1, nRate, phi, phiRate, t;
        Phase phase = Phase.LEVEL;
        /** Every DT: t u w h psi gam n phi phase. */
        final List<double[]> out = new ArrayList<>();

        Jet(double h, double v) {
            this.h = h;
            this.v = this.vt = v;
            record();
        }

        /**
         * One step, the stick asking for load factor nc (g) and bank phic (radians, right +): each eased toward what's
         * asked like a critically damped spring, never faster than a smooth hand on the stick (3 g a second, 70 degrees a second).
         */
        void step(double nc, double phic) {
            double wn = 3.0, wp = 3.5;
            nRate = Mth.clamp(nRate + (wn * wn * (nc - n) - 2 * wn * nRate) * DT, -3, 3);
            n += nRate * DT;
            phiRate = Mth.clamp(phiRate + (wp * wp * (phic - phi) - 2 * wp * phiRate) * DT, -1.2, 1.2);
            phi += phiRate * DT;
            double g = gam;
            gam += G / v * (n * Math.cos(phi) - Math.cos(g)) * DT;
            psi += G / v * n * Math.sin(phi) / Math.cos(g) * DT;
            // a little faster down the dive, slower up the climb, the throttle bringing it back
            v += (0.4 * (vt - v) - 0.3 * G * Math.sin(g)) * DT;
            double c = Math.cos(gam);
            u += v * c * Math.cos(psi) * DT;
            w += v * c * Math.sin(psi) * DT;
            h += v * Math.sin(gam) * DT;
            t += DT;
            record();
        }

        private void record() {
            out.add(new double[] {t, u, w, h, psi, gam, n, phi, phase.ordinal()});
        }

        /** The load factor that brings the flight path round to gt (radians, climbing +), at gain k, within lo..hi g. */
        double hold(double gt, double k, double lo, double hi) {
            double nc = Math.cos(gam) / Math.max(0.3, Math.cos(phi)) + v / G * k * (gt - gam);
            return Mth.clamp(nc, lo, hi);
        }

        /** Where the nose line meets the ground along the street (u), far off if it's not pointing down. */
        double aim() {
            return aim(u, h, gam, n);
        }

        static double aim(double u, double h, double gam, double n) {
            double down = -(gam + aoa(n));
            return down > 0.01 ? u + h / Math.tan(down) : 1e9;
        }
    }

    /**
     * Flies one run in the jet's own frame: level at in, the dive (radians), and at trigger height the firing (the
     * nose eased up q radians a second) or the round out; then the break to side (+1 right), bank radians, until
     * it has turned turn radians.
     */
    private static Jet fly(boolean strafe, double speed, double in, double dive, double trigger, double q, int side,
            double bank, double turn, double top) {
        Jet j = new Jet(in, speed);
        double t0 = 0, g0 = 0;
        while (j.t < 75) {
            switch (j.phase) {
                case LEVEL -> {
                    j.step(j.hold(0, 0.9, 0.3, 4.5), 0);
                    if (j.t > 1) {
                        j.phase = Phase.DIVE;
                    }
                }
                case DIVE -> {
                    // a gentle push over (never below a third of a g) into the dive, held
                    j.step(j.hold(-dive, 0.9, 0.3, 4.5), 0);
                    if (j.h <= trigger) {
                        j.phase = strafe ? Phase.FIRE : Phase.ROUND;
                        t0 = j.t;
                        g0 = j.gam;
                    }
                }
                case FIRE -> {
                    j.step(j.hold(g0 + q * (j.t - t0), 1.5, 0.3, 4.5), 0);
                    if (j.t - t0 >= FIRE) {
                        j.phase = Phase.PULL;
                    }
                }
                case ROUND -> {
                    j.step(j.hold(0, 0.9, 0.3, 4.5), 0);
                    if (Math.abs(j.gam) < Math.toRadians(0.5) && j.t - t0 > 1) {
                        j.phase = Phase.FLAT;
                        t0 = j.t;
                    }
                }
                case FLAT -> {
                    j.step(j.hold(0, 0.9, 0.3, 4.5), 0);
                    if (j.t - t0 >= 2.8) {
                        j.phase = Phase.PULL;
                    }
                }
                case PULL -> {
                    // throttle up and pull, wings level, until the nose is well up
                    j.vt = speed + 12;
                    j.step(4, 0);
                    if (j.gam > Math.toRadians(14)) {
                        j.phase = Phase.BREAK;
                    }
                }
                case BREAK -> {
                    j.step(j.hold(Math.toRadians(j.h < top - 50 ? 12 : 0), 1, 0.5, 5), side * bank);
                    if (Math.abs(j.psi) > turn) {
                        j.phase = Phase.OUT;
                    }
                }
                case OUT -> j.step(j.hold(j.h < top ? Math.toRadians(6) : 0, 0.8, 0.3, 4), 0);
            }
        }
        return j;
    }

    private static int first(Jet j, Phase phase) {
        for (int i = 0; i < j.out.size(); i++) {
            if (j.out.get(i)[8] >= phase.ordinal()) {
                return i;
            }
        }
        return j.out.size() - 1;
    }

    // ------------------------------------------------------------------ the runs

    /**
     * A strafing run at the target down dir: in level at in over the target, the dive (radians), the shells
     * walked along the line, bottoming out low over the target, breaking to side.
     */
    static Plan strafe(ServerLevel level, Vec3 target, Vec3 dir, double speed, double dive, double low, double in, int side,
            double bank, double turn) {
        // the height it opens fire at: try heights until the pull up after the walk bottoms out at low; and at each,
        // how fast to ease the nose up for the shells to walk the whole line while the cannon fires
        double trigger = in * 0.6, q = 0, top = top(level, target);
        Jet j = null;
        for (int k = 0; k < 8; k++) {
            double lo = 0, hi = 0.25;
            for (int b = 0; b < 22; b++) {
                double mid = (lo + hi) / 2;
                Jet test = fly(true, speed, in, dive, trigger, mid, side, bank, turn, top);
                int opens = first(test, Phase.FIRE), ends = first(test, Phase.PULL);
                double[] a = test.out.get(opens), e = test.out.get(ends);
                double walked = Jet.aim(e[1], e[3], e[5], e[6]) - Jet.aim(a[1], a[3], a[5], a[6]);
                if (walked > LINE) {
                    hi = mid;
                } else {
                    lo = mid;
                }
            }
            q = lo;
            j = fly(true, speed, in, dive, trigger, q, side, bank, turn, top);
            double bottom = Double.MAX_VALUE;
            for (int i = first(j, Phase.FIRE); i < j.out.size(); i++) {
                bottom = Math.min(bottom, j.out.get(i)[3]);
            }
            trigger = Mth.clamp(trigger + low - bottom, low + 8, in - 10);
        }
        int opens = first(j, Phase.FIRE);
        double[] a = j.out.get(opens);
        // slid along so the nose is on the line's near end as the cannon opens up
        double shift = -LINE / 2 - Jet.aim(a[1], a[3], a[5], a[6]);
        Built built = build(level, target, dir, j, shift);
        double from = built.time(opens);
        return new Plan(built.path, from, from + FIRE, new double[0]);
    }

    /**
     * A bombing run: in level at in, the dive (radians), rounded out to level at low over the roofs, a stick of
     * bombs (count) let go so the middle one lands on the target, then the break to side.
     */
    static Plan bomb(ServerLevel level, Vec3 target, Vec3 dir, double speed, double dive, double low, double in, int side,
            double bank, double turn, int count) {
        double trigger = low + 15, top = top(level, target);
        Jet j = null;
        for (int k = 0; k < 10; k++) {
            j = fly(false, speed, in, dive, trigger, 0, side, bank, turn, top);
            double bottom = Double.MAX_VALUE;
            for (int i = first(j, Phase.ROUND); i < first(j, Phase.PULL); i++) {
                bottom = Math.min(bottom, j.out.get(i)[3]);
            }
            trigger = Mth.clamp(trigger + low - bottom, low + 2, in - 5);
        }
        // the middle bomb goes 1.2 s into the level run: where would it land, falling from there
        int mid = first(j, Phase.FLAT) + (int) Math.round(1.2 / DT);
        double[] p = j.out.get(mid);
        double vh = j.v * Math.cos(p[5]), vy = j.v * Math.sin(p[5]), drop = Math.max(0, p[3] - 1.5);
        double fall = (vy + Math.sqrt(vy * vy + 2 * G * drop)) / G;
        double lands = p[1] + vh * Math.cos(p[4]) * fall;
        Built built = build(level, target, dir, j, -lands);
        double[] release = new double[count];
        double t = built.time(mid);
        for (int i = 0; i < count; i++) {
            release[i] = t + (i - (count - 1) / 2.0) * STICK / speed;
        }
        return new Plan(built.path, -1, -1, release);
    }

    /** How high over the target it climbs away to, under the top of the world. */
    private static double top(ServerLevel level, Vec3 target) {
        return Math.max(60, Math.min(170, level.getMaxY() - 30 - target.y));
    }

    // ------------------------------------------------------------------ into the world

    /** A run's path in the world, and how many of its points (every DT) came before the flown part. */
    private record Built(float[] path, int before) {
        /** Seconds into the path of the flown part's point i. */
        double time(int i) {
            return (before + i) * DT;
        }
    }

    /**
     * Turns the flown run (slid shift along the street) into the world's path: level flight from REACH out up to
     * where it was flown from, the run, and on until it's REACH away again; a point every STEP, each with which
     * way the nose points and the wings bank.
     */
    private static Built build(ServerLevel level, Vec3 target, Vec3 dir, Jet j, double shift) {
        List<double[]> all = new ArrayList<>();
        double[] start = j.out.get(0);
        int before = 0;
        for (double u = shift - j.v * DT; u > -REACH; u -= j.v * DT) {
            all.add(new double[] {0, u - shift, 0, start[3], 0, 0, 1, 0, 0});
            before++;
        }
        java.util.Collections.reverse(all);
        boolean away = false;
        for (double[] p : j.out) {
            all.add(p);
            double u = p[1] + shift;
            away |= p[8] >= Phase.BREAK.ordinal() && u * u + p[2] * p[2] < REACH * REACH;
            if (away && u * u + p[2] * p[2] > REACH * REACH) {
                break;
            }
        }
        int every = (int) Math.round(WorldEvents.Event.STEP / DT), stride = WorldEvents.Event.STRIDE;
        // every STEP from the first point onwards that falls on the run's own
        int skip = before % every, n = (all.size() - 1 - skip) / every + 1;
        float[] path = new float[n * stride];
        Vec3 across = new Vec3(-dir.z, 0, dir.x);
        float yaw = Float.NaN;
        for (int i = 0; i < n; i++) {
            double[] p = all.get(skip + i * every);
            Vec3 at = target.add(dir.scale(p[1] + shift)).add(across.scale(p[2]));
            float[] look = attitude(dir, across, p[4], p[5], p[6], p[7]);
            // yaw carried on round the turn rather than wrapped, so it's smooth between points
            yaw = Float.isNaN(yaw) ? look[0] : yaw + Mth.wrapDegrees(look[0] - yaw);
            path[i * stride] = (float) at.x;
            path[i * stride + 1] = (float) Math.min(target.y + p[3], level.getMaxY() - 8);
            path[i * stride + 2] = (float) at.z;
            path[i * stride + 3] = yaw;
            path[i * stride + 4] = look[1];
            path[i * stride + 5] = look[2];
        }
        return new Built(path, before - skip);
    }

    /**
     * Minecraft's yaw, the nose above the horizon and the bank (right wing down), degrees, of a jet heading psi off
     * the street, climbing gam, pulling n g, banked phi: its nose rides the angle of attack above the way it's going,
     * tipped with the wings.
     */
    private static float[] attitude(Vec3 dir, Vec3 across, double psi, double gam, double n, double phi) {
        Vec3 heading = dir.scale(Math.cos(psi)).add(across.scale(Math.sin(psi)));
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 going = heading.scale(Math.cos(gam)).add(up.scale(Math.sin(gam)));
        Vec3 lift = heading.scale(-Math.sin(gam)).add(up.scale(Math.cos(gam)));
        Vec3 right = new Vec3(-heading.z, 0, heading.x);
        Vec3 wings = lift.scale(Math.cos(phi)).add(right.scale(Math.sin(phi)));
        double a = aoa(n);
        Vec3 nose = going.scale(Math.cos(a)).add(wings.scale(Math.sin(a)));
        Vec3 top = wings.scale(Math.cos(a)).subtract(going.scale(Math.sin(a)));
        float yaw = (float) Math.toDegrees(Math.atan2(-nose.x, nose.z));
        float pitch = (float) Math.toDegrees(Math.asin(Mth.clamp(nose.y, -1, 1)));
        Vec3 levelRight = new Vec3(-nose.z, 0, nose.x).normalize();
        Vec3 levelUp = up.subtract(nose.scale(nose.y)).normalize();
        float bank = (float) Math.toDegrees(Math.atan2(top.dot(levelRight), top.dot(levelUp)));
        return new float[] {yaw, pitch, bank};
    }
}
