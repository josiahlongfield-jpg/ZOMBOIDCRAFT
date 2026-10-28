package com.zomboidcraft.creature;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Where a Harvestman's four feet are, worked out on the server each tick and sent to every client (so a shot lands on
 * the leg everyone sees). The approved preview's rules: each foot finds a spot on whatever block face is under it, the
 * feet lift in a wave as it walks and swing to land a little ahead, and standing still any foot out of place is tidied
 * in one at a time, now and then one lifting and stabbing back down. Rearing up, the front pair are held up and forward;
 * dead, the legs splay out and slide flat; a leg shot off at the knee is left out.
 */
public final class HarvestmanLegs {
    private static final double STRIDE = 2.2, CLING_SPREAD = 1.85;
    /** A wave: left rear, left front, right rear, right front. */
    private static final double[] GAIT_OFF = {0.25, 0, 0.75, 0.5};

    private static final class Step {
        Vec3 p0, n0, p1, n1;
        double u, dur, lift;
        boolean fixed;
    }

    public final Vec3[] feet = new Vec3[4], footN = new Vec3[4];
    /** How far each foot is through a step (0 planted, 1 at the top of its swing). */
    public final double[] lift = new double[4];
    private final Step[] steps = new Step[4];
    private final Vec3[] targets = new Vec3[4], targetN = new Vec3[4];
    /** Whether each foot's spot is on something solid (a point never comes down on thin air). */
    private final boolean[] solid = new boolean[4];
    private double phase, prevPhase, still, fid = 2.5;
    private Vec3 lastCentre, lastFwd, vel = Vec3.ZERO;
    private double spd, turn;

    private static Vec3 norm(Vec3 v) {
        double l = v.length();
        return l < 1e-9 ? v : v.scale(1 / l);
    }

    public static double floorness(Vec3 up) {
        double x = Mth.clamp((up.y - 0.55) / 0.4, 0, 1);
        return x * x * (3 - 2 * x);
    }

    /** The flat frame: up off the surface, nose along its heading. */
    public static HarvestmanBody.Frame flat(Vec3 centre, Vec3 up, Vec3 fwd) {
        return new HarvestmanBody.Frame(centre, up.cross(fwd), up, fwd);
    }

    /** Its stride: longer the faster it goes, so at a burst it bounds rather than scuttles. */
    private double stride() {
        return Mth.clamp(STRIDE + 0.25 * (spd - 3), STRIDE, 4.2);
    }

    /** How fast it's going along its surface (blocks a second). */
    public double speed() {
        return spd;
    }

    /**
     * One tick.
     *
     * @param h how far the body is held off its surface
     * @param rear how far it's reared up (0..1)
     * @param dead it's dead: the legs splay and slide flat
     * @param lost legs shot off at the knee (bits)
     */
    public void tick(Entity e, Vec3 centre, Vec3 up, Vec3 fwd, double h, double rear, boolean dead, int lost, RandomSource random) {
        double dt = 0.05;
        boolean jumped = lastCentre == null || lastCentre.distanceToSqr(centre) > 16;
        if (!jumped) {
            vel = vel.lerp(centre.subtract(lastCentre).scale(1 / dt), 1 - Math.exp(-dt * 6));
            Vec3 a = lastFwd.subtract(up.scale(lastFwd.dot(up)));
            double tr = Math.atan2(a.cross(fwd).dot(up), a.dot(fwd)) / dt;
            turn = Mth.lerp(1 - Math.exp(-dt * 8), turn, Mth.clamp(tr, -4, 4));
        }
        lastCentre = centre;
        lastFwd = fwd;
        spd = vel.subtract(up.scale(vel.dot(up))).length();
        still = spd > 0.05 ? 0 : still + dt;
        phase += (spd * dt + Math.abs(turn) * dt * 1.5) / STRIDE;
        HarvestmanBody.Frame fr = flat(centre, up, fwd);
        Level level = e.level();
        if (feet[0] == null || jumped) {
            for (int li = 0; li < 4; li++) {
                target(li, fr, h, level, e);
                feet[li] = targets[li];
                footN[li] = targetN[li];
                steps[li] = null;
                lift[li] = 0;
            }
            prevPhase = phase;
            return;
        }
        if (dead) {
            // the legs give way: each point slides out flat along the floor
            for (int li = 0; li < 4; li++) {
                HarvestmanBody.Leg L = HarvestmanBody.leg(li);
                double s = HarvestmanBody.side(li), r = L.lc() + L.a() * 0.6;
                Vec3 want = fr.at(s * Math.sin(L.restA()) * r, -h, Math.cos(L.restA()) * r * 0.8);
                BlockHitResult hit = cast(level, e, want.add(up.scale(1.5)), want.subtract(up.scale(3)));
                if (hit.getType() != HitResult.Type.MISS) {
                    want = hit.getLocation();
                }
                feet[li] = feet[li].lerp(want, 1 - Math.exp(-dt * 3));
                steps[li] = null;
                lift[li] = 0;
            }
            return;
        }
        stepFeet(dt, fr, h, rear, lost, level, e, random);
        // rearing, the front pair are held up and forward
        if (rear > 0.02) {
            double k = rear * rear * (3 - 2 * rear);
            for (int li : new int[] {0, 2}) {
                if ((lost >> li & 1) != 0) {
                    continue;
                }
                Vec3 high = fr.at(HarvestmanBody.side(li) * 1.2, 1.5, 2.3);
                feet[li] = feet[li].lerp(high, k);
                footN[li] = norm(footN[li].lerp(up, k));
                lift[li] = Math.max(lift[li], rear);
            }
        }
    }

