package com.zomboidcraft.client.creature;

import com.zomboidcraft.creature.HarvestmanBody;
import com.zomboidcraft.creature.HarvestmanEntity;
import java.util.ArrayList;
import java.util.List;
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
 * One Harvestman as this client draws it: the body where the entity is, eased and riding on its pose (reared up, jolted
 * by a hit, bowed to feed, sprawled dead), and each leg bent from its hip to the foot the server planted (the feet come
 * in each tick and are drawn as far behind as the body is). A leg shot off at the knee leaves its thigh hanging and
 * twitching, and its lower half flies off and lies where it lands. Render thread only.
 */
public final class HarvestmanRig {
    /** How many ticks behind the server the body is drawn (its position eases in over a few ticks). */
    private static final int DELAY = 2;

    /** Part matrices from the model's frames to the entity's drawn position: body, then thigh and lower leg per leg. */
    public final Matrix4f body = new Matrix4f();
    public final Matrix4f[] thigh = new Matrix4f[4], shin = new Matrix4f[4];
    public int lost;
    public float camo = 1, glowPulse;
    public boolean feedArms;

    /** A lower leg shot off: where it is, how it's moving, and how long it has lain. */
    public static final class Flying {
        public final Matrix4f at = new Matrix4f();
        Vec3 pos, vel;
        final Quaternionf turn = new Quaternionf();
        final Vector3f spin = new Vector3f();
        double age;
        boolean down;
        public final int leg;
        public float fade = 1;

        Flying(int leg) {
            this.leg = leg;
        }
    }

    public final List<Flying> flying = new ArrayList<>();

    private double t, jolt, rear, pitch = 0.1, deadRoll;
    private int flinchesSeen = -1, lostSeen = -1;
    private long lastNanos;

    public HarvestmanRig() {
        for (int i = 0; i < 4; i++) {
            thigh[i] = new Matrix4f();
            shin[i] = new Matrix4f();
        }
    }

    private static Vec3 norm(Vec3 v) {
        double l = v.length();
        return l < 1e-9 ? v : v.scale(1 / l);
    }

