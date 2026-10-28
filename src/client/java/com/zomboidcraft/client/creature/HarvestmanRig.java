package com.zomboidcraft.client.creature;

import com.zomboidcraft.creature.HarvestmanEntity;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One Harvestman's legs, worked out live on each client from where its body goes: the same rules as the approved
 * preview (the test room's stepFeet and the leg IK). Each foot finds a spot on whatever block face is under it, the feet
 * lift in a wave as it walks and swing to land a little ahead, and standing still any foot left out of place is tidied
 * in, one at a time. The knees rise up and out over their feet, and a foot planted closer than the folded leg reaches
 * slides out along its surface. Render thread only.
 */
public final class HarvestmanRig {
    private static final double STRIDE = 2.2, CLING_SPREAD = 1.85, PITCH = 0.1;
    /** A wave: left rear, left front, right rear, right front. */
    private static final double[] GAIT_OFF = {0.25, 0, 0.75, 0.5};

    private static final class Step {
        Vec3 p0, n0, p1, n1;
        double u, dur, lift;
        boolean fixed;
    }

    /** Part matrices from the model's frames to the entity's drawn position: body, then thigh and lower leg per leg. */
    public final Matrix4f body = new Matrix4f();
    public final Matrix4f[] thigh = new Matrix4f[4], shin = new Matrix4f[4];

    private final Vec3[] feet = new Vec3[4], footN = new Vec3[4];
    private final Step[] steps = new Step[4];
    private final double[] lift = new double[4];
    private final Vec3[] targets = new Vec3[4], targetN = new Vec3[4];
    private Vec3 up = new Vec3(0, 1, 0), fwd = new Vec3(0, 0, 1), last, vel = Vec3.ZERO;
    private double spd, turn, phase, prevPhase, h = HarvestmanEntity.STAND, still, fid = 2.5, t;
    private long lastNanos;
    private final java.util.Random random = new java.util.Random();

    public HarvestmanRig() {
        for (int i = 0; i < 4; i++) {
            thigh[i] = new Matrix4f();
            shin[i] = new Matrix4f();
        }
    }

    // ------------------------------------------------------------------------------------------------ the frame

    private static Vec3 at(Vec3 o, Vec3 x, Vec3 y, Vec3 z, double px, double py, double pz) {
        return o.add(x.scale(px)).add(y.scale(py)).add(z.scale(pz));
    }

    private static double floorness(Vec3 up) {
        double x = Mth.clamp((up.y - 0.55) / 0.4, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static Vec3 norm(Vec3 v) {
        double l = v.length();
        return l < 1e-9 ? v : v.scale(1 / l);
    }

    // the flat frame (up from the surface, nose along its heading) and the body frame riding on it
    private Vec3 fo, fx, fy, fz, bo, bx, by, bz;

    /**
     * Brings the legs up to now and works out every part's matrix.
     *
     * @param centre the body's centre, as drawn this frame
     * @param drawnAt the entity's drawn position (what the matrices are relative to)
     * @param q how it lies, as drawn this frame (model to world)
     */
    public void update(HarvestmanEntity e, Level level, HarvestmanModel model, Vec3 centre, Vec3 drawnAt, Quaternionf q) {
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        t += dt;
        Vector3f yv = q.transform(new Vector3f(0, 1, 0)), zv = q.transform(new Vector3f(0, 0, 1));
        Vec3 nUp = norm(new Vec3(yv.x, yv.y, yv.z)), nFwd = norm(new Vec3(zv.x, zv.y, zv.z));
        // how fast it's going along its surface, and how fast it's turning
        boolean jumped = last == null || last.distanceToSqr(centre) > 16;
        if (dt > 1e-4 && !jumped) {
            Vec3 v = centre.subtract(last).scale(1 / dt);
            vel = vel.lerp(v, 1 - Math.exp(-dt * 6));
            Vec3 a = fwd.subtract(nUp.scale(fwd.dot(nUp)));
            double tr = Math.atan2(a.cross(nFwd).dot(nUp), a.dot(nFwd)) / dt;
            turn = Mth.lerp(1 - Math.exp(-dt * 8), turn, Mth.clamp(tr, -4, 4));
        }
        last = centre;
        up = nUp;
        fwd = nFwd;
        Vec3 flatV = vel.subtract(up.scale(vel.dot(up)));
        spd = flatV.length();
        still = spd > 0.05 ? 0 : still + dt;
        phase += (spd * dt + Math.abs(turn) * dt * 1.5) / STRIDE;
        h = Mth.lerp(1 - Math.exp(-dt * 5), h, e.lift());

        fy = up;
        fz = fwd;
        fx = up.cross(fwd);
        double dip = 0, roll = 0, pitch = 0;
        for (int li = 0; li < 4; li++) {
            double l = lift[li], s = li < 2 ? -1 : 1;
            dip += l;
            roll += s * l;
            if ((li & 1) == 0) {
                pitch -= l;
            }
        }
        fo = centre;
        bo = centre.subtract(up.scale(0.03 * dip));
        bodyAxes(PITCH + 0.035 * pitch, 0, 0.045 * roll);

        HarvestmanModel.Leg[] legs = model.legs;
        if (feet[0] == null || jumped) {
            for (int li = 0; li < 4; li++) {
                target(li, legs, level, e);
                feet[li] = targets[li];
                footN[li] = targetN[li];
                steps[li] = null;
                lift[li] = 0;
            }
            prevPhase = phase;
        } else if (dt > 1e-4) {
            stepFeet(dt, legs, level, e);
        }

        // the body sways and bobs a little, and its legs hang from it
        boolean walking = spd > 0.1;
        double bob = walking ? 0.05 * Math.sin(t * 3.4) : 0.03 * Math.sin(t * 1.2), sway = 0.02 * Math.sin(t * 0.8);
        bo = bo.add(up.scale(bob));
        bodyAxes(PITCH + 0.035 * pitch + (walking ? 0.025 * Math.sin(t * 1.7) : 0), sway * 0.5, 0.045 * roll + sway);
        set(body, bo, bx, by, bz, drawnAt);
        for (int li = 0; li < 4; li++) {
            leg(li, legs[li & 1], drawnAt);
        }
    }

    /** The body frame: the flat frame turned by a yaw, a pitch (nose down) and a roll, as the preview's rot(). */
    private void bodyAxes(double pitch, double yaw, double roll) {
        // R = R0 * ry(yaw) * rx(pitch) * rz(roll), applied to the flat frame's axes
        double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch), cr = Math.cos(roll), sr = Math.sin(roll);
        // columns of ry*rx*rz in the flat frame
        double[][] m = {
                {cy * cr + sy * sp * sr, -cy * sr + sy * sp * cr, sy * cp},
                {cp * sr, cp * cr, -sp},
                {-sy * cr + cy * sp * sr, sy * sr + cy * sp * cr, cy * cp}};
        bx = fx.scale(m[0][0]).add(fy.scale(m[1][0])).add(fz.scale(m[2][0]));
        by = fx.scale(m[0][1]).add(fy.scale(m[1][1])).add(fz.scale(m[2][1]));
        bz = fx.scale(m[0][2]).add(fy.scale(m[1][2])).add(fz.scale(m[2][2]));
    }

    private Vec3 hip(int li, HarvestmanModel.Leg L) {
        double s = li < 2 ? -1 : 1;
        return at(bo, bx, by, bz, s * L.hipX, L.hipY, L.hipZ);
    }

    private static void set(Matrix4f m, Vec3 o, Vec3 x, Vec3 y, Vec3 z, Vec3 drawnAt) {
        m.set((float) x.x, (float) x.y, (float) x.z, 0f,
                (float) y.x, (float) y.y, (float) y.z, 0f,
                (float) z.x, (float) z.y, (float) z.z, 0f,
                (float) (o.x - drawnAt.x), (float) (o.y - drawnAt.y), (float) (o.z - drawnAt.z), 1f);
    }

    // ------------------------------------------------------------------------------------------------ feet

    private BlockHitResult cast(Level level, HarvestmanEntity e, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
    }

    private static Vec3 normal(BlockHitResult hit) {
        Direction d = hit.getDirection();
        return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ());
    }

    /**
     * Where a foot wants to be: out from its hip at its resting angle, far enough out that the folded leg still reaches
     * when the body sinks, a little ahead along the way it moves and round the way it turns; landed on whatever block
     * face is there (along the line from the hip, or else straight down), and pushed further out past anything right by
     * the hip.
     */
    private void target(int li, HarvestmanModel.Leg[] legs, Level level, HarvestmanEntity e) {
        double s = li < 2 ? -1 : 1;
        HarvestmanModel.Leg L = legs[li & 1];
        double cyc = (spd + 1.5 * Math.abs(turn)) / STRIDE, sf = Mth.clamp(0.3 + 0.12 * spd / 1.8, 0.3, 0.42);
        double r0 = L.restR * Mth.lerp(floorness(up), CLING_SPREAD, 1), dmin = L.lc - L.a + 0.45;
        double r = Math.max(r0, Math.sqrt(Math.max(0, dmin * dmin - h * h)) + 0.65);
        Vec3 H = hip(li, L);
        Vec3 off = fx.scale(s * Math.sin(L.restA) * r).add(fy.scale(-h)).add(fz.scale(Math.cos(L.restA) * r));
        double st = cyc > 0.02 ? (1 - sf) / (2 * cyc) : 0, ta = Mth.clamp(turn * st, -0.45, 0.45);
        if (Math.abs(ta) > 1e-4) {
            Vec3 hu = up.scale(off.dot(up)), flat = off.subtract(hu), sd = up.cross(flat);
            off = hu.add(flat.scale(Math.cos(ta))).add(sd.scale(Math.sin(ta)));
        }
        Vec3 lead = vel.scale(st);
        if (lead.length() > 0.95) {
            lead = norm(lead).scale(0.95);
        }
        Vec3 P0 = fo.add(off).add(lead);
        Vec3 d = P0.subtract(H);
        double len = Math.max(1e-3, d.length());
        BlockHitResult hit = cast(level, e, H, H.add(d.scale(1.4)));
        Vec3 p, n;
        if (hit.getType() != HitResult.Type.MISS) {
            p = hit.getLocation();
            n = normal(hit);
        } else {
            hit = cast(level, e, P0.add(up.scale(1.5)), P0.subtract(up.scale(2.5)));
            p = hit.getType() != HitResult.Type.MISS ? hit.getLocation() : P0;
            n = hit.getType() != HitResult.Type.MISS ? normal(hit) : up;
        }
        double reach = L.lc - L.a + 0.3;
        if (p.distanceTo(H) < reach) {
            BlockHitResult h2 = cast(level, e, P0.add(up.scale(1.5)), P0.subtract(up.scale(2.5)));
            if (h2.getType() != HitResult.Type.MISS && h2.getLocation().distanceTo(H) > p.distanceTo(H)) {
                p = h2.getLocation();
                n = normal(h2);
            }
        }
        for (int k = 1; k <= 4 && p.distanceTo(H) < reach; k++) {
            Vec3 dd = P0.subtract(fo), dh = norm(dd.subtract(up.scale(dd.dot(up)))), P1 = P0.add(dh.scale(0.4 * k));
            BlockHitResult h3 = cast(level, e, P1.add(up.scale(1.5)), P1.subtract(up.scale(2.5)));
            if (h3.getType() != HitResult.Type.MISS && h3.getLocation().distanceTo(H) > p.distanceTo(H)) {
                p = h3.getLocation();
                n = normal(h3);
            }
        }
        targets[li] = p;
        targetN[li] = n;
    }

    private boolean cramped(int li, HarvestmanModel.Leg[] legs) {
        HarvestmanModel.Leg L = legs[li & 1];
        return feet[li].distanceTo(hip(li, L)) < L.lc - L.a + 0.3;
    }

    private void stepFeet(double dt, HarvestmanModel.Leg[] legs, Level level, HarvestmanEntity e) {
        double cyc = (spd + 1.5 * Math.abs(turn)) / STRIDE, sf = Mth.clamp(0.3 + 0.12 * spd / 1.8, 0.3, 0.42);
        for (int li = 0; li < 4; li++) {
            target(li, legs, level, e);
        }
        int active = 0;
        for (int i = 0; i < 4; i++) {
            Step st = steps[i];
            if (st == null) {
                continue;
            }
            st.u += dt / st.dur;
            if (!st.fixed && st.u < 0.85) {
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
        for (int i = 0; i < 4; i++) {
            if (steps[i] != null) {
                continue;
            }
            double a = prevPhase + GAIT_OFF[i], b = phase + GAIT_OFF[i];
            boolean due = moving && Math.floor(b) > Math.floor(a);
            double far = feet[i].distanceTo(targets[i]);
            if (due && far > 0.15 || far > 2.3 || cramped(i, legs) && far > 0.3 && active == 0) {
                lift(i, Mth.clamp(sf / Math.max(cyc, 0.05), 0.26, 0.75), false);
                active++;
            }
        }
        prevPhase = phase;
        // standing: tidy the stance one foot at a time, and now and then a leg lifts and stabs back down where it was
        if (!moving && active == 0) {
            int worst = -1;
            double wd = 0.35;
            for (int i = 0; i < 4; i++) {
                double d = feet[i].distanceTo(targets[i]);
                if (d > wd || cramped(i, legs) && d > 0.2 && worst < 0) {
                    wd = Math.max(d, wd);
                    worst = i;
                }
            }
            if (worst >= 0) {
                lift(worst, 0.5, false);
            } else if (still > 1.2) {
                fid -= dt;
                if (fid <= 0) {
                    lift(random.nextInt(4), 0.75, true);
                    fid = 2.5 + random.nextDouble() * 3.5;
                }
            }
        }
    }

    private void lift(int i, double dur, boolean fixed) {
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

    // ------------------------------------------------------------------------------------------------ legs

    /**
     * A leg from hip to foot: the knee bends toward a pole up and out over the foot (the thigh is short against the long
     * lower leg, so it always leans back from the foot; pushed out over it, it leans out from the hull instead of in
     * across it), and a foot closer than the folded leg reaches slides out along its surface.
     */
    private void leg(int li, HarvestmanModel.Leg L, Vec3 drawnAt) {
        Vec3 H = hip(li, L), F = feet[li];
        double dmin = L.lc - L.a + 0.2;
        if (F.distanceTo(H) < dmin) {
            Vec3 sn = footN[li];
            double hh = H.subtract(F).dot(sn);
            Vec3 Hp = H.subtract(sn.scale(hh));
            double r = Math.sqrt(Math.max(0, dmin * dmin - hh * hh));
            Vec3 dr = F.subtract(Hp);
            if (dr.length() < 1e-3) {
                Vec3 ho = H.subtract(bo);
                dr = ho.subtract(sn.scale(ho.dot(sn)));
            }
            F = Hp.add(norm(dr).scale(r));
        }
        Vec3 ho = H.subtract(bo), fo2 = F.subtract(bo), fh = fo2.subtract(up.scale(fo2.dot(up)));
        Vec3 out = fh.length() > 0.05 ? norm(fh) : norm(ho.subtract(up.scale(ho.dot(up))));
        Vec3 dn = norm(F.subtract(H)), p0 = norm(up.scale(0.4).add(out));
        Vec3 pole = p0.subtract(dn.scale(p0.dot(dn))).add(up.subtract(dn.scale(up.dot(dn))).scale(0.6));
        // two bones bent toward the pole
        double a = L.a, b = L.lc;
        Vec3 d = F.subtract(H);
        double dl = Mth.clamp(d.length(), Math.abs(a - b) + 0.02, a + b - 0.02);
        double cb = Mth.clamp((a * a + dl * dl - b * b) / (2 * a * dl), -1, 1), sb = Math.sqrt(1 - cb * cb);
        Vec3 bp = pole.subtract(dn.scale(pole.dot(dn)));
        bp = bp.length() > 1e-4 ? norm(bp) : norm(dn.cross(new Vec3(0.3, 0.9, 0.2)));
        Vec3 K = H.add(dn.scale(a * cb)).add(bp.scale(a * sb));
        Vec3 P = K.add(norm(H.add(dn.scale(dl)).subtract(K)).scale(b));
        Vec3 n = norm(dn.cross(bp));
        Vec3 tx = norm(K.subtract(H)), sx = norm(P.subtract(K));
        set(thigh[li], H, tx, n.cross(tx), n, drawnAt);
        set(shin[li], K, sx, n.cross(sx), n, drawnAt);
    }
}