    private static BlockHitResult cast(Level level, Entity e, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
    }

    private static Vec3 normal(BlockHitResult hit) {
        Direction d = hit.getDirection();
        return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ());
    }

    /**
     * Where a foot wants to be: out from its hip at its resting angle, far enough out that the folded leg still reaches
     * when the body sinks, a little ahead along the way it moves and round the way it turns; landed on whatever block
     * face is there (along the line from the hip, or else straight down), and pushed out past anything right by the hip.
     */
    private void target(int li, HarvestmanBody.Frame fr, double h, Level level, Entity e) {
        Vec3 up = fr.y();
        double s = HarvestmanBody.side(li);
        HarvestmanBody.Leg L = HarvestmanBody.leg(li);
        double cyc = (spd + 1.5 * Math.abs(turn)) / stride(), sf = Mth.clamp(0.3 + 0.12 * spd / 1.8, 0.3, 0.42);
        double r0 = L.restR() * Mth.lerp(floorness(up), CLING_SPREAD, 1), dmin = L.lc() - L.a() + 0.45;
        double r = Math.max(r0, Math.sqrt(Math.max(0, dmin * dmin - h * h)) + 0.65);
        Vec3 H = fr.hip(li);
        Vec3 off = fr.x().scale(s * Math.sin(L.restA()) * r).add(up.scale(-h)).add(fr.z().scale(Math.cos(L.restA()) * r));
        double st = cyc > 0.02 ? (1 - sf) / (2 * cyc) : 0, ta = Mth.clamp(turn * st, -0.45, 0.45);
        if (Math.abs(ta) > 1e-4) {
            Vec3 hu = up.scale(off.dot(up)), flat = off.subtract(hu), sd = up.cross(flat);
            off = hu.add(flat.scale(Math.cos(ta))).add(sd.scale(Math.sin(ta)));
        }
        Vec3 lead = vel.scale(st);
        double leadMax = Mth.clamp(0.95 + 0.12 * (spd - 3), 0.95, 1.7);
        if (lead.length() > leadMax) {
            lead = norm(lead).scale(leadMax);
        }
        Vec3 P0 = fr.o().add(off).add(lead);
        Vec3 d = P0.subtract(H);
        BlockHitResult hit = cast(level, e, H, H.add(d.scale(1.4)));
        Vec3 p, n;
        boolean found = hit.getType() != HitResult.Type.MISS;
        if (found) {
            p = hit.getLocation();
            n = normal(hit);
        } else {
            hit = cast(level, e, P0.add(up.scale(1.5)), P0.subtract(up.scale(2.5)));
            Vec3 under = fr.o().subtract(up.scale(h + 0.6));
            if (hit.getType() == HitResult.Type.MISS) {
                // past an outer edge: the point hooks round on to the face under the edge
                hit = cast(level, e, P0.subtract(up.scale(0.6)), under);
            }
            if (hit.getType() == HitResult.Type.MISS) {
                // over the top of what it's climbing: it reaches down and in for the face beyond
                hit = cast(level, e, P0.add(up.scale(1.0)), fr.o().subtract(up.scale(h)));
            }
            found = hit.getType() != HitResult.Type.MISS;
            p = found ? hit.getLocation() : P0;
            n = found ? normal(hit) : up;
        }
        double reach = L.lc() - L.a() + 0.3;
        if (p.distanceTo(H) < reach) {
            BlockHitResult h2 = cast(level, e, P0.add(up.scale(1.5)), P0.subtract(up.scale(2.5)));
            if (h2.getType() != HitResult.Type.MISS && h2.getLocation().distanceTo(H) > p.distanceTo(H)) {
                p = h2.getLocation();
                n = normal(h2);
            }
        }
        for (int k = 1; k <= 4 && p.distanceTo(H) < reach; k++) {
            Vec3 dd = P0.subtract(fr.o()), dh = norm(dd.subtract(up.scale(dd.dot(up)))), P1 = P0.add(dh.scale(0.4 * k));
            BlockHitResult h3 = cast(level, e, P1.add(up.scale(1.5)), P1.subtract(up.scale(2.5)));
            if (h3.getType() != HitResult.Type.MISS && h3.getLocation().distanceTo(H) > p.distanceTo(H)) {
                p = h3.getLocation();
                n = normal(h3);
            }
        }
        targets[li] = p;
        targetN[li] = n;
        solid[li] = found && p.distanceTo(H) <= L.a() + L.lc() - 0.05;
    }

    /**
     * How many of its legs (those it still has) could plant a point on something solid if its body stood in this
     * frame, held h off its surface. Its crawl only goes where enough of them can.
     */
    public int support(Entity e, HarvestmanBody.Frame fr, double h, int lost) {
        int n = 0;
        for (int li = 0; li < 4; li++) {
            if ((lost >> li & 1) == 0) {
                target(li, fr, h, e.level(), e);
                if (solid[li]) {
                    n++;
                }
            }
        }
        return n;
    }

    private boolean cramped(int li, HarvestmanBody.Frame fr) {
        HarvestmanBody.Leg L = HarvestmanBody.leg(li);
        return feet[li].distanceTo(fr.hip(li)) < L.lc() - L.a() + 0.3;
    }

    private void stepFeet(double dt, HarvestmanBody.Frame fr, double h, double rear, int lost, Level level, Entity e, RandomSource random) {
        Vec3 up = fr.y();
        double cyc = (spd + 1.5 * Math.abs(turn)) / stride(), sf = Mth.clamp(0.3 + 0.12 * spd / 1.8, 0.3, 0.42);
        for (int li = 0; li < 4; li++) {
            target(li, fr, h, level, e);
        }
        int active = 0;
        for (int i = 0; i < 4; i++) {
            Step st = steps[i];
            if (st == null) {
                continue;
            }
            st.u += dt / st.dur;
            if (!st.fixed && st.u < 0.85 && solid[i]) {
                st.p1 = targets[i];
                st.n1 = targetN[i];
            }
            double u = Math.min(1, st.u), ease = u * u * (3 - 2 * u);
            Vec3 nm = norm(st.n0.add(st.n1).add(up.scale(2)));
            feet[i] = st.p0.lerp(st.p1, ease).add(nm.scale(Math.pow(Math.sin(Math.PI * u), 0.8) * st.lift));
            footN[i] = norm(st.n0.lerp(st.n1, ease));
            lift[i] = Math.sin(Math.PI * u);
            if (st.u >= 1) {
                feet[i] = st.p1;
                footN[i] = st.n1;
                steps[i] = null;
                lift[i] = 0;
            } else {
                active++;
            }
        }
        boolean moving = cyc > 0.05;
        // a leg short (three legs left), the others step quicker to make up for it
        double hurry = 1 + 0.35 * Integer.bitCount(lost);
        for (int i = 0; i < 4; i++) {
            if (steps[i] != null || (lost >> i & 1) != 0 || rear > 0.3 && (i & 1) == 0) {
                continue;
            }
            double a = prevPhase * hurry + GAIT_OFF[i], b = phase * hurry + GAIT_OFF[i];
            boolean due = moving && Math.floor(b) > Math.floor(a);
            double far = feet[i].distanceTo(targets[i]);
            if (!solid[i]) {
                continue;       // nothing there to stand on: the point stays where it holds
            }
            if (due && far > 0.15 || far > 2.3 || cramped(i, fr) && far > 0.3 && active == 0) {
                lift(i, Mth.clamp(sf / Math.max(cyc * hurry, 0.05), spd > 5 ? 0.13 : 0.26, 0.75), false, spd);
                active++;
            }
        }
        prevPhase = phase;
        if (!moving && active == 0) {
            int worst = -1;
            double wd = 0.35;
            for (int i = 0; i < 4; i++) {
                if ((lost >> i & 1) != 0 || rear > 0.3 && (i & 1) == 0) {
                    continue;
                }
                double d = solid[i] ? feet[i].distanceTo(targets[i]) : 0;
                if (d > wd || cramped(i, fr) && d > 0.2 && worst < 0) {
                    wd = Math.max(d, wd);
                    worst = i;
                }
            }
            if (worst >= 0) {
                lift(worst, 0.5, false, spd);
            } else if (still > 1.2) {
                fid -= dt;
                if (fid <= 0) {
                    int i = random.nextInt(4);
                    if ((lost >> i & 1) == 0) {
                        lift(i, 0.75, true, spd);
                    }
                    fid = 2.5 + random.nextDouble() * 3.5;
                }
            }
        }
    }

    private void lift(int i, double dur, boolean fixed, double spd) {
        Step st = new Step();
        st.p0 = feet[i];
        st.n0 = footN[i];
        st.p1 = fixed ? feet[i] : targets[i];
        st.n1 = fixed ? footN[i] : targetN[i];
        st.dur = dur;
        st.lift = fixed ? 0.8 : 0.55 + 0.25 * Math.min(1, spd / 1.6);
        st.fixed = fixed;
        steps[i] = st;
    }

    /** A foot just came down (for its tap): true once, the tick it lands. */
    public boolean landed(int i, double before) {
        return before > 0 && lift[i] == 0;
    }
}