    /**
     * Brings it up to now and works out every part's matrix.
     *
     * @param centre the body's centre as drawn this frame
     * @param drawnAt the entity's drawn position (what the matrices are relative to)
     * @param q how it lies as drawn this frame (model to world)
     */
    public void update(HarvestmanEntity e, Level level, Vec3 centre, Vec3 drawnAt, Quaternionf q, float partialTick) {
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        t += dt;
        Vector3f xv = q.transform(new Vector3f(1, 0, 0)), yv = q.transform(new Vector3f(0, 1, 0)), zv = q.transform(new Vector3f(0, 0, 1));
        Vec3 fx = norm(new Vec3(xv.x, xv.y, xv.z)), up = norm(new Vec3(yv.x, yv.y, yv.z)), fz = norm(new Vec3(zv.x, zv.y, zv.z));
        int pose = e.pose();
        boolean dead = pose == HarvestmanEntity.DEAD, feeding = pose == HarvestmanEntity.FEED, holding = pose == HarvestmanEntity.HOLD;
        feedArms = feeding || holding;
        // a hit jolts it
        if (flinchesSeen >= 0 && e.flinches() != flinchesSeen) {
            jolt = 1;
        }
        flinchesSeen = e.flinches();
        jolt = Math.max(0, jolt - dt * 2.4);
        double j = Math.sin(Math.min(1, jolt) * Math.PI);
        rear = Mth.lerp(1 - Math.exp(-dt * 10), rear, e.rear());
        double pitchWant = dead ? 0.05 : feedArms ? 0.0 : 0.1 - 0.42 * rear - 0.14 * j;
        pitch = Mth.lerp(1 - Math.exp(-dt * 8), pitch, pitchWant);
        deadRoll = Mth.lerp(1 - Math.exp(-dt * 1.5), deadRoll, dead ? 0.22 : 0);
        camo = (float) Mth.lerp(1 - Math.exp(-dt * 6), camo, e.camo());
        glowPulse = feeding ? (float) (0.6 + 0.4 * Math.sin(t * 7.5)) : 1f;

        // the feet as the server planted them, as far behind as the body is drawn
        Vec3[] feet = new Vec3[4];
        for (int i = 0; i < 4; i++) {
            Vec3 a = e.feetSeen[i][(e.feetSeenAt - DELAY - 1) & 3], b = e.feetSeen[i][(e.feetSeenAt - DELAY) & 3];
            feet[i] = a != null && b != null ? a.lerp(b, partialTick) : e.foot(i);
        }
        // the body: risen as it rears, dipped by a jolt, bobbing as it walks or works at what it holds
        boolean walking = !dead && e.position().distanceToSqr(new Vec3(e.xo, e.yo, e.zo)) > 0.0004;
        double bob = dead ? 0 : feeding ? 0.02 * Math.sin(t * 7.5) : walking ? 0.05 * Math.sin(t * 3.4) : 0.03 * Math.sin(t * 1.2);
        double sway = dead ? 0 : 0.02 * Math.sin(t * 0.8);
        Vec3 o = centre.add(up.scale(bob + 0.28 * rear - 0.28 * j));
        Vec3[] ax = axes(fx, up, fz, pitch + (walking ? 0.025 * Math.sin(t * 1.7) : 0), sway * 0.5, sway + deadRoll);
        HarvestmanBody.Frame frame = new HarvestmanBody.Frame(o, ax[0], ax[1], ax[2]);
        set(body, o, ax[0], ax[1], ax[2], drawnAt);

        int nowLost = e.lost();
        for (int li = 0; li < 4; li++) {
            HarvestmanBody.Bent b;
            if ((nowLost >> li & 1) != 0) {
                b = HarvestmanEntity.dangle(frame, up, li);
                // the stump twitches
                Vec3 tw = up.scale(0.12 * Math.sin(t * 9 + li * 2) * (dead ? 0 : 1));
                b = new HarvestmanBody.Bent(b.hip(), b.knee().add(tw), b.knee().add(tw), b.n());
            } else {
                b = HarvestmanBody.bend(frame, up, li, feet[li], up);
            }
            Vec3 tx = norm(b.knee().subtract(b.hip())), sx = norm(b.tip().subtract(b.knee()));
            if (tx.lengthSqr() < 0.5) {
                tx = fx;
            }
            if (sx.lengthSqr() < 0.5) {
                sx = tx;
            }
            set(thigh[li], b.hip(), tx, b.n().cross(tx), b.n(), drawnAt);
            // just shot off: the lower leg flies from where it was
            if (lostSeen >= 0 && (nowLost >> li & 1) != 0 && (lostSeen >> li & 1) == 0) {
                Flying f = new Flying(li);
                f.pos = b.knee();
                Matrix4f m = new Matrix4f(shin[li]);
                f.turn.setFromNormalized(m);
                Vec3 out = norm(b.knee().subtract(centre));
                f.vel = out.scale(3 + e.getRandom().nextDouble() * 2).add(0, 3.5, 0);
                f.spin.set((float) e.getRandom().nextGaussian() * 6, (float) e.getRandom().nextGaussian() * 6, (float) e.getRandom().nextGaussian() * 6);
                flying.add(f);
            }
            if ((nowLost >> li & 1) == 0) {
                set(shin[li], b.knee(), sx, b.n().cross(sx), b.n(), drawnAt);
            }
        }
        lostSeen = nowLost;
        lost = nowLost;
        fly(level, e, dt, drawnAt);
    }

    /** The flying lower legs fall, tumble, land, lie a minute and fade. */
    private void fly(Level level, HarvestmanEntity e, double dt, Vec3 drawnAt) {
        for (int k = flying.size() - 1; k >= 0; k--) {
            Flying f = flying.get(k);
            f.age += dt;
            if (!f.down) {
                Vec3 next = f.pos.add(f.vel.scale(dt));
                BlockHitResult hit = level.clip(new ClipContext(f.pos, next.add(0, -0.15, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
                if (hit.getType() != HitResult.Type.MISS) {
                    // it lands and lies along the floor
                    f.pos = hit.getLocation().add(0, 0.12, 0);
                    f.down = true;
                    Vector3f along = f.turn.transform(new Vector3f(1, 0, 0));
                    Vec3 flat = norm(new Vec3(along.x, 0, along.z));
                    if (flat.lengthSqr() < 0.5) {
                        flat = new Vec3(1, 0, 0);
                    }
                    Vec3 side = new Vec3(0, 1, 0).cross(flat);
                    // lying on its side, the bow along the floor
                    f.turn.setFromNormalized(new org.joml.Matrix3f(
                            new Vector3f((float) flat.x, (float) flat.y, (float) flat.z),
                            new Vector3f((float) side.x, (float) side.y, (float) side.z),
                            new Vector3f(0, 1, 0)));
                } else {
                    f.pos = next;
                    f.vel = f.vel.add(0, -12 * dt, 0);
                    f.turn.rotateXYZ(f.spin.x * (float) dt, f.spin.y * (float) dt, f.spin.z * (float) dt);
                }
            }
            if (f.age > 60) {
                f.fade = (float) Math.max(0, 1 - (f.age - 60) / 3);
            }
            if (f.age > 63 || f.pos.distanceToSqr(e.position()) > 64 * 64) {
                flying.remove(k);
                continue;
            }
            Vector3f x = f.turn.transform(new Vector3f(1, 0, 0)), y = f.turn.transform(new Vector3f(0, 1, 0)), z = f.turn.transform(new Vector3f(0, 0, 1));
            set(f.at, f.pos, new Vec3(x.x, x.y, x.z), new Vec3(y.x, y.y, y.z), new Vec3(z.x, z.y, z.z), drawnAt);
        }
    }

    /** The body's axes: the flat frame turned by a yaw, a pitch (nose down) and a roll, as the preview's rot(). */
    private static Vec3[] axes(Vec3 fx, Vec3 fy, Vec3 fz, double pitch, double yaw, double roll) {
        double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch), cr = Math.cos(roll), sr = Math.sin(roll);
        double[][] m = {
                {cy * cr + sy * sp * sr, -cy * sr + sy * sp * cr, sy * cp},
                {cp * sr, cp * cr, -sp},
                {-sy * cr + cy * sp * sr, sy * sr + cy * sp * cr, cy * cp}};
        Vec3[] out = new Vec3[3];
        for (int c = 0; c < 3; c++) {
            out[c] = fx.scale(m[0][c]).add(fy.scale(m[1][c])).add(fz.scale(m[2][c]));
        }
        return out;
    }

    private static void set(Matrix4f m, Vec3 o, Vec3 x, Vec3 y, Vec3 z, Vec3 drawnAt) {
        m.set((float) x.x, (float) x.y, (float) x.z, 0f,
                (float) y.x, (float) y.y, (float) y.z, 0f,
                (float) z.x, (float) z.y, (float) z.z, 0f,
                (float) (o.x - drawnAt.x), (float) (o.y - drawnAt.y), (float) (o.z - drawnAt.z), 1f);
    }

    // ------------------------------------------------------------------------------------------------ its eyes

    /** Three small red eyes down each side of its front, in the body's frame: centre and size. */
    public static final float[][] EYES;

    static {
        double[][] row = {{0.83, 1.22, 0.032}, {0.875, 1.02, 0.026}, {0.915, 1.18, 0.021}};
        EYES = new float[6][];
        int k = 0;
        for (int side = -1; side <= 1; side += 2) {
            for (double[] r : row) {
                double[] p = shell(side * r[1], r[0], 0.075);
                EYES[k++] = new float[] {(float) p[0], (float) p[1], (float) p[2], (float) r[2]};
            }
        }
    }

    /** A point on the hull (the preview's b7Pt), off it along its normal by off. */
    private static double[] shell(double a, double s, double off) {
        double[] p = b7(a, s), e1 = b7(a, s + 1e-3), e0 = b7(a, s - 1e-3), a1 = b7(a + 1e-3, s), a0 = b7(a - 1e-3, s);
        double[] ds = {e1[0] - e0[0], e1[1] - e0[1], e1[2] - e0[2]}, da = {a1[0] - a0[0], a1[1] - a0[1], a1[2] - a0[2]};
        double[] n = {ds[1] * da[2] - ds[2] * da[1], ds[2] * da[0] - ds[0] * da[2], ds[0] * da[1] - ds[1] * da[0]};
        double l = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        double cy = centreLine(s), cz = -1.55 + 2.5 * s;
        if ((p[0]) * n[0] + (p[1] - cy) * n[1] + (p[2] - cz) * n[2] < 0) {
            l = -l;
        }
        return new double[] {p[0] + n[0] / l * off, p[1] + n[1] / l * off, p[2] + n[2] / l * off};
    }

    private static double centreLine(double s) {
        double k = Mth.clamp((s - 0.42) / 0.58, 0, 1);
        return 0.1 + 0.55 * Math.pow(1 - s, 2.2) - 0.5 * k * k * (3 - 2 * k);
    }

    private static double[] b7(double a, double s) {
        s = Mth.clamp(s, 0, 1);
        double e = s < 0.5 ? Math.pow(Math.max(0, s / 0.5), 0.7) : Math.pow(Math.max(0, 1 - (s - 0.5) / 0.5), 0.85);
        double W = 0.52 * e, T = 0.26 * e, Bt = 0.22 * e, c = centreLine(s), z = -1.55 + 2.5 * s, sa = Math.sin(a), ca = Math.cos(a);
        if (ca >= 0) {
            return new double[] {W * Math.signum(sa) * Math.pow(Math.abs(sa), 0.75), c + T * Math.pow(ca, 0.7), z};
        }
        return new double[] {W * Math.signum(sa) * Math.pow(Math.abs(sa), 0.8), c - Bt * Math.pow(-ca, 0.85), z};
    }
}
